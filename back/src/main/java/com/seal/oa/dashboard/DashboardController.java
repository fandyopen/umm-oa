package com.seal.oa.dashboard;

import com.seal.oa.common.ApiResult;
import com.seal.oa.common.JdbcMaps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DashboardController {

    private final JdbcTemplate jdbc;

    public DashboardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/project/analysis/business/sale")
    public Map<String, Object> businessOpportunityChart() {
        return ApiResult.ok(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT COALESCE(stage, '未分类') AS name, COUNT(*) AS num
                FROM oa_business_opportunity
                GROUP BY COALESCE(stage, '未分类')
                ORDER BY num DESC
                """)));
    }
}
