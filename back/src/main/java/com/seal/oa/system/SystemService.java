package com.seal.oa.system;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SystemService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final ModuleActionService moduleActionService;

    public SystemService(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            ModuleActionService moduleActionService
    ) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.moduleActionService = moduleActionService;
    }

    public Optional<UserAccount> findLoginUser(String loginName) {
        List<UserAccount> users = jdbc.query("""
                SELECT user_id, tenant_id, dept_id, user_name, nick_name, password,
                       avatar, sex, phonenumber, status
                FROM sys_user
                WHERE (user_name = ? OR phonenumber = ?) AND del_flag = '0'
                LIMIT 1
                """, this::mapUser, loginName, loginName);
        return users.stream().findFirst();
    }

    public UserAccount requireCurrentUser() {
        return findById(AuthContext.userId())
                .orElseThrow(() -> new ApiException(401, "登录用户不存在"));
    }

    public Optional<UserAccount> findById(Long userId) {
        List<UserAccount> users = jdbc.query("""
                SELECT user_id, tenant_id, dept_id, user_name, nick_name, password,
                       avatar, sex, phonenumber, status
                FROM sys_user
                WHERE user_id = ? AND del_flag = '0'
                """, this::mapUser, userId);
        return users.stream().findFirst();
    }

    public boolean passwordMatches(UserAccount user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.password());
    }

    public Map<String, Object> userDetails(UserAccount user) {
        return userDetails(user, roleKeys(user.userId()));
    }

    public Map<String, Object> userDetails(UserAccount user, List<String> roles) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.userId());
        result.put("tenantId_", user.tenantId());
        result.put("userName", user.username());
        result.put("nickName", user.nickname());
        result.put("email", "");
        result.put("phonenumber", user.phoneNumber());
        result.put("sex", user.sex());
        result.put("avatar", user.avatar());
        result.put("status", user.status());
        result.put("delFlag", "0");
        result.put("isAdmin", isAdmin(user.userId()) ? "1" : "0");
        result.put("isOwner", isOwner(user.userId()) ? "1" : "0");
        result.put("deptId", user.deptId());
        result.put("dept", dept(user.deptId()));
        result.put("roles", roles);
        return result;
    }

    public List<String> roleKeys(Long userId) {
        if (isSystemAdmin(userId)) {
            return List.of("admin");
        }
        return jdbc.queryForList("""
                SELECT DISTINCT r.role_key
                FROM sys_role r
                JOIN sys_user_role ur ON ur.role_id = r.role_id
                WHERE ur.user_id = ? AND r.status = '0' AND r.del_flag = '0'
                """, String.class, userId);
    }

    public List<String> permissions(Long userId) {
        return permissions(userId, roleKeys(userId));
    }

    public List<String> permissions(Long userId, List<String> roles) {
        if (roles.contains("admin")) {
            return List.of("*:*:*");
        }
        List<String> modulePermissions = jdbc.queryForList("""
                SELECT DISTINCT m.perms
                FROM sys_role_module rm
                JOIN sys_module m ON m.module_id = rm.module_id
                JOIN sys_role r ON r.role_id = rm.role_id
                JOIN sys_user_role ur ON ur.role_id = rm.role_id
                WHERE ur.user_id = ? AND r.status = '0' AND r.del_flag = '0'
                  AND m.status = '0'
                  AND m.perms IS NOT NULL
                  AND m.perms <> ''
                  AND m.perms LIKE '%:list'
                """, String.class, userId);
        List<String> actionPermissions = jdbc.queryForList("""
                SELECT DISTINCT ma.permission_code
                FROM sys_role_module_action rma
                JOIN sys_module_action ma
                  ON ma.module_id = rma.module_id AND ma.action_code = rma.action_code
                JOIN sys_user_role ur ON ur.role_id = rma.role_id
                JOIN sys_role r ON r.role_id = rma.role_id
                WHERE ur.user_id = ? AND r.status = '0' AND r.del_flag = '0'
                  AND ma.permission_code IS NOT NULL
                  AND ma.permission_code <> ''
                ORDER BY ma.permission_code
                """, String.class, userId);
        return java.util.stream.Stream.concat(
                        modulePermissions.stream(),
                        actionPermissions.stream()
                )
                .filter(permission -> permission != null && !permission.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 是否不受授权过滤：角色 admin、系统管理员身份，或持有通配权限。
     * 菜单下发与组织权限判定统一走这里，避免各处自行拼装 admin 判断。
     */
    public boolean isUnrestricted(Long userId) {
        return roleKeys(userId).contains("admin");
    }

    public boolean hasModuleAction(Long userId, long moduleId, String actionCode) {
        return isUnrestricted(userId)
                || moduleActionService.hasAction(userId, moduleId, actionCode)
                || (!"manage".equals(actionCode)
                    && moduleActionService.hasAction(userId, moduleId, "manage"));
    }

    public boolean hasModuleAccess(Long userId, long moduleId) {
        return isUnrestricted(userId)
                || moduleActionService.hasModule(userId, moduleId);
    }

    public boolean isAdmin(Long userId) {
        return flag(userId, "is_admin");
    }

    public boolean isOwner(Long userId) {
        return flag(userId, "is_owner");
    }

    public boolean isSystemAdmin(Long userId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sys_admin_identity identity
                JOIN sys_user u ON u.user_name = identity.user_name
                WHERE u.user_id = ? AND u.del_flag = '0'
                """, Long.class, userId);
        return count != null && count > 0L;
    }

    private boolean flag(Long userId, String column) {
        List<String> values = jdbc.queryForList(
                "SELECT " + column + " FROM sys_user WHERE user_id = ? AND del_flag = '0'",
                String.class,
                userId
        );
        return !values.isEmpty() && "1".equals(values.get(0));
    }

    public Map<String, Object> dept(Long deptId) {
        if (deptId == null) {
            return Map.of();
        }
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT dept_id, parent_id, ancestors, dept_name, order_num, leader,
                       phone, email, status
                FROM sys_dept
                WHERE dept_id = ?
                """, (resultSet, rowNumber) -> {
            Map<String, Object> dept = new LinkedHashMap<>();
            dept.put("deptId", resultSet.getLong("dept_id"));
            dept.put("parentId", resultSet.getLong("parent_id"));
            dept.put("ancestors", resultSet.getString("ancestors"));
            dept.put("deptName", resultSet.getString("dept_name"));
            dept.put("orderNum", resultSet.getInt("order_num"));
            dept.put("leader", resultSet.getString("leader"));
            dept.put("phone", resultSet.getString("phone"));
            dept.put("email", resultSet.getString("email"));
            dept.put("status", resultSet.getString("status"));
            return dept;
        }, deptId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public List<String> posts(Long userId) {
        return jdbc.queryForList("""
                SELECT p.post_code
                FROM sys_post p
                JOIN sys_user_post up ON up.post_id = p.post_id
                WHERE up.user_id = ?
                ORDER BY p.post_sort
                """, String.class, userId);
    }

    private UserAccount mapUser(ResultSet resultSet, int rowNumber) throws SQLException {
        return new UserAccount(
                resultSet.getLong("user_id"),
                resultSet.getLong("tenant_id"),
                resultSet.getLong("dept_id"),
                resultSet.getString("user_name"),
                resultSet.getString("nick_name"),
                resultSet.getString("password"),
                resultSet.getString("avatar"),
                resultSet.getString("sex"),
                resultSet.getString("phonenumber"),
                resultSet.getString("status")
        );
    }

    public record UserAccount(
            Long userId,
            Long tenantId,
            Long deptId,
            String username,
            String nickname,
            String password,
            String avatar,
            String sex,
            String phoneNumber,
            String status
    ) {
    }
}

