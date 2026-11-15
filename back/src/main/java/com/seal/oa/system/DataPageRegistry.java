package com.seal.oa.system;

import java.util.*;

/** Explicit page/source contracts. Titles are searchable labels, never SQL or identifiers. */
final class DataPageRegistry {
    record Filter(String field, String op, List<String> values) {}
    record Source(String table, String role, String note) {}
    record Page(String key, String title, String component, String route, String table,
                List<Filter> filters, List<Source> sources, String note) {}
    static final Map<String, Page> PAGES = new LinkedHashMap<>();
    static {
        page("customers", "客户与合同管理 / 客户档案", "eos/crm/customer/index", "", "oa_merchant", List.of(),
            "客户名称来自主档；联系人、跟进、职位、合同和财务数据可在单条关联中定位。",
            source("oa_customer_followup", "明细", "客户跟进"), source("oa_merchant_contact", "明细", "联系人"));
        page("opportunities", "客户与合同管理 / 商机管理", "eos/business/index", "", "biz_record",
            List.of(eq("module", "/project/business/list")), "当前商机使用通用业务表的 payload_json，不使用历史 oa_business_opportunity 表。");
        page("contracts", "客户与合同管理 / 合同列表", "eos/crm/contract/index", "", "oa_crm_contract",
            List.of(eq("deleted", "0")), "费用和收费节点在版本快照内；逻辑删除合同可在原始表模式中查看。",
            source("oa_crm_contract_version", "明细", "合同版本及收费条款快照"), source("oa_crm_contract_receipt", "明细", "实际到账流水"));
        page("jobs", "招聘管理 / 职位管理", "eos/recruitment/jobs/index", "", "oa_recruit_job",
            List.of(new Filter("status", "ne", List.of("deleted"))), "不包含已逻辑删除职位。", source("oa_recruit_version", "明细", "职位版本"));
        page("talents", "招聘管理 / 人才库", "eos/recruitment/talents/index", "", "oa_recruit_talent", List.of(),
            "人才状态、简历与推荐记录分别保存。", source("oa_recruit_resume", "明细", "简历版本"));
        page("applications", "招聘管理 / 招聘流程", "eos/recruitment/pipeline/index", "", "oa_recruit_application", List.of(),
            "每条记录是一位候选人在一个职位的招聘流程。", source("oa_recruit_job", "关联", "职位"), source("oa_recruit_talent", "关联", "候选人"));
        for (String[] item : new String[][]{{"receipt","回款登记"},{"invoice","开票管理"},{"refund","退款管理"},{"cost","成本记录"},{"adjustment","核销与冲销"}}) {
            String type = item[0];
            page("finance-" + type, "财务与回款管理 / " + item[1], "eos/crm/finance/" + type, "/finance/" + type, "oa_finance_record",
                List.of(type.equals("adjustment") ? new Filter("record_type", "in", List.of("writeoff", "reversal")) : eq("record_type", type)),
                "列表主表按业务类型固定筛选；客户名、合同名、职位名及审批状态来自关联表。已入账数据请走退款或冲销，整客户清理使用系统 → 特殊功能。",
                source("oa_merchant", "关联", "客户名称"), source("oa_crm_contract", "关联", "合同名称及编号"),
                source("oa_recruit_job", "关联", "职位名称"), source("oa_workflow_request", "关联", "审批状态与任务"),
                source("oa_crm_contract_receipt", "明细", "实际到账通过 receipt_id 关联，不使用列表行 ID"),
                source("oa_finance_history", "明细", "操作历史以 entity_type + entity_id 定位"),
                source("oa_commission_allocation", "明细", "实际到账的提成分配"));
        }
        page("finance-ledger", "财务与回款管理 / 应收台账", "eos/crm/finance/ledger", "/finance/ledger", "oa_finance_receivable", List.of(),
            "组合页面：默认打开手工应收表；合同应收由生效合同版本的收费节点计算，不能用手工应收表替代完整台账。",
            source("oa_crm_contract", "计算来源", "生效合同"), source("oa_crm_contract_version", "计算来源", "签署版本的收费节点 JSON"),
            source("oa_finance_record", "计算来源", "已确认回款、退款和核销"));
        page("finance-collections", "财务与回款管理 / 逾期催收", "eos/crm/finance/collections", "/finance/collections", "oa_finance_collection", List.of(),
            "催收记录按客户、合同或手工应收关联。", source("oa_finance_receivable", "关联", "手工应收"));
        page("finance-reconcile", "财务与回款管理 / 对账报表", "eos/crm/finance/reconcile", "/finance/reconcile", "oa_finance_record", List.of(),
            "汇总页面：默认查看财务记录来源；报表按客户汇总应收、净回款、开票、成本，不存在独立报表结果表。",
            source("oa_finance_receivable", "计算来源", "手工应收"), source("oa_crm_contract_version", "计算来源", "合同收费节点"), source("oa_merchant", "关联", "客户"));
        page("employees", "人事管理 / 员工档案", "enterprise/hr/employees", "", "oa_hr_employee", List.of(),
            "姓名、手机号来自成员表，发薪账户、工作地和紧急联系人来自员工档案。",
            source("sys_user", "关联", "成员姓名及手机号"), source("oa_hr_salary", "明细", "定薪调薪记录"));
        page("salaries", "人事管理 / 员工档案 / 薪资标准", "enterprise/hr/employees", "", "oa_hr_salary", List.of(),
            "展示全部薪资标准记录；业务使用的生效版本还需结合生效日期、审批及更正快照判断。", source("oa_hr_salary_effective", "计算来源", "更正期间原生效快照"));
        page("hr-changes", "人事管理 / 人事异动", "enterprise/hr/changes", "", "oa_hr_event", List.of(), "入职、调岗和离职事件。");
        page("attendance", "人事管理 / 考勤管理 / 员工考勤表", "enterprise/hr/attendance", "", "oa_hr_attendance_row", List.of(),
            "考勤按批次和员工保存；确认状态在考勤批次表。", source("oa_hr_attendance_batch", "关联", "考勤月份与确认状态"));
        page("payroll", "人事管理 / 工资管理", "enterprise/hr/payroll", "", "oa_hr_payroll_item", List.of(),
            "默认打开员工工资明细；批次审批、规则快照、实际发放分别保存。",
            source("oa_hr_payroll", "关联", "工资批次与审批状态"), source("oa_hr_payment", "明细", "实际发放"), source("oa_hr_payroll_rule", "计算来源", "公司计薪规则"));
        page("payroll-rules", "人事管理 / 工资管理 / 计薪规则", "enterprise/hr/payroll", "", "oa_hr_payroll_rule", List.of(), "公司计薪规则模板。");
        page("finance-payroll", "财务与回款管理 / 工资发放", "eos/crm/finance/payroll", "/finance/payroll", "oa_hr_payroll_item", List.of(),
            "与人事工资管理使用同一工资明细；业务页面还根据工资批次状态限制可发放范围。",
            source("oa_hr_payroll", "关联", "工资批次状态"), source("oa_hr_payment", "明细", "登记发放记录"));
        page("commission", "财务与回款管理 / 提成核算", "eos/crm/finance/commission", "/finance/commission", "oa_commission_batch", List.of(),
            "核算批次、提成明细、规则和回款分配是独立数据来源。", source("oa_commission_line", "明细", "员工提成明细"),
            source("oa_commission_rule", "计算来源", "规则版本"), source("oa_commission_attribution", "计算来源", "业绩归属"), source("oa_commission_allocation", "计算来源", "回款分配"));
        page("modules", "系统 / 模块管理", "system/module/index", "", "sys_module", List.of(), "页面、目录来自模块表，按钮独立保存。", source("sys_module_action", "明细", "按钮动作"));
        page("roles", "系统 / 角色权限", "system/role/index", "", "sys_role", List.of(), "角色、成员与授权分别保存。", source("sys_user_role", "明细", "成员角色"), source("sys_role_module_action", "明细", "按钮授权"));
        page("members", "系统 / 组织机构 / 全体成员", "enterprise/saasIndex/index", "", "sys_user", List.of(),
            "这里展示成员账号原始来源；组织机构的成员列表还会排除超级管理员身份，并按在职、离职等页签过滤。",
            source("oa_hr_employee", "关联", "员工编号及档案"), source("sys_user_role", "明细", "成员角色"), source("sys_admin_identity", "关联", "超级管理员身份"));
        page("departments", "系统 / 组织机构 / 组织架构", "enterprise/saasIndex/index", "", "sys_dept", List.of(), "部门主数据。", source("sys_dept_principal", "明细", "部门负责人"));
        page("todos", "待办事项 / 我的待办", "eos/todoItem/todo/index", "", "oa_todo", List.of(),
            "待办消息包含业务提醒与流程待办；审批任务本身另存在流程任务表。", source("oa_process_task", "关联", "审批任务"), source("oa_process_instance", "关联", "流程实例"));
        page("login-log", "系统 / 日志管理 / 登录日志", "system/log/logininfor", "", "sys_logininfor", List.of(), "登录审计记录。");
        page("operation-log", "系统 / 日志管理 / 操作日志", "system/log/operlog", "", "sys_oper_log", List.of(), "接口操作审计记录。");
    }
    private static Source source(String table, String role, String note) { return new Source(table, role, note); }
    private static Filter eq(String field, String value) { return new Filter(field, "eq", List.of(value)); }
    private static void page(String key, String title, String component, String route, String table, List<Filter> filters, String note, Source... sources) {
        PAGES.put(key, new Page(key, title, component, route, table, filters, List.of(sources), note));
    }
    private DataPageRegistry() {}
}
