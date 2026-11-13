package com.seal.oa.system;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Account-specific data maintenance. All SQL identifiers originate in JDBC metadata. */
@Service
public class DataTableService {
    private final JdbcTemplate jdbc;
    private final SystemService system;
    private final ObjectMapper json;
    private final PasswordEncoder passwords;
    private volatile Catalog cached;
    private record Column(String name, String label, int type, String typeName, int size, int scale,
                          boolean nullable, boolean auto, boolean generated, String defaultValue) {}
    private record Table(String name, String remarks, LinkedHashMap<String, Column> columns, List<String> keys) {}
    private record Catalog(Map<String, Table> tables, List<DataTableRegistry.Relation> relations, long time) {}

    public DataTableService(JdbcTemplate jdbc, SystemService system, ObjectMapper json, PasswordEncoder passwords) {
        this.jdbc = jdbc; this.system = system; this.json = json; this.passwords = passwords;
    }
    private SystemService.UserAccount admin() {
        if (AuthContext.get() == null) throw new ApiException(401, "请先登录");
        var user = system.requireCurrentUser();
        if (!system.isUnrestricted(user.userId())) throw new ApiException(403, "数据表管理仅限系统超级管理员使用");
        return user;
    }
    private boolean fullMaintenance() {
        // Resolve the account from the authenticated user ID, never from a submitted username/phone.
        return "13610316814".equals(admin().username());
    }
    private static String q(String name) {
        if (name == null || !name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) throw new ApiException(400, "无效的表或字段");
        return "`" + name + "`";
    }
    private synchronized Catalog catalog() {
        if (cached != null && System.currentTimeMillis() - cached.time < 30000) return cached;
        cached = jdbc.execute((ConnectionCallback<Catalog>) connection -> {
            var meta = connection.getMetaData();
            String db = connection.getCatalog(), schema = connection.getSchema();
            Map<String, Table> tables = new TreeMap<>();
            List<DataTableRegistry.Relation> relations = new ArrayList<>();
            try (var rs = meta.getTables(db, schema, "%", new String[]{"TABLE", "BASE TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                    if (!name.matches("[a-z_][a-z0-9_]*")) continue;
                    tables.put(name, new Table(name, rs.getString("REMARKS"), new LinkedHashMap<>(), new ArrayList<>()));
                }
            }
            for (Table table : tables.values()) {
                try (var rs = meta.getColumns(db, schema, table.name, "%")) {
                    while (rs.next()) {
                        String name = rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                        table.columns.put(name, new Column(name, DataTableRegistry.fieldLabel(name, rs.getString("REMARKS")),
                            rs.getInt("DATA_TYPE"), rs.getString("TYPE_NAME"), rs.getInt("COLUMN_SIZE"), rs.getInt("DECIMAL_DIGITS"),
                            rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls, "YES".equals(rs.getString("IS_AUTOINCREMENT")),
                            "YES".equals(rs.getString("IS_GENERATEDCOLUMN")), rs.getString("COLUMN_DEF")));
                    }
                }
                TreeMap<Integer, String> keys = new TreeMap<>();
                try (var rs = meta.getPrimaryKeys(db, schema, table.name)) {
                    while (rs.next()) keys.put(rs.getInt("KEY_SEQ"), rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
                table.keys.addAll(keys.values());
                try (var rs = meta.getImportedKeys(db, schema, table.name)) {
                    while (rs.next()) relations.add(new DataTableRegistry.Relation(table.name, rs.getString("FKCOLUMN_NAME").toLowerCase(Locale.ROOT),
                        rs.getString("PKTABLE_NAME").toLowerCase(Locale.ROOT), rs.getString("PKCOLUMN_NAME").toLowerCase(Locale.ROOT)));
                }
            }
            for (var r : DataTableRegistry.RELATIONS) {
                if (tables.containsKey(r.table()) && tables.containsKey(r.target()) && tables.get(r.table()).columns.containsKey(r.column())
                    && tables.get(r.target()).columns.containsKey(r.targetColumn()) && !relations.contains(r)) relations.add(r);
            }
            return new Catalog(tables, relations, System.currentTimeMillis());
        });
        return cached;
    }
    private Table table(String name) {
        admin(); q(name);
        Table t = catalog().tables.get(name);
        if (t == null) throw new ApiException(404, "数据表不存在");
        return t;
    }
    /* A table without a proven tenant path is never implicitly treated as global. */
    private String scope(Table t, String alias, Set<String> visited, long tenant) {
        if (!visited.add(t.name)) return null;
        if (t.columns.containsKey("tenant_id")) return alias + ".`tenant_id`=" + tenant;
        if (DataTableRegistry.GLOBAL.contains(t.name)) return "1=1";
        if (t.name.equals("oa_finance_history")) {
            List<String> terms = new ArrayList<>();
            for (var e : Map.of("record","oa_finance_record","receivable","oa_finance_receivable","collection","oa_finance_collection").entrySet()) {
                Table parent = catalog().tables.get(e.getValue()); if (parent == null) continue;
                String pa = alias + "h", ps = scope(parent,pa,new HashSet<>(visited),tenant);
                if (ps != null) terms.add("(" + alias + ".`entity_type`='" + e.getKey() + "' AND EXISTS (SELECT 1 FROM " + q(parent.name) + " " + pa + " WHERE " + pa + ".`id`=" + alias + ".`entity_id` AND " + ps + "))");
            }
            return terms.isEmpty() ? null : "(" + String.join(" OR ",terms) + ")";
        }
        // Attachments and audit events can have no employee ID (e.g. payroll files).
        String owner = switch(t.name) {case "oa_hr_attachment", "oa_hr_payroll", "oa_hr_attendance_batch", "oa_recruit_approval" -> "create_by"; case "oa_hr_history", "oa_recruit_event" -> "operator_id"; default -> null;};
        if (owner != null) return "EXISTS (SELECT 1 FROM sys_user " + alias + "u WHERE " + alias + "u.user_id=" + alias + "." + q(owner) + " AND " + alias + "u.tenant_id=" + tenant + ")";
        for (var r : catalog().relations) {
            if (!r.table().equals(t.name) || visited.contains(r.target())) continue;
            Table parent = catalog().tables.get(r.target());
            String pa = alias + "p", parentScope = scope(parent, pa, new HashSet<>(visited),tenant);
            if (parentScope != null && !"1=1".equals(parentScope))
                return "EXISTS (SELECT 1 FROM " + q(parent.name) + " " + pa + " WHERE " + pa + "." + q(r.targetColumn()) + "=" + alias + "." + q(r.column()) + " AND " + parentScope + ")";
        }
        return null;
    }
    private String scope(Table t) { return scope(t, "d", new HashSet<>(),admin().tenantId()); }
    private String requiredScope(Table t) {
        String sql = scope(t);
        if (sql == null) throw new ApiException(403, "此表的企业归属尚未登记，暂仅开放结构查看");
        return sql;
    }
    private boolean hidden(Table t, Column c) {
        return DataTableRegistry.sensitive(c.name) || (t.name.equals("sys_config") && c.name.equals("config_value"))
            || Set.of(Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB).contains(c.type);
    }
    private Set<String> editable(Table t) {
        return writable(t,false,fullMaintenance());
    }
    private Set<String> writable(Table t, boolean create, boolean full) {
        if (full) return t.columns.values().stream()
            .filter(c -> !c.auto && !c.generated && !c.name.equals("tenant_id") && (create || !t.keys.contains(c.name)))
            .filter(c -> !Set.of(Types.BINARY,Types.VARBINARY,Types.LONGVARBINARY,Types.BLOB).contains(c.type))
            .map(c -> c.name).collect(Collectors.toSet());
        return DataTableRegistry.EDITABLE.getOrDefault(t.name, Set.of()).stream()
            .filter(t.columns::containsKey).filter(n -> !t.keys.contains(n) && !hidden(t, t.columns.get(n))).collect(Collectors.toSet());
    }
    private boolean canCreate(Table t, boolean full) {
        return !t.keys.isEmpty() && (full || DataTableRegistry.CRUD.contains(t.name)) && !t.keys.contains("tenant_id");
    }
    private boolean canDelete(Table t, boolean full) {
        return !t.keys.isEmpty() && (full || DataTableRegistry.CRUD.contains(t.name));
    }
    public List<Map<String,Object>> list() {
        long tenant = admin().tenantId();
        List<Map<String,Object>> result = new ArrayList<>();
        for (var t : catalog().tables.values()) {
            var desc = DataTableRegistry.describe(t.name, t.remarks);
            String scope = scope(t,"d",new HashSet<>(),tenant);
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("name", t.name); item.put("label", desc.label()); item.put("group", desc.group()); item.put("core", desc.core());
            item.put("business", desc.route()); item.put("registered", DataTableRegistry.TABLES.containsKey(t.name));
            item.put("legacy", DataTableRegistry.LEGACY.contains(t.name)); item.put("readable", scope != null);
            item.put("scope", scope == null ? "企业归属待登记" : DataTableRegistry.GLOBAL.contains(t.name) ? "全局共享" : "当前企业");
            item.put("count", scope == null ? null : jdbc.queryForObject("SELECT COUNT(*) FROM " + q(t.name) + " d WHERE " + scope, Long.class));
            item.put("editable", !t.keys.isEmpty() && !editable(t).isEmpty()); result.add(item);
        }
        return result;
    }
    public Map<String,Object> detail(String name) {
        Table t = table(name); boolean full = fullMaintenance();
        Set<String> edit = writable(t,false,full), create = writable(t,true,full);
        var columns = t.columns.values().stream().map(c -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("name",c.name); m.put("label",c.label); m.put("type",c.typeName); m.put("size",c.size); m.put("scale",c.scale);
            m.put("nullable",c.nullable); m.put("auto",c.auto); m.put("defaultValue",c.defaultValue); m.put("primary",t.keys.contains(c.name));
            m.put("sensitive",hidden(t,c)); m.put("writeOnly",hidden(t,c) && full && create.contains(c.name));
            m.put("editable",!t.keys.isEmpty() && edit.contains(c.name)); m.put("createEditable",canCreate(t,full) && create.contains(c.name));
            m.put("immutable", !full && (c.name.equals("merchant_id") || c.name.equals("dict_type")));
            m.put("kind",kind(c)); return m;
        }).toList();
        return Map.of("columns",columns,"keys",t.keys,"createAllowed",canCreate(t,full),"fullMaintenance",full,
            "editAllowed",!edit.isEmpty() && !t.keys.isEmpty(),"deleteAllowed",canDelete(t,full),
            "relations",catalog().relations.stream().filter(r -> r.table().equals(name) || r.target().equals(name)).map(r ->
                Map.of("table",r.table(),"column",r.column(),"target",r.target(),"targetColumn",r.targetColumn())).toList());
    }
    private String kind(Column c) {
        return switch (c.type) {
            case Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT, Types.DECIMAL, Types.NUMERIC, Types.FLOAT, Types.REAL, Types.DOUBLE -> "number";
            case Types.DATE -> "date";
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "datetime";
            case Types.BOOLEAN, Types.BIT -> "boolean";
            default -> c.size > 500 || c.type == Types.CLOB || c.type == Types.LONGVARCHAR ? "text" : "string";
        };
    }
    private String select(Table t) {
        // Hidden values participate in concurrency checks but never leave the service.
        return t.columns.values().stream().filter(c -> !Set.of(Types.BINARY,Types.VARBINARY,Types.LONGVARBINARY,Types.BLOB).contains(c.type))
            .map(c -> "d." + q(c.name)).collect(Collectors.joining(","));
    }
    private Column visibleColumn(Table t, String name) {
        Column c = t.columns.get(name);
        if (c == null || hidden(t,c)) throw new ApiException(400,"无效或不可查询的字段");
        return c;
    }
    public Map<String,Object> rows(String name, Map<String,String> query) {
        Table t = table(name); String where = requiredScope(t); List<Object> args = new ArrayList<>();
        if (query.containsKey("filters") && !query.get("filters").isBlank()) {
            List<Map<String,Object>> filters;
            try { filters = json.readValue(query.get("filters"),new TypeReference<>() {}); }
            catch (Exception e) { throw new ApiException(400,"筛选格式错误"); }
            if (filters.size() > 8) throw new ApiException(400,"最多设置8个筛选条件");
            for (var f : filters) {
                Column c = visibleColumn(t, Objects.toString(f.get("field"),"")); String col = "d." + q(c.name);
                String op = Objects.toString(f.get("op"),"eq");
                if (op.equals("null") || op.equals("notnull")) { where += " AND " + col + (op.equals("null") ? " IS NULL" : " IS NOT NULL"); continue; }
                if (op.equals("contains")) {
                    if (!Set.of("string","text").contains(kind(c))) throw new ApiException(400,"包含筛选仅支持文本字段");
                    String v = Objects.toString(f.get("value"),"").replace("!","!!").replace("%","!%").replace("_","!_");
                    where += " AND " + col + " LIKE ? ESCAPE '!'"; args.add("%" + v + "%");
                } else {
                    String operator = switch(op) {case "eq" -> "="; case "ne" -> "<>"; case "gt" -> ">"; case "ge" -> ">="; case "lt" -> "<"; case "le" -> "<="; default -> throw new ApiException(400,"筛选操作无效");};
                    where += " AND " + col + operator + "?"; args.add(convert(c,f.get("value")));
                }
            }
        }
        String sort = query.getOrDefault("sort", t.keys.isEmpty() ? t.columns.keySet().iterator().next() : t.keys.get(0));
        visibleColumn(t,sort); String direction = query.getOrDefault("direction","desc");
        if (!Set.of("asc","desc").contains(direction)) throw new ApiException(400,"排序方向无效");
        int page = integer(query.getOrDefault("page","1"),1,1000000), size = integer(query.getOrDefault("size","20"),1,100);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + q(name) + " d WHERE " + where,Long.class,args.toArray());
        args.add(size); args.add((page-1)*size);
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT " + select(t) + " FROM " + q(name) + " d WHERE " + where + " ORDER BY d." + q(sort) + " " + direction + " LIMIT ? OFFSET ?",args.toArray());
        return Map.of("rows",rows.stream().map(r -> pack(t,r)).toList(),"total",count);
    }
    private static int integer(String value, int min, int max) {
        try { int n = Integer.parseInt(value); if (n >= min && n <= max) return n; } catch (Exception ignored) {}
        throw new ApiException(400,"分页参数无效");
    }
    private Object scalar(Object value) {
        try {
            if (value instanceof Clob c) return c.getSubString(1, Math.toIntExact(c.length()));
            if (value instanceof Long || value instanceof java.math.BigInteger || value instanceof BigDecimal) return value.toString();
            if (value instanceof java.util.Date || value instanceof java.time.temporal.TemporalAccessor) return value.toString();
            return value;
        } catch (Exception e) { throw new ApiException(500,"字段读取失败"); }
    }
    private Object redact(Object value) {
        if (value instanceof Map<?,?> m) {
            Map<String,Object> r = new LinkedHashMap<>(); m.forEach((k,v) -> r.put(k.toString(),DataTableRegistry.sensitive(k.toString()) ? "[已隐藏]" : redact(v))); return r;
        }
        if (value instanceof List<?> l) return l.stream().map(this::redact).toList();
        if (value instanceof String s && (s.stripLeading().startsWith("{") || s.stripLeading().startsWith("["))) {
            try { return json.writeValueAsString(redact(json.readValue(s,Object.class))); } catch (Exception ignored) {}
        }
        return value;
    }
    private Map<String,Object> normalized(Map<String,Object> row, boolean mask) {
        Map<String,Object> result = new TreeMap<>(); row.forEach((k,v) -> result.put(k.toLowerCase(Locale.ROOT),mask ? redact(scalar(v)) : scalar(v))); return result;
    }
    private Map<String,Object> pack(Table t, Map<String,Object> row) {
        Map<String,Object> key = new LinkedHashMap<>(); t.keys.forEach(k -> key.put(k,scalar(row.get(k))));
        return Map.of("values",snapshot(t,row),"key",key,"version",hash(normalized(row,false)));
    }
    private Map<String,Object> snapshot(Table t, Map<String,Object> row) {
        Map<String,Object> visible = new LinkedHashMap<>();
        row.forEach((key,value) -> { Column c = t.columns.get(key.toLowerCase(Locale.ROOT)); if (c != null && !hidden(t,c)) visible.put(key,value); });
        return normalized(visible,true);
    }
    private String stringify(Object object) {
        try { return json.writeValueAsString(object); } catch(Exception e) { throw new ApiException(400,"数据格式错误"); }
    }
    private String hash(Object object) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stringify(object).getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private Object convert(Column c, Object value) {
        if (value == null) { if (!c.nullable) throw new ApiException(400,c.label + "不能为空"); return null; }
        String s = value.toString();
        try {
            if (kind(c).equals("number")) {
                BigDecimal n = new BigDecimal(s);
                if (Set.of(Types.BIGINT,Types.INTEGER,Types.SMALLINT,Types.TINYINT).contains(c.type)) {
                    long l = n.longValueExact();
                    if (c.type == Types.INTEGER) return Math.toIntExact(l);
                    if (c.type == Types.SMALLINT && (l < Short.MIN_VALUE || l > Short.MAX_VALUE)) throw new ArithmeticException();
                    if (c.type == Types.TINYINT && (l < -128 || l > 255)) throw new ArithmeticException();
                    return l;
                }
                if (n.scale() > c.scale || n.precision() - n.scale() > c.size - c.scale) throw new ArithmeticException();
                return n;
            }
            if (kind(c).equals("date")) return java.sql.Date.valueOf(LocalDate.parse(s));
            if (kind(c).equals("datetime")) return Timestamp.valueOf(LocalDateTime.parse(s.replace(' ','T')));
            if (kind(c).equals("boolean")) {
                if (!Set.of("true","false","0","1").contains(s)) throw new IllegalArgumentException();
                return s.equals("true") || s.equals("1");
            }
            if (s.length() > Math.min(c.size,100000)) throw new IllegalArgumentException();
            if (!c.nullable && s.isBlank()) throw new IllegalArgumentException();
            return s;
        } catch (Exception e) { throw new ApiException(400,c.label + "的类型、长度或精度不正确"); }
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> object(Map<String,Object> body,String key) {
        if (!(body.get(key) instanceof Map<?,?> m)) throw new ApiException(400,key + "格式错误");
        return (Map<String,Object>)m;
    }
    private String keyWhere(Table t, Map<String,Object> key, List<Object> args) {
        if (t.keys.isEmpty() || !key.keySet().equals(new HashSet<>(t.keys))) throw new ApiException(400,"主键不完整或数据表不支持维护");
        return t.keys.stream().map(k -> { args.add(convert(t.columns.get(k),key.get(k))); return "d." + q(k) + "=?"; }).collect(Collectors.joining(" AND "));
    }
    private Map<String,Object> current(Table t, Map<String,Object> body, boolean lock) {
        List<Object> args = new ArrayList<>(); String key = keyWhere(t,object(body,"key"),args);
        var rows = jdbc.queryForList("SELECT " + select(t) + " FROM " + q(t.name) + " d WHERE " + requiredScope(t) + " AND " + key + (lock ? " FOR UPDATE" : ""),args.toArray());
        if (rows.isEmpty()) throw new ApiException(404,"数据不存在或不属于当前企业");
        var row = rows.get(0);
        if (!Objects.equals(body.get("version"),hash(normalized(row,false)))) throw new ApiException(409,"数据已被修改，请刷新后重试");
        return row;
    }
    public List<Map<String,Object>> impact(String name,Map<String,Object> body) {
        Table t = table(name); return references(t,current(t,body,false));
    }
    private List<Map<String,Object>> references(Table t, Map<String,Object> row) {
        List<Map<String,Object>> result = new ArrayList<>();
        for (var r : catalog().relations) if (r.target().equals(t.name) && row.get(r.targetColumn()) != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + q(r.table()) + " WHERE " + q(r.column()) + "=?",Long.class,row.get(r.targetColumn()));
            if (count > 0) result.add(Map.of("table",r.table(),"label",DataTableRegistry.describe(r.table(),"").label(),"column",r.column(),"count",count));
        }
        return result;
    }
    private void audit(Table t, String action, Map<String,Object> key, Object before, Object after, String reason) {
        var user = admin();
        jdbc.update("INSERT INTO sys_data_table_history(tenant_id,table_name,action,record_key,before_data,after_data,reason,operator_id,operator_name) VALUES(?,?,?,?,?,?,?,?,?)",
            user.tenantId(),t.name,action,stringify(key),before == null ? null : stringify(before),after == null ? null : stringify(after),reason,user.userId(),user.nickname() == null ? user.username() : user.nickname());
    }
    private String reason(Map<String,Object> body) {
        String r = Objects.toString(body.get("reason"),"").trim();
        if (r.isEmpty() || r.length() > 500) throw new ApiException(400,"请填写500字以内的操作原因");
        return r;
    }
    @Transactional
    public Map<String,Object> save(String name,Map<String,Object> body,boolean create) {
        Table t = table(name); requiredScope(t); String reason = reason(body); boolean full = fullMaintenance();
        Set<String> allowed = writable(t,create,full);
        if (t.keys.isEmpty() || allowed.isEmpty() || (create && !canCreate(t,full))) throw new ApiException(403,"此表不支持当前维护操作");
        Map<String,Object> before = create ? null : current(t,body,true);
        Map<String,Object> input = object(body,"values"), values = new LinkedHashMap<>();
        if (input.isEmpty()) throw new ApiException(400,"没有提交修改内容");
        for (var e : input.entrySet()) {
            if (!allowed.contains(e.getKey())) throw new ApiException(403,"字段不允许维护：" + e.getKey());
            if (!full && !create && Set.of("merchant_id","dict_type").contains(e.getKey()) && !Objects.equals(scalar(before.get(e.getKey())),scalar(convert(t.columns.get(e.getKey()),e.getValue()))))
                throw new ApiException(403,"不允许变更所属客户或字典编码");
            Object value = convert(t.columns.get(e.getKey()),e.getValue());
            if (name.equals("sys_user") && e.getKey().equals("password")) {
                if (value == null || value.toString().isBlank()) throw new ApiException(400,"登录密码不能为空");
                value = passwords.encode(value.toString());
            }
            values.put(e.getKey(),value);
        }
        if (create) {
            if (t.columns.containsKey("tenant_id")) values.put("tenant_id",admin().tenantId());
            for (String field : List.of("create_by","update_by")) if (t.columns.containsKey(field) && !values.containsKey(field))
                values.put(field,convert(t.columns.get(field),kind(t.columns.get(field)).equals("number") ? admin().userId() : admin().username()));
            for (Column c : t.columns.values()) if (!c.nullable && !c.auto && !c.generated && c.defaultValue == null && !values.containsKey(c.name)) throw new ApiException(400,"缺少必填字段：" + c.label);
        } else {
            values.entrySet().removeIf(e -> Objects.equals(scalar(before.get(e.getKey())),scalar(e.getValue())));
            if (values.isEmpty()) throw new ApiException(400,"数据未发生变化");
            if (t.columns.containsKey("revision") && !values.containsKey("revision")) values.put("revision",((Number)before.get("revision")).longValue()+1);
            if (t.columns.containsKey("update_time") && !values.containsKey("update_time")) values.put("update_time",Timestamp.valueOf(LocalDateTime.now()));
            if (t.columns.containsKey("update_by") && !values.containsKey("update_by")) {
                Column c = t.columns.get("update_by"); values.put("update_by",convert(c,kind(c).equals("number") ? admin().userId() : admin().username()));
            }
        }
        Map<String,Object> merged = new LinkedHashMap<>(); if (before != null) merged.putAll(before); merged.putAll(values);
        validate(t,merged,create,full);
        Map<String,Object> key = new LinkedHashMap<>();
        if (create) {
            var holder = new GeneratedKeyHolder();
            String sql = "INSERT INTO " + q(name) + " (" + values.keySet().stream().map(DataTableService::q).collect(Collectors.joining(",")) + ") VALUES (" + String.join(",",Collections.nCopies(values.size(),"?")) + ")";
            jdbc.update(connection -> { var ps = connection.prepareStatement(sql,t.keys.toArray(String[]::new)); int i=1; for (Object v : values.values()) ps.setObject(i++,v); return ps; },holder);
            Map<String,Object> generated = holder.getKeys();
            for (String field : t.keys) {
                Object value = values.get(field);
                if (value == null && generated != null) {
                    value = generated.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(field)).map(Map.Entry::getValue).findFirst().orElse(null);
                    if (value == null && t.keys.size() == 1 && generated.size() == 1) value = generated.values().iterator().next();
                }
                if (value == null) throw new ApiException(400,"无法取得新增记录主键：" + field);
                key.put(field,value);
            }
        } else {
            key.putAll(object(body,"key")); List<Object> args = new ArrayList<>(values.values()); String keySql = keyWhere(t,key,args);
            jdbc.update("UPDATE " + q(name) + " d SET " + values.keySet().stream().map(k -> q(k)+"=?").collect(Collectors.joining(",")) + " WHERE " + requiredScope(t) + " AND " + keySql,args.toArray());
        }
        List<Object> args = new ArrayList<>(); String keySql = keyWhere(t,key,args);
        var saved = jdbc.queryForList("SELECT " + select(t) + " FROM " + q(name) + " d WHERE " + requiredScope(t) + " AND " + keySql,args.toArray());
        if (saved.isEmpty()) throw new ApiException(400,"保存后数据超出当前企业范围，已撤销本次操作");
        var after = saved.get(0);
        Map<String,Object> auditBefore = before == null ? null : snapshot(t,before), auditAfter = snapshot(t,after);
        for (String field : input.keySet()) if (hidden(t,t.columns.get(field))) {
            if (auditBefore != null) auditBefore.put(field,"[已隐藏]");
            auditAfter.put(field,"[已更新]");
        }
        audit(t,create ? "新增" : "修改",normalized(key,true),auditBefore,auditAfter,reason);
        return pack(t,after);
    }
    private void validate(Table t, Map<String,Object> row, boolean create, boolean full) {
        for (var r : catalog().relations) if (r.table().equals(t.name) && row.get(r.column()) != null) {
            Table parent = catalog().tables.get(r.target()); String scope = requiredScope(parent);
            var parents = jdbc.queryForList("SELECT d." + q(r.targetColumn()) + " FROM " + q(r.target()) + " d WHERE " + scope + " AND d." + q(r.targetColumn()) + "=? FOR UPDATE",row.get(r.column()));
            if (parents.isEmpty()) throw new ApiException(400,"关联数据不存在或不属于当前企业：" + r.column());
        }
        if (full) return;
        if (t.name.equals("oa_merchant_contact") && "1".equals(Objects.toString(row.get("primary_contact"),""))) {
            // Updating other rows implicitly would omit their individual before/after audit. Require an explicit edit instead.
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM oa_merchant_contact WHERE merchant_id=? AND primary_contact=1" + (create ? "" : " AND id<>?"),Long.class,
                create ? new Object[]{row.get("merchant_id")} : new Object[]{row.get("merchant_id"),row.get("id")});
            if (count > 0) throw new ApiException(400,"该客户已有主要联系人，请先将原联系人设为非主要联系人");
        }
        for (String field : switch(t.name) {case "oa_merchant" -> List.of("name","tax_number"); case "oa_recruit_talent" -> List.of("phone"); default -> List.<String>of();}) {
            Object value = row.get(field); if (value == null || value.toString().isBlank()) continue;
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + q(t.name) + " d WHERE " + requiredScope(t) + " AND " + q(field) + "=? AND id<>?",Long.class,value,row.get("id"));
            if (count > 0) throw new ApiException(400,DataTableRegistry.fieldLabel(field,"") + "已存在");
        }
        for (String field : List.of("primary_contact","experience_years","expected_salary","dict_sort"))
            if (row.get(field) != null && new BigDecimal(row.get(field).toString()).signum() < 0) throw new ApiException(400,field+"不能为负数");
        if (t.columns.containsKey("primary_contact") && row.get("primary_contact") != null && !Set.of("0","1").contains(row.get("primary_contact").toString())) throw new ApiException(400,"主要联系人仅允许0或1");
        if (DataTableRegistry.CRUD.contains(t.name) && t.columns.containsKey("status") && row.get("status") != null && !Set.of("0","1").contains(row.get("status").toString())) throw new ApiException(400,"状态仅允许0或1");
        if (t.name.equals("sys_dict_data")) {
            if (row.get("is_default") != null && !Set.of("Y","N").contains(row.get("is_default").toString())) throw new ApiException(400,"默认选项仅允许Y或N");
            List<Object> args = new ArrayList<>(Arrays.asList(row.get("dict_type"),row.get("dict_value")));
            String sql = "SELECT COUNT(*) FROM sys_dict_data WHERE dict_type=? AND dict_value=?";
            if (!create) { sql += " AND dict_code<>?"; args.add(row.get("dict_code")); }
            if (jdbc.queryForObject(sql,Long.class,args.toArray()) > 0) throw new ApiException(400,"同一字典下的选项值不能重复");
        }
    }
    @Transactional
    public void delete(String name,Map<String,Object> body) {
        Table t = table(name); String reason = reason(body);
        if (!canDelete(t,fullMaintenance())) throw new ApiException(403,"此表不支持当前删除操作");
        var before = current(t,body,true);
        if (!references(t,before).isEmpty()) throw new ApiException(409,"存在关联数据，不能直接删除，请先在业务页面处理关联");
        List<Object> args = new ArrayList<>(); String key = keyWhere(t,object(body,"key"),args);
        jdbc.update("DELETE FROM " + q(name) + " d WHERE " + requiredScope(t) + " AND " + key,args.toArray());
        audit(t,"删除",object(body,"key"),snapshot(t,before),null,reason);
    }
    public Map<String,Object> history(String name,int page,int size) {
        table(name); var tenant = admin().tenantId(); page = integer(""+page,1,1000000); size = integer(""+size,1,100);
        var rows = jdbc.queryForList("SELECT * FROM sys_data_table_history WHERE tenant_id=? AND table_name=? ORDER BY id DESC LIMIT ? OFFSET ?",tenant,name,size,(page-1)*size);
        return Map.of("rows",rows.stream().map(r -> normalized(r,true)).toList(),"total",jdbc.queryForObject("SELECT COUNT(*) FROM sys_data_table_history WHERE tenant_id=? AND table_name=?",Long.class,tenant,name));
    }
}
