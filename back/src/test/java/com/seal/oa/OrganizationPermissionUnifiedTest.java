package com.seal.oa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.seal.oa.system.ModuleActionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = """
        spring.datasource.url=jdbc:h2:mem:umm_oa_org_unified;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1
        """)
@AutoConfigureMockMvc
class OrganizationPermissionUnifiedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ModuleActionService moduleActionService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void moduleGrantControlsOrganizationAndGeneratedButtonPermissions() throws Exception {
        String authorization = login("17655872713");

        mockMvc.perform(get("/crm/user/get_user_info").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perms.strategy").value("module"))
                .andExpect(jsonPath("$.perms.is_owner").value(false))
                .andExpect(jsonPath("$.perms.org_module_granted").value(true))
                .andExpect(jsonPath("$.perms.has_team_read_perms").value(true))
                .andExpect(jsonPath("$.perms.has_team_manage_perms").value(true));

        mockMvc.perform(get("/getInfo").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasItem("system:notice:list")))
                .andExpect(jsonPath("$.permissions", hasItem("system:notice:add")))
                .andExpect(jsonPath("$.permissions", not(hasItem("system:user:add"))));
    }

    @Test
    void legacyMenuPermissionDoesNotGrantOrganizationWithoutModule() throws Exception {
        String authorization = login("15609825919");

        mockMvc.perform(get("/crm/user/get_user_info").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perms.org_module_granted").value(false))
                .andExpect(jsonPath("$.perms.has_team_read_perms").value(false))
                .andExpect(jsonPath("$.perms.has_role_read_perms").value(false));
    }

    @Test
    @Transactional
    void selectedModuleActionsControlButtonsAndBackendRequests() throws Exception {
        String adminAuthorization = login("17630135660");

        mockMvc.perform(post("/app/user/addModule")
                        .header("Authorization", adminAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleId": 19,
                                  "tenantModuleIds": [
                                    340316
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String userAuthorization = login("17600000001");
        mockMvc.perform(get("/getInfo").header("Authorization", userAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasItem("system:notice:list")))
                .andExpect(jsonPath("$.permissions", not(hasItem("system:notice:query"))))
                .andExpect(jsonPath("$.permissions", not(hasItem("system:notice:add"))));

        mockMvc.perform(post("/system/notice")
                        .header("Authorization", userAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"noticeTitle":"Denied notice","noticeType":"1","status":"0"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/app/user/addModule")
                        .header("Authorization", adminAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleId": 19,
                                  "tenantModuleIds": [
                                    340316,
                                    "action:340316:add"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/system/notice")
                        .header("Authorization", userAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "noticeTitle": "ACTION ALLOWED",
                                  "noticeType": "1",
                                  "status": "0"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Transactional
    void moduleGrantAllowsReadsAndActionGrantControlsWrites() throws Exception {
        String adminAuthorization = login("17630135660");

        mockMvc.perform(post("/app/user/addModule")
                        .header("Authorization", adminAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleId": 19,
                                  "tenantModuleIds": [
                                    340316
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String userAuthorization = login("17600000001");
        mockMvc.perform(get("/system/notice/list")
                        .header("Authorization", userAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/app/user/addModule")
                        .header("Authorization", adminAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleId": 19,
                                  "tenantModuleIds": [
                                    340316
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/system/notice")
                        .header("Authorization", userAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"noticeTitle":"Permission denied","noticeType":"1","status":"0"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/app/user/addModule")
                        .header("Authorization", adminAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleId": 19,
                                  "tenantModuleIds": [
                                    340316,
                                    "action:340316:add"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/system/notice")
                        .header("Authorization", userAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"noticeTitle":"Permission allowed","noticeType":"1","status":"0"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void departmentAndUserModuleTreeEndpointsAreRetired() throws Exception {
        String authorization = login("17630135660");

        mockMvc.perform(get("/app/user/deptMenuTree?deptId=324")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(410));

        mockMvc.perform(get("/app/user/userMenuTree?userId=35")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(410));
    }

    @Test
    void businessModuleOnlyExposesMeaningfulButtonActions() {
        List<String> actionCodes = moduleActionService.actions(340590L).stream()
                .map(action -> String.valueOf(action.get("actionCode")))
                .toList();

        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("add", "edit", "remove", "track", "transfer"),
                actionCodes
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                moduleActionService.actions(340351L).isEmpty()
        );
        org.junit.jupiter.api.Assertions.assertTrue(moduleActionService.actions(340343L).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(moduleActionService.actions(340746L).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(moduleActionService.actions(340481L).isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("remove", "export"),
                actionCodes(340318L)
        );
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("invite", "transfer", "dismiss"),
                actionCodes(341001L)
        );
    }

    @Test
    void hrManagerGetsStandaloneMenuAndSharedMemberActions() throws Exception {
        String authorization = login("17600000001");

        mockMvc.perform(get("/getInfo").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasItem("hr:member:list")))
                .andExpect(jsonPath("$.permissions", hasItem("hr:member:invite")))
                .andExpect(jsonPath("$.permissions", hasItem("hr:member:transfer")))
                .andExpect(jsonPath("$.permissions", hasItem("hr:member:dismiss")));

        mockMvc.perform(get("/getRouters").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].path", hasItem("/hr")));

        mockMvc.perform(get("/system/user/list?pageNum=1&pageSize=10")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/system/user/dismiss_list?pageNum=1&pageSize=10")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void hrManagerRoleAppearsOnceInProcessDesignGroup() throws Exception {
        String authorization = login("17630135660");
        MvcResult result = mockMvc.perform(get("/system/role/admin/list")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode roles = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("roles");
        long hrManagerCount = 0L;
        long groupId = 0L;
        for (JsonNode role : roles) {
            if ("hr_manager".equals(role.path("roleKey").asText())) {
                hrManagerCount++;
                groupId = role.path("groupId").asLong();
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(1L, hrManagerCount);
        org.junit.jupiter.api.Assertions.assertEquals(53L, groupId);

        mockMvc.perform(post("/system/role")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleName":"人事经理","roleKey":"duplicate_hr"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @Transactional
    void inviteMemberWithoutUserNameUsesPhoneAsLoginName() throws Exception {
        String authorization = login("17600000001");

        mockMvc.perform(post("/system/user/add")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickName": "欧值到",
                                  "phonenumber": "13654343456",
                                  "email": "43434343",
                                  "countryCode": 86,
                                  "deptId": 324
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNumber());

        Map<String, Object> user = jdbc.queryForMap("""
                SELECT user_name, phonenumber, nick_name, dept_id
                FROM sys_user WHERE phonenumber = ?
                """, "13654343456");
        org.junit.jupiter.api.Assertions.assertEquals("13654343456", user.get("user_name"));
        org.junit.jupiter.api.Assertions.assertEquals("欧值到", user.get("nick_name"));
        org.junit.jupiter.api.Assertions.assertEquals(324L, ((Number) user.get("dept_id")).longValue());
    }

    @Test
    @Transactional
    void businessTrackingAndTransferUseDedicatedActions() throws Exception {
        String adminAuthorization = login("17630135660");
        String userAuthorization = login("17600000001");

        grantBusinessActions(adminAuthorization, 340590L);
        mockMvc.perform(post("/project/business/stage/insert")
                        .header("Authorization", userAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessCode":"TEST","state":"需求","remark":"track"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        grantBusinessActions(adminAuthorization, 340590L, "track");
        mockMvc.perform(post("/project/business/stage/insert")
                        .header("Authorization", userAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessCode":"TEST","state":"需求","remark":"track"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/project/business/stage/transfer")
                        .header("Authorization", userAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessCode":"TEST","state":"转化项目"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        grantBusinessActions(adminAuthorization, 340590L, "track", "transfer");
        mockMvc.perform(post("/project/business/stage/transfer")
                        .header("Authorization", userAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessCode":"TEST","state":"转化项目"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private void grantBusinessActions(
            String authorization,
            long moduleId,
            String... actionCodes
    ) throws Exception {
        List<String> values = new java.util.ArrayList<>();
        values.add(String.valueOf(moduleId));
        for (String actionCode : actionCodes) {
            values.add("action:" + moduleId + ":" + actionCode);
        }
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "roleId", 19,
                "tenantModuleIds", values
        ));
        mockMvc.perform(post("/app/user/addModule")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private List<String> actionCodes(long moduleId) {
        return moduleActionService.actions(moduleId).stream()
                .map(action -> String.valueOf(action.get("actionCode")))
                .toList();
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"admin123123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token")
                .asText();
        return "Bearer " + token;
    }
}
