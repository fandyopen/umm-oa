package com.seal.oa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.system.HrMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:employee_deletion;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class EmployeeDeletionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired HrMemberService members;

    @Test
    void superAdminDeletesActiveEmployeeAndClearsSupervisorWithoutRemovingHistory() throws Exception {
        long employee = employee("19900000001", "删除测试甲");
        long dept = jdbc.queryForObject("SELECT dept_id FROM sys_user WHERE user_id = ?", Long.class, employee);
        jdbc.update("DELETE FROM sys_dept_principal WHERE dept_id = ?", dept);
        jdbc.update("INSERT INTO sys_dept_principal (dept_id, user_id) VALUES (?, ?)", dept, employee);
        jdbc.update("UPDATE sys_dept SET leader = '19900000001' WHERE dept_id = ?", dept);
        jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, 2)", employee);
        jdbc.update("INSERT INTO sys_user_post (user_id, post_id) VALUES (?, 1)", employee);
        String admin = login("13610316814");
        String oldEmployeeToken = login("19900000001");

        mvc.perform(delete("/system/user/{ids}", employee).header("Authorization", admin))
                .andExpect(jsonPath("$.code").value(200));

        assertEquals("1", flag(employee));
        assertEquals("1", jdbc.queryForObject("SELECT status FROM sys_user WHERE user_id = ?", String.class, employee));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id = ?", Integer.class, employee));
        for (String table : new String[]{"sys_dept_principal", "sys_user_role", "sys_user_post"}) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, employee));
        }
        assertEquals("", jdbc.queryForObject("SELECT leader FROM sys_dept WHERE dept_id = ?", String.class, dept));
        assertAbsentFromBothLists(admin, "删除测试甲");
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"19900000001\",\"password\":\"admin123123\"}"))
                .andExpect(jsonPath("$.code").value(500));
        mvc.perform(get("/auth/session").header("Authorization", oldEmployeeToken))
                .andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/project/activity/taskWaitList").header("Authorization", oldEmployeeToken))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void departedEmployeeCanBeDeletedRatherThanRemainingInDepartedList() throws Exception {
        long employee = employee("19900000002", "删除测试乙");
        jdbc.update("UPDATE sys_user SET del_flag = '2' WHERE user_id = ?", employee);
        String admin = login("13610316814");
        mvc.perform(delete("/system/user/{ids}", employee).header("Authorization", admin))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("1", flag(employee));
        assertAbsentFromBothLists(admin, "删除测试乙");
    }

    @Test
    void ordinaryHrManagerCannotDeleteEvenWithOrganizationManagementPermission() throws Exception {
        long employee = employee("19900000003", "删除测试丙");
        mvc.perform(delete("/system/user/{ids}", employee).header("Authorization", login("17600000001")))
                .andExpect(jsonPath("$.code").value(403));
        assertEquals("0", flag(employee));
    }

    @Test
    void protectedAdministratorOrMissingEmployeeRejectsEntireBatch() throws Exception {
        long employee = employee("19900000004", "删除测试丁");
        String admin = login("13610316814");
        mvc.perform(delete("/system/user/{ids}", employee + ",1").header("Authorization", admin))
                .andExpect(jsonPath("$.code").value(403));
        assertEquals("0", flag(employee));
        assertEquals("0", flag(1L));
        mvc.perform(delete("/system/user/{ids}", employee + ",99999999").header("Authorization", admin))
                .andExpect(jsonPath("$.code").value(404));
        assertEquals("0", flag(employee));
        mvc.perform(delete("/system/user/{ids}", employee + ",invalid").header("Authorization", admin))
                .andExpect(jsonPath("$.code").value(400));
        assertEquals("0", flag(employee));
    }

    @Test
    void administratorCannotBeDismissedThroughLegacyEndpoint() throws Exception {
        mvc.perform(get("/system/user/dismiss").param("userIds", "1")
                        .header("Authorization", login("13610316814")))
                .andExpect(jsonPath("$.code").value(403));
        assertEquals("0", flag(1L));
    }

    private long employee(String phone, String name) {
        Long dept = jdbc.queryForObject("SELECT MIN(dept_id) FROM sys_dept WHERE del_flag = '0'", Long.class);
        return members.invite(Map.of("phonenumber", phone, "nickName", name, "deptId", dept), "test");
    }

    private String flag(long id) {
        return jdbc.queryForObject("SELECT del_flag FROM sys_user WHERE user_id = ?", String.class, id);
    }

    private void assertAbsentFromBothLists(String admin, String name) throws Exception {
        for (String endpoint : new String[]{"/system/user/list", "/system/user/dismiss_list"}) {
            mvc.perform(get(endpoint).header("Authorization", admin).param("nickName", name))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.total").value(0));
        }
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", "admin123123"))))
                .andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(body).path("token").asText();
    }
}
