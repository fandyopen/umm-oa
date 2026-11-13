package com.seal.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.system.ModuleActionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = """
        spring.datasource.url=jdbc:h2:mem:umm_oa_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1
        """)
@AutoConfigureMockMvc
@Transactional
class WorkflowActionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ModuleActionService moduleActionService;

    @BeforeEach
    void ensureReviewerFixture() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_name = '17600000001'", Integer.class);
        if (count == 0) {
            jdbc.update("""
                    INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, phonenumber, password, status, del_flag)
                    SELECT 9921, 1, dept_id, '17600000001', '流程测试审批人', '17600000001', password, '0', '0'
                    FROM sys_user WHERE user_id = 4
                    """);
        }
    }

    @Test
    void workflowRoleOptionsExcludeInactiveDeletedAndOtherTenantRoles() throws Exception {
        jdbc.update("INSERT INTO sys_role (role_id,tenant_id,role_name,role_key,status,del_flag) VALUES (9961,1,'选项角色','select_role','0','0'),(9962,1,'停用角色','disabled_role','1','0'),(9963,1,'删除角色','deleted_role','0','2'),(9964,2,'其他企业','other_tenant_role','0','0')");
        String adminToken = login("13610316814");
        MvcResult result = mockMvc.perform(get("/workflow/roles").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andReturn();
        JsonNode roles = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        java.util.Set<String> keys = new java.util.HashSet<>();
        roles.forEach(role -> {
            keys.add(role.path("roleKey").asText());
            assertEquals(3, role.size());
        });
        assertTrue(keys.contains("select_role"));
        assertTrue(!keys.contains("disabled_role") && !keys.contains("deleted_role") && !keys.contains("other_tenant_role"));
        mockMvc.perform(get("/workflow/roles")).andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/workflow/roles").header("Authorization", "Bearer " + login("17600000001")))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void actionApprovalCreatesTodoAndExecutesAfterApproval() throws Exception {
        String adminToken = login("13610316814");
        mockMvc.perform(get("/system/module/341001/actions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data[0].actionCode").value("invite"));
        mockMvc.perform(get("/system/module/341001/actions")
                        .param("workflowOnly", "true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data[0].handlerKey").isNotEmpty());
        mockMvc.perform(get("/system/module/340396")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
        publishFlow(adminToken, "invite", true);
        mockMvc.perform(post("/activiti/mongo/detailProcess")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tenantAppId", 1,
                                "menuId", 341001,
                                "actionCode", "invite"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionCode").value("invite"))
                .andExpect(jsonPath("$.approvalEnabled").value(true))
                .andExpect(jsonPath("$.processData.nodes[1].type").value("approval"));
        String phone = "13900001111";

        MvcResult submitResult = mockMvc.perform(post("/workflow/action/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "moduleId", 341001,
                                "actionCode", "invite",
                                "payload", Map.of(
                                        "nickName", "审批邀请成员",
                                        "phonenumber", phone,
                                        "deptId", 324
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("pendingApproval"))
                .andExpect(jsonPath("$.data.instanceId").isNotEmpty())
                .andExpect(jsonPath("$.data.taskId").isNotEmpty())
                .andReturn();
        JsonNode submitted = objectMapper.readTree(
                submitResult.getResponse().getContentAsString()
        ).path("data");
        assertUserCount(phone, 0);
        updateButton(adminToken, "invite", Map.of("flowDesignEnabled", false), 200);

        String approverToken = login("17600000001");
        mockMvc.perform(get("/activiti/mongoWorkflow/taskWaitList")
                        .header("Authorization", "Bearer " + approverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", greaterThan(0)));

        mockMvc.perform(post("/activiti/process/complete")
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "taskId", submitted.path("taskId").asText(),
                                "instanceId", submitted.path("instanceId").asText(),
                                "variables", Map.of("pass", true, "comment", "同意")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertUserCount(phone, 1);
        String requestStatus = jdbc.queryForObject("""
                SELECT status FROM oa_workflow_request WHERE id = ?
                """, String.class, submitted.path("requestId").asLong());
        org.junit.jupiter.api.Assertions.assertEquals("approved", requestStatus);
    }

    @Test
    void disabledApprovalExecutesActionImmediately() throws Exception {
        String adminToken = login("13610316814");
        publishFlow(adminToken, "invite", false);
        String phone = "13900002222";

        mockMvc.perform(post("/workflow/action/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "moduleId", 341001,
                                "actionCode", "invite",
                                "payload", Map.of(
                                        "nickName", "直接邀请成员",
                                        "phonenumber", phone,
                                        "deptId", 324
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("completed"));

        assertUserCount(phone, 1);
    }

    @Test
    void moduleButtonCodeIsGeneratedReturnedAndVisibleInRoleTree() throws Exception {
        String adminToken = login("13610316814");
        MvcResult previewResult = mockMvc.perform(get("/system/module/341001/actions/next-code")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionCode").isNotEmpty())
                .andReturn();
        String actionCode = objectMapper.readTree(
                previewResult.getResponse().getContentAsString()
        ).path("data").path("actionCode").asText();
        assertTrue(actionCode.matches("action_\\d{3}"));

        mockMvc.perform(post("/system/module/341001/actions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actionName", "生成测试按钮",
                                "status", "0"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionCode").value(actionCode))
                .andExpect(jsonPath("$.data.actionName").value("生成测试按钮"));

        MvcResult roleTreeResult = mockMvc.perform(get("/app/user/roleMenuTree")
                        .param("roleId", "19")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(roleTreeResult.getResponse().getContentAsString(StandardCharsets.UTF_8)
                .contains("生成测试按钮（" + actionCode + "）"));

        MvcResult managementTreeResult = mockMvc.perform(
                        get("/system/module/management-tree")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(managementTreeResult.getResponse().getContentAsString(StandardCharsets.UTF_8)
                .contains("\"actionCode\":\"" + actionCode + "\""));
    }

    @Test
    void customActionUsesConfiguredHandlerAcrossBootstrap() throws Exception {
        String adminToken = login("13610316814");
        mockMvc.perform(post("/system/module/341001/actions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actionCode", "flow_test",
                                "actionName", "流程测试",
                                "handlerKey", "workflow.noop",
                                "configJson", "{}",
                                "status", "0"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        moduleActionService.bootstrap();
        JsonNode actions = objectMapper.readTree(mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/system/module/341001/actions")
                                .header("Authorization", "Bearer " + adminToken))
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        boolean found = false;
        for (JsonNode action : actions) {
            if ("flow_test".equals(action.path("actionCode").asText())) {
                found = true;
                assertEquals(0, action.path("builtin").asInt());
                assertEquals("workflow.noop", action.path("handlerKey").asText());
            }
        }
        assertTrue(found);

        publishFlow(adminToken, "flow_test", true);
        MvcResult submitResult = mockMvc.perform(post("/workflow/action/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "moduleId", 341001,
                                "actionCode", "flow_test",
                                "payload", Map.of("remark", "handlKey registry test")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pendingApproval"))
                .andReturn();
        JsonNode submitted = objectMapper.readTree(
                submitResult.getResponse().getContentAsString()
        ).path("data");

        String approverToken = login("17600000001");
        mockMvc.perform(post("/activiti/process/complete")
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "taskId", submitted.path("taskId").asText(),
                                "instanceId", submitted.path("instanceId").asText(),
                                "variables", Map.of("pass", true, "comment", "同意")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String result = jdbc.queryForObject("""
                SELECT result_json FROM oa_workflow_request WHERE id = ?
                """, String.class, submitted.path("requestId").asLong());
        assertTrue(result.contains("流程审批已完成"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/system/module/341001/actions/flow_test")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void editedDefaultButtonAndDesignSwitchSurviveBootstrap() throws Exception {
        String token = login("13610316814");
        publishFlow(token, "invite", true);
        String processId = jdbc.queryForObject("SELECT process_design_id FROM oa_module_action_flow WHERE module_id = 341001 AND action_code = 'invite'", String.class);
        updateButton(token, "invite", Map.of("actionName", "成员入职申请", "permissionCode", "custom:member:invite",
                "actionSort", 5, "actionDescription", "可维护按钮", "configJson", "{}", "flowDesignEnabled", false), 200);
        moduleActionService.refreshModule(341001L, "changed:member:list", "enterprise/index");
        moduleActionService.bootstrap();
        Map<String, Object> action = moduleActionService.action(341001L, "invite");
        assertEquals("成员入职申请", action.get("actionName"));
        assertEquals("custom:member:invite", action.get("permissionCode"));
        assertEquals(0L, ((Number) action.get("flowDesignEnabled")).longValue());
        assertEquals(5L, ((Number) action.get("actionSort")).longValue());
        assertEquals("可维护按钮", action.get("actionDescription"));
        mockMvc.perform(post("/activiti/mongo/setProcess").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("menuId", 341001, "actionCode", "invite"))))
                .andExpect(jsonPath("$.code").value(400));
        updateButton(token, "invite", Map.of("flowDesignEnabled", true), 200);
        assertEquals(processId, jdbc.queryForObject("SELECT process_design_id FROM oa_module_action_flow WHERE module_id = 341001 AND action_code = 'invite'", String.class));
        moduleActionService.bootstrap();
        assertEquals(1L, ((Number) moduleActionService.action(341001L, "invite").get("flowDesignEnabled")).longValue());
    }

    @Test
    void deletedDefaultButtonIsNotReseededOrReused() throws Exception {
        String token = login("13610316814");
        mockMvc.perform(delete("/system/module/341001/actions/dismiss").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200));
        moduleActionService.bootstrap();
        assertTrue(moduleActionService.action(341001L, "dismiss").isEmpty());
        assertEquals("2", jdbc.queryForObject("SELECT status FROM sys_module_action WHERE module_id = 341001 AND action_code = 'dismiss'", String.class));
        mockMvc.perform(post("/system/module/341001/actions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("actionCode", "dismiss", "actionName", "重复身份"))))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void deletedCustomerTrackCanBeRestoredByItsOriginalCode() throws Exception {
        String token = login("13610316814");
        mockMvc.perform(delete("/system/module/340352/actions/track").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200));
        moduleActionService.bootstrap();
        assertTrue(moduleActionService.action(340352L, "track").isEmpty());
        mockMvc.perform(post("/system/module/340352/actions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("actionCode", "track", "actionName", "新增跟进"))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.restored").value(true))
                .andExpect(jsonPath("$.data.actionCode").value("track"))
                .andExpect(jsonPath("$.data.permissionCode").value("project:merchant:track"))
                .andExpect(jsonPath("$.data.status").value("0"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_module_action WHERE module_id=340352 AND action_code='track'", Integer.class));
        mockMvc.perform(post("/system/module/340352/actions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("actionCode", "track", "actionName", "不能覆盖"))))
                .andExpect(jsonPath("$.code").value(400));
        assertEquals("新增跟进", moduleActionService.action(340352L, "track").get("actionName"));
    }

    @Test
    void restoredButtonKeepsArchivedDesignWithoutReactivatingApprovalOrRoleGrants() throws Exception {
        String token = login("13610316814");
        publishFlow(token, "invite", true);
        String processId = jdbc.queryForObject("SELECT process_design_id FROM oa_module_action_flow WHERE module_id=341001 AND action_code='invite'", String.class);
        jdbc.update("INSERT INTO sys_role_module_action(role_id,module_id,action_code) SELECT 1,341001,'invite' WHERE NOT EXISTS (SELECT 1 FROM sys_role_module_action WHERE role_id=1 AND module_id=341001 AND action_code='invite')");
        mockMvc.perform(delete("/system/module/341001/actions/invite").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/system/module/341001/actions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("actionCode", "invite", "actionName", "恢复邀请",
                                "handlerKey", "hr.member.invite", "flowDesignEnabled", true))))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.restored").value(true))
                .andExpect(jsonPath("$.data.flowDesignEnabled").value(0));
        assertEquals(processId, jdbc.queryForObject("SELECT process_design_id FROM oa_module_action_flow WHERE module_id=341001 AND action_code='invite'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT approval_enabled FROM oa_module_action_flow WHERE module_id=341001 AND action_code='invite'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module_action WHERE module_id=341001 AND action_code='invite'", Integer.class));
    }

    @Test
    void invalidHandlerAndIdentityChangesAreRejected() throws Exception {
        String token = login("13610316814");
        updateButton(token, "invite", Map.of("actionCode", "other"), 400);
        updateButton(token, "invite", Map.of("handlerKey", "missing.handler"), 400);
        mockMvc.perform(post("/system/module/341001/actions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("actionName", "未接入按钮", "flowDesignEnabled", true))))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(put("/system/module/341201/actions/submit").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("handlerKey", "workflow.noop"))))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void workflowAdministratorCanConfigureFlowsWithoutStructurePermissions() throws Exception {
        jdbc.update("INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, status, del_flag) SELECT 9951, 1, dept_id, '17600000991', '流程配置测试', password, '0', '0' FROM sys_user WHERE user_id = 4");
        jdbc.update("INSERT INTO sys_role (role_id, role_name, role_key) VALUES (9951, '流程配置测试', 'workflow_config_test')");
        jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (9951, 9951)");
        jdbc.update("INSERT INTO sys_role_module (role_id, module_id) VALUES (9951, 340772)");
        jdbc.update("INSERT INTO sys_role_module_action (role_id, module_id, action_code) VALUES (9951, 340772, 'workflow')");
        String token = login("17600000991");
        mockMvc.perform(get("/workflow/roles").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").isArray());
        mockMvc.perform(get("/system/role/list/all").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/system/module/management-tree").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.canManageStructure").value(false)).andExpect(jsonPath("$.canManageWorkflow").value(true));
        updateButton(token, "invite", Map.of("flowDesignEnabled", false), 200);
        updateButton(token, "invite", Map.of("actionName", "禁止修改结构"), 403);
        publishFlow(token, "invite", true);
        mockMvc.perform(delete("/system/module/341001/actions/invite").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(post("/system/module/341001/actions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("actionName", "禁止新增"))))
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(put("/system/module").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("menuId", 341001))))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void legacyPageBindingIsPreservedButCannotBePublished() throws Exception {
        String token = login("13610316814");
        publishFlow(token, "invite", true);
        jdbc.update("UPDATE oa_module_action_flow SET action_code = 'page' WHERE module_id = 341001 AND action_code = 'invite'");
        moduleActionService.bootstrap();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_module_action_flow WHERE module_id = 341001 AND action_code = 'page'", Integer.class));
        mockMvc.perform(post("/activiti/mongo/setProcess").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("menuId", 341001, "actionCode", "page"))))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void buttonsWithoutExecutorsCanSaveAndReloadDraftsButCannotEnableApproval() throws Exception {
        String token = login("13610316814");
        moduleActionService.createAction(341001L, Map.of("actionCode", "draft_action", "actionName", "待接入按钮", "flowDesignEnabled", true));
        Map<String, Object> body = Map.of("menuId", 341001, "actionCode", "draft_action", "draft", true,
                "approvalEnabled", true, "processData", Map.of("nodes", java.util.List.of(Map.of("id", "draft-node"))));
        mockMvc.perform(post("/activiti/mongo/setProcess").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/activiti/mongo/detailProcess").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.draft").value(true)).andExpect(jsonPath("$.processData.nodes[0].id").value("draft-node"));
        Map<String, Object> published = new java.util.LinkedHashMap<>(body);
        published.put("draft", false);
        mockMvc.perform(post("/activiti/mongo/setProcess").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(published)))
                .andExpect(jsonPath("$.code").value(400));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_module_action_flow WHERE action_code = 'draft_action'", Integer.class));
    }

    @Test
    void draftEditsNeverReplaceThePublishedWorkflow() throws Exception {
        String token = login("13610316814");
        publishFlow(token, "invite", true);
        String published = jdbc.queryForObject("SELECT process_data FROM oa_process_design WHERE id = (SELECT process_design_id FROM oa_module_action_flow WHERE module_id = 341001 AND action_code = 'invite')", String.class);
        mockMvc.perform(post("/activiti/mongo/setProcess").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("menuId", 341001, "actionCode", "invite", "draft", true, "approvalEnabled", false, "processData", Map.of("nodes", java.util.List.of())))))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(published, jdbc.queryForObject("SELECT process_data FROM oa_process_design WHERE id = (SELECT process_design_id FROM oa_module_action_flow WHERE module_id = 341001 AND action_code = 'invite')", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT approval_enabled FROM oa_module_action_flow WHERE module_id = 341001 AND action_code = 'invite'", Integer.class));
        publishFlow(token, "invite", true);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM biz_record WHERE module = 'lowcode:button-flow-draft' AND business_key = '1:341001:invite'", Integer.class));
    }

    @Test
    void batchMemberImportFreezesResolvedDepartmentsAndWaitsForInvitationApproval() throws Exception {
        String token=login("13610316814");
        publishFlow(token,"invite",true);
        var rows=java.util.List.of(
                Map.of("nickName","批量成员甲","phonenumber","13985500101","department","324"),
                Map.of("nickName","批量成员乙","phonenumber","13985500102","department","综合部"));
        var submitted=mockMvc.perform(post("/workflow/action/execute")
                .header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("moduleId",341001,"actionCode","invite","payload",Map.of("rows",rows,"memberImport",true)))))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.status").value("pendingApproval")).andReturn();
        var data=objectMapper.readTree(submitted.getResponse().getContentAsByteArray()).path("data");
        assertUserCount("13985500101",0); assertUserCount("13985500102",0);
        var snapshot=objectMapper.readTree(jdbc.queryForObject("SELECT payload_json FROM oa_workflow_request WHERE id=?",String.class,data.path("requestId").asLong()));
        assertEquals("324",snapshot.path("rows").get(1).path("department").asText());
        assertEquals("综合部",snapshot.path("rows").get(1).path("deptName").asText());
        String approver=login("17600000001");
        mockMvc.perform(post("/activiti/process/complete").header("Authorization","Bearer "+approver)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                        "taskId",data.path("taskId").asText(),"instanceId",data.path("instanceId").asText(),
                        "variables",Map.of("pass",true,"comment","同意批量邀请")))))
                .andExpect(jsonPath("$.code").value(200));
        assertUserCount("13985500101",1); assertUserCount("13985500102",1);
        assertEquals(324L,jdbc.queryForObject("SELECT dept_id FROM sys_user WHERE user_name='13985500102'",Long.class));
    }

    private void updateButton(String token, String actionCode, Map<String, Object> body, int code) throws Exception {
        mockMvc.perform(put("/system/module/341001/actions/" + actionCode).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code));
    }

    private void publishFlow(String token, String actionCode, boolean approvalEnabled) throws Exception {
        mockMvc.perform(put("/system/module/341001/actions/" + actionCode)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flowDesignEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        Map<String, Object> processData = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start", "name", "发起人"),
                        Map.of(
                                "id", "approval_1",
                                "type", "approval",
                                "name", "人事经理审批",
                                "approver", "17600000001"
                        ),
                        Map.of("id", "end", "type", "end", "name", "流程结束")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "approval_1"),
                        Map.of("source", "approval_1", "target", "end")
                )
        );
        mockMvc.perform(post("/activiti/mongo/setProcess")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tenantAppId", 1,
                                "menuId", 341001,
                                "menuName", "成员管理",
                                "actionCode", actionCode,
                                "approvalEnabled", approvalEnabled,
                                "processData", processData
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private String login(String username) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "admin123123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("token")
                .asText();
    }

    private void assertUserCount(String phone, int expected) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE phonenumber = ?",
                Integer.class,
                phone
        );
        org.junit.jupiter.api.Assertions.assertEquals(expected, count);
    }
}
