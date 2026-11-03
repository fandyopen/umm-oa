package com.seal.oa.oa;

import com.seal.oa.system.SystemService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 客户按负责人授权；下级来自部门主管及其下属部门，不按角色名称或创建人放行。 */
@Service
public class CustomerAccessService {
    private final JdbcTemplate jdbc;
    private final SystemService systemService;

    public CustomerAccessService(JdbcTemplate jdbc, SystemService systemService) {
        this.jdbc = jdbc;
        this.systemService = systemService;
    }

    public boolean canAccessOwner(long userId, Object ownerId) {
        return systemService.isUnrestricted(userId) || visibleOwners(userId).contains(number(ownerId));
    }

    public List<Map<String, Object>> filterRows(long userId, List<Map<String, Object>> rows, String ownerKey) {
        if (systemService.isUnrestricted(userId)) return rows;
        Set<Long> owners = visibleOwners(userId);
        return rows.stream().filter(row -> owners.contains(number(row.get(ownerKey)))).toList();
    }

    private Set<Long> visibleOwners(long userId) {
        Set<Long> departments = new HashSet<>(jdbc.queryForList("""
                SELECT p.dept_id FROM sys_dept_principal p JOIN sys_dept d ON d.dept_id = p.dept_id
                WHERE p.user_id = ? AND d.del_flag = '0' AND d.status = '0'
                """, Long.class, userId));
        List<Map<String, Object>> tree = jdbc.queryForList(
                "SELECT dept_id, parent_id FROM sys_dept WHERE del_flag = '0' AND status = '0'");
        boolean added;
        do {
            added = false;
            for (Map<String, Object> dept : tree) {
                if (departments.contains(number(dept.get("parent_id")))) {
                    added |= departments.add(number(dept.get("dept_id")));
                }
            }
        } while (added);
        Set<Long> owners = new HashSet<>();
        owners.add(userId);
        if (!departments.isEmpty()) {
            List<Object> args = new ArrayList<>(departments);
            owners.addAll(jdbc.queryForList("SELECT user_id FROM sys_user WHERE del_flag = '0' AND dept_id IN ("
                    + String.join(",", java.util.Collections.nCopies(args.size(), "?")) + ")", Long.class, args.toArray()));
        }
        return owners;
    }

    private long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
