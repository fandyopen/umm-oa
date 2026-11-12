package com.seal.oa;

import com.seal.oa.auth.JwtService;
import com.seal.oa.system.SystemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Module selection grants page access; button permissions remain independent. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:module_access_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class ModuleAccessIntegrationTest {
    private static final long USER = 9901L;
    private static final long ROLE = 9901L;

    @Autowired SystemService system;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    private String authorization;

    @BeforeEach
    void prepare() {
        jdbc.update("INSERT INTO sys_role (role_id,role_name,role_key,data_scope) VALUES (?, '模块访问测试', 'module_access_test', '5')", ROLE);
        jdbc.update("""
                INSERT INTO sys_user (user_id,tenant_id,dept_id,user_name,nick_name,password,status,del_flag)
                SELECT ?,tenant_id,dept_id,'17690009901','模块访问测试',password,'0','0'
                FROM sys_user WHERE user_id=4
                """, USER);
        jdbc.update("INSERT INTO sys_user_role (user_id,role_id) VALUES (?,?)", USER, ROLE);
        jdbc.update("""
                INSERT INTO oa_hr_employee (user_id,employee_no,employment_status,bank_account)
                VALUES (?, 'ACCESS-TEST', 'active', 'PRIVATE-ACCOUNT')
                """, USER);
        authorization = "Bearer " + jwt.createToken(USER, "17690009901");
    }

    @Test
    void everyActiveModuleUsesModuleSelectionForAccess() {
        var modules = jdbc.queryForList("SELECT module_id FROM sys_module WHERE status='0'", Long.class);
        assertFalse(modules.isEmpty());
        for (long module : modules) {
            assertFalse(system.hasModuleAction(USER, module, "access"), "unselected module " + module);
            grant(module);
            assertTrue(system.hasModuleAccess(USER, module), "module grant " + module);
            assertTrue(system.hasModuleAction(USER, module, "access"), "page access " + module);
            for (String action : new String[]{"add", "edit", "remove", "export", "manage"}) {
                assertFalse(system.hasModuleAction(USER, module, action), "ungranted button " + module + ":" + action);
            }
            jdbc.update("DELETE FROM sys_role_module WHERE role_id=? AND module_id=?", ROLE, module);
            assertFalse(system.hasModuleAction(USER, module, "access"), "revoked module " + module);
        }
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
            "341002, /project/hr/employees",
            "341002, /project/hr/options",
            "341003, /project/hr/changes",
            "341003, /project/hr/options",
            "341004, /project/hr/attendance",
            "341004, /project/hr/options",
            "341005, /project/hr/payroll",
            "341005, /project/hr/options",
            "341006, /project/hr/self/attendance",
            "341006, /project/hr/self/payslips",
            "341409, /project/hr/finance/payroll",
            "341410, /project/commission/options",
            "341410, /project/commission/batches",
            "341409, /project/hr/options",
            "341301, /project/recruitment/jobs",
            "341302, /project/recruitment/talents",
            "341303, /project/recruitment/applications",
            "340352, /project/merchant/list",
            "340590, /project/business/list",
            "340644, /project/crm/analysis/overview",
            "341101, /project/crm/risk/list",
            "341201, /project/crm/contracts",
            "341202, /project/crm/contracts",
            "341203, /project/crm/contracts",
            "341204, /project/crm/contracts",
            "341400, /project/crm/finance/ledger",
            "340782, /system/user/list",
            "341001, /system/user/list",
            "340597, /system/role/list",
            "340771, /system/module/list",
            "340772, /system/module/list",
            "340771, /system/module/management-tree",
            "340772, /system/module/management-tree"
    })
    void pageReadsRequireOnlyTheirModuleGrant(long module, String path) throws Exception {
        readCode(path, 403);
        grant(module);
        readCode(path, 200);
        jdbc.update("DELETE FROM sys_role_module WHERE role_id=?", ROLE);
        readCode(path, 403);
    }

    @Test
    void moduleAccessPreservesDataScopeAndSensitiveFieldProtection() throws Exception {
        grant(341002);
        mvc.perform(get("/project/hr/employees").header("Authorization", authorization))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].userId").value(USER));
        mvc.perform(get("/project/hr/employees/" + USER).header("Authorization", authorization))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.salaryVisible").value(false))
                .andExpect(jsonPath("$.data.salaries").isEmpty())
                .andExpect(jsonPath("$.data.bankAccount").doesNotExist());
        readCode("/project/hr/employees/4", 403);
    }

    @Test
    void moduleSelectionDoesNotGrantExportEditOrSalaryManagement() throws Exception {
        grant(341002);
        grant(341301);
        readCode("/project/hr/reports/employees", 403);
        readCode("/project/recruitment/jobs/export", 403);
        mvc.perform(put("/project/hr/employees/" + USER).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/project/hr/employees/" + USER + "/salary").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(403));
        jdbc.update("INSERT INTO sys_role_module_action (role_id,module_id,action_code) VALUES (?,341301,'export')", ROLE);
        readCode("/project/recruitment/jobs/export", 200);
        jdbc.update("UPDATE sys_module_action SET status='1' WHERE module_id=341301 AND action_code='export'");
        readCode("/project/recruitment/jobs/export", 403);
        readCode("/project/recruitment/jobs", 200);
    }

    @Test
    void inactiveAndDeletedRolesCannotGrantPageAccess() throws Exception {
        grant(341002);
        jdbc.update("UPDATE sys_role SET status='1' WHERE role_id=?", ROLE);
        assertFalse(system.hasModuleAction(USER, 341002, "access"));
        readCode("/project/hr/employees", 403);
        jdbc.update("UPDATE sys_role SET status='0', del_flag='2' WHERE role_id=?", ROLE);
        assertFalse(system.hasModuleAction(USER, 341002, "access"));
        readCode("/project/hr/employees", 403);
    }

    @Test
    void manageButtonWithoutModuleSelectionDoesNotGrantPageAccess() throws Exception {
        jdbc.update("INSERT INTO sys_role_module_action (role_id,module_id,action_code) VALUES (?,340597,'manage')", ROLE);
        assertTrue(system.hasModuleAction(USER, 340597, "manage"));
        assertFalse(system.hasModuleAction(USER, 340597, "access"));
        assertTrue(system.hasModuleAction(1L, 340597, "access"));
    }

    private void grant(long module) {
        jdbc.update("INSERT INTO sys_role_module (role_id,module_id) VALUES (?,?)", ROLE, module);
    }

    private void readCode(String path, int code) throws Exception {
        mvc.perform(get(path).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
    }
}
