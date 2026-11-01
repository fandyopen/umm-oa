export const routeMapEntries = [
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
    group: '待办',
    menu: '我的待办',
    route: '/todoItem/todo',
    component: 'eos/todoItem/todo/index',
    file: 'src/views/eos/todoItem/todo/index.vue',
    related: ['src/views/eos/todoItem/todo/todo.vue', 'src/views/eos/todoItem/todo/busDetail.vue'],
    api: ['src/api/activiti/process.js', 'src/api/workflow/action.js'],
    backend: ['ActivityController', 'LowCodeWorkflowService', 'WorkflowActionController'],
    note: '列表入口为 index.vue，主要逻辑位于 todo.vue',
    permission: 'todoItem:todo:list'
  },
  {
    group: '待办',
    menu: '我的已办',
    route: '/todoItem/done',
    component: 'eos/todoItem/done/index',
    file: 'src/views/eos/todoItem/done/index.vue',
    related: ['src/views/eos/todoItem/done/done.vue', 'src/views/eos/todoItem/todo/busDetail.vue'],
    api: ['src/api/activiti/process.js', 'src/api/workflow/action.js'],
    backend: ['ActivityController', 'WorkflowActionController'],
    note: '已办列表逻辑位于 done.vue',
    permission: 'todoItem:done:list'
  },
  {
    group: '待办',
    menu: '我的发起',
    route: '/todoItem/my-process-start',
    component: 'eos/todoItem/todo/processStartedPage',
    file: 'src/views/eos/todoItem/todo/processStartedPage.vue',
    related: ['src/views/eos/todoItem/todo/processStarted.vue', 'src/views/eos/todoItem/todo/busDetail.vue'],
    api: ['src/api/activiti/process.js'],
    backend: ['ActivityController', 'LowCodeWorkflowService'],
    note: '查询当前用户发起的流程',
    permission: 'todoItem:done:list'
  },
  {
    group: '待办',
    menu: '抄送我的',
    route: '/todoItem/copy',
    component: 'eos/todoItem/copy/index',
    file: 'src/views/eos/todoItem/copy/index.vue',
    related: ['src/views/eos/todoItem/copy/copy.vue', 'src/views/eos/todoItem/todo/busDetail.vue'],
    api: ['src/api/activiti/process.js', 'src/api/activiti/workflow.js'],
    backend: ['ActivityController'],
    note: '抄送列表与已读状态',
    permission: ''
  },
  {
    group: '营销',
    menu: '商机管理',
    route: '/sale/business-mange',
    component: 'eos/business/index',
    file: 'src/views/eos/business/index.vue',
    related: [
      'src/views/eos/business/modules/BusinessForm.vue',
      'src/views/eos/business/modules/BusinessDetail.vue',
      'src/views/eos/business/modules/BusinessStageForm.vue'
    ],
    api: ['src/api/eos/business/business.js', 'src/api/workflow/action.js'],
    backend: ['BusinessQueryController', 'WorkflowActionController'],
    note: '商机列表、跟进阶段和转化动作',
    permission: 'project:business:list'
  },
  {
    group: '营销',
    menu: '招标投标',
    route: '/sale/bid',
    component: 'eos/bid/index',
    file: 'src/views/eos/bid/index.vue',
    related: ['src/views/eos/bid/modules/BidForm.vue', 'src/views/eos/bid/modules/BidDetail.vue'],
    api: ['src/api/eos/bid/bid.js'],
    backend: ['CompatController', 'ParityRecordService', 'ModuleActionInterceptor'],
    note: '当前没有独立 BidController',
    permission: 'project:bid:list'
  },
  {
    group: '营销',
    menu: '客商管理',
    route: '/sale/merchant',
    component: 'eos/merchant/index',
    file: 'src/views/eos/merchant/index.vue',
    related: ['src/views/eos/merchant/modules/MerchantForm.vue', 'src/views/eos/merchant/modules/MerchantDetail.vue'],
    api: ['src/api/eos/merchant/merchant.js'],
    backend: ['MerchantController', 'ModuleActionInterceptor'],
    note: '客商资料与联系人、账户',
    permission: 'project:merchant:list'
  },
  {
    group: '人事',
    menu: '成员管理',
    route: '/hr/members',
    component: 'enterprise/hr/index',
    file: 'src/views/enterprise/hr/index.vue',
    related: [
      'src/views/enterprise/components/memberElement/index.vue',
      'src/views/enterprise/components/memberElement/details.vue',
      'src/views/enterprise/components/selectDepartmentElement/index.vue'
    ],
    api: [
      'src/api/enterprise/index.js',
      'src/api/system/dept.js',
      'src/api/system/user.js',
      'src/api/workflow/action.js'
    ],
    backend: ['SystemController', 'HrMemberService', 'WorkflowActionController', 'HrMemberActionHandler'],
    note: '邀请、调岗、离职通过动作级流程绑定发起',
    permission: 'hr:member:list'
  },
  {
    group: '系统',
    menu: '组织机构',
    route: '/system/zzjg',
    component: 'enterprise/Contacts/contactsOp',
    file: 'src/views/enterprise/Contacts/contactsOp.vue',
    related: [
      'src/views/enterprise/components/memberElement/index.vue',
      'src/views/enterprise/components/postElement/index.vue',
      'src/views/enterprise/components/roleElement/index.vue',
      'src/views/enterprise/components/adminElement/index.vue'
    ],
    api: ['src/api/enterprise/index.js', 'src/api/system/role.js', 'src/api/system/post.js'],
    backend: ['SystemController', 'OrganizationPermissionService'],
    note: '部门、岗位、角色和管理员分组',
    permission: ''
  },
  {
    group: '系统',
    menu: '角色权限',
    route: '/system/menu-perms',
    component: 'enterprise/modulePerms/index',
    file: 'src/views/enterprise/modulePerms/index.vue',
    related: [
      'src/views/enterprise/modulePerms/modules/rolePerms.vue',
      'src/views/enterprise/modulePerms/modules/menuTree.vue',
      'src/views/enterprise/modulePerms/modules/deptPerms.vue',
      'src/views/enterprise/modulePerms/modules/userPerms.vue'
    ],
    api: ['src/api/system/role.js', 'src/api/system/menu.js'],
    backend: ['SystemController', 'ModuleController', 'ModuleActionService'],
    note: '模块授权与动作授权维护入口',
    permission: 'sys:module'
  },
  {
    group: '系统',
    menu: '基础数据',
    route: '/system/dict',
    component: 'system/dict/index',
    file: 'src/views/system/dict/index.vue',
    related: [
      'src/views/system/dict/modules/DictData.vue',
      'src/views/system/dict/modules/CreateForm.vue',
      'src/views/system/dict/modules/CreateDataForm.vue'
    ],
    api: ['src/api/system/dict/type.js', 'src/api/system/dict/data.js'],
    backend: ['MetadataController'],
    note: '字典类型和字典值管理',
    permission: 'system:dict:list'
  },
  {
    group: '系统',
    menu: '流程设置',
    route: '/system/module_manage',
    component: 'system/menu/index',
    file: 'src/views/system/menu/index.vue',
    related: ['src/views/system/menu/modules/CreateForm.vue', 'src/views/system/moduleAction/index.vue'],
    api: ['src/api/system/menu.js', 'src/api/user/module.js'],
    backend: ['ModuleController', 'AuthController', 'ModuleActionService'],
    note: '按模块维护审批流程设计入口',
    permission: 'sys:module'
  },
  {
    group: '系统',
    menu: '模块管理',
    route: '/system/module',
    component: 'system/module/index',
    file: 'src/views/system/module/index.vue',
    related: ['src/views/system/module/modules/ModuleForm.vue', 'src/views/system/moduleAction/index.vue'],
    api: ['src/api/system/menu.js'],
    backend: ['ModuleController', 'ModuleService', 'ModuleActionService'],
    note: '维护与角色模块树同源的目录、页面和按钮动作',
    permission: 'sys:module'
  },
  {
    group: '系统',
    menu: '参数设置',
    route: '/system/config',
    component: 'system/config/index',
    file: 'src/views/system/config/index.vue',
    related: ['src/views/system/config/modules/CreateForm.vue'],
    api: ['src/api/system/config.js'],
    backend: ['MetadataController'],
    note: '系统参数和首页地址配置',
    permission: 'system:config:list'
  },
  {
    group: '系统',
    menu: '使用文档',
    route: '/system/docs',
    component: 'docs/index',
    file: 'src/views/docs/index.vue',
    related: [
      'src/views/docs/components/RouteMap.vue',
      'src/views/docs/components/ApiDocs.vue',
      'src/views/docs/components/ModuleDocs.vue'
    ],
    api: ['src/api/system/config.js'],
    backend: ['Frontend-only', 'MetadataController'],
    note: '隐藏路由，由 system:docs:list 授权，顶部入口受 sys.docs.enabled 控制',
    permission: 'system:docs:list'
  },
  {
    group: '系统',
    menu: '通知公告',
    route: '/system/notice',
    component: 'system/notice/index',
    file: 'src/views/system/notice/index.vue',
    related: ['src/views/system/notice/CreateForm.vue', 'src/views/system/notice/TinymceEditor.vue'],
    api: ['src/api/system/notice.js'],
    backend: ['MetadataController'],
    note: '公告列表、编辑和消息发送',
    permission: 'system:notice:list'
  },
  {
    group: '系统',
    menu: '操作日志',
    route: '/system/log/operlog',
    component: 'system/log/operlog/index',
    file: 'src/views/system/log/operlog/index.vue',
    related: ['src/views/system/log/operlog/modules/ViewForm.vue'],
    api: ['src/api/system/log/operlog.js'],
    backend: ['MetadataController', 'OperationLogInterceptor'],
    note: '操作审计记录',
    permission: 'monitor:operlog:list'
  },
  {
    group: '系统',
    menu: '登录日志',
    route: '/system/log/logininfor',
    component: 'system/log/logininfor/index',
    file: 'src/views/system/log/logininfor/index.vue',
    related: [],
    api: ['src/api/system/log/logininfor.js'],
    backend: ['MetadataController', 'AuthController'],
    note: '登录审计记录',
    permission: 'monitor:logininfor:list'
  },
  {
    group: '全局',
    menu: '流程设计器',
    route: '/processDesign',
    component: 'processDesign',
    file: 'src/views/lowCode/processDesign/index.vue',
    related: ['src/views/lowCode/components/Process/index.vue', 'src/views/lowCode/formDesignElement/index.vue'],
    api: ['src/api/processDesign/index.js', 'src/api/processDesign/form.js', 'src/api/system/menu.js'],
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
      { method: 'GET', path: '/captchaImage', desc: '获取登录验证码', source: 'AuthController', auth: '公开' },
      { method: 'POST', path: '/login', desc: '账号密码登录并返回 JWT', source: 'AuthController', auth: '公开' },
      { method: 'GET', path: '/getInfo', desc: '获取用户、角色、权限码和岗位', source: 'AuthController', auth: '登录' },
      { method: 'GET', path: '/getRouters', desc: '按用户模块授权返回动态菜单树', source: 'AuthController / ModuleService', auth: '登录' }
    ]
  },
  {
    name: '待办与流程',
    items: [
      { method: 'GET', path: '/project/activity/taskWaitList', desc: '我的待办', source: 'ActivityController', auth: '登录' },
      { method: 'GET', path: '/project/activity/taskDoneList', desc: '我的已办', source: 'ActivityController', auth: '登录' },
      { method: 'POST', path: '/project/activity/complete', desc: '完成待办任务', source: 'ActivityController', auth: '登录' },
      { method: 'POST', path: '/workflow/action/execute', desc: '执行业务动作并触发审批', source: 'WorkflowActionController', auth: '模块动作' }
    ]
  },
  {
    name: '成员与组织',
    items: [
      { method: 'GET', path: '/system/user/list', desc: '成员分页，支持部门、新增日期和姓名筛选', source: 'SystemController', auth: 'hr:member:list' },
      { method: 'GET', path: '/system/user/dismiss_list', desc: '离职成员分页', source: 'SystemController', auth: 'hr:member:list' },
      { method: 'POST', path: '/system/user/add', desc: '邀请成员', source: 'SystemController / HrMemberService', auth: 'hr.member.invite' },
      { method: 'GET', path: '/system/dept/admin/tree', desc: '组织机构部门树', source: 'SystemController', auth: '组织或人事访问权限' }
    ]
  },
  {
    name: '模块、角色与权限',
    items: [
      { method: 'GET', path: '/system/module/list', desc: '模块列表', source: 'ModuleController', auth: 'sys:module' },
      { method: 'GET', path: '/system/module/{moduleId}/actions', desc: '查询模块动作', source: 'ModuleController', auth: 'sys:module' },
      { method: 'POST', path: '/system/module/{moduleId}/actions', desc: '新增模块动作', source: 'ModuleController', auth: 'sys:module' },
      { method: 'PUT', path: '/system/role', desc: '更新角色模块及动作授权', source: 'SystemController', auth: 'sys:module' }
    ]
  },
  {
    name: '基础数据与系统配置',
    items: [
      { method: 'GET', path: '/system/dict/type/list', desc: '字典类型列表', source: 'MetadataController', auth: 'system:dict:list' },
      { method: 'GET', path: '/system/config/list', desc: '系统参数列表', source: 'MetadataController', auth: 'system:config:list' },
      { method: 'GET', path: '/system/config/configKey/{configKey}', desc: '按键读取系统参数', source: 'MetadataController', auth: '登录' },
      { method: 'GET', path: '/system/notice/list', desc: '通知公告列表', source: 'MetadataController', auth: 'system:notice:list' }
    ]
  },
  {
    name: '营销业务',
    items: [
      { method: 'GET', path: '/project/business/list', desc: '商机分页与数据范围过滤', source: 'BusinessQueryController', auth: 'project:business:list' },
      { method: 'GET', path: '/project/business/{id}', desc: '商机详情', source: 'BusinessQueryController', auth: 'project:business:list' },
      { method: 'GET', path: '/project/bid/list', desc: '招标投标列表', source: 'CompatController', auth: 'project:bid:list' },
      { method: 'GET', path: '/project/merchant/list', desc: '客商列表', source: 'MerchantController', auth: 'project:merchant:list' }
    ]
  },
  {
    name: '流程设计器',
    items: [
      { method: 'GET', path: '/form/list', desc: '流程表单列表', source: 'LowCodeController', auth: '登录' },
      { method: 'POST', path: '/form/create', desc: '创建流程表单', source: 'LowCodeController', auth: '登录' },
      { method: 'POST', path: '/activiti/mongo/setProcess', desc: '保存流程设计', source: 'LowCodeController', auth: '登录' },
      { method: 'POST', path: '/activiti/mongo/submitProcess', desc: '提交流程实例', source: 'LowCodeController', auth: '登录' }
    ]
  }
]

export { moduleGroups } from './moduleGuides.js'
