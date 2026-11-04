package com.seal.oa.oa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.system.DataScopeService;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/project/business")
@RequiresModuleAction(moduleId = 340590L)
public class BusinessQueryController {

    private static final String MODULE = "/project/business/list";
    // Department names are editable; opportunity ownership uses the stable department ID.
    private static final long CUSTOMER_SALES_DEPARTMENT_ID = 326L;
    private static final List<String> ACTIVE_STAGES = List.of("初步接洽", "需求确认", "方案报价", "商务谈判");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ParityRecordService parityRecords;
    private final DataScopeService dataScopeService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public BusinessQueryController(
            ParityRecordService parityRecords,
            DataScopeService dataScopeService,
            JdbcTemplate jdbc,
            ObjectMapper objectMapper
    ) {
        this.parityRecords = parityRecords;
        this.dataScopeService = dataScopeService;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/list")
    @RequiresModuleAction(moduleId = 340590L, action = "access")
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        List<Map<String, Object>> rows = parityRecords.rows(MODULE).stream()
                .filter(row -> contains(row, "name", query.get("name")))
                .filter(row -> contains(row, "code", query.get("code")))
                .filter(row -> contains(row, "customerId", query.get("customerId")))
                .filter(row -> contains(row, "customerName", query.get("customerName")))
                .filter(row -> contains(row, "stage", query.get("stage")))
                .filter(row -> contains(row, "ownerUserName", query.get("ownerUserName")))
                .filter(row -> searchAll(row, query.get("queryAll")))
                .toList();
        applyOwnerDepartments(rows);
        rows = dataScopeService.filterRows(
                AuthContext.userId(),
                rows,
                List.of("deptId", "dept_id"),
                List.of("createBy", "create_by", "ownerUserName", "ownerUserId")
        );
        int pageSize = Math.min(Math.max(integer(query.get("pageSize"), 10), 1), 200);
        int pageNum = Math.max(integer(query.get("pageNum"), 1), 1);
        int from = Math.min((pageNum - 1) * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        return ApiResult.page(rows.subList(from, to), rows.size());
    }

    @GetMapping("/owner/options")
    @RequiresModuleAction(moduleId = 340590L, action = "access")
    public Map<String, Object> ownerOptions(@RequestParam Map<String, String> query) {
        StringBuilder where = new StringBuilder("WHERE u.del_flag = '0' AND u.status = '0' AND u.dept_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(CUSTOMER_SALES_DEPARTMENT_ID);
        if (query.get("keyword") != null && !query.get("keyword").isBlank()) {
            where.append(" AND (u.nick_name LIKE ? OR u.user_name LIKE ?)");
            args.add("%" + query.get("keyword").trim() + "%");
            args.add("%" + query.get("keyword").trim() + "%");
        }
        where.append(dataScopeService.userFilter(AuthContext.userId(), args));
        List<Map<String, Object>> owners = jdbc.queryForList("""
                SELECT u.user_id, u.user_name, u.nick_name, u.dept_id, d.dept_name
                FROM sys_user u JOIN sys_dept d ON d.dept_id = u.dept_id
                %s ORDER BY u.nick_name, u.user_id
                """.formatted(where), args.toArray()).stream()
                .map(row -> {
                    Map<String, Object> owner = new LinkedHashMap<>();
                    owner.put("userId", row.get("user_id"));
                    owner.put("userName", row.get("user_name"));
                    owner.put("nickName", row.get("nick_name"));
                    owner.put("deptId", row.get("dept_id"));
                    owner.put("deptName", row.get("dept_name"));
                    return owner;
                })
                .toList();
        return ApiResult.ok(owners);
    }

    @GetMapping("/{id}")
    @RequiresModuleAction(moduleId = 340590L, action = "access")
    public Map<String, Object> detail(@PathVariable String id) {
        return ApiResult.ok(requireAccessibleOpportunity(id));
    }

    @PostMapping
    @RequiresModuleAction(moduleId = 340590L, action = "add")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        long id = parityRecords.rows(MODULE).stream()
                .map(row -> number(row.get("id"), 0L))
                .max(Long::compareTo)
                .orElse(0L) + 1L;
        Map<String, Object> payload = new LinkedHashMap<>(body);
        payload.put("id", id);
        payload.putIfAbsent("code", "SJ-" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        payload.put("stage", ACTIVE_STAGES.get(0));
        payload.put("businessState", ACTIVE_STAGES.get(0));
        assignSalesOwner(payload);
        payload.put("createBy", AuthContext.username());
        payload.put("createTime", LocalDateTime.now().toString());
        payload.put("sealBusinessStages", List.of(Map.of(
                "state", ACTIVE_STAGES.get(0),
                "recordTime", payload.get("createTime"),
                "remark", value(body.get("remark"), ""),
                "attachment", ""
        )));
        insertRecord(payload);
        return ApiResult.ok(id);
    }

    @PutMapping
    @RequiresModuleAction(moduleId = 340590L, action = "edit")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> update(@RequestBody Map<String, Object> body) {
        long id = number(body.get("id"), 0L);
        Map<String, Object> existing = lockOpportunity(id);
        String current = currentStage(existing);
        for (String key : List.of("stage", "businessState")) {
            if (body.containsKey(key) && !current.equals(body.get(key))) {
                throw new ApiException(400, "商机阶段只能通过阶段推进按顺序更新，请刷新后重试");
            }
        }
        Map<String, Object> changes = new LinkedHashMap<>(body);
        // Ordinary editing must never replace the stage history loaded before another progression.
        for (String key : List.of("stage", "businessState", "sealBusinessStages")) {
            changes.remove(key);
        }
        existing.putAll(changes);
        assignSalesOwner(existing);
        existing.put("id", id);
        replaceRecord(id, existing);
        return ApiResult.ok(id);
    }

    @DeleteMapping("/{id}")
    @RequiresModuleAction(moduleId = 340590L, action = "remove")
    public Map<String, Object> delete(@PathVariable String id) {
        requireAccessibleOpportunity(id);
        jdbc.update("""
                DELETE FROM biz_record
                WHERE module = ? AND (business_key = ? OR payload_json LIKE ?)
                """, MODULE, MODULE + ":" + id, "%\"id\":" + id + ",%");
        return ApiResult.ok();
    }

    @PostMapping("/stage/insert")
    @RequiresModuleAction(moduleId = 340590L, action = "track")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> addStage(@RequestBody Map<String, Object> body) {
        long businessId = number(body.get("businessId"), 0L);
        Map<String, Object> opportunity;
        if (businessId > 0L) {
            opportunity = lockOpportunity(businessId);
        } else {
            String code = value(body.get("businessCode"), "");
            List<Map<String, Object>> matches = parityRecords.rows(MODULE).stream()
                    .filter(this::canAccess)
                    .filter(row -> code.equals(value(row.get("code"), "")))
                    .toList();
            if (matches.isEmpty()) throw new ApiException(404, "商机不存在");
            if (matches.size() > 1) throw new ApiException(400, "商机编码重复，请刷新页面后重试");
            opportunity = lockOpportunity(number(matches.get(0).get("id"), 0L));
        }
        String current = currentStage(opportunity);
        String target = value(body.get("state"), "");
        int currentIndex = ACTIVE_STAGES.indexOf(current);
        boolean allowed = currentIndex >= 0 && (currentIndex < ACTIVE_STAGES.size() - 1
                ? ACTIVE_STAGES.get(currentIndex + 1).equals(target)
                : List.of("赢单", "输单").contains(target));
        if (!allowed) {
            throw new ApiException(400, "商机只能按顺序推进到下一阶段，不允许跳级、回退或再次推进已结束的商机");
        }
        if (value(body.get("recordTime"), "").isBlank()
                || value(body.get("remark"), "").isBlank()) {
            throw new ApiException(400, "请填写活动时间和活动事件");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = opportunity.get("sealBusinessStages")
                instanceof List<?> values
                ? new ArrayList<>((List<Map<String, Object>>) values)
                : new ArrayList<>();
        stages.add(new LinkedHashMap<>(body));
        opportunity.put("sealBusinessStages", stages);
        opportunity.put("stage", target);
        opportunity.put("businessState", target);
        replaceRecord(number(opportunity.get("id"), 0L), opportunity);
        return ApiResult.ok();
    }

    @DeleteMapping("/stage/{stageId}")
    @RequiresModuleAction(moduleId = 340590L, action = "edit")
    public Map<String, Object> deleteStage(@PathVariable Long stageId) {
        for (Map<String, Object> opportunity : parityRecords.rows(MODULE)) {
            if (!canAccess(opportunity)) {
                continue;
            }
            if (!(opportunity.get("sealBusinessStages") instanceof List<?> values)) {
                continue;
            }
            List<Map<String, Object>> stages = new ArrayList<>();
            for (Object raw : values) {
                if (raw instanceof Map<?, ?> stage
                        && number(stage.get("id"), 0L) != stageId) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) stage);
                    stages.add(copy);
                }
            }
            if (stages.size() != values.size()) {
                opportunity.put("sealBusinessStages", stages);
                replaceRecord(number(opportunity.get("id"), 0L), opportunity);
                return ApiResult.ok();
            }
        }
        return ApiResult.ok();
    }

