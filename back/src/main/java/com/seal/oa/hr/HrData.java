package com.seal.oa.hr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.system.DataScopeService;
import com.seal.oa.system.SystemService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Component
public class HrData {
    public static final long EMPLOYEES = 341002,
            CHANGES = 341003,
            ATTENDANCE = 341004,
            PAYROLL = 341005,
            SELF = 341006,
            FINANCE = 341409;
    final JdbcTemplate jdbc;
    final ObjectMapper mapper;
    final SystemService system;
    final DataScopeService scope;

    public HrData(
            JdbcTemplate jdbc, ObjectMapper mapper, SystemService system, DataScopeService scope) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.system = system;
        this.scope = scope;
    }

    public boolean allowed(long module, String action) {
        return system.hasModuleAction(AuthContext.userId(), module, action);
    }

    public void require(long module, String action) {
        if (!allowed(module, action)) throw new ApiException(403, "没有该操作权限");
    }

    List<Map<String, Object>> rows(String sql, Object... args) {
        return JdbcMaps.camelCase(jdbc.queryForList(sql, args));
    }

    Map<String, Object> one(String sql, Object... args) {
        var rows = rows(sql, args);
        if (rows.isEmpty()) throw new ApiException(404, "记录不存在");
        return rows.get(0);
    }

    long insert(String sql, Object... args) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(
                connection -> {
                    var p = connection.prepareStatement(sql, new String[] {"id"});
                    for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
                    return p;
                },
                keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    Map<String, Object> employee(long user) {
        var row =
                one(
                        "SELECT"
                            + " u.user_id,u.nick_name,u.phonenumber,u.email,u.dept_id,u.del_flag,u.status,d.dept_name,e.*"
                            + " FROM sys_user u JOIN oa_hr_employee e ON e.user_id=u.user_id LEFT"
                            + " JOIN sys_dept d ON d.dept_id=u.dept_id WHERE u.user_id=?",
                        user);
        if (!scope.canAccess(AuthContext.userId(), row.get("deptId"), user))
            throw new ApiException(403, "员工不在数据范围内");
        return row;
    }

    void revision(Map<String, Object> row, Map<String, Object> body) {
        if (!body.containsKey("revision") || id(body.get("revision")) != id(row.get("revision")))
            throw new ApiException(409, "数据版本已变化，请刷新后重试");
    }

    void expect(Map<String, Object> row, String... states) {
        if (!Set.of(states).contains(text(row.get("status"))))
            throw new ApiException(409, "当前状态不允许此操作");
    }

    String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ApiException(400, "数据不能序列化");
        }
    }

    Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> m) {
            var result = new LinkedHashMap<String, Object>();
            m.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        try {
            return mapper.readValue(
                    String.valueOf(value), new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            throw new ApiException(400, "记录内容无效");
        }
    }

    List<Map<String, Object>> records(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty() || list.size() > 5000)
            throw new ApiException(400, "请上传1至5000行有效数据");
        return list.stream().map(this::object).toList();
    }

    void history(String type, long record, Long user, String action, Object details) {
        insert(
                "INSERT INTO"
                    + " oa_hr_history(entity_type,entity_id,user_id,action,detail_json,operator_id)"
                    + " VALUES(?,?,?,?,?,?)",
                type,
                record,
                user,
                action,
                json(details),
                AuthContext.userId());
    }

    long event(
            long user,
            String type,
            LocalDate effective,
            String reason,
            Object before,
            Object after,
            String status) {
        return insert(
                "INSERT INTO"
                    + " oa_hr_event(user_id,event_type,effective_date,reason,before_json,after_json,status,operator_id)"
                    + " VALUES(?,?,?,?,?,?,?,?)",
                user,
                type,
                effective,
                reason,
                json(before),
                json(after),
                status,
                AuthContext.userId());
    }

    void todo(long user, String title, String type, long business, String key) {
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oa_todo WHERE task_id=? AND user_id=? AND"
                                + " status='todo'",
                        Long.class,
                        key,
                        user)
                == 0)
            jdbc.update(
                    "INSERT INTO oa_todo(user_id,title,business_type,business_id,task_id)"
                            + " VALUES(?,?,?,?,?)",
                    user,
                    title,
                    type,
                    business,
                    key);
    }

    void closeTodos(String key) {
        jdbc.update("UPDATE oa_todo SET status='done' WHERE task_id=?", key);
    }

    static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    static long id(Object value) {
        try {
            return new BigDecimal(text(value)).longValueExact();
        } catch (Exception ex) {
            throw new ApiException(400, "编号或版本无效");
        }
    }

    static long optionalId(Object value) {
        return text(value).isEmpty() ? 0 : id(value);
    }

    static String required(Object value, String label, int max) {
        String t = text(value);
        if (t.isEmpty() || t.length() > max)
            throw new ApiException(400, label + "不能为空且不能超过" + max + "字");
        return t;
    }

    static String limited(Object value, int max) {
        String t = text(value);
        if (t.length() > max) throw new ApiException(400, "文本过长");
        return t;
    }

    static LocalDate date(Object value) {
        try {
            return LocalDate.parse(text(value).substring(0, 10));
        } catch (Exception ex) {
            throw new ApiException(400, "日期格式应为YYYY-MM-DD");
        }
    }

    static LocalDate nullableDate(Object value) {
        return text(value).isEmpty() ? null : date(value);
    }

    static String period(Object value) {
        try {
            return YearMonth.parse(text(value)).toString();
        } catch (Exception ex) {
            throw new ApiException(400, "月份格式应为YYYY-MM");
        }
    }

    static BigDecimal number(Object value, String label) {
        try {
            BigDecimal n = new BigDecimal(text(value));
            if (n.abs().compareTo(new BigDecimal("999999999999")) > 0 || n.scale() > 6)
                throw new Exception();
            return n;
        } catch (Exception ex) {
            throw new ApiException(400, label + "必须是有效数字");
        }
    }

    static BigDecimal nonnegative(Object value, String label) {
        var n = number(value, label);
        if (n.signum() < 0) throw new ApiException(400, label + "不能为负数");
        return n;
    }

    static BigDecimal currency(Object value, String label) {
        var n = number(value, label);
        if (n.stripTrailingZeros().scale() > 2) throw new ApiException(400, label + "最多保留两位小数");
        return money(n);
    }

    static BigDecimal optionalMoney(Object value) {
        return text(value).isEmpty() ? BigDecimal.ZERO : number(value, "金额");
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
