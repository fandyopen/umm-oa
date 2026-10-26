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

    public SystemService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
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
        result.put("deptId", user.deptId());
        result.put("dept", dept(user.deptId()));
        result.put("roles", roleKeys(user.userId()));
        return result;
    }

    public List<String> roleKeys(Long userId) {
        return jdbc.queryForList("""
                SELECT DISTINCT r.role_key
                FROM sys_role r
                JOIN sys_user_role ur ON ur.role_id = r.role_id
                WHERE ur.user_id = ? AND r.status = '0'
                """, String.class, userId);
    }

    public List<String> permissions(Long userId) {
        List<String> permissions = jdbc.queryForList("""
                SELECT DISTINCT m.perms
                FROM sys_menu m
                JOIN sys_role_menu rm ON rm.menu_id = m.menu_id
                JOIN sys_user_role ur ON ur.role_id = rm.role_id
                WHERE ur.user_id = ? AND m.perms IS NOT NULL AND m.perms <> ''
                """, String.class, userId);
        return permissions.isEmpty() && roleKeys(userId).contains("admin")
                ? List.of("*:*:*")
                : permissions;
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

    public List<Map<String, Object>> posts(Long userId) {
        return jdbc.query("""
                SELECT p.post_id, p.post_code, p.post_name
                FROM sys_post p
                JOIN sys_user_post up ON up.post_id = p.post_id
                WHERE up.user_id = ?
                """, (resultSet, rowNumber) -> Map.of(
                "postId", resultSet.getLong("post_id"),
                "postCode", resultSet.getString("post_code"),
                "postName", resultSet.getString("post_name")
        ), userId);
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

