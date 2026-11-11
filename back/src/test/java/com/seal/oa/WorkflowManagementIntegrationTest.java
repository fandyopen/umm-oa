package com.seal.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:workflow_management_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class WorkflowManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    private static final long USER = 9911L;
    private static final long ROLE = 9911L;
    private String token;

    @BeforeEach
    void prepare() throws Exception {
        jdbc.update("INSERT INTO sys_role(role_id,role_name,role_key,data_scope) VALUES (?,'流程只读测试','workflow_reader_test','5')", ROLE);
        jdbc.update("""
                INSERT INTO sys_user(user_id,tenant_id,dept_id,user_name,nick_name,password,status,del_flag)
                SELECT ?,tenant_id,dept_id,'17690009911','流程只读测试',password,'0','0' FROM sys_user WHERE user_id=4
                """, USER);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", USER, ROLE);
        token = "Bearer " + jwt.createToken(USER, "17690009911");
        // Use a dedicated action to avoid depending on demo workflow configurations.
        jdbc.update("""
                INSERT INTO sys_module_action(module_id,action_code,action_name,handler_key,status,flow_design_enabled)
                VALUES (340352,'overview_test','新增跟进测试','workflow.business-api','0',1)
                """);
    }

    @Test
    void moduleSelectionAloneGrantsReadonlyAccessAndDoesNotGrantDesign() throws Exception {
        mvc.perform(get("/system/workflows")).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/system/workflows").header("Authorization", token)).andExpect(jsonPath("$.code").value(403));
        grantPage();
        mvc.perform(get("/system/workflows").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.canDesign").value(false));
        mvc.perform(get("/system/module/340352/actions").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/activiti/mongo/setProcess").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(403));
        assertEquals("system/workflow/index", jdbc.queryForObject("SELECT component FROM sys_module WHERE module_id=340773", String.class));
    }

    @Test
    void separatelyGrantedDesignPermissionCanUseExistingDesignerEndpoints() throws Exception {
        grantPage();
        jdbc.update("INSERT INTO sys_role_module_action(role_id,module_id,action_code) VALUES (?,340773,'design')", ROLE);
        mvc.perform(get("/system/workflows").header("Authorization", token))
                .andExpect(jsonPath("$.data.canDesign").value(true));
        mvc.perform(get("/system/module/340352/actions").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/workflow/roles").header("Authorization", token)).andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/activiti/mongo/detailProcess").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuId\":341201,\"tenantAppId\":1,\"actionCode\":\"submit\"}"))
                .andExpect(jsonPath("$.code").value(200));
        // A design grant must never become module structure management.
        mvc.perform(post("/system/module").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void publishedVersionAndDraftRemainIndependentAndDisabledConfigurationsStayVisible() throws Exception {
        grantPage();
        publish("overview_test", true, "已发布节点");
        draft("overview_test", "草稿节点");
        JsonNode row = list("overview_test").get(0);
        assertEquals("active", row.path("state").asText());
        assertTrue(row.path("hasDraft").asBoolean());
        assertFalse(row.has("publishedProcess"));
        assertTrue(row.path("modulePath").asText().contains("客户"));
        JsonNode detail = detail(row.path("key").asText());
        assertEquals("已发布节点", detail.path("publishedNodes").get(0).path("name").asText());
        assertEquals("草稿节点", detail.path("draftNodes").get(0).path("name").asText());
        assertTrue(detail.path("publishedNodes").get(0).path("approverLabel").asText().contains("角色："));
        assertFalse(detail.path("canDesign").asBoolean());
        jdbc.update("UPDATE oa_module_action_flow SET approval_enabled=0 WHERE action_code='overview_test'");
        assertEquals("disabled", list("overview_test").get(0).path("state").asText());
        jdbc.update("UPDATE sys_module_action SET flow_design_enabled=0 WHERE action_code='overview_test'");
        assertEquals("closed", list("overview_test").get(0).path("state").asText());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM biz_record WHERE module='lowcode:button-flow-draft' AND business_key='1:340352:overview_test'", Long.class));
    }

    @Test
    void draftOnlyUnconfiguredAndMissingBusinessIntegrationAreDistinct() throws Exception {
        grantPage();
        assertEquals("unconfigured", list("overview_test").get(0).path("state").asText());
        draft("overview_test", "未发布节点");
        assertEquals("unpublished", list("overview_test").get(0).path("state").asText());
        jdbc.update("UPDATE sys_module_action SET handler_key='' WHERE action_code='overview_test'");
        assertEquals("integration", list("overview_test").get(0).path("state").asText());
        jdbc.update("UPDATE sys_module_action SET flow_design_enabled=0 WHERE action_code='overview_test'");
        assertEquals("closed", list("overview_test").get(0).path("state").asText());
    }

    @Test
    void allSourcesFilteringAndTenantIsolationWorkWithoutExposingAnotherTenantDetails() throws Exception {
        grantPage();
        jdbc.update("""
                INSERT INTO oa_form_definition(tenant_id,tenant_app_id,form_key,form_type,flow_name,process_data,status)
                VALUES (1,1,'overview_form','flow','报销流程总览测试',?,'published')
                """, process("报销审批"));
        jdbc.update("""
                INSERT INTO oa_form_definition(tenant_id,tenant_app_id,form_key,form_type,flow_name,process_data,status)
                VALUES (2,1,'foreign_overview_form','flow','外部企业流程',?,'published')
                """, process("私有节点"));
        mvc.perform(get("/system/workflows").header("Authorization", token).param("source", "form").param("keyword", "报销流程总览测试"))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.rows[0].source").value("form"));
        mvc.perform(get("/system/workflows").header("Authorization", token).param("keyword", "外部企业流程"))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/system/workflows/detail").header("Authorization", token).param("key", "form:foreign_overview_form"))
                .andExpect(jsonPath("$.code").value(404));
        mvc.perform(get("/system/workflows").header("Authorization", token).param("state", "active").param("keyword", "overview_test"))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/system/workflows").header("Authorization", token).param("module", "module:340352").param("keyword", "overview_test"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    private void grantPage() { jdbc.update("INSERT INTO sys_role_module(role_id,module_id) VALUES (?,340773)", ROLE); }
    private String process(String name) throws Exception {
        return json.writeValueAsString(Map.of("nodes", List.of(Map.of("id","approval","type","approval","name",name,"approver","hr_manager","approvalMode","any"))));
    }
    private void publish(String action, boolean enabled, String name) throws Exception {
        String id = "overview-" + action;
        jdbc.update("INSERT INTO oa_process_design(id,tenant_id,tenant_app_id,menu_id,menu_name,process_data,status) VALUES (?,1,1,340352,'客户档案',?,'published')", id, process(name));
        jdbc.update("INSERT INTO oa_module_action_flow(tenant_id,tenant_app_id,module_id,action_code,process_design_id,approval_enabled) VALUES (1,1,340352,?,?,?)", action,id,enabled?1:0);
    }
    private void draft(String action, String name) throws Exception {
        String payload = json.writeValueAsString(Map.of("processData", json.readTree(process(name)), "approvalEnabled", true));
        jdbc.update("INSERT INTO biz_record(tenant_id,module,business_key,payload_json) VALUES (1,'lowcode:button-flow-draft',?,?)", "1:340352:" + action,payload);
    }
    private JsonNode list(String keyword) throws Exception {
        String body = mvc.perform(get("/system/workflows").header("Authorization", token).param("keyword", keyword))
                .andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(body).path("data").path("rows");
    }
    private JsonNode detail(String key) throws Exception {
        String body = mvc.perform(get("/system/workflows/detail").header("Authorization", token).param("key", key))
                .andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(body).path("data");
    }
}
