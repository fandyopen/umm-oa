package com.seal.oa.system;

import com.seal.oa.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** A batch is validated as a whole, and uses the same invitation workflow as a single member. */
@Service
public class MemberImportService {
    private final JdbcTemplate jdbc;
    private final SystemService system;
    private final HrMemberService members;

    public MemberImportService(JdbcTemplate jdbc, SystemService system, HrMemberService members) {
        this.jdbc = jdbc;
        this.system = system;
        this.members = members;
    }

    public List<Map<String, Object>> prepare(long moduleId, Map<String, Object> payload,
                                             String operator, boolean savedRequest) {
        var account = system.findLoginUser(operator).filter(user -> "0".equals(user.status()))
                .orElseThrow(() -> new ApiException(401, "申请人不存在或已停用"));
        if (!Set.of(341001L, 340782L).contains(moduleId)
                || !system.hasModuleAccess(account.userId(), moduleId)
                || !system.hasModuleAction(account.userId(), moduleId, "invite"))
            throw new ApiException(403, "没有该模块的邀请成员权限");
        if (!(payload.get("rows") instanceof List<?> rows) || rows.isEmpty() || rows.size() > 200)
            throw new ApiException(400, "每次导入1至200名成员");
        List<Map<String, Object>> normalized = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> phones = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            int line = i + 2;
            try {
                if (!(rows.get(i) instanceof Map<?, ?> row)) throw new ApiException(400, "成员数据格式错误");
                try { line = Math.max(2, Math.min(100000, Integer.parseInt(text(row.get("rowNumber"))))); }
                catch (NumberFormatException ignored) { /* Use the physical row when supplied. */ }
                String name = text(row.get("nickName")), phone = text(row.get("phonenumber"));
                String email = text(row.get("email")), sex = text(row.get("sex")), remark = text(row.get("remark"));
                if (name.isBlank() || name.length() > 64) throw new ApiException(400, "姓名必填且不能超过64字");
                if (!phone.matches("\\+?[0-9]{6,20}")) throw new ApiException(400, "手机号码格式错误，请使用文本格式填写号码");
                if (!phones.add(phone)) throw new ApiException(400, "文件中手机号码重复：" + phone);
                if (email.length() > 128 || (!email.isEmpty() && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))
                    throw new ApiException(400, "邮箱格式错误");
                sex = switch (sex) { case "", "2", "未知" -> "2"; case "0", "男" -> "0"; case "1", "女" -> "1"; default -> throw new ApiException(400, "性别须为0/男、1/女或2/未知"); };
                if (remark.length() > 500) throw new ApiException(400, "备注不能超过500字");
                String department = text(row.containsKey("department") ? row.get("department") : row.get("deptId"));
                if (department.isEmpty()) throw new ApiException(400, "请填写部门ID或部门名称");
                boolean id = department.matches("[0-9]+");
                Object lookup = department;
                if (id) {
                    try { lookup = Long.parseLong(department); }
                    catch (NumberFormatException ex) { throw new ApiException(400, "部门ID无效：" + department); }
                }
                var departments = jdbc.queryForList("SELECT dept_id,dept_name FROM sys_dept WHERE tenant_id=? AND status='0' AND del_flag='0' AND "
                        + (id ? "dept_id=?" : "dept_name=?"), account.tenantId(), lookup);
                // The database may use case-insensitive collation; names must match exactly after trimming.
                if (!id) departments.removeIf(dept -> !department.equals(text(dept.get("dept_name"))));
                if (departments.isEmpty()) throw new ApiException(400, "部门不存在、已停用或不属于当前企业：" + department);
                if (departments.size() > 1) throw new ApiException(400, "部门名称重名，请改填部门ID：" + department);
                var dept = departments.get(0);
                String deptName = text(dept.get("dept_name"));
                if (savedRequest && (!id || !deptName.equals(text(row.get("deptName")))))
                    throw new ApiException(409, "部门信息已变化，请重新校验后提交");
                Map<String, Object> member = new LinkedHashMap<>();
                member.put("rowNumber", line);
                member.put("nickName", name); member.put("phonenumber", phone); member.put("email", email);
                member.put("department", String.valueOf(dept.get("dept_id")));
                member.put("deptId", dept.get("dept_id")); member.put("deptName", deptName);
                member.put("tenantId_", account.tenantId()); member.put("sex", sex); member.put("remark", remark);
                members.validateInvite(member);
                normalized.add(member);
            } catch (ApiException ex) {
                errors.add("第" + line + "行：" + ex.getMessage());
            }
        }
        if (!errors.isEmpty()) throw new ApiException(400, "导入校验失败，整批未导入。" + String.join("；", errors.subList(0, Math.min(20, errors.size())))
                + (errors.size() > 20 ? "；共" + errors.size() + "处错误" : ""));
        return normalized;
    }

    @Transactional
    public int execute(long moduleId, Map<String, Object> payload, String operator, boolean savedRequest) {
        var rows = prepare(moduleId, payload, operator, savedRequest);
        for (var row : rows) members.invite(row, operator);
        return rows.size();
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
