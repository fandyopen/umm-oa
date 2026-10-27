package com.seal.oa.system;

import com.seal.oa.auth.AuthContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DataScopeService {

    private final JdbcTemplate jdbc;
    private final SystemService systemService;
    private final OrganizationPermissionService organizationPermissionService;

    public DataScopeService(
            JdbcTemplate jdbc,
            SystemService systemService,
            OrganizationPermissionService organizationPermissionService
    ) {
        this.jdbc = jdbc;
        this.systemService = systemService;
        this.organizationPermissionService = organizationPermissionService;
    }

    public Scope resolve(Long userId) {
        if (systemService.isUnrestricted(userId)) {
            return Scope.allScope();
        }
        String dataScope = organizationPermissionService.widestDataScope(userId);
        return switch (dataScope) {
            case "1" -> Scope.allScope();
            case "5" -> new Scope(false, true, List.of());
            case "3" -> new Scope(false, false, currentDepartments(userId, false));
            case "2", "4" -> new Scope(false, false, currentDepartments(userId, true));
            default -> new Scope(false, true, List.of());
        };
    }

    public String userFilter(Long userId, List<Object> arguments) {
        Scope scope = resolve(userId);
        if (scope.all()) {
            return "";
        }
        if (scope.selfOnly()) {
            arguments.add(userId);
            return " AND u.user_id = ?";
        }
        if (scope.deptIds().isEmpty()) {
            return " AND 1 = 0";
        }
        arguments.addAll(scope.deptIds());
        return " AND u.dept_id IN (" + placeholders(scope.deptIds().size()) + ")";
    }

    public String rowFilter(
            Long userId,
            List<Object> arguments,
            String deptColumn,
            String... userColumns
    ) {
        Scope scope = resolve(userId);
        if (scope.all()) {
            return "";
        }
        List<String> conditions = new ArrayList<>();
        if (!scope.selfOnly() && !scope.deptIds().isEmpty()) {
            conditions.add(deptColumn + " IN (" + placeholders(scope.deptIds().size()) + ")");
            arguments.addAll(scope.deptIds());
        }
        String username = AuthContext.username();
        if (username != null && !username.isBlank()) {
            for (String column : userColumns) {
                conditions.add(column + " = ?");
                arguments.add(username);
            }
        }
        return conditions.isEmpty()
                ? " AND 1 = 0"
                : " AND (" + String.join(" OR ", conditions) + ")";
    }

    public List<Map<String, Object>> filterRows(
            Long userId,
            List<Map<String, Object>> rows,
            List<String> deptKeys,
            List<String> userKeys
    ) {
        Scope scope = resolve(userId);
        if (scope.all()) {
            return rows;
        }
        return rows.stream()
                .filter(row -> canAccess(
                        scope,
                        firstValue(row, deptKeys),
                        values(row, userKeys)
                ))
                .toList();
    }

    public boolean canAccess(Long userId, Object deptId, Object... userValues) {
        return canAccess(resolve(userId), deptId, java.util.Arrays.asList(userValues));
    }

    private List<Long> currentDepartments(Long userId, boolean includeChildren) {
        List<Long> deptIds = jdbc.queryForList("""
                SELECT dept_id FROM sys_user WHERE user_id = ? AND del_flag = '0'
                """, Long.class, userId);
        if (deptIds.isEmpty() || !includeChildren) {
            return deptIds;
        }
        long deptId = deptIds.get(0);
        List<Long> visible = new ArrayList<>();
        visible.add(deptId);
        visible.addAll(jdbc.queryForList("""
                SELECT dept_id FROM sys_dept
                WHERE del_flag = '0'
                  AND (
                    dept_id = ?
                    OR CONCAT(',', COALESCE(ancestors, ''), ',')
                       LIKE CONCAT('%,', ?, ',%')
                  )
                ORDER BY dept_id
                """, Long.class, deptId, deptId));
        return visible.stream().distinct().toList();
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private boolean canAccess(Scope scope, Object deptId, List<Object> userValues) {
        if (scope.all()) {
            return true;
        }
        String username = AuthContext.username();
        String currentUserId = String.valueOf(AuthContext.userId());
        for (Object value : userValues) {
            if (value == null) {
                continue;
            }
            String candidate = String.valueOf(value);
            if (candidate.equals(username) || candidate.equals(currentUserId)) {
                return true;
            }
        }
        Long departmentId = number(deptId);
        return !scope.selfOnly()
                && departmentId != null
                && scope.deptIds().contains(departmentId);
    }

    private List<Object> values(Map<String, Object> row, List<String> keys) {
        List<Object> result = new ArrayList<>();
        for (String key : keys) {
            Object value = value(row, key);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private Object firstValue(Map<String, Object> row, List<String> keys) {
        for (String key : keys) {
            Object value = value(row, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Long number(Object value) {
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record Scope(boolean all, boolean selfOnly, List<Long> deptIds) {
        static Scope allScope() {
            return new Scope(true, false, List.of());
        }
    }
}
