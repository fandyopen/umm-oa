package com.seal.oa.oa;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.system.DataScopeService;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/project/business")
@RequiresModuleAction(moduleId = 340590L)
public class BusinessQueryController {

    private static final String MODULE = "/project/business/list";

    private final ParityRecordService parityRecords;
    private final DataScopeService dataScopeService;

    public BusinessQueryController(
            ParityRecordService parityRecords,
            DataScopeService dataScopeService
    ) {
        this.parityRecords = parityRecords;
        this.dataScopeService = dataScopeService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        List<Map<String, Object>> rows = parityRecords.rows(MODULE).stream()
                .filter(row -> matches(row, "name", query.get("name")))
                .filter(row -> matches(row, "code", query.get("code")))
                .toList();
        rows = dataScopeService.filterRows(
                AuthContext.userId(),
                rows,
                List.of("deptId", "dept_id"),
                List.of("createBy", "create_by")
        );
        int pageSize = Math.min(Math.max(integer(query.get("pageSize"), 10), 1), 200);
        int pageNum = Math.max(integer(query.get("pageNum"), 1), 1);
        int from = Math.min((pageNum - 1) * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        return ApiResult.page(rows.subList(from, to), rows.size());
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        return parityRecords.rows(MODULE).stream()
                .filter(row -> id.equals(String.valueOf(row.get("id"))))
                .findFirst()
                .map(ApiResult::ok)
                .orElseThrow(() -> new ApiException(404, "商机不存在"));
    }

    private boolean matches(Map<String, Object> row, String key, String query) {
        return query == null
                || query.isBlank()
                || String.valueOf(row.getOrDefault(key, "")).contains(query);
    }

    private int integer(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}
