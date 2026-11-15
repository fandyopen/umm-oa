package com.seal.oa.hr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:payroll_rule_api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class PayrollRuleApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    String authorization(long user) { return "Bearer " + jwt.createToken(user, user == 1 ? "13610316814" : "test"); }

    Map<String,Object> body() {
        return Map.of("name", "公司统一计薪规则", "effectiveMonth", "2026-11", "reason", "公司批准", "baseVersion", 0,
                "rules", Map.of("hoursPerDay", 8, "personalPaidRate", 0, "sickPaidRate", 0.5,
                        "absencePaidRate", 0, "overtimeMultiplier", 1.5, "latePenalty", 0, "earlyPenalty", 0));
    }

    @Test
    void rulesEndpointSupportsVersionSaveAndMonthlyLookupWithoutIdConversionErrors() throws Exception {
        mvc.perform(post("/project/hr/payroll/rules").header("Authorization", authorization(1))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.versionNo").value(1));
        mvc.perform(get("/project/hr/payroll/rules").param("period", "2027-01").header("Authorization", authorization(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.applicableRule.name").value("公司统一计薪规则"))
                .andExpect(jsonPath("$.data.versions.length()").value(1));
        mvc.perform(get("/project/hr/payroll/rules").param("period", "2026-10").header("Authorization", authorization(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.applicableRule").doesNotExist());
    }

    @Test
    void readOnlyPayrollUserCannotSaveCompanyRules() throws Exception {
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=4");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(4,2)");
        jdbc.update("DELETE FROM sys_role_module_action WHERE role_id=2 AND module_id=341005");
        jdbc.update("INSERT INTO sys_role_module(role_id,module_id) SELECT 2,341005 WHERE NOT EXISTS(SELECT 1 FROM sys_role_module WHERE role_id=2 AND module_id=341005)");
        mvc.perform(get("/project/hr/payroll/rules").param("period", "2026-11").header("Authorization", authorization(4)))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/project/hr/payroll/rules").header("Authorization", authorization(4))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void workplaceOptionsRouteAndTaxWritePermissionUseExistingHrAuthorization() throws Exception {
        mvc.perform(get("/project/hr/employees/work-locations").header("Authorization",authorization(1)))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.length()").value(34));
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=4");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(4,2)");
        jdbc.update("DELETE FROM sys_role_module_action WHERE role_id=2 AND module_id=341005");
        jdbc.update("INSERT INTO sys_role_module(role_id,module_id) SELECT 2,341005 WHERE NOT EXISTS(SELECT 1 FROM sys_role_module WHERE role_id=2 AND module_id=341005)");
        mvc.perform(put("/project/hr/payroll/999/items/999/tax").header("Authorization",authorization(4))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("revision",0,"tax",0,"reason","只读角色无权改税"))))
                .andExpect(jsonPath("$.code").value(403));
    }
}
