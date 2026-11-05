package com.seal.oa.hr;

import static com.seal.oa.hr.HrData.*;

import com.seal.oa.common.ApiException;
import com.seal.oa.workflow.WorkflowActionContext;
import com.seal.oa.workflow.WorkflowActionHandler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Component
public class HrWorkflowHandler implements WorkflowActionHandler {
    private final HrData d;
    private final HrPayrollService payroll;
    private final HrEmployeeService employees;

    public HrWorkflowHandler(HrData d, HrPayrollService payroll, HrEmployeeService employees) {
        this.d = d;
        this.payroll = payroll;
        this.employees = employees;
    }

    @Override
    public Set<String> handlerKeys() {
        return Set.of("hr.payroll.submit", "hr.salary.submit", "hr.change.submit");
    }

    @Override
    public boolean supportsAction(long module, String action) {
        return module == PAYROLL && "submit".equals(action)
                || module == EMPLOYEES && "salary_submit".equals(action)
                || module == CHANGES && "submit".equals(action);
    }

    private String table(WorkflowActionContext c) {
        return c.moduleId() == EMPLOYEES ? "oa_hr_salary" : "oa_hr_event";
    }

    private long record(WorkflowActionContext c) {
        return id(c.payload().get(c.moduleId() == EMPLOYEES ? "salaryId" : "eventId"));
    }

    @Override
    public void validate(WorkflowActionContext c) {
        String expected =
                c.moduleId() == PAYROLL
                        ? "hr.payroll.submit"
                        : c.moduleId() == EMPLOYEES ? "hr.salary.submit" : "hr.change.submit";
        if (!supportsAction(c.moduleId(), c.actionCode()) || !expected.equals(c.handlerKey()))
            throw new ApiException(400, "人事审批执行器不匹配");
        if (c.moduleId() == PAYROLL) {
            payroll.validateWorkflow(c);
            return;
        }
        var row = d.one("SELECT * FROM " + table(c) + " WHERE id=?", record(c));
        d.employee(id(row.get("userId")));
        if (c.requestId() == 0) {
            d.expect(row, "draft", "rejected");
            d.revision(row, c.payload());
        } else if (!"approving".equals(row.get("status"))
                || optionalId(row.get("workflowRequestId")) != c.requestId())
            throw new ApiException(409, "审批记录已变化");
    }

    @Override
    @Transactional
    public void submitted(WorkflowActionContext c) {
        if (c.moduleId() == PAYROLL) {
            payroll.submitted(c);
            return;
        }
        validate(
                new WorkflowActionContext(
                        0,
                        c.moduleId(),
                        c.actionCode(),
                        c.handlerKey(),
                        c.config(),
                        c.payload(),
                        c.operatorId(),
                        c.operatorName()));
        d.jdbc.update(
                "UPDATE "
                        + table(c)
                        + " SET status='approving',workflow_request_id=?,revision=revision+1 WHERE"
                        + " id=?",
                c.requestId(),
                record(c));
    }

    @Override
    @Transactional
    public Object execute(WorkflowActionContext c) {
        validate(c);
        if (c.moduleId() == PAYROLL) return payroll.approved(c);
        var row = d.one("SELECT * FROM " + table(c) + " WHERE id=? FOR UPDATE", record(c));
        if (c.moduleId() == EMPLOYEES) {
            d.jdbc.update(
                    "UPDATE oa_hr_salary SET status='confirmed',revision=revision+1 WHERE id=?",
                    record(c));
            d.history(
                    "salary",
                    record(c),
                    id(row.get("userId")),
                    "薪资标准审批通过",
                    Map.of("requestId", c.requestId()));
        } else {
            var b = d.object(row.get("afterJson"));
            var employee = d.employee(id(row.get("userId")));
            if (optionalId(b.get("revision")) != id(employee.get("revision")))
                throw new ApiException(409, "员工资料在审批期间发生变化，请退回重新提交");
            LocalDate effective = date(row.get("effectiveDate"));
            boolean future = effective.isAfter(LocalDate.now());
            if (!future)
                employees.apply(id(row.get("userId")), text(row.get("eventType")), effective, b);
            else if ("dismiss".equals(row.get("eventType")))
                d.jdbc.update(
                        "UPDATE oa_hr_employee SET employment_status='leaving',revision=revision+1"
                                + " WHERE user_id=?",
                        row.get("userId"));
            d.jdbc.update(
                    "UPDATE oa_hr_event SET status=?,revision=revision+1 WHERE id=?",
                    future ? "scheduled" : "effective",
                    record(c));
        }
        return Map.of("id", record(c), "status", "completed");
    }

    @Override
    public void validateCompletion(
            WorkflowActionContext c,
            String instance,
            String task,
            boolean pass,
            String comment,
            Long operator) {
        long module = c.moduleId();
        String applicant =
                c.requestId() > 0
                        ? text(
                                d.one(
                                                "SELECT create_by FROM oa_workflow_request WHERE"
                                                        + " id=?",
                                                c.requestId())
                                        .get("createBy"))
                        : c.operatorName();
        String approver =
                text(
                        d.one("SELECT user_name FROM sys_user WHERE user_id=?", operator)
                                .get("userName"));
        if (Objects.equals(operator, c.operatorId()) || applicant.equals(approver))
            throw new ApiException(403, "不能审批本人提交的人事或工资申请");
        if (!d.system.hasModuleAction(operator, module, "approve"))
            throw new ApiException(403, "审批人缺少此业务的审批权限");
        if (!pass && text(comment).isEmpty()) throw new ApiException(400, "退回需填写原因");
        validate(c);
    }

    @Override
    @Transactional
    public void workflowStateChanged(
            WorkflowActionContext c, String instance, String status, String task, String name) {
        if (c.moduleId() == PAYROLL) {
            payroll.rejected(c, status);
            return;
        }
        if (!Set.of("审批未通过", "rejected", "cancelled", "canceled", "withdrawn").contains(status))
            return;
        d.jdbc.update(
                "UPDATE "
                        + table(c)
                        + " SET status='rejected',revision=revision+1 WHERE id=? AND"
                        + " workflow_request_id=? AND status='approving'",
                record(c),
                c.requestId());
    }
}
