export const routeMapEntries = [
  {
    related: ['src/views/eos/todoItem/todo/todo.vue', 'src/views/eos/todoItem/todo/busDetail.vue'],
    api: ['src/api/activiti/process.js', 'src/api/workflow/action.js'],
    backend: ['ActivityController', 'LowCodeWorkflowService', 'WorkflowActionController'],
    note: '列表入口为 index.vue，主要逻辑位于 todo.vue',
    group: '待办',
    menu: '我的待办',
    route: '/todoItem/todo',
    component: 'eos/todoItem/todo/index',
    file: 'src/views/eos/todoItem/todo/index.vue',
    permission: 'todoItem:todo:list'
  },
  {
    related: ['src/views/eos/todoItem/done/done.vue', 'src/views/eos/todoItem/todo/busDetail.vue'],
    api: ['src/api/activiti/process.js', 'src/api/workflow/action.js'],
    backend: ['ActivityController', 'WorkflowActionController'],
    note: '已办列表逻辑位于 done.vue',
    group: '待办',
    menu: '我的已办',
    route: '/todoItem/done',
    component: 'eos/todoItem/done/index',
    file: 'src/views/eos/todoItem/done/index.vue',
    permission: 'todoItem:done:list'
  },
  {
    related: ['src/views/eos/todoItem/todo/processStarted.vue', 'src/views/eos/todoItem/todo/busDetail.vue'],
    api: ['src/api/activiti/process.js'],
    backend: ['ActivityController', 'LowCodeWorkflowService'],
    note: '查询当前用户发起的流程',
    group: '待办',
    menu: '我的发起',
    route: '/todoItem/my-process-start',
    component: 'eos/todoItem/todo/processStartedPage',
    file: 'src/views/eos/todoItem/todo/processStartedPage.vue',
    permission: 'todoItem:done:list'
  },
  {
    related: ['src/views/eos/todoItem/copy/copy.vue', 'src/views/eos/todoItem/todo/busDetail.vue'],
    api: ['src/api/activiti/process.js', 'src/api/activiti/workflow.js'],
    backend: ['ActivityController'],
    note: '抄送列表与已读状态',
    group: '待办',
    menu: '抄送我的',
    route: '/todoItem/copy',
    component: 'eos/todoItem/copy/index',
    file: 'src/views/eos/todoItem/copy/index.vue',
    permission: ''
  },
  {
    related: ['src/views/eos/business/modules/BusinessForm.vue', 'src/views/eos/business/modules/BusinessDetail.vue', 'src/views/eos/business/modules/BusinessStageForm.vue'],
    api: ['src/api/eos/business/business.js', 'src/api/workflow/action.js'],
    backend: ['BusinessQueryController', 'WorkflowActionController'],
    note: '商机列表、跟进阶段和转化动作',
    group: '客户与合同',
    menu: '商机管理',
    route: '/crm/opportunities',
    component: 'eos/business/index',
    file: 'src/views/eos/business/index.vue',
    permission: 'project:business:list'
  },
  {
    related: [],
    api: ['src/api/eos/crm/index.js'],
    backend: ['CrmController'],
    note: '客户和合同统计分析',
    group: '客户与合同',
    menu: '客户分析',
    route: '/crm/analysis',
    component: 'eos/crm/analysis/index',
    file: 'src/views/eos/crm/analysis/index.vue',
    permission: 'project:analysis:list'
  },
  {
    related: ['src/views/eos/crm/customer/modules/CustomerForm.vue', 'src/components/IndustrySelect/index.vue', 'src/components/RegionSelect/index.vue'],
    api: ['src/api/eos/crm/index.js', 'src/api/eos/merchant/merchant.js'],
    backend: ['MerchantController', 'CrmController'],
    note: '客户档案、行业地区选择、导入导出与客户跟进',
    group: '客户与合同',
    menu: '客户档案',
    route: '/crm/customers',
    component: 'eos/crm/customer/index',
    file: 'src/views/eos/crm/customer/index.vue',
    permission: 'project:merchant:list'
  },
  {
    related: ['src/views/enterprise/components/memberElement/index.vue', 'src/views/enterprise/components/roleElement/index.vue', 'src/views/enterprise/components/adminElement/index.vue'],
    api: ['src/api/enterprise/index.js', 'src/api/system/dept.js', 'src/api/system/role.js'],
    backend: ['SystemController', 'OrganizationPermissionService'],
    note: '部门、部门主管、角色和固定超级管理员；岗位不再单独管理',
    group: '系统',
    menu: '组织机构',
    route: '/system/zzjg',
    component: 'enterprise/Contacts/contactsOp',
    file: 'src/views/enterprise/Contacts/contactsOp.vue',
    permission: ''
  },
  {
    related: ['src/views/enterprise/modulePerms/modules/rolePerms.vue', 'src/views/enterprise/modulePerms/modules/menuTree.vue'],
    api: ['src/api/system/role.js', 'src/api/system/menu.js'],
    backend: ['SystemController', 'ModuleController', 'ModuleActionService'],
    note: '模块授权与动作授权维护入口',
    group: '系统',
    menu: '角色权限',
    route: '/system/menu-perms',
    component: 'enterprise/modulePerms/index',
    file: 'src/views/enterprise/modulePerms/index.vue',
    permission: 'sys:module'
  },
  {
    related: ['src/views/system/dict/modules/DictData.vue', 'src/views/system/dict/modules/CreateForm.vue', 'src/views/system/dict/modules/CreateDataForm.vue'],
    api: ['src/api/system/dict/type.js', 'src/api/system/dict/data.js'],
    backend: ['MetadataController'],
    note: '字典类型和字典值管理',
    group: '系统',
    menu: '基础数据',
    route: '/system/dict',
    component: 'system/dict/index',
    file: 'src/views/system/dict/index.vue',
    permission: 'system:dict:list'
  },
  {
    related: ['src/views/system/moduleAction/index.vue'],
    api: ['src/api/system/menu.js', 'src/api/user/module.js'],
    backend: ['ModuleController', 'AuthController', 'ModuleActionService'],
    note: '历史流程设置书签，跳转统一模块管理',
    group: '系统',
    menu: '流程设置',
    route: '/system/module_manage',
    component: 'system/menu/index',
    file: 'src/views/system/menu/index.vue',
    permission: 'sys:module'
  },
  {
    related: ['src/views/system/config/modules/CreateForm.vue'],
    api: ['src/api/system/config.js'],
    backend: ['MetadataController'],
    note: '系统参数和首页地址配置',
    group: '系统',
    menu: '参数设置',
    route: '/system/config',
    component: 'system/config/index',
    file: 'src/views/system/config/index.vue',
    permission: 'system:config:list'
  },
  {
    related: ['src/views/system/notice/CreateForm.vue', 'src/views/system/notice/TinymceEditor.vue'],
    api: ['src/api/system/notice.js'],
    backend: ['MetadataController'],
    note: '公告列表、编辑和消息发送',
    group: '系统',
    menu: '通知公告',
    route: '/system/notice',
    component: 'system/notice/index',
    file: 'src/views/system/notice/index.vue',
    permission: 'system:notice:list'
  },
  {
    related: ['src/views/system/log/operlog/modules/ViewForm.vue'],
    api: ['src/api/system/log/operlog.js'],
    backend: ['MetadataController', 'OperationLogInterceptor'],
    note: '操作审计记录',
    group: '系统',
    menu: '操作日志',
    route: '/system/log/operlog',
    component: 'system/log/operlog/index',
    file: 'src/views/system/log/operlog/index.vue',
    permission: 'monitor:operlog:list'
  },
  {
    related: [],
    api: ['src/api/system/log/logininfor.js'],
    backend: ['MetadataController', 'AuthController'],
    note: '登录审计记录',
    group: '系统',
    menu: '登录日志',
    route: '/system/log/logininfor',
    component: 'system/log/logininfor/index',
    file: 'src/views/system/log/logininfor/index.vue',
    permission: 'monitor:logininfor:list'
  },
  {
    related: ['src/views/enterprise/components/memberElement/index.vue', 'src/views/enterprise/components/memberElement/details.vue', 'src/views/enterprise/components/selectDepartmentElement/index.vue'],
    api: ['src/api/enterprise/index.js', 'src/api/system/dept.js', 'src/api/system/user.js', 'src/api/workflow/action.js'],
    backend: ['SystemController', 'HrMemberService', 'WorkflowActionController', 'HrMemberActionHandler'],
    note: '邀请、调岗、离职通过动作级流程绑定发起',
    group: '人事',
    menu: '成员管理',
    route: '/hr/members',
    component: 'enterprise/hr/index',
    file: 'src/views/enterprise/hr/index.vue',
    permission: 'hr:member:list'
  },
  {
    related: ['src/views/docs/components/RouteMap.vue', 'src/views/docs/components/ApiDocs.vue', 'src/views/docs/components/ModuleDocs.vue'],
    api: ['src/api/system/config.js'],
    backend: ['Frontend-only', 'MetadataController'],
    note: '隐藏路由，由 system:docs:list 授权，顶部入口受 sys.docs.enabled 控制',
    group: '系统',
    menu: '使用文档',
    route: '/system/docs',
    component: 'docs/index',
    file: 'src/views/docs/index.vue',
    permission: 'system:docs:list'
  },
  {
    related: ['src/views/system/module/modules/ModuleForm.vue', 'src/views/system/moduleAction/index.vue'],
    api: ['src/api/system/menu.js'],
    backend: ['ModuleController', 'ModuleService', 'ModuleActionService'],
    note: '维护与角色模块树同源的目录、页面和按钮动作',
    group: '系统',
    menu: '模块管理',
    route: '/system/module',
    component: 'system/module/index',
    file: 'src/views/system/module/index.vue',
    permission: 'sys:module'
  },
  {
    related: [],
    api: ['src/api/eos/crm/index.js'],
    backend: ['CrmController', 'CrmContractService'],
    note: '客户和合同的风险提示',
    group: '客户与合同',
    menu: '风险提醒',
    route: '/crm/risks',
    component: 'eos/crm/risk/index',
    file: 'src/views/eos/crm/risk/index.vue',
    permission: 'project:risk:list'
  },
  {
    related: ['src/views/eos/crm/contract/modules/ContractForm.vue', 'src/views/eos/crm/contract/modules/ContractDetail.vue'],
    api: ['src/api/eos/crm/contracts.js', 'src/api/workflow/action.js'],
    backend: ['CrmContractController', 'CrmContractService', 'CrmContractWorkflowService'],
    note: '合同统一列表，发起审批走 workflow/action/execute，办理走统一待办',
    group: '客户与合同',
    menu: '合同列表',
    route: '/crm/contracts/list',
    component: 'eos/crm/contract/index',
    file: 'src/views/eos/crm/contract/index.vue',
    permission: 'crm:contract:list'
  },
  {
    related: ['src/views/eos/crm/contract/modules/LegacyContractEntry.vue'],
    api: ['src/api/eos/crm/contracts.js', 'src/api/workflow/action.js'],
    backend: ['CrmContractController', 'CrmContractService', 'CrmContractWorkflowService'],
    note: '隐藏的历史书签入口；跳转统一合同列表，同时保留独立模块授权',
    group: '客户与合同',
    menu: '合同审批',
    route: '/crm/contracts/approvals',
    component: 'eos/crm/contract/approvals',
    file: 'src/views/eos/crm/contract/approvals.vue',
    permission: 'crm:approval:list'
  },
  {
    related: ['src/views/eos/crm/contract/modules/LegacyContractEntry.vue'],
    api: ['src/api/eos/crm/contracts.js', 'src/api/workflow/action.js'],
    backend: ['CrmContractController', 'CrmContractService', 'CrmContractWorkflowService'],
    note: '隐藏的历史书签入口；跳转统一合同列表，同时保留独立模块授权',
    group: '客户与合同',
    menu: '收费条款',
    route: '/crm/contracts/fees',
    component: 'eos/crm/contract/fees',
    file: 'src/views/eos/crm/contract/fees.vue',
    permission: 'crm:fee:list'
  },
  {
    related: ['src/views/eos/crm/contract/modules/LegacyContractEntry.vue'],
    api: ['src/api/eos/crm/contracts.js', 'src/api/workflow/action.js'],
    backend: ['CrmContractController', 'CrmContractService', 'CrmContractWorkflowService'],
    note: '隐藏的历史书签入口；跳转统一合同列表，同时保留独立模块授权',
    group: '客户与合同',
    menu: '合同版本',
    route: '/crm/contracts/versions',
    component: 'eos/crm/contract/versions',
    file: 'src/views/eos/crm/contract/versions.vue',
    permission: 'crm:version:list'
  },
  {
    related: ['src/views/eos/crm/contract/modules/LegacyContractEntry.vue'],
    api: ['src/api/eos/crm/contracts.js', 'src/api/workflow/action.js'],
    backend: ['CrmContractController', 'CrmContractService', 'CrmContractWorkflowService'],
    note: '隐藏的历史书签入口；跳转统一合同列表，同时保留独立模块授权',
    group: '客户与合同',
    menu: '合同提醒',
    route: '/crm/contracts/reminders',
    component: 'eos/crm/contract/reminders',
    file: 'src/views/eos/crm/contract/reminders.vue',
    permission: 'crm:reminder:list'
  },
  {
    group: '工作台',
    menu: '首页 / 工作台',
    route: '/index',
    component: 'Index',
    file: 'src/views/home/index.vue',
    related: ['src/views/dashboard/components/todo/index.vue', 'src/views/dashboard/components/finance/BusinessOpportunity.vue'],
    api: ['src/api/user/module.js', 'src/api/eos/analysis/analyst.js'],
    backend: ['AuthController', 'DashboardController', 'ActivityController'],
    note: 'indexRouterMap 固定路由',
    permission: ''
  },
  {
    group: '全局',
    menu: '流程设计器',
    route: '/processDesign',
    component: 'processDesign',
    file: 'src/views/lowCode/processDesign/index.vue',
    related: ['src/views/lowCode/components/Process/index.vue', 'src/views/lowCode/formDesignElement/index.vue'],
    api: ['src/api/processDesign/index.js', 'src/api/system/menu.js'],
    backend: ['LowCodeController', 'ModuleController', 'ModuleActionService'],
    note: 'indexRouterMap 固定路由',
    permission: ''
  },
  {
    group: '全局',
    menu: '动作管理',
    route: '/system/moduleActions',
    component: 'ModuleActions',
    file: 'src/views/system/moduleAction/index.vue',
    related: [],
    api: ['src/api/system/menu.js'],
    backend: ['ModuleController', 'ModuleActionService', 'WorkflowActionController'],
    note: '配置模块按钮、自动生成动作编码、handlerKey 与流程绑定',
    permission: ''
  },
  {
    group: '全局',
    menu: '个人中心',
    route: '/account/center',
    component: 'AccountCenter',
    file: 'src/views/user/account/center/index.vue',
    related: ['src/views/user/account/settings/index.vue'],
    api: ['src/api/system/user.js'],
    backend: ['SystemController'],
    note: 'indexRouterMap 固定路由',
    permission: ''
  }
]

