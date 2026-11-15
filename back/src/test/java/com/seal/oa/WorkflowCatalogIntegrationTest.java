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
    @Autowired com.seal.oa.system.ModuleActionService moduleActions;
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

    @Test void salaryActionLabelsMigrateWithoutChangingWorkflowOrApprovalGrants() throws Exception {
        jdbc.update("DELETE FROM sys_permission_migration WHERE migration_key='hr_salary_action_labels_v1'");
        jdbc.update("UPDATE sys_module_action SET action_name='提交定薪调薪',flow_design_enabled=1,config_json='{}' WHERE module_id=341002 AND action_code='salary_submit'");
        jdbc.update("UPDATE sys_module_action SET action_name='审批薪资标准' WHERE module_id=341002 AND action_code='approve'");
        var permissions = jdbc.queryForList("SELECT role_id,action_code FROM sys_role_module_action WHERE module_id=341002 ORDER BY role_id,action_code");
        var submitBefore = jdbc.queryForMap("SELECT permission_code,handler_key,config_json,status,flow_design_enabled FROM sys_module_action WHERE module_id=341002 AND action_code='salary_submit'");
        moduleActions.bootstrap();
        assertEquals("提交薪资审批", jdbc.queryForObject("SELECT action_name FROM sys_module_action WHERE module_id=341002 AND action_code='salary_submit'", String.class));
        assertEquals("薪资审批权限", jdbc.queryForObject("SELECT action_name FROM sys_module_action WHERE module_id=341002 AND action_code='approve'", String.class));
        assertEquals(submitBefore, jdbc.queryForMap("SELECT permission_code,handler_key,config_json,status,flow_design_enabled FROM sys_module_action WHERE module_id=341002 AND action_code='salary_submit'"));
        assertEquals(permissions, jdbc.queryForList("SELECT role_id,action_code FROM sys_role_module_action WHERE module_id=341002 ORDER BY role_id,action_code"));
        JsonNode options = call(get("/system/workflows/catalog/options").param("moduleId", "341002"), null, 200).path("data").path("buttons");
        for (JsonNode option : options) {
            if ("approve".equals(option.path("actionCode").asText())) {
                assertEquals("仅审批授权", option.path("integrationLabel").asText());
                assertFalse(option.path("ready").asBoolean());
            } else if ("salary_submit".equals(option.path("actionCode").asText())) {
                assertTrue(option.path("ready").asBoolean());
            }
        }
        JsonNode actions = call(get("/system/module/341002/actions"), null, 200).path("data");
        for (JsonNode action : actions) if ("approve".equals(action.path("actionCode").asText())) {
            assertTrue(action.path("approvalPermissionOnly").asBoolean());
            assertEquals("仅审批授权", action.path("workflowState").asText());
            assertEquals("hr:employee:approve", action.path("permissionCode").asText());
        }
        call(put("/system/module/341002/actions/approve"), Map.of("flowDesignEnabled", true), 400);
        call(put("/system/module/341002/actions/approve"), Map.of("handlerKey", "hr.salary.submit"), 400);
        jdbc.update("UPDATE sys_module_action SET action_name='自定义薪资申请' WHERE module_id=341002 AND action_code='salary_submit'");
        moduleActions.bootstrap();
        assertEquals("自定义薪资申请", jdbc.queryForObject("SELECT action_name FROM sys_module_action WHERE module_id=341002 AND action_code='salary_submit'", String.class));
    }

    @Test void allKnownApprovalPermissionsAreLabeledAndCannotInitiateWorkflowsWithoutChangingGrants() throws Exception {
        var labels = Map.of(341002L,"薪资审批权限",341003L,"人事异动审批权限",341005L,"工资审批权限",
                341410L,"提成审批权限",341400L,"财务审批权限",341202L,"合同审批权限");
        var oldLabels = Map.of(341002L,"审批薪资标准",341003L,"审批人事异动",341005L,"审批工资",
                341410L,"审批提成",341400L,"办理财务审批或退回",341202L,"审批");
        jdbc.update("DELETE FROM sys_permission_migration WHERE migration_key='approval_permission_labels_v1'");
        for (var entry : oldLabels.entrySet())
            jdbc.update("UPDATE sys_module_action SET action_name=? WHERE module_id=? AND action_code='approve'",entry.getValue(),entry.getKey());
        var grants = jdbc.queryForList("SELECT * FROM sys_role_module_action ORDER BY role_id,module_id,action_code");
        var submissions = jdbc.queryForList("SELECT * FROM sys_module_action WHERE handler_key IS NOT NULL AND TRIM(handler_key)<>'' ORDER BY module_id,action_code");
        var bindings = jdbc.queryForList("SELECT * FROM oa_module_action_flow ORDER BY id");
        moduleActions.bootstrap();
        assertEquals(grants,jdbc.queryForList("SELECT * FROM sys_role_module_action ORDER BY role_id,module_id,action_code"));
        assertEquals(submissions,jdbc.queryForList("SELECT * FROM sys_module_action WHERE handler_key IS NOT NULL AND TRIM(handler_key)<>'' ORDER BY module_id,action_code"));
        assertEquals(bindings,jdbc.queryForList("SELECT * FROM oa_module_action_flow ORDER BY id"));
        for (var entry : labels.entrySet()) {
            long module = entry.getKey();
            var action = moduleActions.actions(module).stream().filter(a->"approve".equals(a.get("actionCode"))).findFirst().orElseThrow();
            assertEquals(entry.getValue(),action.get("actionName"));
            assertEquals(true,action.get("approvalPermissionOnly"));
            assertEquals("仅审批授权",action.get("workflowState"));
            assertTrue(action.get("approvalPermissionHint").toString().contains("发起流程"));
            assertTrue(assertThrows(com.seal.oa.common.ApiException.class,()->moduleActions.requireFlowDesign(module,"approve")).getMessage().contains("仅审批授权"));
            assertThrows(com.seal.oa.common.ApiException.class,()->moduleActions.requireWorkflowExecution(module,"approve"));
            assertThrows(com.seal.oa.common.ApiException.class,()->moduleActions.updateAction(module,"approve",Map.of("flowDesignEnabled",true)));
            moduleActions.updateAction(module,"approve",Map.of("actionName","自定义审批权限"));
            assertEquals("自定义审批权限",moduleActions.action(module,"approve").get("actionName"));
            jdbc.update("UPDATE sys_module_action SET handler_key='workflow.business-api',flow_design_enabled=1 WHERE module_id=? AND action_code='approve'",module);
            assertTrue(moduleActions.workflowActions(module).stream().noneMatch(a->"approve".equals(a.get("actionCode"))));
            // A prior mistaken configuration cannot turn an authorization into a workflow entry.
            assertThrows(com.seal.oa.common.ApiException.class,()->moduleActions.requireWorkflowExecution(module,"approve"));
        }
        var rows = call(get("/system/workflows"), null, 200).path("data");
        assertFalse(rows.toString().contains("自定义审批权限"));
        jdbc.update("DELETE FROM sys_permission_migration WHERE migration_key='approval_permission_labels_v1'");
        moduleActions.bootstrap();
        for(long module:labels.keySet()) assertEquals("自定义审批权限",moduleActions.action(module,"approve").get("actionName"));
    }

    @Test void customActionNamedApproveRemainsAConfigurableBusinessButton() {
        moduleActions.createAction(340590L, Map.of("actionCode","approve","actionName","自定义业务审批","flowDesignEnabled",true));
        assertFalse(com.seal.oa.system.ModuleActionService.isApprovalPermissionOnly(340590L,"approve"));
        assertDoesNotThrow(()->moduleActions.requireFlowDesign(340590L,"approve"));
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
