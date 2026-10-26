package com.seal.oa.system;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.system.SystemService.UserAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
public class SystemController {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final SystemService systemService;

    public SystemController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, SystemService systemService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.systemService = systemService;
    }

    @GetMapping("/system/user/list")
    public Map<String, Object> users(@RequestParam Map<String, String> query) {
        String where = "WHERE u.del_flag = '0'";
        List<Object> arguments = new ArrayList<>();
        if (StringUtils.hasText(query.get("userName"))) {
            where += " AND u.user_name LIKE ?";
            arguments.add("%" + query.get("userName") + "%");
        }
        if (StringUtils.hasText(query.get("nickName"))) {
            where += " AND u.nick_name LIKE ?";
            arguments.add("%" + query.get("nickName") + "%");
        }
        if (StringUtils.hasText(query.get("phonenumber"))) {
            where += " AND u.phonenumber LIKE ?";
            arguments.add("%" + query.get("phonenumber") + "%");
        }
        if (StringUtils.hasText(query.get("deptId"))) {
            where += " AND u.dept_id = ?";
            arguments.add(Long.parseLong(query.get("deptId")));
        }
        if (StringUtils.hasText(query.get("status"))) {
            where += " AND u.status = ?";
            arguments.add(query.get("status"));
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user u " + where, Long.class, arguments.toArray());
        int pageNum = Math.max(parseInt(query.get("pageNum"), 1), 1);
        int pageSize = Math.min(Math.max(parseInt(query.get("pageSize"), 10), 1), 200);
        List<Object> pageArguments = new ArrayList<>(arguments);
        pageArguments.add(pageSize);
        pageArguments.add((pageNum - 1) * pageSize);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.user_id, u.tenant_id, u.dept_id, u.user_name, u.nick_name,
                       u.email, u.phonenumber, u.sex, u.avatar, u.status, u.del_flag,
                       u.login_ip, u.login_date, u.create_by, u.create_time, u.remark,
                       d.dept_name
                FROM sys_user u
                LEFT JOIN sys_dept d ON d.dept_id = u.dept_id
                %s
                ORDER BY u.user_id
                LIMIT ? OFFSET ?
                """.formatted(where), pageArguments.toArray());

        List<Map<String, Object>> result = JdbcMaps.camelCase(rows);
        result.forEach(row -> {
            row.put("tenantId_", row.get("tenantId"));
            row.put("roles", roles(((Number) row.get("userId")).longValue()));
            Map<String, Object> dept = new LinkedHashMap<>();
            dept.put("deptId", row.get("deptId"));
            dept.put("deptName", row.get("deptName"));
            row.put("dept", dept);
        });
        return ApiResult.page(result, total == null ? 0 : total);
    }

    @GetMapping("/system/user/list/queryAll")
    public Map<String, Object> usersAll() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT user_id, tenant_id, dept_id, user_name, nick_name, phonenumber, status
                FROM sys_user WHERE del_flag = '0' ORDER BY user_id
                """);
        return ApiResult.ok(JdbcMaps.camelCase(rows));
    }

    @GetMapping("/system/user/getByUsername")
    public Map<String, Object> userByUsername(@RequestParam String userName) {
        UserAccount user = systemService.findLoginUser(userName)
                .orElseThrow(() -> new ApiException(404, "用户不存在"));
        Map<String, Object> result = ApiResult.ok(systemService.userDetails(user));
        return result;
    }

    @GetMapping("/system/user/{userId}")
    public Map<String, Object> user(@PathVariable Long userId) {
        UserAccount user = systemService.findById(userId)
                .orElseThrow(() -> new ApiException(404, "用户不存在"));
        Map<String, Object> details = systemService.userDetails(user);
        details.put("roleIds", jdbc.queryForList(
                "SELECT role_id FROM sys_user_role WHERE user_id = ?", Long.class, userId));
        details.put("postIds", jdbc.queryForList(
                "SELECT post_id FROM sys_user_post WHERE user_id = ?", Long.class, userId));
        return ApiResult.ok(details);
    }

    @PostMapping({"/system/user", "/system/user/add"})
    public Map<String, Object> createUser(@RequestBody Map<String, Object> body) {
        String username = string(body.get("userName"));
        if (!StringUtils.hasText(username)) {
            throw new ApiException(400, "用户名不能为空");
        }
        String rawPassword = StringUtils.hasText(string(body.get("password")))
                ? string(body.get("password"))
                : "admin123123";
        jdbc.update("""
                INSERT INTO sys_user (
                    tenant_id, dept_id, user_name, nick_name, password, email, phonenumber,
                    sex, avatar, status, create_by, remark
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                longValue(body.get("tenantId_"), 1L),
                longValue(body.get("deptId"), 101L),
                username,
                defaultString(body.get("nickName"), username),
                passwordEncoder.encode(rawPassword),
                defaultString(body.get("email"), ""),
                defaultString(body.get("phonenumber"), username),
                defaultString(body.get("sex"), "0"),
                defaultString(body.get("avatar"), ""),
                defaultString(body.get("status"), "0"),
                AuthContext.username(),
                defaultString(body.get("remark"), "")
        );
        Long id = jdbc.queryForObject("SELECT user_id FROM sys_user WHERE user_name = ?", Long.class, username);
        bind(body, "roleIds", id, "sys_user_role", "user_id", "role_id");
        bind(body, "postIds", id, "sys_user_post", "user_id", "post_id");
        return ApiResult.ok(id);
    }

    @PutMapping({"/system/user", "/system/user/edit"})
    public Map<String, Object> updateUser(@RequestBody Map<String, Object> body) {
        Long userId = longValue(body.get("userId"), longValue(body.get("id"), 0L));
        if (userId == 0L) {
            throw new ApiException(400, "userId 不能为空");
        }
        jdbc.update("""
                UPDATE sys_user SET dept_id = ?, nick_name = ?, email = ?, phonenumber = ?,
                    sex = ?, status = ?, remark = ?, update_by = ?, update_time = ?
                WHERE user_id = ?
                """,
                longValue(body.get("deptId"), 101L),
                defaultString(body.get("nickName"), ""),
                defaultString(body.get("email"), ""),
                defaultString(body.get("phonenumber"), ""),
                defaultString(body.get("sex"), "0"),
                defaultString(body.get("status"), "0"),
                defaultString(body.get("remark"), ""),
                AuthContext.username(),
                Timestamp.valueOf(LocalDateTime.now()),
                userId
        );
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM sys_user_post WHERE user_id = ?", userId);
        bind(body, "roleIds", userId, "sys_user_role", "user_id", "role_id");
        bind(body, "postIds", userId, "sys_user_post", "user_id", "post_id");
        return ApiResult.ok();
    }

    @PutMapping("/system/user/resetPwd")
    public Map<String, Object> resetPassword(@RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE sys_user SET password = ?, update_time = ? WHERE user_id = ?",
                passwordEncoder.encode(defaultString(body.get("password"), "admin123123")),
                Timestamp.valueOf(LocalDateTime.now()),
                longValue(body.get("userId"), 0L));
        return ApiResult.ok();
    }

    @PutMapping("/system/user/changeStatus")
    public Map<String, Object> changeUserStatus(@RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE sys_user SET status = ? WHERE user_id = ?",
                defaultString(body.get("status"), "0"),
                longValue(body.get("userId"), 0L));
        return ApiResult.ok();
    }

    @DeleteMapping("/system/user/{userIds}")
    public Map<String, Object> deleteUsers(@PathVariable String userIds) {
        for (String value : userIds.split(",")) {
            jdbc.update("UPDATE sys_user SET del_flag = '2' WHERE user_id = ?", Long.parseLong(value));
        }
        return ApiResult.ok();
    }

    @GetMapping({"/system/dept/list", "/system/dept/allTree", "/system/dept/admin/tree", "/system/dept/treeselect"})
    public Map<String, Object> departments() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT dept_id, tenant_id, parent_id, ancestors, dept_name, order_num,
                       leader, phone, email, status
                FROM sys_dept WHERE del_flag = '0' ORDER BY parent_id, order_num
                """));
        return ApiResult.ok(buildDeptTree(rows));
    }

    @GetMapping("/system/dept/list/page")
    public Map<String, Object> departmentPage(@RequestParam Map<String, String> query) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT dept_id, tenant_id, parent_id, ancestors, dept_name, order_num,
                       leader, phone, email, status
                FROM sys_dept WHERE del_flag = '0' ORDER BY parent_id, order_num
                """));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/system/dept/{deptId}")
    public Map<String, Object> department(@PathVariable Long deptId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT dept_id, tenant_id, parent_id, ancestors, dept_name, order_num,
                       leader, phone, email, status
                FROM sys_dept WHERE dept_id = ?
                """, deptId));
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/system/dept")
    public Map<String, Object> createDepartment(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                INSERT INTO sys_dept (tenant_id, parent_id, ancestors, dept_name, order_num, leader, phone, email, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                longValue(body.get("tenantId"), 1L),
                longValue(body.get("parentId"), 100L),
                defaultString(body.get("ancestors"), "0,100"),
                defaultString(body.get("deptName"), "新部门"),
                longValue(body.get("orderNum"), 0L),
                defaultString(body.get("leader"), ""),
                defaultString(body.get("phone"), ""),
                defaultString(body.get("email"), ""),
                defaultString(body.get("status"), "0")
        );
        Long id = jdbc.queryForObject("SELECT MAX(dept_id) FROM sys_dept", Long.class);
        return ApiResult.ok(id);
    }

    @PutMapping("/system/dept")
    public Map<String, Object> updateDepartment(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                UPDATE sys_dept SET parent_id = ?, dept_name = ?, order_num = ?, leader = ?,
                    phone = ?, email = ?, status = ?, update_time = ?
                WHERE dept_id = ?
                """,
                longValue(body.get("parentId"), 100L),
                defaultString(body.get("deptName"), ""),
                longValue(body.get("orderNum"), 0L),
                defaultString(body.get("leader"), ""),
                defaultString(body.get("phone"), ""),
                defaultString(body.get("email"), ""),
                defaultString(body.get("status"), "0"),
                Timestamp.valueOf(LocalDateTime.now()),
                longValue(body.get("deptId"), longValue(body.get("id"), 0L))
        );
        return ApiResult.ok();
    }

    @DeleteMapping("/system/dept/{deptId}")
    public Map<String, Object> deleteDepartment(@PathVariable Long deptId) {
        jdbc.update("UPDATE sys_dept SET del_flag = '2' WHERE dept_id = ?", deptId);
        return ApiResult.ok();
    }

    @GetMapping({"/system/dept/addUser", "/system/dept/add/principal", "/system/dept/add/principal/leader"})
    public Map<String, Object> assignDepartmentUser(@RequestParam Map<String, String> query) {
        Long deptId = Long.parseLong(query.getOrDefault("deptId", "0"));
        for (String userId : query.getOrDefault("userIds", query.getOrDefault("userId", "")).split(",")) {
            if (StringUtils.hasText(userId)) {
                jdbc.update("UPDATE sys_user SET dept_id = ? WHERE user_id = ?", deptId, Long.parseLong(userId));
            }
        }
        return ApiResult.ok();
    }

    @GetMapping({"/system/dept/dept/leader", "/system/dept/dept/principal"})
    public Map<String, Object> departmentLeaders() {
        return ApiResult.page(new ArrayList<>(), 0);
    }

    @GetMapping("/system/role/list")
    public Map<String, Object> roles(@RequestParam Map<String, String> query) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT role_id, tenant_id, role_name, role_key, role_sort, data_scope,
                       status, del_flag, create_time, remark
                FROM sys_role WHERE del_flag = '0' ORDER BY role_sort
                """));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/system/role/list/all")
    public Map<String, Object> rolesAll() {
        return ApiResult.ok(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT role_id, role_name, role_key FROM sys_role WHERE del_flag = '0'
                """)));
    }

    @GetMapping("/system/role/{roleId}")
    public Map<String, Object> role(@PathVariable Long roleId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT role_id, tenant_id, role_name, role_key, role_sort, data_scope,
                       status, remark FROM sys_role WHERE role_id = ?
                """, roleId));
        if (!rows.isEmpty()) {
            rows.get(0).put("menuIds", jdbc.queryForList(
                    "SELECT menu_id FROM sys_role_menu WHERE role_id = ?", Long.class, roleId));
        }
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/system/role")
    public Map<String, Object> createRole(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                INSERT INTO sys_role (tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                longValue(body.get("tenantId"), 1L),
                defaultString(body.get("roleName"), "新角色"),
                defaultString(body.get("roleKey"), "custom"),
                longValue(body.get("roleSort"), 10L),
                defaultString(body.get("dataScope"), "1"),
                defaultString(body.get("status"), "0"),
                defaultString(body.get("remark"), "")
        );
        return ApiResult.ok(jdbc.queryForObject("SELECT MAX(role_id) FROM sys_role", Long.class));
    }

    @PutMapping({"/system/role", "/system/role/dataScope"})
    public Map<String, Object> updateRole(@RequestBody Map<String, Object> body) {
        Long roleId = longValue(body.get("roleId"), longValue(body.get("id"), 0L));
        jdbc.update("""
                UPDATE sys_role SET role_name = ?, role_key = ?, role_sort = ?,
                    data_scope = ?, status = ?, remark = ?, update_time = ?
                WHERE role_id = ?
                """,
                defaultString(body.get("roleName"), ""),
                defaultString(body.get("roleKey"), ""),
                longValue(body.get("roleSort"), 0L),
                defaultString(body.get("dataScope"), "1"),
                defaultString(body.get("status"), "0"),
                defaultString(body.get("remark"), ""),
                Timestamp.valueOf(LocalDateTime.now()),
                roleId
        );
        if (body.get("menuIds") instanceof List<?> menuIds) {
            jdbc.update("DELETE FROM sys_role_menu WHERE role_id = ?", roleId);
            menuIds.forEach(menuId -> jdbc.update(
                    "INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)",
                    roleId, ((Number) menuId).longValue()));
        }
        return ApiResult.ok();
    }

    @PutMapping("/system/role/changeStatus")
    public Map<String, Object> changeRoleStatus(@RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE sys_role SET status = ? WHERE role_id = ?",
                defaultString(body.get("status"), "0"),
                longValue(body.get("roleId"), 0L));
        return ApiResult.ok();
    }

    @DeleteMapping({"/system/role/{roleId}", "/system/role/remove/{roleId}"})
    public Map<String, Object> deleteRole(@PathVariable Long roleId) {
        jdbc.update("UPDATE sys_role SET del_flag = '2' WHERE role_id = ?", roleId);
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id = ?", roleId);
        jdbc.update("DELETE FROM sys_user_role WHERE role_id = ?", roleId);
        return ApiResult.ok();
    }

    @GetMapping("/system/role_group/group_list")
    public Map<String, Object> roleGroups() {
        return ApiResult.page(List.of(), 0);
    }

    @PostMapping("/system/role_group/group_update")
    public Map<String, Object> updateRoleGroup(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @DeleteMapping("/system/role_group/group_remove/{groupId}")
    public Map<String, Object> deleteRoleGroup(@PathVariable Long groupId) {
        return ApiResult.ok();
    }

    @GetMapping("/system/role/admin/list")
    public Map<String, Object> adminRoles() {
        return ApiResult.ok(jdbc.queryForList("""
                SELECT role_id, role_name, role_key FROM sys_role WHERE del_flag = '0' AND status = '0'
                """));
    }

    @GetMapping("/system/role/list_member")
    public Map<String, Object> roleMembers(@RequestParam Map<String, String> query) {
        return ApiResult.page(List.of(), 0);
    }

    @PostMapping("/system/role/add_member")
    public Map<String, Object> addRoleMember(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @DeleteMapping("/system/role/remove_member")
    public Map<String, Object> removeRoleMember(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResult.ok();
    }

    @PostMapping({"/system/role/insert", "/system/role/update"})
    public Map<String, Object> legacyRoleWrite(@RequestBody Map<String, Object> body) {
        return body.containsKey("roleId") ? updateRole(body) : createRole(body);
    }

    @GetMapping("/system/menu/list")
    public Map<String, Object> menus(@RequestParam Map<String, String> query) {
        return ApiResult.page(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT menu_id, menu_name, parent_id, order_num, path, component, query,
                       is_frame, is_cache, menu_type, visible, status, perms, icon
                FROM sys_menu ORDER BY parent_id, order_num
                """)), 0);
    }

    @GetMapping("/system/menu/treeselect")
    public Map<String, Object> menuTree() {
        return ApiResult.ok(buildMenuTree(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT menu_id, menu_name, parent_id, order_num, path, component, menu_type, icon
                FROM sys_menu ORDER BY parent_id, order_num
                """))));
    }

    @GetMapping("/system/menu/roleMenuTreeselect/{roleId}")
    public Map<String, Object> roleMenuTree(@PathVariable Long roleId) {
        Map<String, Object> result = ApiResult.ok(buildMenuTree(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT menu_id, menu_name, parent_id, order_num, path, component, menu_type, icon
                FROM sys_menu ORDER BY parent_id, order_num
                """))));
        result.put("checkedKeys", jdbc.queryForList(
                "SELECT menu_id FROM sys_role_menu WHERE role_id = ?", Long.class, roleId));
        return result;
    }

    @GetMapping("/system/menu/{menuId}")
    public Map<String, Object> menu(@PathVariable Long menuId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM sys_menu WHERE menu_id = ?", menuId));
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/system/menu")
    public Map<String, Object> createMenu(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                defaultString(body.get("menuName"), ""),
                longValue(body.get("parentId"), 0L),
                longValue(body.get("orderNum"), 0L),
                defaultString(body.get("path"), ""),
                defaultString(body.get("component"), ""),
                defaultString(body.get("menuType"), "C"),
                defaultString(body.get("visible"), "0"),
                defaultString(body.get("status"), "0"),
                defaultString(body.get("perms"), ""),
                defaultString(body.get("icon"), "")
        );
        return ApiResult.ok();
    }

    @PutMapping("/system/menu")
    public Map<String, Object> updateMenu(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                UPDATE sys_menu SET menu_name = ?, parent_id = ?, order_num = ?, path = ?,
                    component = ?, menu_type = ?, visible = ?, status = ?, perms = ?, icon = ?
                WHERE menu_id = ?
                """,
                defaultString(body.get("menuName"), ""),
                longValue(body.get("parentId"), 0L),
                longValue(body.get("orderNum"), 0L),
                defaultString(body.get("path"), ""),
                defaultString(body.get("component"), ""),
                defaultString(body.get("menuType"), "C"),
                defaultString(body.get("visible"), "0"),
                defaultString(body.get("status"), "0"),
                defaultString(body.get("perms"), ""),
                defaultString(body.get("icon"), ""),
                longValue(body.get("menuId"), 0L)
        );
        return ApiResult.ok();
    }

    @DeleteMapping("/system/menu/{menuId}")
    public Map<String, Object> deleteMenu(@PathVariable Long menuId) {
        jdbc.update("DELETE FROM sys_menu WHERE menu_id = ?", menuId);
        return ApiResult.ok();
    }

    @GetMapping("/system/admin/query/adminGroup")
    public Map<String, Object> adminGroups() {
        return ApiResult.ok(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT group_id, group_name, tenant_id, create_time FROM sys_admin_group
                """)));
    }

    @GetMapping("/system/admin/query/admin")
    public Map<String, Object> admins() {
        return ApiResult.ok(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT u.user_id, u.user_name, u.nick_name, u.phonenumber
                FROM sys_user u WHERE u.del_flag = '0'
                """)));
    }

    private List<String> roles(Long userId) {
        return jdbc.queryForList("""
                SELECT r.role_name FROM sys_role r
                JOIN sys_user_role ur ON ur.role_id = r.role_id
                WHERE ur.user_id = ?
                """, String.class, userId);
    }

    private List<Map<String, Object>> buildDeptTree(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> node = new LinkedHashMap<>(row);
            Long id = ((Number) row.get("deptId")).longValue();
            node.put("id", id);
            node.put("label", row.get("deptName"));
            node.put("children", new ArrayList<Map<String, Object>>());
            nodes.put(id, node);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> node : nodes.values()) {
            Long parentId = ((Number) node.get("parentId")).longValue();
            Map<String, Object> parent = nodes.get(parentId);
            if (parent == null || parentId == 0L) {
                roots.add(node);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                children.add(node);
            }
        }
        return roots;
    }

    private List<Map<String, Object>> buildMenuTree(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> node = new LinkedHashMap<>(row);
            Long id = ((Number) row.get("menuId")).longValue();
            node.put("id", id);
            node.put("label", row.get("menuName"));
            node.put("children", new ArrayList<Map<String, Object>>());
            nodes.put(id, node);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> node : nodes.values()) {
            Long parentId = ((Number) node.get("parentId")).longValue();
            Map<String, Object> parent = nodes.get(parentId);
            if (parent == null || parentId == 0L) {
                roots.add(node);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                children.add(node);
            }
        }
        return roots;
    }

    private void bind(
            Map<String, Object> body,
            String property,
            Long ownerId,
            String table,
            String ownerColumn,
            String valueColumn
    ) {
        Object value = body.get(property);
        if (!(value instanceof List<?> values)) {
            return;
        }
        values.stream()
                .filter(Objects::nonNull)
                .map(item -> item instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(item)))
                .forEach(id -> jdbc.update(
                        "INSERT INTO " + table + " (" + ownerColumn + ", " + valueColumn + ") VALUES (?, ?)",
                        ownerId, id));
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private long longValue(Object value, long defaultValue) {
        try {
            return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String defaultString(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
