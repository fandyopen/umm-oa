// Component selectors resolve current platform metadata; numeric module IDs are never assumed.
export const catalog = {
  customers: {
    title: '客户档案',
    group: '客户与合同',
    icon: '客',
    component: 'eos/crm/customer/index',
    list: '/project/merchant/list',
    detail: '/project/merchant/',
    search: 'name',
    create: true
  },
  contracts: {
    title: '合同列表',
    group: '客户与合同',
    icon: '约',
    component: 'eos/crm/contract/index',
    list: '/project/crm/contracts',
    detail: '/project/crm/contracts/',
    search: 'keyword'
  },
  risks: {
    title: '客户风险',
    group: '客户与合同',
    icon: '醒',
    component: 'eos/crm/risk/index',
    list: '/project/crm/risk/list',
    clientPage: true
  },
  jobs: {
    title: '职位管理',
    group: '招聘管理',
    icon: '职',
    component: 'eos/recruitment/jobs/index',
    list: '/project/recruitment/jobs',
    detail: '/project/recruitment/jobs/',
    create: true
  },
  talents: {
    title: '人才库',
    group: '招聘管理',
    icon: '才',
    component: 'eos/recruitment/talents/index',
    list: '/project/recruitment/talents',
    detail: '/project/recruitment/talents/',
    create: true
  },
  applications: {
    title: '招聘流程',
    group: '招聘管理',
    icon: '聘',
    component: 'eos/recruitment/pipeline/index',
    list: '/project/recruitment/applications',
    detail: '/project/recruitment/applications/',
    create: true
  },
  ledger: { title: '应收台账', group: '财务与回款', icon: '账', component: 'eos/crm/finance/index', list: '/project/crm/finance/ledger' },
  receipt: {
    title: '回款登记',
    group: '财务与回款',
    icon: '款',
    component: 'eos/crm/finance/index',
    list: '/project/crm/finance/receipt',
    detail: '/project/crm/finance/records/',
    create: true
  },
  invoice: {
    title: '开票申请',
    group: '财务与回款',
    icon: '票',
    component: 'eos/crm/finance/index',
    list: '/project/crm/finance/invoice',
    detail: '/project/crm/finance/records/',
    create: true
  },
  commission: {
    title: '我的提成',
    group: '员工自助',
    icon: '成',
    component: 'enterprise/hr/self',
    list: '/project/commission/self',
    self: true
  },
  attendance: {
    title: '我的考勤',
    group: '员工自助',
    icon: '勤',
    component: 'enterprise/hr/self',
    list: '/project/hr/self/attendance',
    self: true
  },
  payslips: {
    title: '我的工资条',
    group: '员工自助',
    icon: '薪',
    component: 'enterprise/hr/self',
    list: '/project/hr/self/payslips',
    self: true
  }
}
export const taskPaths = { todo: 'taskWaitList', done: 'taskDoneList', copy: 'taskCopyList', start: 'started' }
export function positiveId(value) {
  return /^\d+$/.test(String(value)) && Number.isSafeInteger(Number(value)) && Number(value) > 0 ? Number(value) : null
}
export function pageRows(response) {
  const data = response.data === undefined ? response : response.data
  if (Array.isArray(data)) return { rows: data, total: data.length }
  return { rows: data.rows || data.items || [], total: Number(data.total || 0) }
}
export function actionAllowed(session, meta, action) {
  if (!meta || !meta.moduleId) return false
  const config = (session.moduleActions || []).find((a) => String(a.moduleId) === String(meta.moduleId) && a.actionCode === action)
  if (!config || String(config.status) !== '0') return false
  const permissions = session.permissions || []
  const permission =
    (config && config.permissionCode) ||
    (String(meta.perms || '').endsWith(':list') ? meta.perms.slice(0, -5) + ':' + action : `module:${meta.moduleId}:${action}`)
  return (
    permissions.includes('*:*:*') ||
    permissions.includes(permission) ||
    permissions.includes(`module:${meta.moduleId}:${action}`) ||
    permissions.includes(`module:${meta.moduleId}:manage`)
  )
}
export function moduleVisible(session, meta) {
  return !!meta && ((session.permissions || []).includes('*:*:*') || (!!meta.perms && (session.permissions || []).includes(meta.perms)))
}
