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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:workflow_catalog_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class WorkflowCatalogIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    String admin;

    @BeforeEach void setup() {
        admin = "Bearer " + jwt.createToken(jdbc.queryForObject("SELECT user_id FROM sys_user WHERE user_name='13610316814'",Long.class), "13610316814");
        jdbc.update("INSERT INTO sys_user(user_id,tenant_id,dept_id,user_name,nick_name,password,status,del_flag) SELECT 9931,1,326,'catalog-owner','目录流程负责人',password,'0','0' FROM sys_user WHERE user_id=4");
        jdbc.update("DELETE FROM oa_module_action_flow WHERE module_id=340590 AND action_code IN ('add','edit')");
        jdbc.update("UPDATE sys_module_action SET flow_design_enabled=0 WHERE module_id=340590 AND action_code IN ('add','edit')");
    }

    @Test void createPublishAssociateAndOldDesignerShareTheSameDefinition() throws Exception {
        String id = create();
        assertEquals("unpublished", row(id).path("state").asText());
        save(id, 0, true, "草稿");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_module_action_flow WHERE module_id=340590 AND action_code='add'", Integer.class));
        save(id, 1, false, "目录发布");
        assertEquals("unbound", row(id).path("state").asText());
        associate(id, "add", "", 2, 200);
        assertEquals("active", row(id).path("state").asText());
        JsonNode old = call(post("/activiti/mongo/detailProcess"), Map.of("menuId",340590,"actionCode","add"),200);
        assertEquals(id, old.path("_id").asText());
        assertEquals("目录发布", old.path("processData").path("nodes").get(1).path("name").asText());
        Map<String,Object> body = new LinkedHashMap<>(saveBody(3,true,"原入口草稿"));
        body.putAll(Map.of("menuId",340590,"actionCode","add","_id",id));
        call(post("/activiti/mongo/setProcess"),body,200);
        JsonNode updated = call(get("/system/workflows/catalog/"+id),null,200).path("data");
        assertEquals(4,updated.path("workflowRevision").asInt());
        assertEquals("原入口草稿",updated.path("processData").path("nodes").get(1).path("name").asText());
        assertEquals("目录发布", call(get("/system/workflows/detail").param("key","catalog:"+id),null,200).path("data").path("publishedNodes").get(1).path("name").asText());
        body.putAll(saveBody(4,false,"原入口发布"));
        call(post("/activiti/mongo/setProcess"),body,200);
        assertEquals("原入口发布",call(get("/system/workflows/catalog/"+id),null,200).path("data").path("processData").path("nodes").get(1).path("name").asText());
        assertEquals(1, call(get("/system/workflows").param("keyword","目录测试审批"),null,200).path("data").path("total").asInt());
        assertEquals(0, call(get("/system/workflows").param("source","button").param("keyword","add").param("module","module:340590"),null,200).path("data").path("total").asInt());
    }

    @Test void staleWritesReplacementsAndCrossModuleLinksAreRejected() throws Exception {
        String first=create(); save(first,0,false,"首版"); associate(first,"add","",1,200);
        call(put("/system/workflows/catalog/"+first),saveBody(0,false,"过期"),409);
        String second=create(); save(second,0,false,"新版");
        associate(second,"add","",1,409);
        associate(second,"add",first,1,200);
        assertEquals("unbound",row(first).path("state").asText());
        assertEquals("active",row(second).path("state").asText());
        call(post("/activiti/mongo/setProcess"),Map.of("menuId",340590,"actionCode","add","_id",first,"workflowRevision",2,"processData",process("过期")),409);
        call(post("/system/workflows/catalog/"+first+"/associate"),Map.of("moduleId",340352,"actionCode","track","expectedProcessId","","workflowRevision",2),400);
        associate(second,"edit","",2,409);
    }

    @Test void unpublishedAndUnintegratedButtonsCannotBeAssociatedAndDisabledSwitchStaysClosed() throws Exception {
        String id=create(); associate(id,"add","",0,400);
        save(id,0,false,"审核"); associate(id,"remove","",1,400);
        associate(id,"add","",1,200);
        call(put("/system/module/340590/actions/add"),Map.of("flowDesignEnabled",false),200);
        save(id,2,false,"更新");
        assertEquals(0,jdbc.queryForObject("SELECT flow_design_enabled FROM sys_module_action WHERE module_id=340590 AND action_code='add'",Integer.class));
        assertEquals("closed",row(id).path("state").asText());
    }

    @Test void moduleButtonCanChoosePublishedCatalogAtomically() throws Exception {
        String id=create(); save(id,0,false,"选择关联");
        call(put("/system/module/340590/actions/add"),Map.of("workflowId",id,"workflowRevision",1,"expectedProcessId","","flowDesignEnabled",true),200);
        assertEquals(id,jdbc.queryForObject("SELECT process_design_id FROM oa_module_action_flow WHERE module_id=340590 AND action_code='add'",String.class));
        String other=create(); save(other,0,false,"过期关联");
        call(put("/system/module/340590/actions/add"),Map.of("workflowId",other,"workflowRevision",1,"expectedProcessId","","flowDesignEnabled",false),409);
        assertEquals(1,jdbc.queryForObject("SELECT flow_design_enabled FROM sys_module_action WHERE module_id=340590 AND action_code='add'",Integer.class));
    }

    @Test void readonlyPermissionAndTenantIsolationApplyToNewEndpoints() throws Exception {
        String id=create();
        jdbc.update("INSERT INTO sys_role(role_id,role_name,role_key) VALUES (9931,'流程只读','catalog_reader')");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (9931,9931)");
        jdbc.update("INSERT INTO sys_role_module(role_id,module_id) VALUES (9931,340773)");
        String original=admin; admin="Bearer "+jwt.createToken(9931L,"catalog-owner");
        call(get("/system/workflows"),null,200);
        call(post("/system/workflows/catalog"),Map.of("moduleId",340590,"flowName","越权"),403);
        call(get("/system/workflows/catalog/options"),null,403);
        admin=original;
        jdbc.update("INSERT INTO oa_workflow_catalog(id,tenant_id,module_id,flow_name) VALUES ('foreign-catalog',2,340590,'外部流程')");
        call(get("/system/workflows/catalog/foreign-catalog"),null,404);
        call(put("/system/workflows/catalog/foreign-catalog"),saveBody(0,true,"外部"),404);
        assertFalse(call(get("/system/workflows"),null,200).toString().contains("foreign-catalog"));
        call(post("/system/workflows/catalog"),Map.of("moduleId",340309,"flowName","目录模块"),400);
        assertEquals(id,row(id).path("workflowId").asText());
    }

    @Test void businessCreationAndReminderWaitForApprovalAndUseFrozenDefinition() throws Exception {
        String id=create(); save(id,0,false,"原审核"); associate(id,"add","",1,200);
        Map<String,Object> payload=Map.of("name","审批后商机","customerName","测试客户","ownerUserId",9931,"ownerUserName","目录流程负责人");
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM biz_record WHERE module='/project/business/list'",Integer.class);
        JsonNode submitted=call(post("/workflow/action/execute"),Map.of("moduleId",340590,"actionCode","add","payload",payload,"submissionKey","catalog_approval_0001"),200).path("data");
        assertEquals("pendingApproval",submitted.path("status").asText());
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM biz_record WHERE module='/project/business/list'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type='crmOpportunityFollowup' AND user_id=9931",Integer.class));
        save(id,2,false,"之后审核");
        String frozen=jdbc.queryForObject("SELECT payload_json FROM oa_workflow_request WHERE id=?",String.class,submitted.path("requestId").asLong());
        assertTrue(frozen.contains("原审核")); assertFalse(frozen.contains("之后审核"));
        call(post("/activiti/process/complete"),Map.of("taskId",submitted.path("taskId").asText(),"instanceId",submitted.path("instanceId").asText(),"variables",Map.of("pass",true,"comment","同意")),200);
        assertEquals(before+1,jdbc.queryForObject("SELECT COUNT(*) FROM biz_record WHERE module='/project/business/list'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type='crmOpportunityFollowup' AND user_id=9931 AND status='todo'",Integer.class));
    }

    @Test void invalidBusinessPayloadIsRejectedBeforeApprovalAndLegacyConfigurationStillWorks() throws Exception {
        String id=create(); save(id,0,false,"校验"); associate(id,"add","",1,200);
        call(post("/workflow/action/execute"),Map.of("moduleId",340590,"actionCode","add","payload",Map.of("name","无效负责人","ownerUserId",999999),"submissionKey","catalog_invalid_0002"),400);
        call(put("/system/module/340590/actions/edit"),Map.of("flowDesignEnabled",true),200);
        call(post("/activiti/mongo/setProcess"),Map.of("menuId",340590,"actionCode","edit","approvalEnabled",true,"processData",process("原入口独立配置")),200);
        JsonNode old=call(post("/activiti/mongo/detailProcess"),Map.of("menuId",340590,"actionCode","edit"),200);
        assertTrue(old.path("_id").asText().startsWith("process-"));
        assertEquals("原入口独立配置",old.path("processData").path("nodes").get(1).path("name").asText());
    }

    String create() throws Exception { return call(post("/system/workflows/catalog"),Map.of("moduleId",340590,"flowName","目录测试审批"),200).path("data").path("id").asText(); }
    void save(String id,long revision,boolean draft,String name) throws Exception { call(put("/system/workflows/catalog/"+id),saveBody(revision,draft,name),200); }
    Map<String,Object> saveBody(long revision,boolean draft,String name) { return Map.of("workflowRevision",revision,"draft",draft,"approvalEnabled",true,"processData",process(name)); }
    Map<String,Object> process(String name) { return Map.of("nodes",List.of(Map.of("id","start","type","start"),Map.of("id","review","type","approval","name",name,"approver","13610316814"),Map.of("id","end","type","end")),"edges",List.of(Map.of("source","start","target","review"),Map.of("source","review","target","end"))); }
    void associate(String id,String code,String expected,long revision,int status) throws Exception { call(post("/system/workflows/catalog/"+id+"/associate"),Map.of("moduleId",340590,"actionCode",code,"expectedProcessId",expected,"workflowRevision",revision),status); }
    JsonNode row(String id) throws Exception { return call(get("/system/workflows/detail").param("key","catalog:"+id),null,200).path("data"); }
    JsonNode call(MockHttpServletRequestBuilder request,Object body,int code) throws Exception {
        request.header("Authorization",admin);
        if(body!=null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return json.readTree(mvc.perform(request).andExpect(jsonPath("$.code").value(code)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
