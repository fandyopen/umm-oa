package com.seal.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.system.ModuleActionService;
import com.seal.oa.system.OrganizationPermissionService;
import com.seal.oa.system.SystemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = """
        spring.datasource.url=jdbc:h2:mem:umm_oa_crm;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1
        """)
@AutoConfigureMockMvc
class CrmIntegrationTest {

    private static final long SELF_ONLY_EMPLOYEE_ID = 900L;
    private static final String SELF_ONLY_EMPLOYEE = "17690000009";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ModuleActionService moduleActionService;

    @Autowired
    private OrganizationPermissionService organizationPermissionService;

    @Autowired
    private SystemService systemService;

    @Test
    void crmMenusAndActionsUseTheConvertedMarketingModule() throws Exception {
        String authorization = login("17630135660");

        MvcResult result = mockMvc.perform(get("/getRouters")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode roots = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data");

        assertModuleRoute(roots, 340310L, "/crm");
        assertModuleRoute(roots, 340352L, "customers");
        assertModuleRoute(roots, 340590L, "opportunities");
        assertModuleRoute(roots, 340644L, "analysis");
        assertModuleRoute(roots, 341101L, "risks");

        assertEquals(
                List.of("add", "edit", "remove", "export", "track"),
                actionCodes(340352L)
        );
        assertEquals(
                List.of("add", "edit", "remove", "track"),
                actionCodes(340590L)
        );
    }

    @Test
    @Transactional
    void customerImportReportsBadRowsAndExportReturnsFilteredAuthorizedRows() throws Exception {
        String admin = login("17630135660");
        mockMvc.perform(post("/project/merchant/import")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rows": [
                                    {"name":"CRM 批量导入客户","serial":"CRM-IMPORT-001","industry":"教育","companyScale":"51-100人"},
                                    {"name":"CRM 批量导入客户","serial":"CRM-IMPORT-002","industry":"金融"},
                                    {"name":"CRM 缺少行业客户","serial":"CRM-IMPORT-003","industry":""}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.imported").value(1))
                .andExpect(jsonPath("$.data.skipped").value(2))
                .andExpect(jsonPath("$.data.errors[0].row").value(3))
                .andExpect(jsonPath("$.data.errors[0].reason").value("客户名称或税号已存在，请勿重复建档"))
                .andExpect(jsonPath("$.data.errors[1].row").value(4))
                .andExpect(jsonPath("$.data.errors[1].reason").value("行业不能为空"));

        mockMvc.perform(get("/project/merchant/export")
                        .param("name", "CRM 批量导入客户")
                        .param("publicPool", "0")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].industry").value("教育"))
                .andExpect(jsonPath("$.data[0].companyScale").value("51-100人"));

        createSelfOnlyEmployee();
        grantRoleModule(admin, 2L, 340352L);
        String employee = login(SELF_ONLY_EMPLOYEE);
        mockMvc.perform(get("/project/merchant/export")
                        .header("Authorization", employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        grantRoleAction(admin, 2L, 340352L, "export");
        mockMvc.perform(get("/project/merchant/export")
                        .param("name", "CRM 批量导入客户")
                        .header("Authorization", employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Transactional
    void customerLifecycleSupportsContactsFollowupsPoolAndHandover() throws Exception {
        String admin = login("17630135660");
        createSelfOnlyEmployee();

        long customerId = createCustomer(admin, """
                {
                  "name": "CRM 生命周期客户",
                  "serial": "CRM-LIFECYCLE-001",
                  "taxNumber": "91310000CRM000001",
                  "industry": "软件与信息服务",
                  "companyScale": "101-500人",
                  "region": "华东",
                  "enterpriseNature": "民营企业",
                  "customerSource": "官网咨询",
                  "cooperationStatus": "潜在客户",
                  "ownerUserId": 900,
                  "sealMerchantUsers": [
                    {
                      "name": "张决策",
                      "position": "总经理",
                      "phone": "13800000001",
                      "wechat": "zhang-decision",
                      "email": "zhang@example.com",
                      "decisionRole": "最终决策",
                      "primaryContact": "1"
                    },
                    {
                      "name": "李执行",
                      "position": "项目经理",
                      "phone": "13800000002",
                      "decisionRole": "业务执行",
                      "primaryContact": "0"
                    }
                  ],
                  "collaboratorIds": [9]
                }
                """);

        mockMvc.perform(post("/project/merchant")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "CRM 生命周期客户",
                                  "taxNumber": "91310000CRM000001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));

        mockMvc.perform(get("/project/merchant/" + customerId)
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.industry").value("软件与信息服务"))
                .andExpect(jsonPath("$.data.sealMerchantUsers.length()").value(2))
                .andExpect(jsonPath("$.data.collaborators.length()").value(1));

        mockMvc.perform(post("/project/merchant/" + customerId + "/followups")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "followupTime": "2026-10-29 10:00:00",
                                  "method": "会议",
                                  "content": "确认年度招聘服务需求",
                                  "nextPlan": "下周提交服务方案",
                                  "nextFollowupTime": "2026-11-05 10:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        MvcResult followupsResult = mockMvc.perform(
                        get("/project/merchant/" + customerId + "/followups")
                                .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();
        long followupId = objectMapper.readTree(followupsResult.getResponse().getContentAsString())
                .path("data").get(0).path("id").asLong();

        mockMvc.perform(delete("/project/merchant/followup/" + followupId)
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/project/merchant/" + customerId + "/assign")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerUserId": 900, "collaboratorIds": [9]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/project/merchant/" + customerId + "/release")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        grantRoleModule(admin, 2L, 340352L);
        jdbc.update("UPDATE sys_role SET data_scope = '5' WHERE role_id = 2");
        assertEquals("5", organizationPermissionService.widestDataScope(SELF_ONLY_EMPLOYEE_ID));
        assertFalse(systemService.isSystemAdmin(SELF_ONLY_EMPLOYEE_ID));
        String employee = login(SELF_ONLY_EMPLOYEE);

        mockMvc.perform(get("/project/merchant/list")
                        .param("publicPool", "1")
                        .param("name", "CRM 生命周期客户")
                        .header("Authorization", employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(post("/project/merchant/" + customerId + "/claim")
                        .header("Authorization", employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/project/merchant/handover")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromUserId": 900, "toUserId": 9}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(1));

        String owner = jdbc.queryForObject(
                "SELECT owner_user_name FROM oa_merchant WHERE id = ?",
                String.class,
                customerId
        );
        assertEquals("苏记勇", owner);

        mockMvc.perform(post("/project/merchant/" + customerId + "/followups")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"电话","content":"保留历史的回访记录"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(delete("/project/merchant/" + customerId)
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(false))
                .andExpect(jsonPath("$.data.disabled").value(true));
        assertEquals("1", jdbc.queryForObject(
                "SELECT status FROM oa_merchant WHERE id = ?", String.class, customerId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM oa_customer_followup WHERE merchant_id = ?", Integer.class, customerId));
    }

    @Test
    @Transactional
    void unreferencedCustomerCanBeDeleted() throws Exception {
        String admin = login("17630135660");
        long customerId = createCustomer(admin, """
                {"name":"CRM 未引用草稿","serial":"CRM-DELETE-001","industry":"教育"}
                """);

        mockMvc.perform(delete("/project/merchant/" + customerId)
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.disabled").value(false));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM oa_merchant WHERE id = ?", Integer.class, customerId));

        long activeCustomerId = createCustomer(admin, """
                {"name":"CRM 已建档客户","serial":"CRM-DELETE-002","industry":"制造业","cooperationStatus":"合作中"}
                """);
        mockMvc.perform(delete("/project/merchant/" + activeCustomerId)
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(false))
                .andExpect(jsonPath("$.data.disabled").value(true));
        assertEquals("1", jdbc.queryForObject(
                "SELECT status FROM oa_merchant WHERE id = ?", String.class, activeCustomerId));
    }

    @Test
    @Transactional
    void customerDetailAndFollowupsRespectDataScopeAndTrackAction() throws Exception {
        String admin = login("17630135660");
        createSelfOnlyEmployee();
        long ownCustomerId = createCustomer(admin, """
                {
                  "name": "CRM 本人客户",
                  "serial": "CRM-SCOPE-OWN",
                  "taxNumber": "91310000CRM000002",
                  "industry": "教育",
                  "ownerUserId": 900
                }
                """);
        long otherCustomerId = createCustomer(admin, """
                {
                  "name": "CRM 他人客户",
                  "serial": "CRM-SCOPE-OTHER",
                  "taxNumber": "91310000CRM000003",
                  "industry": "金融",
                  "ownerUserId": 1
                }
                """);

        grantRoleModule(admin, 2L, 340352L);
        jdbc.update("UPDATE sys_role SET data_scope = '5' WHERE role_id = 2");
        assertEquals("5", organizationPermissionService.widestDataScope(SELF_ONLY_EMPLOYEE_ID));
        assertFalse(systemService.isSystemAdmin(SELF_ONLY_EMPLOYEE_ID));
        String employee = login(SELF_ONLY_EMPLOYEE);

        mockMvc.perform(get("/project/merchant/list")
                        .param("name", "CRM ")
                        .param("pageSize", "200")
                        .header("Authorization", employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].id").value(ownCustomerId));

        mockMvc.perform(get("/project/merchant/" + otherCustomerId)
                        .header("Authorization", employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/project/merchant/" + ownCustomerId + "/followups")
                        .header("Authorization", employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method": "电话", "content": "第一次联系"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        grantRoleAction(admin, 2L, 340352L, "track");
        employee = login(SELF_ONLY_EMPLOYEE);

        mockMvc.perform(post("/project/merchant/" + ownCustomerId + "/followups")
                        .header("Authorization", employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method": "电话", "content": "第一次联系"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Transactional
    void opportunityAnalysisAndRiskTodosFormAClosedCrmLoop() throws Exception {
        String admin = login("17630135660");
        long customerId = createCustomer(admin, """
                {
                  "name": "CRM 风险分析客户",
                  "serial": "CRM-RISK-001",
                  "taxNumber": "91310000CRM000004",
                  "industry": "制造业",
                  "companyScale": "501-1000人",
                  "region": "华南",
                  "enterpriseNature": "民营企业",
                  "customerSource": "渠道合作",
                  "cooperationStatus": "合作中",
                  "ownerUserId": 1,
                  "lastFollowupTime": "2026-01-01 09:00:00",
                  "contractExpireDate": "%s",
                  "lastPaymentDate": "%s",
                  "contractAmount": 100000,
                  "receivedAmount": 20000,
                  "cooperationPositions": 3,
                  "referralCount": 8,
                  "hireCount": 4
                }
                """.formatted(
                LocalDate.now().plusDays(10),
                LocalDate.now().minusDays(90)
        ));

        MvcResult businessResult = mockMvc.perform(post("/project/business")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "CRM 年度招聘服务商机",
                                  "code": "CRM-OPP-001",
                                  "customerId": %d,
                                  "customerName": "CRM 风险分析客户",
                                  "stage": "需求确认",
                                  "potentialCooperation": "年度招聘服务",
                                  "requirement": "需要招聘 20 名技术岗位",
                                  "cooperationIntent": "高",
                                  "expectedAmount": 260000,
                                  "expectedCloseDate": "2026-12-31",
                                  "ownerUserId": 1,
                                  "ownerUserName": "方铭渴",
                                  "statusLabel": "推进中"
                                }
                                """.formatted(customerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        long businessId = objectMapper.readTree(businessResult.getResponse().getContentAsString())
                .path("data").asLong();

        mockMvc.perform(post("/project/business/stage/insert")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessCode": "CRM-OPP-001",
                                  "state": "方案报价",
                                  "recordTime": "2026-10-29 11:00:00",
                                  "remark": "已提交年度招聘方案"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/project/business/" + businessId)
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.stage").value("方案报价"))
                .andExpect(jsonPath("$.data.sealBusinessStages.length()").value(1));

        mockMvc.perform(get("/project/crm/analysis/overview")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.summary.length()").value(8))
                .andExpect(jsonPath("$.data.opportunityCount").isNumber());

        mockMvc.perform(post("/project/crm/risk/sync")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.riskCount").value(3))
                .andExpect(jsonPath("$.data.createdTodos").value(3));

        mockMvc.perform(get("/project/crm/risk/list")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(3));

        Long todos = jdbc.queryForObject("""
                SELECT COUNT(*) FROM oa_todo
                WHERE business_type = 'crmRisk' AND business_id = ? AND status = 'todo'
                """, Long.class, customerId);
        assertNotNull(todos);
        assertEquals(3L, todos);

        jdbc.update("""
                UPDATE oa_merchant
                SET last_followup_time = CURRENT_TIMESTAMP,
                    contract_expire_date = ?,
                    last_payment_date = CURRENT_DATE,
                    received_amount = contract_amount
                WHERE id = ?
                """, LocalDate.now().plusDays(120), customerId);
        mockMvc.perform(post("/project/crm/risk/sync")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.riskCount").value(0))
                .andExpect(jsonPath("$.data.createdTodos").value(0));
        Long openTodos = jdbc.queryForObject("""
                SELECT COUNT(*) FROM oa_todo
                WHERE business_type = 'crmRisk' AND business_id = ? AND status = 'todo'
                """, Long.class, customerId);
        assertEquals(0L, openTodos);
    }

    private long createCustomer(String authorization, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/project/merchant")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        long id = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").asLong();
        assertTrue(id > 0L);
        return id;
    }

    private void createSelfOnlyEmployee() {
        jdbc.update("""
                INSERT INTO sys_user (
                    user_id, tenant_id, dept_id, user_name, nick_name, password, email,
                    phonenumber, sex, avatar, status, del_flag, is_admin, is_owner,
                    create_by, remark
                )
                SELECT ?, 1, 325, ?, 'CRM 普通员工', password, ?, ?,
                       '0', '', '0', '0', '0', '0', 'system', 'CRM data scope test user'
                FROM sys_user WHERE user_id = 4
                """,
                SELF_ONLY_EMPLOYEE_ID,
                SELF_ONLY_EMPLOYEE,
                SELF_ONLY_EMPLOYEE + "@example.com",
                SELF_ONLY_EMPLOYEE);
        jdbc.update("""
                INSERT INTO sys_user_role (user_id, role_id) VALUES (?, 2)
                """, SELF_ONLY_EMPLOYEE_ID);
    }

    private void grantRoleModule(String authorization, long roleId, long moduleId) throws Exception {
        mockMvc.perform(post("/app/user/addModule")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId": %d, "tenantModuleIds": [%d]}
                                """.formatted(roleId, moduleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private void grantRoleAction(
            String authorization,
            long roleId,
            long moduleId,
            String actionCode
    ) throws Exception {
        mockMvc.perform(post("/app/user/addModule")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleId": %d,
                                  "tenantModuleIds": [%d, "action:%d:%s"]
                                }
                                """.formatted(roleId, moduleId, moduleId, actionCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private List<String> actionCodes(long moduleId) {
        return moduleActionService.actions(moduleId).stream()
                .map(action -> String.valueOf(action.get("actionCode")))
                .toList();
    }

    private void assertModuleRoute(JsonNode nodes, long moduleId, String path) {
        JsonNode module = findModule(nodes, moduleId);
        assertNotNull(module, "Missing CRM module " + moduleId);
        assertEquals(path, module.path("path").asText());
    }

    private JsonNode findModule(JsonNode nodes, long moduleId) {
        for (JsonNode node : nodes) {
            if (node.path("moduleId").asLong() == moduleId) {
                return node;
            }
            JsonNode nested = findModule(node.path("children"), moduleId);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"admin123123"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString())
                .path("token").asText();
    }
}
