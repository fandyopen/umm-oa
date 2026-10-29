package com.seal.oa.oa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.system.DataScopeService;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.jdbc.core.JdbcTemplate;
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
        rows = dataScopeService.filterRows(
                AuthContext.userId(),
                rows,
                List.of("deptId", "dept_id"),
                List.of("createBy", "create_by", "ownerUserName")
        );
        int pageSize = Math.min(Math.max(integer(query.get("pageSize"), 10), 1), 200);
        int pageNum = Math.max(integer(query.get("pageNum"), 1), 1);
        int from = Math.min((pageNum - 1) * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        return ApiResult.page(rows.subList(from, to), rows.size());
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
        payload.putIfAbsent("stage", "初步接洽");
        payload.putIfAbsent("businessState", payload.get("stage"));
        payload.putIfAbsent("ownerUserName", AuthContext.username());
        payload.putIfAbsent("ownerUserId", AuthContext.userId());
        payload.putIfAbsent("deptId", currentDeptId());
        payload.put("createBy", AuthContext.username());
        payload.put("createTime", LocalDateTime.now().toString());
        insertRecord(payload);
        return ApiResult.ok(id);
    }

    @PutMapping
    @RequiresModuleAction(moduleId = 340590L, action = "edit")
    public Map<String, Object> update(@RequestBody Map<String, Object> body) {
        long id = number(body.get("id"), 0L);
        Map<String, Object> existing = requireAccessibleOpportunity(String.valueOf(id));
        existing.putAll(body);
        existing.put("id", id);
        if (body.containsKey("stage")) {
            existing.put("businessState", body.get("stage"));
        }
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
    public Map<String, Object> addStage(@RequestBody Map<String, Object> body) {
        String code = value(body.get("businessCode"), "");
        Map<String, Object> opportunity = parityRecords.rows(MODULE).stream()
                .filter(this::canAccess)
                .filter(row -> code.equals(value(row.get("code"), "")))
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "商机不存在"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = opportunity.get("sealBusinessStages")
                instanceof List<?> values
                ? new ArrayList<>((List<Map<String, Object>>) values)
                : new ArrayList<>();
        stages.add(new LinkedHashMap<>(body));
        opportunity.put("sealBusinessStages", stages);
        if (body.get("state") != null) {
            opportunity.put("stage", body.get("state"));
            opportunity.put("businessState", body.get("state"));
        }
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

    private Long currentDeptId() {
        List<Long> deptIds = jdbc.queryForList("""
                SELECT dept_id FROM sys_user WHERE user_id = ? AND del_flag = '0'
                """, Long.class, AuthContext.userId());
        return deptIds.isEmpty() ? null : deptIds.get(0);
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
