package com.seal.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.CaptchaService;
import com.seal.oa.auth.JwtService;
import com.seal.oa.system.SystemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:umm_oa_login_logs;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "app.captcha.validate=true", "app.captcha.include-code=true"
})
@AutoConfigureMockMvc
@Transactional
class LoginLogIntegrationTest {
    private static final String USERNAME = "13610316814";
    private static final String AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36 Edg/130.0.1.2";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired CaptchaService captcha;
    @Autowired JwtService jwt;
    @Autowired SystemService users;
    String authorization;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM sys_logininfor");
        SystemService.UserAccount user = users.findLoginUser(USERNAME).orElseThrow();
        authorization = "Bearer " + jwt.createToken(user.userId(), user.username());
    }

    @Test
    void successfulLoginWritesOneRecordAndAppearsInTheList() throws Exception {
        JsonNode response = login("/login", credentials(USERNAME, "admin123123"), AGENT, "127.0.0.1", "192.168.2.10, 127.0.0.1");
        assertEquals(200, response.path("code").asInt());
        assertFalse(response.path("token").asText().isBlank());
        JsonNode logs = list(get("/monitor/logininfor/list"));
        assertEquals(1, logs.path("total").asInt());
        JsonNode row = logs.path("rows").get(0);
        assertEquals(USERNAME, row.path("userName").asText());
        assertEquals("0", row.path("status").asText());
        assertEquals("登录成功", row.path("msg").asText());
        assertEquals("192.168.2.10", row.path("ipaddr").asText());
        assertEquals("内网地址", row.path("loginLocation").asText());
        assertEquals("Edge 130.0.1.2", row.path("browser").asText());
        assertEquals("Windows", row.path("os").asText());
        assertFalse(row.path("loginTime").asText().isBlank());
        assertFalse(row.toString().contains(response.path("token").asText()));
        assertFalse(row.toString().contains("admin123123"));
    }

    @Test
    void alternatePasswordEndpointAndMissingAgentStillProduceARecord() throws Exception {
        assertEquals(200, login("/login/pwd", credentials(USERNAME, "admin123123"), null, "::1", null).path("code").asInt());
        JsonNode row = list(get("/monitor/logininfor/list")).path("rows").get(0);
        assertEquals("未知", row.path("browser").asText());
        assertEquals("未知", row.path("os").asText());
        assertEquals("本机地址", row.path("loginLocation").asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_oper_log WHERE oper_url = '/login/pwd'", Integer.class));
    }

    @Test
    void wrongPasswordAndUnknownAccountAreAuditedWithoutCredentials() throws Exception {
        for (String name : List.of(USERNAME, "nonexistent-audit-user")) {
            JsonNode response = login("/login", credentials(name, "incorrect-audit-secret"), AGENT, "10.20.30.40", "8.8.8.8");
            assertEquals(500, response.path("code").asInt());
            assertFalse(response.has("token"));
        }
        List<Map<String, Object>> records = jdbc.queryForList("SELECT * FROM sys_logininfor");
        assertEquals(2, records.size());
        for (Map<String, Object> row : records) {
            assertEquals("1", row.get("status"));
            assertEquals("账号或密码错误", row.get("msg"));
            assertEquals("10.20.30.40", row.get("ipaddr"));
            assertFalse(row.toString().contains("incorrect-audit-secret"));
        }
    }

    @Test
    void invalidCaptchaAndDisabledAccountsAreAudited() throws Exception {
        assertEquals(500, login("/login", Map.of("username", USERNAME, "password", "admin123123"), null, "127.0.0.1", null).path("code").asInt());
        assertEquals("验证码错误或已过期", jdbc.queryForObject("SELECT msg FROM sys_logininfor ORDER BY info_id DESC LIMIT 1", String.class));
        jdbc.update("UPDATE sys_user SET status = '1' WHERE user_name = ?", USERNAME);
        assertEquals(500, login("/login", credentials(USERNAME, "admin123123"), AGENT, "127.0.0.1", null).path("code").asInt());
        assertEquals("账号已停用", jdbc.queryForObject("SELECT msg FROM sys_logininfor ORDER BY info_id DESC LIMIT 1", String.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_logininfor WHERE status = '1'", Integer.class));
    }

    @Test
    void longUnknownUsernameDoesNotBreakFailureLogging() throws Exception {
        assertEquals(500, login("/login", credentials("x".repeat(200), "incorrect-audit-secret"), null, "127.0.0.1", null).path("code").asInt());
        assertEquals(64, jdbc.queryForObject("SELECT LENGTH(user_name) FROM sys_logininfor", Integer.class));
    }

    @Test
    void filtersRunBeforePaginationAndIncludeTheWholeEndDate() throws Exception {
        for (int index = 0; index < 12; index++) {
            Timestamp time = Timestamp.valueOf(index < 6 ? "2026-01-01 23:59:59" : "2026-01-02 12:00:00");
            jdbc.update("INSERT INTO sys_logininfor (user_name, ipaddr, browser, os, status, msg, login_time) VALUES ('audit-user', '192.168.2.20', 'Chrome', 'Windows', ?, '测试审计', ?)", String.valueOf(index % 2), time);
        }
        JsonNode first = list(get("/monitor/logininfor/list").param("userName", "AUDIT").param("ipaddr", "192.168").param("status", "1")
                .param("params[beginTime]", "2026-01-01").param("params[endTime]", "2026-01-01").param("pageSize", "2").param("pageNum", "1"));
        JsonNode second = list(get("/monitor/logininfor/list").param("loginName", "audit").param("status", "1")
                .param("beginTime", "2026-01-01").param("endTime", "2026-01-01").param("pageSize", "2").param("pageNum", "2"));
        assertEquals(3, first.path("total").asInt());
        assertEquals(3, second.path("total").asInt());
        assertEquals(2, first.path("rows").size());
        assertEquals(1, second.path("rows").size());
        assertTrue(first.path("rows").get(1).path("infoId").asLong() > second.path("rows").get(0).path("infoId").asLong());
        assertEquals(0, list(get("/monitor/logininfor/list").param("userName", "no-matching-user")).path("total").asInt());
    }

    @Test
    void invalidDateRangesReturnAUsefulValidationError() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                get("/monitor/logininfor/list").param("beginTime", "not-a-date"),
                get("/monitor/logininfor/list").param("beginTime", "2026-02-01").param("endTime", "2026-01-01"))) {
            JsonNode result = read(request.header("Authorization", authorization));
            assertEquals(400, result.path("code").asInt());
        }
    }

    private Map<String, Object> credentials(String name, String password) {
        Map<String, Object> challenge = captcha.create();
        return Map.of("username", name, "password", password, "uuid", challenge.get("uuid"), "code", challenge.get("debugCode"));
    }

    private JsonNode login(String path, Map<String, Object> body, String agent, String remote, String forwarded) throws Exception {
        MockHttpServletRequestBuilder request = post(path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))
                .with(value -> { value.setRemoteAddr(remote); return value; });
        if (agent != null) request.header("User-Agent", agent);
        if (forwarded != null) request.header("X-Forwarded-For", forwarded);
        return read(request);
    }

    private JsonNode list(MockHttpServletRequestBuilder request) throws Exception {
        JsonNode result = read(request.header("Authorization", authorization));
        assertEquals(200, result.path("code").asInt(), result.toString());
        return result;
    }

    private JsonNode read(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
