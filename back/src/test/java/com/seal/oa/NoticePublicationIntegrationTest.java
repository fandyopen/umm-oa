package com.seal.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.system.NoticeSchemaMigration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:notice_publication;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class NoticePublicationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void draftPublishWithdrawRepublishAndEditHaveImmediateVisibility() throws Exception {
        String admin = login("13610316814"), reader = reader();
        long id = create(admin, "通知闭环", false);
        assertEquals(404, call(get("/notices/" + id), reader).path("code").asInt());
        assertEquals(200, call(put("/system/notice/" + id + "/publish"), admin).path("code").asInt());
        JsonNode detail = call(get("/notices/" + id), reader).path("data");
        assertEquals("通知闭环", detail.path("noticeTitle").asText());
        assertFalse(detail.path("publishedBy").asText().isBlank());
        assertNotEquals("admin", detail.path("createBy").asText());
        assertFalse(detail.path("publishedTime").isNull());
        call(put("/system/notice/" + id + "/publish"), admin);
        assertEquals(detail.path("publishedTime"), call(get("/notices/" + id), reader).path("data").path("publishedTime"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_notice WHERE notice_title='通知闭环'", Integer.class));
        assertEquals(200, call(put("/system/notice/" + id + "/withdraw"), admin).path("code").asInt());
        assertEquals(404, call(get("/notices/" + id), reader).path("code").asInt());
        call(put("/system/notice/" + id + "/publish"), admin);
        JsonNode saved = call(put("/system/notice").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                "noticeId", id, "noticeTitle", "新正文草稿", "noticeType", "1", "noticeContent", "修改后的内容"))), admin);
        assertEquals(200, saved.path("code").asInt());
        assertEquals(404, call(get("/notices/" + id), reader).path("code").asInt());
    }

    @Test
    void authorizedAuthorCanPublishDirectlyWithoutAdministratorReview() throws Exception {
        String admin = login("13610316814"), author = reader();
        jdbc.update("INSERT INTO sys_role_module(role_id,module_id) VALUES(19,340316)");
        jdbc.update("INSERT INTO sys_role_module_action(role_id,module_id,action_code) VALUES(19,340316,'add')");
        jdbc.update("INSERT INTO sys_role_module_action(role_id,module_id,action_code) VALUES(19,340316,'edit')");
        long id = create(author, "人事直接发布", true);
        assertEquals("published", call(get("/notices/" + id), author).path("data").path("publishStatus").asText());
    }

    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void failedDirectPublishRollsBackTheDraftAsWell() throws Exception {
        String admin = login("13610316814");
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM sys_notice", Integer.class);
        JsonNode bad = call(post("/system/notice").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                "noticeTitle", "空正文", "noticeType", "1", "noticeContent", "<p>&nbsp;</p>", "publish", true))), admin);
        assertEquals(400, bad.path("code").asInt());
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM sys_notice", Integer.class));
    }

    @Test
    void readerCannotManageOrReadOtherTenantAndFiltersCannotExposeDrafts() throws Exception {
        String admin = login("13610316814"), reader = reader();
        long first = create(admin, "筛选通知A", true), second = create(admin, "筛选通知B", true);
        create(admin, "筛选通知草稿", false);
        assertEquals(403, call(get("/system/notice/list"), reader).path("code").asInt());
        assertEquals(403, call(put("/system/notice/" + first + "/publish"), reader).path("code").asInt());
        assertEquals(403, call(delete("/system/notice/" + first), reader).path("code").asInt());
        JsonNode page = call(get("/notices").param("noticeTitle", "筛选通知").param("pageSize", "1").param("pageNum", "2").param("publishStatus", "draft"), reader);
        assertEquals(2, page.path("total").asInt()); assertEquals(1, page.path("rows").size());
        assertFalse(page.path("rows").get(0).has("noticeContent"));
        jdbc.update("UPDATE sys_notice SET tenant_id=999 WHERE notice_id=?", second);
        assertEquals(404, call(get("/notices/" + second), reader).path("code").asInt());
        assertEquals(404, call(get("/system/notice/" + second), admin).path("code").asInt());
        assertEquals(401, call(get("/notices"), null).path("code").asInt());
    }

    @Test
    void legacyPublishDoesNotCreateDuplicatesAndBulkDeleteIsAtomic() throws Exception {
        String admin = login("13610316814");
        long one = create(admin, "批量删除一", false), two = create(admin, "批量删除二", false);
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM sys_notice", Integer.class);
        assertEquals(400, call(post("/system/message").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"旧发布\"}"), admin).path("code").asInt());
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM sys_notice", Integer.class));
        assertEquals(404, call(delete("/system/notice/" + one + ",99999999"), admin).path("code").asInt());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_notice WHERE notice_id=?", Integer.class, one));
        assertEquals(200, call(delete("/system/notice/" + one + "," + two), admin).path("code").asInt());
        assertEquals(404, call(get("/system/notice/" + one), admin).path("code").asInt());
    }

    @Test
    void oldSchemaUpgradeIsRepeatableAndNeverPublishesHistoricContent() {
        JdbcTemplate legacy = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:notice_legacy;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        legacy.execute("CREATE TABLE sys_notice(notice_id BIGINT PRIMARY KEY,notice_title VARCHAR(200),notice_content TEXT,status VARCHAR(1))");
        legacy.update("INSERT INTO sys_notice VALUES(1,'历史公告','保留正文','0')");
        NoticeSchemaMigration migration = new NoticeSchemaMigration(legacy);
        migration.run(null);
        assertEquals("draft", legacy.queryForObject("SELECT publish_status FROM sys_notice WHERE notice_id=1", String.class));
        legacy.update("UPDATE sys_notice SET publish_status='published',published_by='人事' WHERE notice_id=1");
        migration.run(null);
        assertEquals("published", legacy.queryForObject("SELECT publish_status FROM sys_notice WHERE notice_id=1", String.class));
    }

    private long create(String token, String title, boolean publish) throws Exception {
        JsonNode response = call(post("/system/notice").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                "noticeTitle", title, "noticeType", "1", "noticeContent", "<p>通知正文</p>", "publish", publish))), token);
        assertEquals(200, response.path("code").asInt(), response.toString());
        return response.path("data").path("noticeId").asLong();
    }
    private String reader() throws Exception {
        jdbc.update("DELETE FROM sys_role_module_action WHERE role_id=19 AND module_id=340316");
        jdbc.update("DELETE FROM sys_role_module WHERE role_id=19 AND module_id=340316");
        return login("17600000001");
    }
    private String login(String account) throws Exception {
        return "Bearer " + call(post("/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("username", account, "password", "admin123123"))), null).path("token").asText();
    }
    private JsonNode call(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) request.header("Authorization", token);
        return mapper.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
