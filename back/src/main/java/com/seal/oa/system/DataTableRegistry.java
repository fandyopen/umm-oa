package com.seal.oa.system;

import java.util.*;

/** Business descriptions and deliberately limited data-repair permissions. Structure comes from JDBC. */
final class DataTableRegistry {
    record Description(String label, String group, boolean core, String route) {}
    record Relation(String table, String column, String target, String targetColumn) {}
    static final Map<String, Description> TABLES = new LinkedHashMap<>();
    static final List<Relation> RELATIONS = new ArrayList<>();
    static final Map<String, Set<String>> EDITABLE = new HashMap<>();
    static final Set<String> CRUD = Set.of("oa_merchant_contact", "oa_merchant_account", "sys_dict_type", "sys_dict_data", "sys_notice");
    static final Set<String> GLOBAL = Set.of("sys_post", "sys_menu", "sys_dict_type", "sys_dict_data", "sys_config", "sys_notice", "sys_bootstrap_marker", "sys_permission_migration", "oa_recruit_tag");
    static final Set<String> LEGACY = Set.of("sys_role_group", "sys_role_group_member", "sys_role_menu", "sys_menu", "sys_dept_module", "oa_project", "oa_project_member", "oa_contract", "oa_contract_item", "oa_purchase_apply", "oa_purchase_apply_item", "oa_purchase_payment", "oa_expense", "oa_expense_item", "oa_expense_invoice", "oa_loan", "oa_loan_detail", "oa_repayment", "oa_budget", "oa_budget_item", "oa_fund_plan", "oa_warehouse", "oa_warehouse_in", "oa_warehouse_out", "oa_invoice");
    static final Map<String, String> FIELDS = new HashMap<>();
    static {
        String definitions = """
            sys_tenant|企业|系统|企业信息
            sys_user|成员账号|系统|组织架构
            sys_dept|部门|系统|组织架构
            sys_dept_principal|部门负责人|系统
            sys_role|角色|系统|角色权限
            sys_user_role|成员角色关联|系统
            sys_post|岗位字典|系统
            sys_user_post|成员岗位关联|系统
            sys_module|模块配置|系统|模块管理
            sys_module_action|模块按钮配置|系统
            sys_role_module|角色模块授权|系统
            sys_role_module_action|角色按钮授权|系统
            sys_dept_module|原部门模块授权|系统
            sys_role_group|原角色组|系统
            sys_role_group_member|原角色组成员|系统
            sys_menu|原菜单配置|系统
            sys_role_menu|原角色菜单关联|系统
            sys_admin_identity|管理员身份|系统
            sys_app|应用|系统
            sys_app_module|应用模块|系统
            sys_dict_type|字典分类|系统|基础数据
            sys_dict_data|字典选项|系统|基础数据
            sys_config|参数配置|系统
            sys_notice|通知公告|系统|通知公告
            sys_logininfor|登录日志|系统
            sys_oper_log|操作日志|系统
            sys_bootstrap_marker|数据库初始化标记|系统
            sys_permission_migration|权限升级标记|系统
            sys_data_table_history|数据维护记录|系统
            oa_merchant|客户主档|客户与合同|客户档案
            oa_merchant_contact|客户联系人|客户与合同
            oa_merchant_account|客户银行账户|客户与合同
            oa_merchant_collaborator|客户协作成员|客户与合同
            oa_customer_followup|客户跟进|客户与合同
            oa_business_opportunity|商机|客户与合同|商机管理
            oa_crm_contract|客户合同|客户与合同|合同列表
            oa_crm_contract_version|客户合同版本|客户与合同
            oa_crm_contract_receipt|合同到账记录|财务
            oa_crm_contract_reminder_log|合同提醒记录|客户与合同
            oa_finance_receivable|应收台账|财务|应收台账
            oa_finance_record|财务业务记录|财务|财务与回款管理
            oa_finance_history|财务变更历史|财务
            oa_finance_collection|逾期催收记录|财务
            oa_hr_employee|员工档案|人事|员工档案
            oa_hr_employment_period|员工任职期间|人事
            oa_hr_event|人事异动|人事|人事异动
            oa_hr_salary|薪资标准|人事|薪资标准
            oa_hr_attachment|人事附件|人事
            oa_hr_attendance_batch|考勤批次|人事
            oa_hr_attendance_row|考勤明细|人事
            oa_hr_payroll|薪资核算批次|人事|薪资核算
            oa_hr_payroll_item|员工薪资明细|人事
            oa_hr_payment|发薪记录|人事
            oa_hr_history|人事变更历史|人事
            oa_hr_handover|离职交接事项|人事
            oa_recruit_job|招聘职位|招聘|职位管理
            oa_recruit_talent|人才档案|招聘|人才管理
            oa_recruit_application|招聘推荐记录|招聘|招聘进度
            oa_recruit_event|招聘变更历史|招聘
            oa_recruit_version|职位版本|招聘
            oa_recruit_approval|招聘审批记录|招聘
            oa_recruit_resume|简历版本|招聘
            oa_recruit_interview|面试记录|招聘
            oa_recruit_exception|招聘异常|招聘
            oa_recruit_task|招聘任务|招聘
            oa_recruit_tag|人才标签|招聘
            oa_recruit_search|人才搜索记录|招聘
            oa_recruit_attachment|招聘附件|招聘
            oa_recruit_dispute|招聘争议|招聘
            oa_recruit_replacement|招聘补员|招聘
            oa_recruit_guarantee_handling|保证期处理|招聘
            oa_commission_rule|提成规则|财务|提成核算
            oa_commission_attribution|业绩归属|财务
            oa_commission_allocation|回款提成分配|财务
            oa_commission_batch|提成核算批次|财务|提成核算
            oa_commission_line|提成明细|财务
            oa_commission_history|提成变更历史|财务
            oa_todo|待办消息|审批流程
            oa_process_definition|原流程定义|审批流程
            oa_process_instance|流程实例|审批流程|审批流程
            oa_process_task|流程任务|审批流程
            oa_form_definition|低代码表单定义|审批流程
            oa_form_submission|低代码表单数据|审批流程
            oa_process_design|审批流程设计|审批流程
            oa_module_action_flow|按钮流程绑定|审批流程
            oa_workflow_request|审批业务请求|审批流程|审批流程
            oa_workflow_action_submission|按钮提交去重记录|审批流程
            oa_workflow_catalog|审批流程目录|审批流程|审批流程
            biz_record|通用业务数据|审批流程
            oa_project|原项目|历史业务
            oa_project_member|原项目成员|历史业务
            oa_contract|原项目合同|历史业务
            oa_contract_item|原项目合同明细|历史业务
            oa_purchase_apply|原采购申请|历史业务
            oa_purchase_apply_item|原采购申请明细|历史业务
            oa_purchase_payment|原采购付款|历史业务
            oa_expense|原报销|历史业务
            oa_expense_item|原报销明细|历史业务
            oa_expense_invoice|原报销发票|历史业务
            oa_loan|原借款|历史业务
            oa_loan_detail|原借款明细|历史业务
            oa_repayment|原还款|历史业务
            oa_budget|原预算|历史业务
            oa_budget_item|原预算明细|历史业务
            oa_fund_plan|原资金计划|历史业务
            oa_warehouse|原仓库|历史业务
            oa_warehouse_in|原入库|历史业务
            oa_warehouse_out|原出库|历史业务
            oa_invoice|原项目发票|历史业务
            """;
        for (String line : definitions.strip().split("\n")) {
            String[] parts = line.trim().split("\\|");
            TABLES.put(parts[0], new Description(parts[1], parts[2], parts.length > 3, parts.length > 3 ? parts[3] : ""));
        }
        edits("oa_merchant", "name,address,tax_number,taxpayer_type,industry,company_scale,region,enterprise_nature,customer_source,remark");
        edits("oa_hr_employee", "emergency_contact,emergency_phone");
        edits("oa_recruit_talent", "name,phone,email,city,industry,skills,experience_years,expected_salary,source,remark");
        edits("oa_merchant_contact", "merchant_id,name,phone,wechat,email,position,decision_role,primary_contact,remark");
        edits("oa_merchant_account", "merchant_id,account,account_name,bank,remark");
        edits("sys_dict_type", "dict_name,dict_type,status,remark");
        edits("sys_dict_data", "dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status");
        edits("sys_notice", "notice_title,notice_type,notice_content,status");
        for (String table : List.of("oa_merchant_contact", "oa_merchant_account", "oa_merchant_collaborator", "oa_customer_followup")) rel(table,"merchant_id","oa_merchant","id");
        for (String table : List.of("oa_hr_employee","oa_hr_employment_period","oa_hr_event","oa_hr_salary","oa_hr_handover","oa_hr_attendance_row","oa_hr_payroll_item","oa_workflow_action_submission","sys_user_role","sys_user_post","sys_admin_identity")) rel(table,"user_id","sys_user","user_id");
        rel("sys_dept_principal","dept_id","sys_dept","dept_id"); rel("sys_dept_principal","user_id","sys_user","user_id");
        for (String table : List.of("sys_role_module","sys_role_module_action","sys_role_group_member","sys_role_menu")) rel(table,"role_id","sys_role","role_id");
        for (String table : List.of("sys_module_action","sys_role_module","sys_role_module_action","sys_dept_module")) rel(table,"module_id","sys_module","module_id");
        rel("sys_dept_module","dept_id","sys_dept","dept_id"); rel("sys_role_group_member","group_id","sys_role_group","group_id");
        rel("sys_user_post","post_id","sys_post","post_id"); rel("sys_role_menu","menu_id","sys_menu","menu_id");
        rel("sys_dict_data","dict_type","sys_dict_type","dict_type"); rel("sys_app_module","tenant_app_id","sys_app","tenant_app_id");
        for (String table : List.of("oa_crm_contract_version","oa_crm_contract_receipt","oa_crm_contract_reminder_log","oa_finance_receivable","oa_finance_record","oa_finance_collection")) rel(table,"contract_id","oa_crm_contract","id");
        rel("oa_crm_contract","customer_id","oa_merchant","id"); rel("oa_crm_contract","opportunity_id","oa_business_opportunity","id");
        for (String table : List.of("oa_recruit_job","oa_finance_receivable","oa_finance_record","oa_finance_collection")) rel(table,"customer_id","oa_merchant","id");
        rel("oa_recruit_job","contract_id","oa_crm_contract","id"); rel("oa_recruit_application","job_id","oa_recruit_job","id"); rel("oa_recruit_application","talent_id","oa_recruit_talent","id");
        rel("oa_recruit_version","job_id","oa_recruit_job","id"); rel("oa_recruit_resume","talent_id","oa_recruit_talent","id");
        for (String table : List.of("oa_recruit_interview","oa_recruit_exception","oa_recruit_dispute","oa_recruit_replacement","oa_recruit_guarantee_handling","oa_commission_attribution")) rel(table,"application_id","oa_recruit_application","id");
        rel("oa_hr_payment","item_id","oa_hr_payroll_item","id"); rel("oa_hr_payroll_item","payroll_id","oa_hr_payroll","id");
        rel("oa_hr_attendance_row","batch_id","oa_hr_attendance_batch","id");
        for (String table : List.of("oa_hr_payroll","oa_hr_attendance_batch","oa_hr_attachment","oa_recruit_approval")) rel(table,"create_by","sys_user","user_id");
        for (String table : List.of("oa_hr_history","oa_recruit_event")) rel(table,"operator_id","sys_user","user_id");
        rel("oa_recruit_search","user_id","sys_user","user_id"); rel("oa_recruit_task","talent_id","oa_recruit_talent","id");
        rel("oa_recruit_dispute","talent_id","oa_recruit_talent","id"); rel("oa_recruit_attachment","application_id","oa_recruit_application","id");
        rel("oa_commission_line","batch_id","oa_commission_batch","id");
        rel("sys_logininfor","user_name","sys_user","user_name"); rel("sys_oper_log","oper_name","sys_user","user_name");
        rel("oa_process_task","instance_id","oa_process_instance","id");
        for (String table : List.of("oa_contract","oa_project_member","oa_budget","oa_purchase_apply","oa_expense","oa_loan","oa_fund_plan","oa_invoice","oa_warehouse_in","oa_warehouse_out")) rel(table,"project_id","oa_project","id");
        rel("oa_contract_item","contract_id","oa_contract","id"); rel("oa_purchase_apply_item","apply_id","oa_purchase_apply","id");
        rel("oa_expense_item","expense_id","oa_expense","id"); rel("oa_expense_invoice","expense_id","oa_expense","id");
        rel("oa_loan_detail","loan_id","oa_loan","id"); rel("oa_repayment","loan_id","oa_loan","id"); rel("oa_budget_item","budget_id","oa_budget","id");
    }
    static {
        String systemFields = "parent_id=上级ID,module_name=模块名称,route_name=路由名称,path=路径,component=页面组件,icon=图标,module_sort=模块排序,hidden=隐藏菜单,perms=权限标识,has_flow=启用流程,flow_conditions=流程条件,no_cache=关闭页面缓存,is_app=应用标志,is_frame=外链标志,query=路由参数,redirect=默认跳转,dept_name=部门名称,ancestors=上级路径,order_num=排序,leader=部门负责人,role_name=角色名称,role_key=角色编码,role_sort=角色排序,data_scope=数据范围,action_code=动作编码,action_name=动作名称,permission_code=按钮权限标识,handler_key=业务处理器,config_json=配置内容,builtin=内置标志,tenant_name=企业名称,tenant_app_id=企业应用ID,contact_name=联系姓名,contact_phone=联系电话,is_admin=管理员标志,is_owner=企业负责人标志,post_id=岗位ID,post_code=岗位编码,post_name=岗位名称,post_sort=岗位排序,config_id=参数ID,config_name=参数名称,config_key=参数编码,config_value=参数值,config_type=参数类型,sex=性别,avatar=头像,login_ip=登录IP,login_date=登录时间,record_key=记录主键,before_data=修改前内容,after_data=修改后内容,operator_name=操作人,table_name=数据表名";
        for (String item : systemFields.split(",")) { String[] p = item.split("="); FIELDS.put(p[0],p[1]); }
        String fields = "id=记录ID,user_id=成员ID,tenant_id=企业ID,dept_id=部门ID,role_id=角色ID,module_id=模块ID,name=名称,nick_name=姓名,user_name=登录账号,phonenumber=手机号,password=密码,email=邮箱,phone=联系电话,merchant_id=所属客户,customer_id=客户ID,contract_id=合同ID,job_id=职位ID,talent_id=人才ID,application_id=招聘推荐ID,owner_user_id=负责人ID,owner_user_name=负责人,code=编号,serial=客户编号,title=标题,remark=备注,status=状态,create_by=创建人,create_time=创建时间,update_by=修改人,update_time=修改时间,revision=版本号,employee_no=员工编号,entry_date=入职日期,exit_date=离职日期,regular_date=转正日期,emergency_contact=紧急联系人,emergency_phone=紧急联系电话,employment_status=任职状态,employment_type=用工类型,account_name=账户名称,bank_name=开户银行,bank_account=银行账号,address=地址,tax_number=税号,taxpayer_type=纳税人类型,industry=行业,company_scale=企业规模,region=地区,enterprise_nature=企业性质,customer_source=客户来源,cooperation_status=合作状态,position=职务,wechat=微信,decision_role=决策角色,primary_contact=主要联系人,account=银行账号,bank=开户银行,dict_id=字典ID,dict_name=字典名称,dict_type=字典编码,dict_code=选项ID,dict_sort=排序,dict_label=选项名称,dict_value=选项值,is_default=默认选项,css_class=样式类,list_class=展示样式,notice_id=公告ID,notice_title=公告标题,notice_type=公告类型,notice_content=公告内容,city=城市,skills=技能,experience_years=工作年限,expected_salary=期望薪资,source=来源,stage=阶段,amount=金额,content=内容,effective_date=生效日期,pay_type=计薪方式,base_amount=基本薪资,allowance=津贴,reason=原因,operator_id=操作人ID,period=所属月份,workflow_request_id=审批请求ID,public_pool=是否公海,del_flag=删除标志";
        for (String item : fields.split(",")) { String[] p = item.split("="); FIELDS.put(p[0],p[1]); }
    }
    static void edits(String table, String fields) { EDITABLE.put(table, Set.of(fields.split(","))); }
    static void rel(String table,String column,String target,String targetColumn) { RELATIONS.add(new Relation(table,column,target,targetColumn)); }
    static Description describe(String name, String remark) { return TABLES.getOrDefault(name,new Description(remark == null || remark.isBlank() ? name : remark,"其他",false,"")); }
    static String fieldLabel(String name, String remark) { return remark == null || remark.isBlank() ? FIELDS.getOrDefault(name,name) : remark; }
    static boolean sensitive(String name) { return name.matches("(?i).*(password|passwd|pwd|secret|token|credential|private_key|salt|content_base64|api[_-]?key|authorization|cookie|session[_-]?key).*"); }
}