    private String currentStage(Map<String, Object> opportunity) {
        for (String key : List.of("stage", "businessState")) {
            String stage = value(opportunity.get(key), "");
            if (!stage.isBlank()) return stage;
        }
        if (opportunity.get("sealBusinessStages") instanceof List<?> stages && !stages.isEmpty()
                && stages.get(stages.size() - 1) instanceof Map<?, ?> last) {
            return value(last.get("state"), ACTIVE_STAGES.get(0));
        }
        return ACTIVE_STAGES.get(0);
    }

    private Map<String, Object> lockOpportunity(long id) {
        requireAccessibleOpportunity(String.valueOf(id));
        jdbc.queryForList("""
                SELECT id FROM biz_record
                WHERE module = ? AND (business_key = ? OR payload_json LIKE ?) FOR UPDATE
                """, MODULE, MODULE + ":" + id, "%\"id\":" + id + ",%");
        return requireAccessibleOpportunity(String.valueOf(id));
    }

    private Map<String, Object> requireOpportunity(String id) {
        return parityRecords.rows(MODULE).stream()
                .filter(row -> id.equals(String.valueOf(row.get("id"))))
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "商机不存在"));
    }

    private Map<String, Object> requireAccessibleOpportunity(String id) {
        Map<String, Object> opportunity = requireOpportunity(id);
        if (!canAccess(opportunity)) {
            throw new ApiException(403, "无权访问该商机");
        }
        return opportunity;
    }

    private boolean canAccess(Map<String, Object> opportunity) {
        applyOwnerDepartment(opportunity);
        Object deptId = opportunity.containsKey("deptId")
                ? opportunity.get("deptId")
                : opportunity.get("dept_id");
        return dataScopeService.canAccess(
                AuthContext.userId(),
                deptId,
                opportunity.get("ownerUserId"),
                opportunity.get("ownerUserName"),
                opportunity.get("createBy")
        );
    }

    private void applyOwnerDepartments(List<Map<String, Object>> opportunities) {
        List<Long> ownerIds = opportunities.stream()
                .map(row -> number(row.get("ownerUserId"), 0L))
                .filter(id -> id > 0L)
                .distinct()
                .toList();
        if (ownerIds.isEmpty()) return;
        List<Object> args = new ArrayList<>(ownerIds);
        Map<Long, Long> ownerDepartments = new LinkedHashMap<>();
        jdbc.queryForList("SELECT user_id, dept_id FROM sys_user WHERE user_id IN ("
                        + String.join(",", java.util.Collections.nCopies(ownerIds.size(), "?")) + ")",
                args.toArray()).forEach(row -> ownerDepartments.put(
                number(row.get("user_id"), 0L), number(row.get("dept_id"), 0L)));
        opportunities.forEach(row -> {
            Long ownerDepartment = ownerDepartments.get(number(row.get("ownerUserId"), 0L));
            if (ownerDepartment != null && ownerDepartment > 0L) row.put("deptId", ownerDepartment);
        });
    }

    private void applyOwnerDepartment(Map<String, Object> opportunity) {
        long ownerId = number(opportunity.get("ownerUserId"), 0L);
        if (ownerId <= 0L) return;
        List<Long> departmentIds = jdbc.queryForList(
                "SELECT dept_id FROM sys_user WHERE user_id = ?", Long.class, ownerId);
        if (!departmentIds.isEmpty() && departmentIds.get(0) != null) {
            opportunity.put("deptId", departmentIds.get(0));
        }
    }

    private void assignSalesOwner(Map<String, Object> payload) {
        long requestedOwnerId = number(payload.get("ownerUserId"), 0L);
        if (requestedOwnerId <= 0L) {
            List<Long> owners = jdbc.queryForList("""
                    SELECT user_id FROM sys_user
                    WHERE dept_id = ? AND del_flag = '0' AND status = '0'
                    ORDER BY user_id
                    """, Long.class, CUSTOMER_SALES_DEPARTMENT_ID);
            if (owners.isEmpty()) throw new ApiException(400, "客户销售部没有可用成员，无法设置商机负责人");
            requestedOwnerId = owners.get(0);
        }
        List<Map<String, Object>> owners = jdbc.queryForList("""
                SELECT u.user_id, u.nick_name, u.dept_id, d.dept_name
                FROM sys_user u JOIN sys_dept d ON d.dept_id = u.dept_id
                WHERE u.user_id = ? AND u.dept_id = ? AND u.del_flag = '0' AND u.status = '0'
                """, requestedOwnerId, CUSTOMER_SALES_DEPARTMENT_ID);
        if (owners.isEmpty()) throw new ApiException(400, "商机负责人必须属于客户销售部");
        Map<String, Object> owner = owners.get(0);
        payload.put("ownerUserId", owner.get("user_id"));
        payload.put("ownerUserName", owner.get("nick_name"));
        payload.put("deptId", owner.get("dept_id"));
    }

    private void insertRecord(Map<String, Object> payload) {
        try {
            jdbc.update("""
                    INSERT INTO biz_record (
                        tenant_id, module, business_key, payload_json, status, create_by, create_time
                    ) VALUES (?, ?, ?, ?, 'approved', ?, CURRENT_TIMESTAMP)
                    """,
                    1L,
                    MODULE,
                    MODULE + ":" + payload.get("id"),
                    objectMapper.writeValueAsString(payload),
                    AuthContext.username());
        } catch (Exception exception) {
            throw new ApiException(400, "商机数据保存失败");
        }
    }

    private void replaceRecord(long payloadId, Map<String, Object> payload) {
        try {
            int updated = jdbc.update("""
                    UPDATE biz_record SET payload_json = ?, business_key = ?
                    WHERE module = ? AND (business_key = ? OR payload_json LIKE ?)
                    """,
                    objectMapper.writeValueAsString(payload),
                    MODULE + ":" + payloadId,
                    MODULE,
                    MODULE + ":" + payloadId,
                    "%\"id\":" + payloadId + ",%");
            if (updated == 0) {
                insertRecord(payload);
            }
        } catch (Exception exception) {
            throw new ApiException(400, "商机数据保存失败");
        }
    }

    private boolean contains(Map<String, Object> row, String key, String query) {
        return query == null
                || query.isBlank()
                || value(row.get(key), "").toLowerCase().contains(query.toLowerCase());
    }

    private boolean searchAll(Map<String, Object> row, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        return row.values().stream()
                .map(item -> item == null ? "" : String.valueOf(item))
                .anyMatch(item -> item.toLowerCase().contains(query.toLowerCase()));
    }

    private int integer(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private long number(Object value, long defaultValue) {
        try {
            if (value instanceof Number number) {
                return number.longValue();
            }
            String text = value == null ? "" : String.valueOf(value);
            return text.isBlank() ? defaultValue : Long.parseLong(text);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String value(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}
