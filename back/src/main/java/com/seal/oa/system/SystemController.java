package com.seal.oa.system;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.web.RequiresModuleAction;
import com.seal.oa.web.RequiresAnyModuleAction;
import com.seal.oa.web.ModuleActionRequirement;
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
import java.time.LocalDate;
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
    private final DataScopeService dataScopeService;
    private final HrMemberService hrMemberService;

    public SystemController(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            SystemService systemService,
            DataScopeService dataScopeService,
            HrMemberService hrMemberService
    ) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.systemService = systemService;
        this.dataScopeService = dataScopeService;
        this.hrMemberService = hrMemberService;
    }

    @GetMapping("/system/user/list")
    @RequiresAnyModuleAction({
            @ModuleActionRequirement(moduleId = 340782L, action = "access"),
            @ModuleActionRequirement(moduleId = 341001L, action = "access")
    })
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
        if (StringUtils.hasText(query.get("postId"))) {
            where += " AND EXISTS (SELECT 1 FROM sys_user_post up WHERE up.user_id = u.user_id AND up.post_id = ?)";
            arguments.add(Long.parseLong(query.get("postId")));
        }
        if (StringUtils.hasText(query.get("beginTime"))) {
            where += " AND u.create_time >= ?";
            arguments.add(parseDateBoundary(query.get("beginTime"), false));
        }
        if (StringUtils.hasText(query.get("endTime"))) {
            where += " AND u.create_time < ?";
            arguments.add(parseDateBoundary(query.get("endTime"), true));
        }
        where += dataScopeService.userFilter(AuthContext.userId(), arguments);

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user u " + where, Long.class, arguments.toArray());
        int pageNum = Math.max(parseInt(query.get("pageNum"), 1), 1);
        int pageSize = Math.min(Math.max(parseInt(query.get("pageSize"), 10), 1), 200);
        List<Object> pageArguments = new ArrayList<>(arguments);
        pageArguments.add(pageSize);
        pageArguments.add((pageNum - 1) * pageSize);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.user_id, u.tenant_id, u.dept_id, u.user_name, u.nick_name,
                       u.email, u.phonenumber, u.sex, u.avatar, u.status, u.del_flag,
                       u.is_admin, u.is_owner, u.login_ip, u.login_date,
                       u.create_by, u.create_time, u.remark,
                       d.dept_name
                FROM sys_user u
                LEFT JOIN sys_dept d ON d.dept_id = u.dept_id
                %s
                ORDER BY u.create_time DESC, u.user_id DESC
                LIMIT ? OFFSET ?
                """.formatted(where), pageArguments.toArray());

        List<Map<String, Object>> result = JdbcMaps.camelCase(rows);
        result.forEach(row -> {
            row.put("createTime", formattedTime(row.get("createTime")));
            row.put("tenantId_", row.get("tenantId"));
            row.put("roles", roles(((Number) row.get("userId")).longValue()));
            Map<String, Object> dept = new LinkedHashMap<>();
            dept.put("deptId", row.get("deptId"));
            dept.put("deptName", row.get("deptName"));
            row.put("dept", dept);
            row.put("userDept", List.of());
        });
        return ApiResult.page(result, total == null ? 0 : total);
    }

    @GetMapping("/system/user/list/queryAll")
    @RequiresAnyModuleAction({
            @ModuleActionRequirement(moduleId = 340782L, action = "access"),
            @ModuleActionRequirement(moduleId = 341001L, action = "access")
    })
    public Map<String, Object> usersAll() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT user_id, tenant_id, dept_id, user_name, nick_name, phonenumber, status
                FROM sys_user WHERE del_flag = '0' ORDER BY user_id
                """);
        List<Map<String, Object>> result = JdbcMaps.camelCase(rows);
        return ApiResult.page(result, result.size());
    }

    @GetMapping("/system/user/dismiss_list")
    @RequiresAnyModuleAction({
            @ModuleActionRequirement(moduleId = 340782L, action = "access"),
            @ModuleActionRequirement(moduleId = 341001L, action = "access")
    })
    public Map<String, Object> dismissedUsers(@RequestParam Map<String, String> query) {
        String where = "WHERE u.del_flag = '2'";
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
        if (StringUtils.hasText(query.get("beginTime"))) {
            where += " AND u.create_time >= ?";
            arguments.add(parseDateBoundary(query.get("beginTime"), false));
        }
        if (StringUtils.hasText(query.get("endTime"))) {
            where += " AND u.create_time < ?";
            arguments.add(parseDateBoundary(query.get("endTime"), true));
        }
        where += dataScopeService.userFilter(AuthContext.userId(), arguments);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user u " + where,
                Long.class,
                arguments.toArray()
        );
        int pageSize = Math.min(Math.max(parseInt(query.get("pageSize"), 10), 1), 200);
        int pageNum = Math.max(parseInt(query.get("pageNum"), 1), 1);
        List<Object> pageArguments = new ArrayList<>(arguments);
        pageArguments.add(pageSize);
        pageArguments.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT u.user_id, u.tenant_id, u.dept_id, u.user_name, u.nick_name,
                       u.email, u.phonenumber, u.status, u.create_time, d.dept_name
                FROM sys_user u LEFT JOIN sys_dept d ON d.dept_id = u.dept_id
                %s ORDER BY u.create_time DESC, u.user_id DESC LIMIT ? OFFSET ?
                """.formatted(where), pageArguments.toArray()));
        rows.forEach(row -> {
            row.put("tenantId_", row.get("tenantId"));
            row.put("createTime", formattedTime(row.get("createTime")));
            row.put("roles", roles(longValue(row.get("userId"), 0L)));
            row.put("dept", Map.of(
                    "deptId", row.getOrDefault("deptId", 0L),
                    "deptName", row.getOrDefault("deptName", "")
            ));
            row.put("userDept", List.of());
        });
        return ApiResult.page(rows, total == null ? 0 : total);
    }

    @GetMapping("/system/user/dismiss")
    @RequiresAnyModuleAction({
            @ModuleActionRequirement(moduleId = 340782L, action = "dismiss"),
            @ModuleActionRequirement(moduleId = 341001L, action = "dismiss")
    })
    public Map<String, Object> dismissUsers(@RequestParam Map<String, String> query) {
        hrMemberService.dismiss(ids(
                Map.of("userIds", query.getOrDefault("userIds", "")),
                "userIds"
        ));
        return ApiResult.ok();
    }

    @GetMapping("/system/user/getByUsername")
    @RequiresAnyModuleAction({
            @ModuleActionRequirement(moduleId = 340782L, action = "access"),
            @ModuleActionRequirement(moduleId = 341001L, action = "access")
    })
    public Map<String, Object> userByUsername(@RequestParam String userName) {
        UserAccount user = systemService.findLoginUser(userName)
                .orElseThrow(() -> new ApiException(404, "用户不存在"));
        Map<String, Object> result = ApiResult.ok(systemService.userDetails(user));
        return result;
    }

    @GetMapping("/system/user/{userId}")
    @RequiresAnyModuleAction({
            @ModuleActionRequirement(moduleId = 340782L, action = "access"),
            @ModuleActionRequirement(moduleId = 341001L, action = "access")
    })
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
    @RequiresAnyModuleAction({
            @ModuleActionRequirement(moduleId = 340782L, action = "invite"),
            @ModuleActionRequirement(moduleId = 341001L, action = "invite")
    })
    public Map<String, Object> createUser(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(hrMemberService.invite(body, AuthContext.username()));
    }

    @PutMapping({"/system/user", "/system/user/edit"})
    @RequiresAnyModuleAction({
            @ModuleActionRequirement(moduleId = 340782L, action = "transfer"),
            @ModuleActionRequirement(moduleId = 341001L, action = "transfer")
    })
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
    @RequiresModuleAction(moduleId = 340782L, action = "manage")
    public Map<String, Object> resetPassword(@RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE sys_user SET password = ?, update_time = ? WHERE user_id = ?",
                passwordEncoder.encode(defaultString(body.get("password"), "admin123123")),
                Timestamp.valueOf(LocalDateTime.now()),
                longValue(body.get("userId"), 0L));
        return ApiResult.ok();
    }

    @PutMapping("/system/user/changeStatus")
    @RequiresModuleAction(moduleId = 340782L, action = "manage")
    public Map<String, Object> changeUserStatus(@RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE sys_user SET status = ? WHERE user_id = ?",
                defaultString(body.get("status"), "0"),
                longValue(body.get("userId"), 0L));
        return ApiResult.ok();
    }

    @DeleteMapping("/system/user/{userIds}")
    @RequiresModuleAction(moduleId = 340782L, action = "manage")
    public Map<String, Object> deleteUsers(@PathVariable String userIds) {
        for (String value : userIds.split(",")) {
            jdbc.update("UPDATE sys_user SET del_flag = '2' WHERE user_id = ?", Long.parseLong(value));
        }
        return ApiResult.ok();
    }

    @GetMapping({"/system/dept/list", "/system/dept/allTree", "/system/dept/admin/tree", "/system/dept/treeselect"})
    @RequiresAnyModuleAction({
            @ModuleActionRequirement(moduleId = 340782L, action = "access"),
            @ModuleActionRequirement(moduleId = 341001L, action = "access")
    })
    public Map<String, Object> departments() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT dept_id, tenant_id, parent_id, ancestors, dept_name, order_num,
                       leader, phone, email, status
                FROM sys_dept WHERE del_flag = '0' ORDER BY parent_id, order_num
                """));
        return ApiResult.ok(buildDeptTree(rows));
    }

    @GetMapping("/system/dept/list/page")
    @RequiresAnyModuleAction({
            @ModuleActionRequirement(moduleId = 340782L, action = "access"),
            @ModuleActionRequirement(moduleId = 341001L, action = "access")
    })
    public Map<String, Object> departmentPage(@RequestParam Map<String, String> query) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT dept_id, tenant_id, parent_id, ancestors, dept_name, order_num,
                       leader, phone, email, status, create_time
                FROM sys_dept WHERE del_flag = '0' ORDER BY parent_id, order_num
                """));
        rows.forEach(row -> {
            row.put("createTime", formattedTime(row.get("createTime")));
        });
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/system/dept/{deptId}")
    @RequiresAnyModuleAction({
            @ModuleActionRequirement(moduleId = 340782L, action = "access"),
            @ModuleActionRequirement(moduleId = 341001L, action = "access")
    })
    public Map<String, Object> department(@PathVariable Long deptId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT dept_id, tenant_id, parent_id, ancestors, dept_name, order_num,
                       leader, phone, email, status
                FROM sys_dept WHERE dept_id = ?
                """, deptId));
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/system/dept")
    @RequiresModuleAction(moduleId = 340782L, action = "manage")
    public Map<String, Object> createDepartment(@RequestBody Map<String, Object> body) {
        long parentId = longValue(body.get("parentId"), 322L);
        List<Map<String, Object>> parents = jdbc.queryForList(
                "SELECT ancestors FROM sys_dept WHERE dept_id = ? AND del_flag = '0'",
                parentId
        );
        String parentAncestors = parents.isEmpty()
                ? "0"
                : String.valueOf(parents.get(0).get("ancestors"));
        String ancestors = parentId == 0L ? "0" : parentAncestors + "," + parentId;
        Long nextOrder = jdbc.queryForObject(
                "SELECT COALESCE(MAX(order_num), -1) + 1 FROM sys_dept WHERE parent_id = ? AND del_flag = '0'",
                Long.class,
                parentId
        );
        jdbc.update("""
                INSERT INTO sys_dept (tenant_id, parent_id, ancestors, dept_name, order_num, leader, phone, email, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                longValue(body.get("tenantId"), 1L),
                parentId,
                ancestors,
                defaultString(body.get("deptName"), "未命名部门"),
                longValue(body.get("orderNum"), nextOrder == null ? 0L : nextOrder),
                defaultString(body.get("leader"), ""),
                defaultString(body.get("phone"), ""),
                defaultString(body.get("email"), ""),
                defaultString(body.get("status"), "0")
        );
        Long id = jdbc.queryForObject("SELECT MAX(dept_id) FROM sys_dept", Long.class);
        return ApiResult.ok(id);
    }

    @PutMapping("/system/dept")
    @RequiresModuleAction(moduleId = 340782L, action = "manage")
    public Map<String, Object> updateDepartment(@RequestBody Map<String, Object> body) {
        long deptId = longValue(body.get("deptId"), longValue(body.get("id"), 0L));
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM sys_dept WHERE dept_id = ? AND del_flag = '0'",
                deptId
        ));
        if (rows.isEmpty()) {
            throw new ApiException(404, "Department not found");
        }
        Map<String, Object> existing = rows.get(0);
        long parentId = body.containsKey("parentId")
                ? longValue(body.get("parentId"), longValue(existing.get("parentId"), 322L))
                : longValue(existing.get("parentId"), 322L);
        String ancestors = defaultString(existing.get("ancestors"), "0");
        if (parentId != longValue(existing.get("parentId"), 0L)) {
            List<Map<String, Object>> parents = jdbc.queryForList(
                    "SELECT ancestors FROM sys_dept WHERE dept_id = ? AND del_flag = '0'",
                    parentId
            );
            String parentAncestors = parents.isEmpty()
                    ? "0"
                    : String.valueOf(parents.get(0).get("ancestors"));
            ancestors = parentId == 0L ? "0" : parentAncestors + "," + parentId;
        }
        jdbc.update("""
                UPDATE sys_dept SET parent_id = ?, ancestors = ?, dept_name = ?, order_num = ?, leader = ?,
                    phone = ?, email = ?, status = ?, update_time = ?
                WHERE dept_id = ?
                """,
                parentId,
                ancestors,
                body.containsKey("deptName")
                        ? defaultString(body.get("deptName"), defaultString(existing.get("deptName"), ""))
                        : defaultString(existing.get("deptName"), ""),
                body.containsKey("orderNum")
                        ? longValue(body.get("orderNum"), longValue(existing.get("orderNum"), 0L))
                        : longValue(existing.get("orderNum"), 0L),
                body.containsKey("leader")
                        ? defaultString(body.get("leader"), defaultString(existing.get("leader"), ""))
                        : defaultString(existing.get("leader"), ""),
                body.containsKey("phone")
                        ? defaultString(body.get("phone"), defaultString(existing.get("phone"), ""))
                        : defaultString(existing.get("phone"), ""),
                body.containsKey("email")
                        ? defaultString(body.get("email"), defaultString(existing.get("email"), ""))
                        : defaultString(existing.get("email"), ""),
                body.containsKey("status")
                        ? defaultString(body.get("status"), defaultString(existing.get("status"), "0"))
                        : defaultString(existing.get("status"), "0"),
                Timestamp.valueOf(LocalDateTime.now()),
                deptId
        );
        return ApiResult.ok();
    }

    @DeleteMapping("/system/dept/{deptId}")
    @RequiresModuleAction(moduleId = 340782L, action = "manage")
    public Map<String, Object> deleteDepartment(@PathVariable Long deptId) {
        jdbc.update("UPDATE sys_dept SET del_flag = '2' WHERE dept_id = ?", deptId);
        return ApiResult.ok();
    }

    @GetMapping("/system/dept/addUser")
    @RequiresAnyModuleAction({
            @ModuleActionRequirement(moduleId = 340782L, action = "transfer"),
            @ModuleActionRequirement(moduleId = 341001L, action = "transfer")
    })
    public Map<String, Object> assignDepartmentUser(@RequestParam Map<String, String> query) {
        long deptId = longValue(query.get("deptId"), 0L);
        hrMemberService.transfer(
                deptId,
                ids(
                        Map.of("userIds", query.getOrDefault(
                                "userIds",
                                query.getOrDefault("userId", "")
                        )),
                        "userIds"
                )
        );
        return ApiResult.ok();
    }

    @GetMapping("/system/dept/add/principal")
    @RequiresModuleAction(moduleId = 340782L, action = "manage")
    public Map<String, Object> assignDepartmentPrincipals(@RequestParam Map<String, String> query) {
        replaceDepartmentMembers("sys_dept_principal", longValue(query.get("deptId"), 0L), query.get("userIds"));
        return ApiResult.ok();
    }

    @GetMapping("/system/dept/add/principal/leader")
    @RequiresModuleAction(moduleId = 340782L, action = "manage")
    public Map<String, Object> assignDepartmentLeaders(@RequestParam Map<String, String> query) {
        long deptId = longValue(query.get("deptId"), 0L);
        List<Long> userIds = replaceDepartmentMembers("sys_dept_leader", deptId, query.get("userIds"));
        String leader = userIds.isEmpty()
                ? ""
                : String.join(",", jdbc.queryForList("""
                        SELECT nick_name FROM sys_user WHERE user_id IN (%s) ORDER BY user_id
                        """.formatted(placeholders(userIds.size())), String.class, userIds.toArray()));
        jdbc.update("UPDATE sys_dept SET leader = ?, update_time = ? WHERE dept_id = ?",
                leader, Timestamp.valueOf(LocalDateTime.now()), deptId);
        return ApiResult.ok();
    }

    @GetMapping("/system/dept/dept/principal")
    @RequiresModuleAction(moduleId = 340782L, action = "access")
    public Map<String, Object> departmentPrincipals(@RequestParam Map<String, String> query) {
        return ApiResult.ok(departmentMembers("sys_dept_principal", longValue(query.get("deptId"), 0L)));
    }

    @GetMapping("/system/dept/dept/leader")
    @RequiresModuleAction(moduleId = 340782L, action = "access")
    public Map<String, Object> departmentLeaders(@RequestParam Map<String, String> query) {
        return ApiResult.ok(departmentMembers("sys_dept_leader", longValue(query.get("deptId"), 0L)));
    }

    @GetMapping("/system/dept/sort")
    @RequiresModuleAction(moduleId = 340782L, action = "manage")
    public Map<String, Object> sortDepartments(@RequestParam Map<String, String> query) {
        long dragId = longValue(query.get("target"), 0L);
        long beforeId = longValue(query.get("before"), 0L);
        if (dragId <= 0L) {
            throw new ApiException(400, "target department is required");
        }
        if (beforeId > 0L && beforeId != dragId) {
            List<Map<String, Object>> beforeRows = jdbc.queryForList(
                    "SELECT parent_id, order_num FROM sys_dept WHERE dept_id = ? AND del_flag = '0'",
                    beforeId
            );
            if (!beforeRows.isEmpty()) {
                long parentId = longValue(beforeRows.get(0).get("parent_id"), 322L);
                long orderNum = longValue(beforeRows.get(0).get("order_num"), 0L);
                List<Map<String, Object>> parents = jdbc.queryForList(
                        "SELECT ancestors FROM sys_dept WHERE dept_id = ? AND del_flag = '0'",
                        parentId
                );
                String parentAncestors = parents.isEmpty()
                        ? "0"
                        : String.valueOf(parents.get(0).get("ancestors"));
                jdbc.update("""
                        UPDATE sys_dept SET parent_id = ?, ancestors = ?, order_num = ?, update_time = ?
                        WHERE dept_id = ?
                        """,
                        parentId,
                        parentId == 0L ? "0" : parentAncestors + "," + parentId,
                        orderNum,
                        Timestamp.valueOf(LocalDateTime.now()),
                        dragId
                );
            }
        }
        return ApiResult.ok();
    }

    @GetMapping("/system/role/list")
    @RequiresModuleAction(moduleId = 340597L, action = "access")
    public Map<String, Object> roles(@RequestParam Map<String, String> query) {
        String where = "WHERE del_flag = '0'";
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(query.get("roleName"))) {
            where += " AND role_name LIKE ?";
            args.add("%" + query.get("roleName") + "%");
        }
        if (StringUtils.hasText(query.get("roleKey"))) {
            where += " AND role_key LIKE ?";
            args.add("%" + query.get("roleKey") + "%");
        }
        if (StringUtils.hasText(query.get("status"))) {
            where += " AND status = ?";
            args.add(query.get("status"));
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role " + where,
                Long.class,
                args.toArray()
        );
        int pageSize = Math.min(Math.max(parseInt(query.get("pageSize"), 10), 1), 200);
        int pageNum = Math.max(parseInt(query.get("pageNum"), 1), 1);
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT role_id, tenant_id, role_name, role_key, role_sort, data_scope,
                       status, del_flag, create_time, remark
                FROM sys_role %s ORDER BY role_sort, role_id LIMIT ? OFFSET ?
                """.formatted(where), pageArgs.toArray()));
        rows.forEach(row -> row.put("createTime", formattedTime(row.get("createTime"))));
        return ApiResult.page(rows, total == null ? 0 : total);
    }

    @GetMapping("/system/role/list/all")
    @RequiresModuleAction(moduleId = 340597L, action = "access")
    public Map<String, Object> rolesAll(@RequestParam Map<String, String> query) {
        return roles(query);
    }

    @GetMapping("/system/role/{roleId}")
    @RequiresModuleAction(moduleId = 340597L, action = "access")
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
    @RequiresModuleAction(moduleId = 340597L, action = "manage")
    public Map<String, Object> createRole(@RequestBody Map<String, Object> body) {
        String roleName = defaultString(body.get("roleName"), "新角色");
        String requestedRoleKey = string(body.get("roleKey"));
        String roleKey = StringUtils.hasText(requestedRoleKey)
                ? requestedRoleKey
                : "custom_" + java.util.UUID.randomUUID().toString().replace("-", "");
        Long duplicate = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_role
                WHERE del_flag = '0' AND (role_name = ? OR role_key = ?)
                """, Long.class, roleName, roleKey);
        if (duplicate != null && duplicate > 0L) {
            throw new ApiException(400, "角色名称或权限字符已存在");
        }
        jdbc.update("""
                INSERT INTO sys_role (tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                longValue(body.get("tenantId"), 1L),
                roleName,
                roleKey,
                longValue(body.get("roleSort"), 10L),
                defaultString(body.get("dataScope"), "1"),
                defaultString(body.get("status"), "0"),
                defaultString(body.get("remark"), "")
        );
        Long roleId = jdbc.queryForObject("SELECT MAX(role_id) FROM sys_role", Long.class);
        assignRoleGroup(roleId, longValue(body.get("groupId"), 50L));
        return ApiResult.ok(roleId);
    }

    @PutMapping({"/system/role", "/system/role/dataScope"})
    @RequiresModuleAction(moduleId = 340597L, action = "manage")
    public Map<String, Object> updateRole(@RequestBody Map<String, Object> body) {
        Long roleId = longValue(body.get("roleId"), longValue(body.get("id"), 0L));
        List<Map<String, Object>> existingRows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT role_name, role_key, role_sort, data_scope, status, remark
                FROM sys_role WHERE role_id = ?
                """, roleId));
        if (existingRows.isEmpty()) {
            throw new ApiException(404, "Role not found");
        }
        Map<String, Object> existing = existingRows.get(0);
        String roleName = defaultString(
                body.get("roleName"),
                defaultString(existing.get("roleName"), "")
        );
        String roleKey = defaultString(
                body.get("roleKey"),
                defaultString(existing.get("roleKey"), "")
        );
        Long duplicate = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_role
                WHERE role_id <> ? AND del_flag = '0'
                  AND (role_name = ? OR role_key = ?)
                """, Long.class, roleId, roleName, roleKey);
        if (duplicate != null && duplicate > 0L) {
            throw new ApiException(400, "角色名称或权限字符已存在");
        }
        jdbc.update("""
                UPDATE sys_role SET role_name = ?, role_key = ?, role_sort = ?,
                    data_scope = ?, status = ?, remark = ?, update_time = ?
                WHERE role_id = ?
                """,
                roleName,
                roleKey,
                longValue(body.get("roleSort"), longValue(existing.get("roleSort"), 0L)),
                defaultString(body.get("dataScope"), defaultString(existing.get("dataScope"), "1")),
                defaultString(body.get("status"), defaultString(existing.get("status"), "0")),
                defaultString(body.get("remark"), defaultString(existing.get("remark"), "")),
                Timestamp.valueOf(LocalDateTime.now()),
                roleId
        );
        if (body.get("menuIds") instanceof List<?> menuIds) {
            jdbc.update("DELETE FROM sys_role_menu WHERE role_id = ?", roleId);
            menuIds.forEach(menuId -> jdbc.update(
                    "INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)",
                    roleId, ((Number) menuId).longValue()));
        }
        if (body.containsKey("groupId")) {
            assignRoleGroup(roleId, longValue(body.get("groupId"), 50L));
        }
        return ApiResult.ok();
    }

    @PutMapping("/system/role/changeStatus")
    @RequiresModuleAction(moduleId = 340597L, action = "manage")
    public Map<String, Object> changeRoleStatus(@RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE sys_role SET status = ? WHERE role_id = ?",
                defaultString(body.get("status"), "0"),
                longValue(body.get("roleId"), 0L));
        return ApiResult.ok();
    }

    @DeleteMapping({"/system/role/{roleId}", "/system/role/remove/{roleId}"})
    @RequiresModuleAction(moduleId = 340597L, action = "manage")
    public Map<String, Object> deleteRole(@PathVariable Long roleId) {
        jdbc.update("DELETE FROM sys_role_module_action WHERE role_id = ?", roleId);
        jdbc.update("DELETE FROM sys_role_module WHERE role_id = ?", roleId);
        jdbc.update("UPDATE sys_role SET del_flag = '2' WHERE role_id = ?", roleId);
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id = ?", roleId);
        jdbc.update("DELETE FROM sys_role_group_member WHERE role_id = ?", roleId);
        jdbc.update("DELETE FROM sys_user_role WHERE role_id = ?", roleId);
        return ApiResult.ok();
    }

    @GetMapping("/system/role_group/group_list")
    @RequiresModuleAction(moduleId = 340597L, action = "access")
    public Map<String, Object> roleGroups() {
        return ApiResult.ok(roleGroupRows());
    }

    @PostMapping("/system/role_group/group_update")
    @RequiresModuleAction(moduleId = 340597L, action = "manage")
    public Map<String, Object> updateRoleGroup(@RequestBody Map<String, Object> body) {
        long groupId = longValue(body.get("groupId"), 0L);
        String groupName = defaultString(body.get("groupName"), "新角色组");
        if (groupId == 0L) {
            groupId = longValue(jdbc.queryForObject(
                    "SELECT COALESCE(MAX(group_id), 0) + 1 FROM sys_role_group", Long.class), 1L);
            jdbc.update("""
                    INSERT INTO sys_role_group (group_id, tenant_id, group_name, sort)
                    VALUES (?, 1, ?, ?)
                    """, groupId, groupName, longValue(body.get("sort"), 0L));
        } else {
            jdbc.update("UPDATE sys_role_group SET group_name = ?, sort = ? WHERE group_id = ?",
                    groupName, longValue(body.get("sort"), 0L), groupId);
        }
        return ApiResult.ok(groupId);
    }

    @DeleteMapping("/system/role_group/group_remove/{groupId}")
    @RequiresModuleAction(moduleId = 340597L, action = "manage")
    public Map<String, Object> deleteRoleGroup(@PathVariable Long groupId) {
        jdbc.update("DELETE FROM sys_role_group_member WHERE group_id = ?", groupId);
        jdbc.update("DELETE FROM sys_role_group WHERE group_id = ?", groupId);
        return ApiResult.ok();
    }

    @GetMapping("/system/role/admin/list")
    @RequiresModuleAction(moduleId = 340597L, action = "access")
    public Map<String, Object> adminRoles() {
        List<Map<String, Object>> roles = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT r.role_id, r.role_name, r.role_key, r.role_sort,
                       COALESCE(rgm.group_id, 50) AS group_id
                FROM sys_role r
                LEFT JOIN sys_role_group_member rgm ON rgm.role_id = r.role_id
                WHERE r.del_flag = '0' AND r.status = '0'
                ORDER BY r.role_sort, r.role_id
                """));
        Map<String, Object> result = ApiResult.ok(Map.of(
                "roles", roles,
                "roleGroups", roleGroupRows()
        ));
        return result;
    }

    @GetMapping("/system/role/list_member")
    @RequiresModuleAction(moduleId = 340597L, action = "access")
    public Map<String, Object> roleMembers(@RequestParam Map<String, String> query) {
        long roleId = longValue(query.get("roleId"), 0L);
        if (roleId == 0L) {
            return ApiResult.page(List.of(), 0);
        }
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT u.user_id, u.user_name, u.nick_name, u.phonenumber, u.email,
                       u.dept_id, d.dept_name
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.user_id
                LEFT JOIN sys_dept d ON d.dept_id = u.dept_id
                WHERE ur.role_id = ? AND u.del_flag = '0'
                ORDER BY u.user_id
                """, roleId));
        rows.forEach(row -> {
            row.put("dept", Map.of(
                    "deptId", row.getOrDefault("deptId", 0L),
                    "deptName", row.getOrDefault("deptName", "")
            ));
            row.put("roles", roles(longValue(row.get("userId"), 0L)));
        });
        return ApiResult.page(rows, rows.size());
    }

    @PostMapping("/system/role/add_member")
    @RequiresModuleAction(moduleId = 340597L, action = "manage")
    public Map<String, Object> addRoleMember(@RequestBody Map<String, Object> body) {
        long roleId = longValue(body.get("roleId"), 0L);
        for (Long userId : ids(body, "userIds")) {
            if (roleId > 0L && userId > 0L) {
                jdbc.update("""
                        INSERT INTO sys_user_role (user_id, role_id)
                        SELECT ?, ? WHERE NOT EXISTS (
                            SELECT 1 FROM sys_user_role WHERE user_id = ? AND role_id = ?
                        )
                        """, userId, roleId, userId, roleId);
            }
        }
        return ApiResult.ok();
    }

    @DeleteMapping("/system/role/remove_member")
    @RequiresModuleAction(moduleId = 340597L, action = "manage")
    public Map<String, Object> removeRoleMember(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return ApiResult.ok();
        }
        long roleId = longValue(body.get("roleId"), 0L);
        for (Long userId : ids(body, "userIds")) {
            jdbc.update("DELETE FROM sys_user_role WHERE user_id = ? AND role_id = ?", userId, roleId);
        }
        return ApiResult.ok();
    }

    @PostMapping({"/system/role/insert", "/system/role/update"})
    public Map<String, Object> legacyRoleWrite(@RequestBody Map<String, Object> body) {
        return body.containsKey("roleId") ? updateRole(body) : createRole(body);
    }

    @GetMapping("/system/menu/list")
    @RequiresModuleAction(moduleId = 340771L, action = "access")
    public Map<String, Object> menus(@RequestParam Map<String, String> query) {
        return ApiResult.page(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT menu_id, menu_name, parent_id, order_num, path, component, query,
                       is_frame, is_cache, menu_type, visible, status, perms, icon
                FROM sys_menu ORDER BY parent_id, order_num
                """)), 0);
    }

    @GetMapping("/system/menu/treeselect")
    @RequiresModuleAction(moduleId = 340771L, action = "access")
    public Map<String, Object> menuTree() {
        return ApiResult.ok(buildMenuTree(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT menu_id, menu_name, parent_id, order_num, path, component, menu_type, icon
                FROM sys_menu ORDER BY parent_id, order_num
                """))));
    }

    @GetMapping("/system/menu/roleMenuTreeselect/{roleId}")
    @RequiresModuleAction(moduleId = 340597L, action = "access")
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
    @RequiresModuleAction(moduleId = 340771L, action = "access")
    public Map<String, Object> menu(@PathVariable Long menuId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM sys_menu WHERE menu_id = ?", menuId));
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/system/menu")
    @RequiresModuleAction(moduleId = 340771L, action = "manage")
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
    @RequiresModuleAction(moduleId = 340771L, action = "manage")
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
    @RequiresModuleAction(moduleId = 340771L, action = "manage")
    public Map<String, Object> deleteMenu(@PathVariable Long menuId) {
        jdbc.update("DELETE FROM sys_menu WHERE menu_id = ?", menuId);
        return ApiResult.ok();
    }

    @GetMapping("/system/admin/query/adminGroup")
    @RequiresModuleAction(moduleId = 340782L, action = "access")
    public Map<String, Object> adminGroups() {
        return ApiResult.ok(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT group_id, group_name, tenant_id, is_add, role_read, role_manage,
                       dept_read, dept_manage, sort, create_time
                FROM sys_admin_group ORDER BY sort, group_id
                """)));
    }

    @GetMapping("/system/admin/query/admin")
    @RequiresModuleAction(moduleId = 340782L, action = "access")
    public Map<String, Object> admins() {
        return ApiResult.ok(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT user_id, user_name, nick_name, email, phonenumber, sex, status
                FROM sys_admin_identity ORDER BY sort, user_id
                """)));
    }

    @GetMapping("/system/admin/adminGroup/{groupId}")
    @RequiresModuleAction(moduleId = 340782L, action = "access")
    public Map<String, Object> adminGroup(@PathVariable Long groupId) {
        List<Map<String, Object>> groupRows = jdbc.queryForList("""
                SELECT group_id, group_name, is_add, role_read, role_manage,
                       dept_read, dept_manage, sort
                FROM sys_admin_group WHERE group_id = ?
                """, groupId);
        if (groupRows.isEmpty()) {
            throw new ApiException(404, "Admin group not found");
        }
        Map<String, Object> source = JdbcMaps.camelCase(groupRows).get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groupId", source.get("groupId"));
        result.put("apps", JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT a.tenant_app_id AS id, a.app_name
                FROM sys_admin_group_app aga
                JOIN sys_app a ON a.tenant_app_id = aga.tenant_app_id
                WHERE aga.group_id = ? ORDER BY a.tenant_app_id
                """, groupId)));
        result.put("members", JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT u.user_id, u.user_name, u.nick_name, u.email, u.phonenumber,
                       u.sex, u.status
                FROM sys_admin_group_member agm
                JOIN sys_user u ON u.user_id = agm.user_id
                WHERE agm.group_id = ? AND u.del_flag = '0'
                ORDER BY u.user_name
                """, groupId)));
        List<Map<String, Object>> deptRange = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT d.dept_id, d.dept_name
                FROM sys_admin_group_dept agd
                JOIN sys_dept d ON d.dept_id = agd.dept_id
                WHERE agd.group_id = ? ORDER BY d.order_num, d.dept_id
                """, groupId));
        List<Map<String, Object>> roleRange = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT r.role_id, r.role_name, r.role_key
                FROM sys_admin_group_role agr
                JOIN sys_role r ON r.role_id = agr.role_id
                WHERE agr.group_id = ? ORDER BY r.role_sort, r.role_id
                """, groupId));
        result.put("deptRange", deptRange);
        result.put("hasDeptRange", !deptRange.isEmpty());
        result.put("roleRange", roleRange);
        result.put("hasRoleRange", !roleRange.isEmpty());
        result.put("op", Map.of(
                "appUpdate", enabled(source.get("isAdd")),
                "memberRead", enabled(source.get("deptRead")),
                "memberManage", enabled(source.get("deptManage")),
                "roleRead", enabled(source.get("roleRead")),
                "roleManage", enabled(source.get("roleManage"))
        ));
        return ApiResult.ok(result);
    }

    @PostMapping("/system/admin/addGroup")
    @RequiresModuleAction(moduleId = 340782L, action = "manage")
    public Map<String, Object> addAdminGroup(@RequestBody Map<String, Object> body) {
        String groupName = defaultString(body.get("groupName"), "新管理组");
        jdbc.update("""
                INSERT INTO sys_admin_group
                    (group_name, tenant_id, is_add, role_read, role_manage,
                     dept_read, dept_manage, sort)
                VALUES (?, 1, '0', '0', '0', '0', '0', ?)
                """, groupName, longValue(body.get("sort"), 0L));
        return ApiResult.ok();
    }

    @PostMapping("/system/admin/edit")
    @RequiresModuleAction(moduleId = 340782L, action = "manage")
    public Map<String, Object> editAdminGroup(@RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE sys_admin_group SET group_name = ? WHERE group_id = ?",
                defaultString(body.get("groupName"), ""),
                longValue(body.get("groupId"), 0L));
        return ApiResult.ok();
    }

    @PostMapping("/system/admin/update")
    @RequiresModuleAction(moduleId = 340782L, action = "manage")
    public Map<String, Object> updateAdminGroup(@RequestBody Map<String, Object> body) {
        long groupId = longValue(body.get("groupId"), 0L);
        if (groupId <= 0L) {
            throw new ApiException(400, "groupId is required");
        }
        Object opValue = body.get("op");
        if (opValue instanceof Map<?, ?> op) {
            updateAdminFlag(groupId, "is_add", op.get("appUpdate"));
            updateAdminFlag(groupId, "dept_read", op.get("memberRead"));
            updateAdminFlag(groupId, "dept_manage", op.get("memberManage"));
            updateAdminFlag(groupId, "role_read", op.get("roleRead"));
            updateAdminFlag(groupId, "role_manage", op.get("roleManage"));
        }
        if (body.containsKey("members")) {
            replaceAdminRelations(groupId, "sys_admin_group_member", "user_id", ids(body, "members"));
        }
        if (body.containsKey("apps")) {
            replaceAdminRelations(groupId, "sys_admin_group_app", "tenant_app_id", ids(body, "apps"));
        }
        if (body.containsKey("deptRange")) {
            replaceAdminRelations(groupId, "sys_admin_group_dept", "dept_id", ids(body, "deptRange"));
        }
        if (body.containsKey("roleRange")) {
            replaceAdminRelations(groupId, "sys_admin_group_role", "role_id", ids(body, "roleRange"));
        }
        return ApiResult.ok();
    }

    @PostMapping("/system/admin/addAdmin")
    @RequiresModuleAction(moduleId = 340782L, action = "manage")
    public Map<String, Object> updateSystemAdmins(@RequestBody Map<String, Object> body) {
        List<Long> userIds = ids(body, "userIds");
        jdbc.update("DELETE FROM sys_admin_identity");
        for (Long userId : userIds) {
            List<Map<String, Object>> users = jdbc.queryForList("""
                    SELECT user_id, user_name, nick_name, email, phonenumber, sex, status
                    FROM sys_user WHERE user_id = ? AND del_flag = '0'
                    """, userId);
            if (users.isEmpty()) {
                continue;
            }
            Map<String, Object> user = JdbcMaps.camelCase(users).get(0);
            jdbc.update("""
                    INSERT INTO sys_admin_identity
                        (user_id, user_name, nick_name, email, phonenumber, sex, status, sort)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    user.get("userId"),
                    user.get("userName"),
                    user.get("nickName"),
                    user.get("email"),
                    user.get("phonenumber"),
                    user.get("sex"),
                    user.get("status"),
                    userIds.indexOf(userId)
            );
        }
        return ApiResult.ok();
    }

    @DeleteMapping("/system/admin/remove/{groupId}")
    @RequiresModuleAction(moduleId = 340782L, action = "manage")
    public Map<String, Object> deleteAdminGroup(@PathVariable Long groupId) {
        for (String table : List.of(
                "sys_admin_group_member",
                "sys_admin_group_app",
                "sys_admin_group_dept",
                "sys_admin_group_role"
        )) {
            jdbc.update("DELETE FROM " + table + " WHERE group_id = ?", groupId);
        }
        jdbc.update("DELETE FROM sys_admin_group WHERE group_id = ?", groupId);
        return ApiResult.ok();
    }

    private List<Map<String, Object>> roles(Long userId) {
        return JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT r.role_id, r.role_name, r.role_key, COALESCE(rgm.group_id, 50) AS group_id
                FROM sys_role r
                LEFT JOIN sys_role_group_member rgm ON rgm.role_id = r.role_id
                JOIN sys_user_role ur ON ur.role_id = r.role_id
                WHERE ur.user_id = ?
                ORDER BY r.role_sort, r.role_id
                """, userId));
    }

    private List<Map<String, Object>> roleGroupRows() {
        List<Map<String, Object>> groups = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT group_id, group_name, sort, create_time
                FROM sys_role_group
                ORDER BY sort, group_id
                """));
        for (Map<String, Object> group : groups) {
            long groupId = longValue(group.get("groupId"), 0L);
            group.put("roles", JdbcMaps.camelCase(jdbc.queryForList("""
                    SELECT r.role_id, r.role_name, r.role_key, r.role_sort, r.status,
                           ? AS group_id
                    FROM sys_role r
                    JOIN sys_role_group_member rgm ON rgm.role_id = r.role_id
                    WHERE rgm.group_id = ? AND r.del_flag = '0'
                    ORDER BY r.role_sort, r.role_id
                    """, groupId, groupId)));
        }
        return groups;
    }

    private String formattedTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return value == null ? "" : String.valueOf(value);
    }

    private Timestamp parseDateBoundary(String value, boolean endExclusive) {
        try {
            LocalDate date = LocalDate.parse(value.substring(0, 10));
            LocalDateTime boundary = endExclusive
                    ? date.plusDays(1).atStartOfDay()
                    : date.atStartOfDay();
            return Timestamp.valueOf(boundary);
        } catch (Exception exception) {
            throw new ApiException(400, "日期格式应为 yyyy-MM-dd");
        }
    }

    private void assignRoleGroup(Long roleId, Long groupId) {
        if (roleId == null || roleId == 0L || groupId == null || groupId == 0L) {
            return;
        }
        jdbc.update("DELETE FROM sys_role_group_member WHERE role_id = ?", roleId);
        jdbc.update("INSERT INTO sys_role_group_member (group_id, role_id) VALUES (?, ?)",
                groupId, roleId);
    }

    private List<Long> replaceDepartmentMembers(String table, long deptId, String rawUserIds) {
        if (deptId <= 0L) {
            throw new ApiException(400, "deptId is required");
        }
        List<Long> userIds = java.util.Arrays.stream(
                        defaultString(rawUserIds, "").split(",")
                )
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> longValue(value, 0L))
                .filter(value -> value > 0L)
                .distinct()
                .toList();
        jdbc.update("DELETE FROM " + table + " WHERE dept_id = ?", deptId);
        for (Long userId : userIds) {
            jdbc.update(
                    "INSERT INTO " + table + " (dept_id, user_id) VALUES (?, ?)",
                    deptId,
                    userId
            );
        }
        return userIds;
    }

    private List<Map<String, Object>> departmentMembers(String table, long deptId) {
        return JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT u.user_id, u.user_name, u.nick_name, u.phonenumber, u.email, u.status
                FROM %s relation
                JOIN sys_user u ON u.user_id = relation.user_id
                WHERE relation.dept_id = ? AND u.del_flag = '0'
                ORDER BY u.user_id
                """.formatted(table), deptId));
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private List<Long> ids(Map<String, Object> body, String property) {
        Object value = body.get(property);
        if (value instanceof List<?> values) {
            return values.stream()
                    .map(this::idValue)
                    .filter(id -> id > 0L)
                    .toList();
        }
        if (value == null) {
            return List.of();
        }
        return java.util.Arrays.stream(String.valueOf(value).split(","))
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .map(text -> longValue(text, 0L))
                .filter(id -> id > 0L)
                .toList();
    }

    private long idValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("userId", "id", "roleId", "deptId", "tenantAppId")) {
                if (map.containsKey(key)) {
                    return longValue(map.get(key), 0L);
                }
            }
            return 0L;
        }
        return longValue(value, 0L);
    }

    private boolean enabled(Object value) {
        return "0".equals(String.valueOf(value));
    }

    private void updateAdminFlag(long groupId, String column, Object value) {
        if (value == null) {
            return;
        }
        boolean enabled = value instanceof Boolean bool
                ? bool
                : Boolean.parseBoolean(String.valueOf(value));
        jdbc.update("UPDATE sys_admin_group SET " + column + " = ? WHERE group_id = ?",
                enabled ? "0" : "1", groupId);
    }

    private void replaceAdminRelations(long groupId, String table, String column, List<Long> ids) {
        jdbc.update("DELETE FROM " + table + " WHERE group_id = ?", groupId);
        for (Long id : ids) {
            jdbc.update("INSERT INTO " + table + " (group_id, " + column + ") VALUES (?, ?)",
                    groupId, id);
        }
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
            if (value instanceof java.util.Collection<?> values) {
                return values.stream().findFirst()
                        .map(item -> longValue(item, defaultValue))
                        .orElse(defaultValue);
            }
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
