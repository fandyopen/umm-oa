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

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模块授权策略下的组织权限判定。
 *
 * <p>验证重点：组织权限以 sys_role_module（340782 / 340597）+ sys_role.data_scope 为准，
 * 不再依赖此前被清空的 system:user:* 权限码。
 */
@SpringBootTest(properties = """
        spring.datasource.url=jdbc:h2:mem:umm_oa_org_module;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1
        """)
@AutoConfigureMockMvc
class OrganizationPermissionModuleStrategyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void hrManagerReceivesTeamPermissionsFromModuleGrant() throws Exception {
        String authorization = login("17600000001");

        mockMvc.perform(get("/crm/user/get_user_info").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perms.strategy").value("module"))
                .andExpect(jsonPath("$.perms.data_scope").value("1"))
                .andExpect(jsonPath("$.perms.org_module_granted").value(true))
                .andExpect(jsonPath("$.perms.role_module_granted").value(true))
                .andExpect(jsonPath("$.perms.is_admin").value(false))
                .andExpect(jsonPath("$.perms.has_team_read_perms").value(true))
                .andExpect(jsonPath("$.perms.has_team_manage_perms").value(true))
                .andExpect(jsonPath("$.perms.has_role_read_perms").value(true))
                // 本部门级数据范围只允许只读角色，不允许改角色授权
                .andExpect(jsonPath("$.perms.has_role_manage_perms").value(false));

        // 菜单同样按模块授权下发：系统 → 组织机构 可见
        mockMvc.perform(get("/getRouters").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].path", hasItem("/system")));

        mockMvc.perform(get("/system/user/list?pageNum=1&pageSize=100")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));
    }

    /**
     * 账号即成员：人事经理本身就是组织架构里的一个普通成员，
     * 挂在综合管理部下，且不具备 is_admin / is_owner 标记。
     */
    @Test
    void hrManagerIsAnOrdinaryMemberOfTheOrganization() throws Exception {
        String authorization = login("17630135660");

        mockMvc.perform(get("/system/user/list")
                        .param("userName", "17600000001")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].nickName").value("人事经理"))
                .andExpect(jsonPath("$.rows[0].deptName").value("综合部"))
                .andExpect(jsonPath("$.rows[0].isAdmin").value("0"))
                .andExpect(jsonPath("$.rows[0].isOwner").value("0"))
                .andExpect(jsonPath("$.rows[0].roles[0].roleKey").value("hr_manager"));

        // 参考站成员「金么算」未被顶替
        mockMvc.perform(get("/system/user/list")
                        .param("userName", "17694407532")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].nickName").value("金么算"));
    }

    @Test
    void generalAffairsManagerReceivesTeamPermissionsFromModuleGrant() throws Exception {
        String authorization = login("17655872713");

        mockMvc.perform(get("/crm/user/get_user_info").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perms.org_module_granted").value(true))
                .andExpect(jsonPath("$.perms.has_team_read_perms").value(true))
                .andExpect(jsonPath("$.perms.has_team_manage_perms").value(true));
    }

    @Test
    void purchasingManagerWithoutModuleGrantStaysOutOfOrganization() throws Exception {
        String authorization = login("15609825919");

        mockMvc.perform(get("/crm/user/get_user_info").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perms.org_module_granted").value(false))
                .andExpect(jsonPath("$.perms.has_team_read_perms").value(false))
                .andExpect(jsonPath("$.perms.has_team_manage_perms").value(false))
                .andExpect(jsonPath("$.perms.has_role_read_perms").value(false));
    }

    @Test
    void superAdminKeepsUnrestrictedOrganizationPermissions() throws Exception {
        String authorization = login("17630135660");

        mockMvc.perform(get("/crm/user/get_user_info").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perms.is_admin").value(true))
                .andExpect(jsonPath("$.perms.is_owner").value(true))
                .andExpect(jsonPath("$.perms.has_team_read_perms").value(true))
                .andExpect(jsonPath("$.perms.has_team_manage_perms").value(true))
                .andExpect(jsonPath("$.perms.has_role_read_perms").value(true))
                .andExpect(jsonPath("$.perms.has_role_manage_perms").value(true));

        mockMvc.perform(get("/system/permission/organization").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.has_team_manage_perms").value(true));
    }

    @Test
    @Transactional
    void departmentAndDescendantsIncludesDepartmentsAtTheEndOfAncestors() throws Exception {
        jdbc.update("UPDATE sys_role SET data_scope = '4' WHERE role_id = 19");
        jdbc.update("""
                INSERT INTO sys_dept (
                    dept_id, tenant_id, parent_id, ancestors, dept_name, order_num,
                    leader, phone, status, del_flag
                ) VALUES (9000, 1, 324, '0,322,324', 'Test child department', 99, '', '', '0', '0')
                """);
        jdbc.update("""
                INSERT INTO sys_user (
                    user_id, tenant_id, dept_id, user_name, nick_name, password,
                    status, del_flag
                ) VALUES (
                    9000, 1, 9000, '19000000000', 'Test child user',
                    '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
                    '0', '0'
                )
                """);

        String authorization = login("17600000001");
        mockMvc.perform(get("/system/user/list?pageNum=1&pageSize=100")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.rows[*].deptId", hasItem(9000)));
    }

    @Test
    @Transactional
    void softDeletedRoleNoLongerGrantsPermissions() throws Exception {
        jdbc.update("UPDATE sys_role SET del_flag = '2' WHERE role_id = 19");

        String authorization = login("17600000001");
        mockMvc.perform(get("/getInfo").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isEmpty());

        mockMvc.perform(get("/crm/user/get_user_info").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perms.org_module_granted").value(false))
                .andExpect(jsonPath("$.perms.has_team_read_perms").value(false))
                .andExpect(jsonPath("$.perms.has_role_read_perms").value(false));
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