export const apiGroups = [
  {
    name: '认证与路由',
    items: [
      {
        method: 'GET',
        path: '/captchaImage',
        desc: '获取登录验证码',
        source: 'AuthController',
        auth: '公开'
      },
      {
        method: 'POST',
        path: '/login',
        desc: '账号密码登录并返回 JWT',
        source: 'AuthController',
        auth: '公开'
      },
      {
        method: 'GET',
        path: '/getInfo',
        desc: '获取用户、角色、权限码和岗位',
        source: 'AuthController',
        auth: '登录'
      },
      {
        method: 'GET',
        path: '/getRouters',
        desc: '按用户模块授权返回动态菜单树',
        source: 'AuthController / ModuleService',
        auth: '登录'
      }
    ]
  },
  {
    name: '待办与流程',
    items: [
      {
        method: 'GET',
        path: '/project/activity/taskWaitList',
        desc: '我的待办',
        source: 'ActivityController',
        auth: '登录'
      },
      {
        method: 'GET',
        path: '/project/activity/taskDoneList',
        desc: '我的已办',
        source: 'ActivityController',
        auth: '登录'
      },
      {
        method: 'POST',
        path: '/project/activity/complete',
        desc: '完成待办任务',
        source: 'ActivityController',
        auth: '登录'
      },
      {
        method: 'POST',
        path: '/workflow/action/execute',
        desc: '执行业务动作并触发审批',
        source: 'WorkflowActionController',
        auth: '模块动作'
      }
    ]
  },
  {
    name: '成员与组织',
    items: [
      {
        method: 'GET',
        path: '/system/user/list',
        desc: '成员分页，支持部门、新增日期和姓名筛选',
        source: 'SystemController',
        auth: 'hr:member:list'
      },
      {
        method: 'GET',
        path: '/system/user/dismiss_list',
        desc: '离职成员分页',
        source: 'SystemController',
        auth: 'hr:member:list'
      },
      {
        method: 'POST',
        path: '/system/user/add',
        desc: '邀请成员',
        source: 'SystemController / HrMemberService',
        auth: 'hr.member.invite'
      },
      {
        method: 'GET',
        path: '/system/dept/admin/tree',
        desc: '组织机构部门树',
        source: 'SystemController',
        auth: '组织或人事访问权限'
      }
    ]
  },
  {
    name: '模块、角色与权限',
    items: [
      {
        method: 'GET',
        path: '/system/module/list',
        desc: '模块列表',
        source: 'ModuleController',
        auth: 'sys:module'
      },
      {
        method: 'GET',
        path: '/system/module/{moduleId}/actions',
        desc: '查询模块动作',
        source: 'ModuleController',
        auth: 'sys:module'
      },
      {
        method: 'POST',
        path: '/system/module/{moduleId}/actions',
        desc: '新增模块动作',
        source: 'ModuleController',
        auth: 'sys:module'
      },
      {
        method: 'PUT',
        path: '/system/role',
        desc: '更新角色模块及动作授权',
        source: 'SystemController',
        auth: 'sys:module'
      }
    ]
  },
  {
    name: '基础数据与系统配置',
    items: [
      {
        method: 'GET',
        path: '/system/dict/type/list',
        desc: '字典类型列表',
        source: 'MetadataController',
        auth: 'system:dict:list'
      },
      {
        method: 'GET',
        path: '/system/config/list',
        desc: '系统参数列表',
        source: 'MetadataController',
        auth: 'system:config:list'
      },
      {
        method: 'GET',
        path: '/system/config/configKey/{configKey}',
        desc: '按键读取系统参数',
        source: 'MetadataController',
        auth: '登录'
      },
      {
        method: 'GET',
        path: '/system/notice/list',
        desc: '通知公告列表',
        source: 'MetadataController',
        auth: 'system:notice:list'
      }
    ]
  },
  {
    name: '客户与商机',
    items: [
      {
        method: 'GET',
        path: '/project/business/list',
        desc: '商机分页与数据范围过滤',
        source: 'BusinessQueryController',
        auth: 'project:business:list'
      },
      {
        method: 'GET',
        path: '/project/business/{id}',
        desc: '商机详情',
        source: 'BusinessQueryController',
        auth: 'project:business:list'
      },
      {
        method: 'GET',
        path: '/project/merchant/list',
        desc: '客户档案列表',
        source: 'MerchantController',
        auth: 'project:merchant:list'
      }
    ]
  },
  {
    name: '流程设计器',
    items: [
      {
        method: 'GET',
        path: '/form/list',
        desc: '流程表单列表',
        source: 'LowCodeController',
        auth: '登录'
      },
      {
        method: 'POST',
        path: '/form/create',
        desc: '创建流程表单',
        source: 'LowCodeController',
        auth: '登录'
      },
      {
        method: 'POST',
        path: '/activiti/mongo/setProcess',
        desc: '保存流程设计',
        source: 'LowCodeController',
        auth: '登录'
      },
      {
        method: 'POST',
        path: '/activiti/mongo/submitProcess',
        desc: '提交流程实例',
        source: 'LowCodeController',
        auth: '登录'
      }
    ]
  },
  {
    name: '合同管理',
    items: [
      {
        method: 'GET',
        path: '/crm/contracts',
        desc: '合同统一列表',
        source: 'CrmContractController',
        auth: '341201/access'
      },
      {
        method: 'POST',
        path: '/workflow/action/execute',
        desc: '发起合同审批或签署动作',
        source: 'WorkflowActionController',
        auth: '绑定的模块动作'
      },
      {
        method: 'POST',
        path: '/project/activity/complete',
        desc: '统一待办办理合同审批',
        source: 'ActivityController / CrmContractWorkflowService',
        auth: '当前审批人及合同审批权限'
      }
    ]
  }
]
