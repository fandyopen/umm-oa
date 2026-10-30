package com.seal.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:dept_principal_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class DepartmentPrincipalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void settingReplacingAndClearingSupervisorUpdatesPersistedIdentityAndMemberLists() throws Exception {
        String auth = login("13610316814");
        long yan = jdbc.queryForObject(
                "SELECT user_id FROM sys_user WHERE phonenumber = '13686621113'", Long.class);
        long dept = jdbc.queryForObject("SELECT dept_id FROM sys_user WHERE user_id = ?", Long.class, yan);
        long replacement = jdbc.queryForObject(
                "SELECT MIN(user_id) FROM sys_user WHERE dept_id = ? AND user_id <> ? AND del_flag = '0'",
                Long.class, dept, yan);

        setPrincipal(auth, dept, yan);
        assertIdentity(auth, dept, yan, 1);
        assertIdentity(auth, null, yan, 1);
        assertEquals(jdbc.queryForObject("SELECT user_name FROM sys_user WHERE user_id = ?", String.class, yan),
                jdbc.queryForObject("SELECT leader FROM sys_dept WHERE dept_id = ?", String.class, dept));

        setPrincipal(auth, dept, replacement);
        assertIdentity(auth, dept, yan, 0);
        assertIdentity(auth, dept, replacement, 1);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dept_principal WHERE dept_id = ?", Integer.class, dept));

        // A stale browser must not cancel the supervisor another browser has just selected.
        mvc.perform(delete("/system/dept/{deptId}/principal", dept).param("userId", String.valueOf(yan))
                        .header("Authorization", auth))
                .andExpect(jsonPath("$.code").value(409));
        assertIdentity(auth, dept, replacement, 1);

        mvc.perform(delete("/system/dept/{deptId}/principal", dept).param("userId", String.valueOf(replacement))
                        .header("Authorization", auth))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deptPrincipal").value(0));
        assertIdentity(auth, dept, replacement, 0);
        assertEquals("", jdbc.queryForObject("SELECT leader FROM sys_dept WHERE dept_id = ?", String.class, dept));
    }

    @Test
    void invalidOrUnauthorizedAssignmentCannotChangeSupervisor() throws Exception {
        String auth = login("13610316814");
        mvc.perform(put("/system/dept/324/principal").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":1}"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(put("/system/dept/999999/principal").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":25}"))
                .andExpect(jsonPath("$.code").value(404));
        mvc.perform(put("/system/dept/324/principal").header("Authorization", login("15609825919"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":25}"))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void removedDepartmentRoutesCannotReturnFalseSuccess() throws Exception {
        mvc.perform(get("/system/dept/add/principal/leader").header("Authorization", login("13610316814"))
                        .param("deptId", "324").param("userIds", "25"))
                .andExpect(jsonPath("$.code").value(404));
    }

    private void setPrincipal(String auth, long dept, long user) throws Exception {
        mvc.perform(put("/system/dept/{deptId}/principal", dept).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":" + user + "}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(user))
                .andExpect(jsonPath("$.data.deptPrincipal").value(1));
    }

    private void assertIdentity(String auth, Long dept, long user, int expected) throws Exception {
        var request = get("/system/user/list").header("Authorization", auth).param("pageSize", "200");
        if (dept != null) request.param("deptId", dept.toString());
        JsonNode rows = mapper.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString()).path("rows");
        JsonNode member = null;
        for (JsonNode row : rows) {
            if (row.path("userId").asLong() == user) member = row;
        }
        org.junit.jupiter.api.Assertions.assertNotNull(member);
        assertEquals(expected, member.path("deptPrincipal").asInt(-1));
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"admin123123\"}"))
                .andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(body).path("token").asText();
    }
}
