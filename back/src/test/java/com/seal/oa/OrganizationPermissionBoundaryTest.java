package com.seal.oa;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 组织权限判定的边界与回归用例。
 *
 * <p>判定模型只有一套：{@code sys_role_module} 决定模块，{@code sys_role.data_scope} 决定范围，
 * {@code admin} 角色 / {@code sys_admin_identity} 表示系统级不受限。
 * 本类专门覆盖容易回退的边界，避免后续改动把越权口子重新打开。
 */
@SpringBootTest(properties = """
        spring.datasource.url=jdbc:h2:mem:umm_oa_org_boundary;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1
        """)
@AutoConfigureMockMvc
@Transactional
class OrganizationPermissionBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * sys_user.is_owner / is_admin 是参考站的展示标记，不能单独作为越权依据。
     * 参考站成员「金么算」两者都是 '1'，但没有 admin 角色，也没有组织机构模块。
     */
    @Test
    void ownerAndAdminFlagsAloneDoNotGrantOrganizationAccess() throws Exception {
        String authorization = login("17694407532");

        mockMvc.perform(get("/getInfo").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.isOwner").value("1"))
                .andExpect(jsonPath("$.user.isAdmin").value("1"))
                .andExpect(jsonPath("$.permissions", not(hasItem("*:*:*"))));

        mockMvc.perform(get("/crm/user/get_user_info").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perms.strategy").value("module"))
                .andExpect(jsonPath("$.perms.is_admin").value(false))
                .andExpect(jsonPath("$.perms.is_owner").value(false))
                .andExpect(jsonPath("$.perms.org_module_granted").value(false))
                .andExpect(jsonPath("$.perms.has_team_read_perms").value(false))
                .andExpect(jsonPath("$.perms.has_team_manage_perms").value(false))
                .andExpect(jsonPath("$.perms.has_role_read_perms").value(false));
    }

    /**
     * data_scope = 5（仅本人）表示看不到他人的组织关系，即使持有组织机构模块。
     */
    @Test
    void selfOnlyDataScopeRevokesOrganizationAccess() throws Exception {
        String authorization = login("17655872713");

        jdbc.update("UPDATE sys_role SET data_scope = '5' WHERE role_id IN (12, 13, 14)");

        mockMvc.perform(get("/crm/user/get_user_info").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perms.data_scope").value("5"))
                .andExpect(jsonPath("$.perms.org_module_granted").value(true))
                .andExpect(jsonPath("$.perms.has_team_read_perms").value(false))
                .andExpect(jsonPath("$.perms.has_team_manage_perms").value(false));
    }

    /**
     * 本部门（3）同样属于可管理他人的范围，只有 5 才收回。
     */
    @Test
    void departmentDataScopeKeepsOrganizationAccess() throws Exception {
        String authorization = login("17655872713");

        jdbc.update("UPDATE sys_role SET data_scope = '3' WHERE role_id IN (12, 13, 14)");

        mockMvc.perform(get("/crm/user/get_user_info").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perms.data_scope").value("3"))
                .andExpect(jsonPath("$.perms.has_team_read_perms").value(true))
                .andExpect(jsonPath("$.perms.has_team_manage_perms").value(true));
    }

    /**
     * 角色授权是系统级配置：本部门范围只能只读，只有全部数据范围（1）才允许改动。
     */
    @Test
    void roleManageRequiresFullDataScope() throws Exception {
        jdbc.update("UPDATE sys_role SET data_scope = '4' WHERE role_id = 19");
        String authorization = login("17600000001");

        mockMvc.perform(get("/crm/user/get_user_info").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perms.data_scope").value("4"))
                .andExpect(jsonPath("$.perms.has_role_read_perms").value(true))
                .andExpect(jsonPath("$.perms.has_role_manage_perms").value(false));

        jdbc.update("UPDATE sys_role SET data_scope = '1' WHERE role_id = 19");
        jdbc.update("""
                MERGE INTO sys_role_module_action (role_id, module_id, action_code)
                KEY (role_id, module_id, action_code)
                VALUES (19, 340597, 'manage')
                """);

        mockMvc.perform(get("/crm/user/get_user_info").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perms.data_scope").value("1"))
                .andExpect(jsonPath("$.perms.has_role_read_perms").value(true))
                .andExpect(jsonPath("$.perms.has_role_manage_perms").value(true));
    }

    /**
     * 诊断接口必须与 get_user_info 的 perms 同源，否则排查时会被误导。
     */
    @Test
    void diagnosisEndpointMirrorsUserPermissionPayload() throws Exception {
        jdbc.update("UPDATE sys_role SET data_scope = '4' WHERE role_id = 19");
        String authorization = login("17600000001");

        mockMvc.perform(get("/system/permission/organization").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strategy").value("module"))
                .andExpect(jsonPath("$.data.data_scope").value("4"))
                .andExpect(jsonPath("$.data.org_module_granted").value(true))
                .andExpect(jsonPath("$.data.role_module_granted").value(true))
                .andExpect(jsonPath("$.data.has_team_read_perms").value(true))
                .andExpect(jsonPath("$.data.has_role_manage_perms").value(false));
    }

    /**
     * 没有组织机构模块授权的账号，菜单树里不应出现「系统 → 组织机构」。
     */
    @Test
    void organizationMenuAbsentWithoutModuleGrant() throws Exception {
        String authorization = login("15609825919");

        mockMvc.perform(get("/getRouters").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].path", not(hasItem("/system"))));
    }

    /**
     * 被授权的账号必须能在菜单树里看到「系统」，否则页面无法进入。
     */
    @Test
    void organizationMenuPresentWithModuleGrant() throws Exception {
        String authorization = login("17655872713");

        mockMvc.perform(get("/getRouters").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].path", hasItem("/system")));
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
