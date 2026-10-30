package com.seal.oa.system;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织权限统一判定入口。
 *
 * <p>当前系统唯一授权来源是模块授权：
 * <ul>
 *   <li>{@code sys_role_module} 决定菜单、页面和按钮动作；</li>
 *   <li>{@code sys_role.data_scope} 决定组织数据的可见范围；</li>
 *   <li>{@code sys_admin_identity} 和 admin 角色共同表示固定的唯一超级管理员；</li>
 *   <li>其他人员的管理权限统一由角色模块授权和角色数据范围决定。</li>
 * </ul>
 *
 * <p>旧权限码表 {@code sys_menu}/{@code sys_role_menu} 不再参与鉴权。
 */
@Service
public class OrganizationPermissionService {

    /** 系统 → 组织机构 */
    public static final long ORG_MODULE_ID = 340782L;
    /** 系统 → 角色权限 */
    public static final long ROLE_MODULE_ID = 340597L;

    private static final String SCOPE_ALL = "1";
    private static final String SCOPE_SELF = "5";

    private final JdbcTemplate jdbc;
    private final ModuleService moduleService;
    private final ModuleActionService moduleActionService;
    private final SystemService systemService;

    public OrganizationPermissionService(
            JdbcTemplate jdbc,
            ModuleService moduleService,
            ModuleActionService moduleActionService,
            SystemService systemService
    ) {
        this.jdbc = jdbc;
        this.moduleService = moduleService;
        this.moduleActionService = moduleActionService;
        this.systemService = systemService;
    }

    public String strategy() {
        return "module";
    }

    public Map<String, Object> evaluate(Long userId) {
        List<String> roleKeys = systemService.roleKeys(userId);
        List<String> permissions = systemService.permissions(userId);
        boolean adminRole = roleKeys.contains("admin");
        boolean superAdminIdentity = systemService.isSuperAdminIdentity(userId);
        boolean isAdmin = adminRole || superAdminIdentity;
        boolean unrestricted = isAdmin || permissions.contains("*:*:*");

        Set<Long> moduleIds = moduleService.roleModuleIds(userId);
        boolean orgModuleGranted = moduleIds.contains(ORG_MODULE_ID);
        boolean roleModuleGranted = moduleIds.contains(ROLE_MODULE_ID);
        String dataScope = widestDataScope(userId);
        boolean scopeCoversOthers = coversOthers(dataScope);

        boolean teamManage = moduleActionService.hasAction(userId, ORG_MODULE_ID, "manage");
        boolean roleManage = moduleActionService.hasAction(userId, ROLE_MODULE_ID, "manage");

        boolean teamRead = unrestricted || (orgModuleGranted && scopeCoversOthers);
        teamManage = unrestricted
                || (orgModuleGranted && teamManage && scopeCoversOthers);
        boolean roleRead = unrestricted || roleModuleGranted;
        roleManage = unrestricted || (roleModuleGranted && roleManage && SCOPE_ALL.equals(dataScope));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("is_admin", isAdmin);
        // sys_user.is_owner 在参考数据中并不专指超级管理员，不能再作为越权依据。
        result.put("is_owner", isAdmin);
        result.put("has_team_read_perms", teamRead);
        result.put("has_team_manage_perms", teamManage);
        result.put("has_role_read_perms", roleRead);
        result.put("has_role_manage_perms", roleManage);

        // 诊断信息供接口排查使用
        result.put("strategy", "module");
        result.put("data_scope", dataScope);
        result.put("org_module_granted", orgModuleGranted);
        result.put("role_module_granted", roleModuleGranted);
        return result;
    }

    /**
     * 用户所有启用角色中最宽的数据范围。数值越小范围越宽：
     * 1 全部，2 自定义，3 本部门，4 本部门及以下，5 仅本人。
     */
    public String widestDataScope(Long userId) {
        List<String> scopes = jdbc.queryForList("""
                SELECT r.data_scope
                FROM sys_role r
                JOIN sys_user_role ur ON ur.role_id = r.role_id
                WHERE ur.user_id = ? AND r.status = '0' AND r.del_flag = '0'
                """, String.class, userId);
        String widest = SCOPE_SELF;
        int widestRank = rank(SCOPE_SELF);
        for (String scope : scopes) {
            int current = rank(scope);
            if (current < widestRank) {
                widestRank = current;
                widest = scope == null || scope.isBlank() ? SCOPE_SELF : scope;
            }
        }
        return widest;
    }

    private boolean coversOthers(String dataScope) {
        int current = rank(dataScope);
        return current >= rank(SCOPE_ALL) && current < rank(SCOPE_SELF);
    }

    private int rank(String dataScope) {
        if (dataScope == null || dataScope.isBlank()) {
            return 5;
        }
        return switch (dataScope.trim()) {
            case SCOPE_ALL -> 1;
            case "2" -> 2;
            case "3" -> 3;
            case "4" -> 4;
            default -> 5;
        };
    }
}
