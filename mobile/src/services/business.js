import { request, can, submitAction } from './runtime'
import { catalog } from './catalog'
// These maps and stage predicates are the same pure definitions used by the PC workspace.
export { applicationSubstate } from '../../../src/views/eos/recruitment/shared'
import { applicationActions, jobFields, talentFields, statuses } from '../../../src/views/eos/recruitment/shared'
export const label = (value) =>
  ({
    ...statuses,
    pending: '审批中',
    approved: '已通过',
    active: '履约中',
    confirmed: '已确认',
    paid: '已支付',
    linked: '已关联工资',
    unpaid: '未支付'
  }[value] ||
  value ||
  '—')
export const fields = {
  name: '名称',
  title: '标题',
  serial: '客户编号',
  code: '编号',
  contractCode: '合同编号',
  contractName: '合同名称',
  customerName: '客户',
  jobTitle: '职位',
  talentName: '候选人',
  consultantName: '交付顾问',
  ownerUserName: '负责人',
  ownerName: '负责人',
  industry: '行业',
  companyScale: '企业规模',
  region: '地区',
  city: '城市',
  address: '地址',
  phone: '电话',
  email: '邮箱',
  contactName: '联系人',
  cooperationStatus: '合作状态',
  status: '状态',
  stage: '阶段',
  feedbackStatus: '客户反馈',
  offerStatus: 'Offer状态',
  guaranteeStatus: '保证期状态',
  expectedEntryDate: '预计入职日期',
  actualEntryDate: '实际入职日期',
  guaranteeEndDate: '保证期截止',
  startDate: '开始日期',
  endDate: '结束日期',
  signDate: '签署日期',
  amount: '金额（元）',
  contractAmount: '合同金额（元）',
  receivedAmount: '已收款（元）',
  outstandingAmount: '待收款（元）',
  businessDate: '业务日期',
  period: '所属期间',
  month: '月份',
  netAmount: '实发工资（元）',
  grossAmount: '应发工资（元）',
  paymentStatus: '发放状态',
  reason: '说明',
  remark: '备注',
  requirements: '岗位要求',
  skills: '技能',
  experienceYears: '工作年限',
  expectedSalary: '期望月薪',
  salary: 'Offer薪资',
  headcount: '招聘人数',
  recommendation: '推荐说明',
  followupTime: '跟进时间',
  method: '跟进方式',
  content: '跟进内容',
  nextPlan: '下一步计划',
  nextFollowupTime: '下次跟进',
  createTime: '创建时间',
  applyUserName: '发起人',
  taskName: '当前节点',
  title: '名称',
  termTitle: '收费节点',
  dueDate: '到期日期',
  guaranteeDays: '保证期天数',
  chargeMode: '收费方式',
  rate: '费率',
  versionNo: '版本',
  changeSummary: '变更说明',
  roundNo: '面试轮次',
  interviewTime: '面试时间',
  interviewer: '面试官',
  location: '面试地点',
  result: '结果',
  interviewResult: '面试结果',
  workDays: '出勤天数',
  absenceDays: '缺勤天数',
  leaveDays: '请假天数',
  overtimeHours: '加班时数',
  lateCount: '迟到次数',
  side: '归属部门',
  invoiceTitle: '发票抬头',
  invoiceNo: '发票号码',
  bankAccount: '收款账号',
  receiptDate: '到账日期',
  payrollPaid: '已发工资（元）'
}
export function displayRows(value, custom = []) {
  const labels = { ...fields }
  custom.forEach((f) => {
    if (f.__vModel__ || f.key) labels[f.__vModel__ || f.key] = (f.__config__ && f.__config__.label) || f.label || f.key
  })
  return Object.entries(value || {})
    .filter(([k, v]) => labels[k] && v !== null && v !== undefined && v !== '' && typeof v !== 'object')
    .map(([key, v]) => ({
      key,
      title: labels[key],
      value: ['status', 'stage', 'feedbackStatus', 'offerStatus', 'guaranteeStatus', 'result', 'interviewResult'].includes(key)
        ? label(v)
        : key === 'side'
        ? { development: '客户拓展', delivery: '招聘交付' }[v] || v
        : String(v)
    }))
}
// Flatten snapshots before rendering because native mini-program components cannot recurse into themselves.
export function approvalGroups(value, schema = []) {
  const nested = {
    snapshot: '提交时资料',
    fields: '申请变更内容',
    feeTerms: '收费条款',
    lines: '提成核算明细',
    rows: '员工明细',
    items: '明细',
    commissionSources: '提成来源',
    rule: '采用规则',
    before: '变更前',
    after: '变更后'
  }
  const labels = { ...fields }
  schema.forEach((f) => {
    const key = f.__vModel__ || f.key
    if (key) labels[key] = (f.__config__ && f.__config__.label) || f.label || key
  })
  const groups = []
  function visit(item, title, key, depth) {
    if (!item || typeof item !== 'object' || Array.isArray(item) || depth > 8) return
    const configured = Array.isArray(item._workflowDisplay)
      ? item._workflowDisplay
          .filter((f) => f && f.label)
          .map((f) => ({
            ...f,
            text: typeof f.value === 'object' ? JSON.stringify(f.value, null, 2) : String(f.value === undefined ? '—' : f.value)
          }))
      : []
    const rows = configured.length ? [] : displayRows(item, schema)
    const seen = new Set(rows.map((row) => row.key))
    Object.entries(item).forEach(([field, data]) => {
      if (configured.length) return
      if (seen.has(field) || field.startsWith('_') || /password|token|secret/i.test(field) || nested[field]) return
      if ((labels[field] && Array.isArray(data) && data.every((v) => typeof v !== 'object')) || item._workflowApi)
        rows.push({
          key: field,
          title: labels[field] || field,
          value: typeof data === 'object' ? JSON.stringify(data, null, 2) : String(data === undefined ? '—' : data)
        })
    })
    groups.push({ key, title, depth, rows, configured, attachment: item.attachment || '' })
    if (item._workflowApi && configured.length) return
    const children = { ...nested }
    schema.forEach((f) => {
      const name = f.__vModel__ || f.key
      if (name && Array.isArray(item[name]) && item[name].some((v) => v && typeof v === 'object')) children[name] = labels[name]
    })
    Object.entries(children).forEach(([field, label]) => {
      const data = item[field]
      if (!data || typeof data !== 'object') return
      const values = Array.isArray(data) ? data : [data]
      values.forEach((child, index) =>
        visit(child, label + (values.length > 1 ? ' · ' + (index + 1) : ''), key + '.' + field + '.' + index, depth + 1)
      )
    })
  }
  visit(value, '', 'root', 0)
  return groups
}
Object.assign(fields, {
  requiredDays: '应出勤天数',
  scheduledHours: '排班时数',
  actualHours: '实际工时',
  workDate: '考勤日期',
  personalLeaveHours: '事假时数',
  sickLeaveHours: '病假时数',
  paidLeaveHours: '带薪假时数',
  absentHours: '旷工时数',
  baseAmount: '基本工资（元）',
  allowance: '津贴（元）',
  overtime: '加班工资（元）',
  commission: '提成（元）',
  adjustment: '工资调整（元）',
  deduction: '扣款（元）',
  tax: '个税（元）',
  contribution: '社保公积金（元）',
  bonus: '奖金（元）',
  signedDate: '实际签署日期',
  serviceType: '服务类型',
  demandStatus: '需求确认',
  offerSalary: 'Offer月薪（元）',
  guaranteeEnd: '保证期结束日期',
  recordType: '单据类型',
  receivableTitle: '应收项目',
  referenceNo: '业务流水号',
  channel: '收付款渠道',
  category: '成本类别',
  payee: '收款对象',
  taxNo: '税号',
  employeeName: '员工姓名',
  employeeNo: '工号',
  nickName: '员工姓名',
  deptName: '部门',
  netAllocated: '净回款（元）',
  eligible: '累计可核算（元）',
  reserved: '以前已核算（元）',
  paidAmount: '已发金额（元）',
  targetDate: '目标完成日期',
  salaryMin: '月薪下限（元）',
  salaryMax: '月薪上限（元）',
  entityName: '职位 / 候选人',
  actionName: '审批动作',
  targetName: '目标档案',
  lateMinutes: '迟到分钟',
  absentDays: '旷工天数',
  actualDays: '实际出勤',
  expectedDays: '应出勤',
  baseSalary: '基本工资（元）',
  commissionAmount: '提成金额（元）',
  deductionAmount: '扣款金额（元）',
  bonusAmount: '奖金金额（元）',
  rowNumber: '文件行号',
  phonenumber: '手机号',
  trigger: '触发条件',
  version: '版本',
  evaluation: '评价',
  operatorName: '操作人',
  eventType: '操作',
  originalName: '文件名称'
})
const f = (key, label, type = 'text', required = false, options) => ({ key, label, type, required, options })
const options = (values) => Object.entries(values).map(([value, label]) => ({ value, label }))
const reason = f('reason', '说明 / 原因', 'textarea', true)
const date = () => new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10)
export async function loadOptions(key) {
  if (['jobs', 'talents', 'applications'].includes(key)) return (await request('/project/recruitment/options')).data || {}
  if (['receipt', 'invoice'].includes(key)) return (await request('/project/crm/finance/options')).data || {}
  if (key === 'customers') return { users: (await request('/project/merchant/owner/options')).data || [] }
  return {}
}
const userOptions = (rows) => (rows || []).map((r) => ({ value: r.id || r.userId, label: r.name || r.nickName || r.userName }))
export function formDefinition(key, action, row = {}, opts = {}) {
  const base = { revision: row.revision, id: row.id }
  if (key === 'contracts' && action === 'sign')
    return {
      fields: [f('signedDate', '实际签署日期', 'date', true)],
      values: { ...base, contractId: row.id, signedDate: date(), title: '合同签署：' + row.name }
    }
  if (key === 'customers') {
    if (action === 'track')
      return {
        fields: [
          f('followupTime', '跟进时间', 'datetime', true),
          f(
            'method',
            '跟进方式',
            'select',
            true,
            options({ 电话: '电话', 微信: '微信', 邮件: '邮件', 拜访: '拜访', 会议: '会议', 其他: '其他' })
          ),
          f('content', '跟进内容', 'textarea', true),
          f('nextPlan', '下一步计划', 'textarea'),
          f('nextFollowupTime', '下次跟进时间', 'datetime')
        ],
        values: { customerId: row.id, customerName: row.name, followupTime: date() + ' 09:00:00', method: '电话', content: '' }
      }
    return {
      fields: [
        f('name', '客户名称', 'text', true),
        f('industry', '行业', 'text', true),
        f('ownerUserId', '客户负责人', 'select', true, userOptions(opts.users)),
        f('companyScale', '企业规模'),
        f('region', '地区'),
        f('address', '地址'),
        f('customerSource', '客户来源'),
        f('remark', '备注', 'textarea')
      ],
      values: { ...row }
    }
  }
  if (action === 'add' || action === 'edit') {
    if (key === 'jobs')
      return {
        fields: jobFields(opts).map((f) => ({ ...f, disabled: action === 'edit' && ['customerId', 'ownerUserId'].includes(f.key) })),
        values: { headcount: 1, collaborators: [], ...row }
      }
    if (key === 'talents')
      return {
        fields: talentFields(opts, row.tags || []).map((f) => ({ ...f, disabled: action === 'edit' && f.key === 'ownerUserId' })),
        values: { collaborators: [], tags: [], ...row }
      }
    if (key === 'applications')
      return {
        fields: [
          f(
            'jobId',
            '招聘职位',
            'select',
            true,
            (opts.jobs || []).filter((j) => j.status === 'recruiting').map((j) => ({ value: j.id, label: j.title || j.name }))
          ),
          f(
            'talentId',
            '候选人（仅沟通中、可推荐）',
            'select',
            true,
            (opts.talents || [])
              .filter((t) => ['contacting', 'available'].includes(t.status) && t.canRecommend === true)
              .map((t) => ({ value: t.id, label: t.name }))
          ),
          f('consultantId', '交付顾问', 'select', true, userOptions(opts.users)),
          f('recommendation', '推荐说明', 'textarea', true)
        ],
        values: {}
      }
    if (['receipt', 'invoice'].includes(key))
      return {
        fields: [
          f(
            'sourceKey',
            '应收节点',
            'select',
            true,
            (opts.sources || [])
              .filter((s) => s.confirmed !== false)
              .map((s) => ({
                value: s.sourceKey,
                label: [s.customerName, s.contractCode, s.title || s.termTitle, '待收 ' + s.outstandingAmount].filter(Boolean).join(' · ')
              }))
          ),
          f('amount', '金额（元）', 'number', true),
          f('businessDate', '业务日期', 'date', true),
          ...(key === 'invoice' ? [f('invoiceTitle', '发票抬头', 'text', true), f('taxNo', '纳税识别号')] : []),
          f('reason', '说明', 'textarea'),
          f('attachment', '业务凭证', 'upload', true)
        ],
        values: { businessDate: date(), createToken: 'mobile_' + Date.now() + '_' + Math.random().toString(36).slice(2), ...row }
      }
  }
  if (key === 'jobs' && action === 'status')
    return {
      fields: [
        f(
          'status',
          '职位状态',
          'select',
          true,
          options({ draft: '待启动', recruiting: '招聘中', paused: '暂停', completed: '已完成', closed: '已关闭' })
        ),
        reason
      ],
      values: { ...base, status: row.status }
    }
  if (key === 'jobs' && action === 'demand')
    return { fields: [reason], values: { ...base, entityId: row.id, title: '职位需求确认：' + row.title } }
  if (key === 'jobs' && action === 'change')
    return {
      fields: [
        ...jobFields(opts).filter(
          (f) => !['customerId', 'ownerUserId', 'collaborators', 'cycleDays', 'minRecommendations', 'feedbackDays'].includes(f.key)
        ),
        reason
      ],
      values: { ...row }
    }
  if (key === 'talents' && action === 'contact') return { fields: [reason], values: { ...base } }
  if (key === 'talents' && action === 'status')
    return {
      fields: [
        f(
          'status',
          '人才状态',
          'select',
          true,
          options({
            uncontacted: '待联系',
            contacting: '沟通中',
            available: '可推荐',
            not_now: '暂不考虑',
            dormant: '休眠',
            archived: '已归档',
            disabled: '停用'
          })
        ),
        reason
      ],
      values: { ...base, status: row.status }
    }
  if (key === 'applications') {
    const attachments = (row.attachments || []).map((a) => ({ value: a.id, label: a.originalName || a.name }))
    const doc = f('attachmentId', '业务附件', 'select', true, attachments)
    const terms = (row.guaranteeTerms || []).map((t) => ({ value: t.key || t.termKey, label: t.title || t.termTitle }))
    if (action === 'feedback')
      return {
        fields: [
          f(
            'status',
            '客户反馈',
            'select',
            true,
            options({ pending: '待反馈', interested: '有意向 / 邀约面试', rejected: '不合适', need_info: '需补充资料' })
          ),
          f('dueDate', '下次反馈期限', 'date'),
          reason
        ],
        values: { ...base, status: row.feedbackStatus || 'pending' }
      }
    if (action === 'interview')
      return {
        fields: [
          f('roundNo', '面试轮次', 'number', true),
          f('interviewTime', '面试时间', 'datetime', true),
          f('location', '面试地点', 'text', true),
          f('interviewer', '面试官', 'text', true),
          reason
        ],
        values: { ...base, roundNo: (row.interviews || []).length + 1, operation: 'save' }
      }
    if (action === 'interviewResult')
      return {
        fields: [
          f(
            'interviewId',
            '面试轮次',
            'select',
            true,
            (row.interviews || [])
              .filter((i) => (i.availableOperations || []).includes('result'))
              .map((i) => ({ value: i.id, label: '第' + i.roundNo + '轮 · ' + i.interviewTime }))
          ),
          f('result', '面试结果', 'select', true, options({ pass: '通过', fail: '未通过', pending: '待定' })),
          reason
        ],
        values: { ...base, operation: 'result' }
      }
    if (action === 'offer')
      return {
        fields: [f('salary', 'Offer月薪（元）', 'number', true), f('attachmentId', 'Offer附件', 'select', false, attachments), reason],
        values: { ...base, operation: 'save', salary: row.offerSalary }
      }
    if (['entry', 'correctEntry'].includes(action))
      return {
        fields: [
          f('actualEntryDate', '实际入职日期', 'date', true),
          doc,
          f('termKey', '合同收费与保证期条款', 'select', true, terms),
          reason
        ],
        values: { ...base, operation: action === 'entry' ? 'confirm' : 'correct', actualEntryDate: date() }
      }
    if (['acceptOffer', 'expectedEntry'].includes(action))
      return {
        fields: [f('expectedEntryDate', '预计入职日期', 'date', true), reason],
        values: {
          ...base,
          operation: action === 'acceptOffer' ? 'accept' : 'expected',
          expectedEntryDate: row.expectedEntryDate || date()
        },
        endpoint: action === 'acceptOffer' ? 'offer' : 'entry'
      }
    if (action === 'guarantee')
      return {
        fields: [
          f(
            'operation',
            '保证期处理',
            'select',
            true,
            options({ complete: '期满结案', exit: '保证期离职', refund: '登记退费', replacement: '替换招聘' }).filter((o) =>
              (row.availableOperations || []).includes(
                { complete: 'completeGuarantee', exit: 'guaranteeExit', refund: 'guaranteeRefund', replacement: 'guaranteeReplacement' }[
                  o.value
                ]
              )
            )
          ),
          f('exitDate', '离职日期', 'date'),
          f('amount', '退费金额（退费时填写）', 'number'),
          f('attachmentId', '业务证明', 'select', false, attachments),
          reason
        ],
        values: { ...base }
      }
    if (action === 'postGuaranteeExit')
      return { fields: [f('exitDate', '离职日期', 'date', true), reason], values: { ...base, operation: 'exit' }, endpoint: 'guarantee' }
    if (action === 'entryDelay')
      return {
        fields: [f('expectedEntryDate', '调整后预计入职日期', 'date'), reason],
        values: { ...base, operation: 'create', type: 'entry_delay', followupRequired: true },
        endpoint: 'exception'
      }
    if (action === 'followupManage')
      return {
        fields: [
          f(
            'exceptionId',
            '待处理事项',
            'select',
            true,
            (row.exceptions || [])
              .filter((e) => e.status === 'open' && e.exceptionType !== 'guarantee_replacement')
              .map((e) => ({ value: e.id, label: e.content }))
          ),
          reason
        ],
        values: { ...base, operation: 'resolve' },
        endpoint: 'exception'
      }
    if (action === 'replacement')
      return {
        fields: [
          f(
            'talentId',
            '替换候选人',
            'select',
            true,
            (opts.talents || [])
              .filter((t) => ['contacting', 'available'].includes(t.status) && t.canRecommend === true)
              .map((t) => ({ value: t.id, label: t.name }))
          ),
          f('consultantId', '交付顾问', 'select', true, userOptions(opts.users)),
          f('recommendation', '替换推荐说明', 'textarea', true)
        ],
        values: { ...base, consultantId: row.consultantId },
        endpoint: 'replacement'
      }
    const operations = {
      issueOffer: ['offer', 'issue'],
      rejectOffer: ['offer', 'reject'],
      withdrawOffer: ['offer', 'withdraw'],
      withdrawRecommendation: ['recommend', 'withdraw'],
      recommend: ['recommend', 'submit'],
      finishRecommendation: ['exception', 'finish'],
      candidateWithdraw: ['exception', 'create'],
      customerCancel: ['exception', 'create']
    }
    if (operations[action])
      return {
        fields: [reason],
        values: {
          ...base,
          operation: operations[action][1],
          type: action === 'candidateWithdraw' ? 'candidate_withdraw' : action === 'customerCancel' ? 'customer_cancel' : undefined
        },
        endpoint: operations[action][0]
      }
  }
  if (action === 'issue')
    return {
      fields: [
        f('businessDate', '实际开票日期', 'date', true),
        f('invoiceNo', '发票号码', 'text', true),
        f('attachment', '发票凭证', 'upload', true)
      ],
      values: { ...base, businessDate: date() }
    }
  throw new Error('当前操作尚未提供移动表单，请在平台端办理')
}
export function availableActions(key, row) {
  const all = []
  if (key === 'contracts' && row.canEditCustomer) {
    if (['draft', 'rejected'].includes(row.status)) all.push({ key: 'submit', label: '提交合同审批', permission: 'submit' })
    if (row.status === 'approved' && !row.signingPending) all.push({ key: 'sign', label: '签署生效', permission: 'sign' })
  }
  if (
    key === 'customers' ||
    (key === 'jobs' && ['draft', 'rejected', 'withdrawn'].includes(row.demandStatus)) ||
    (key === 'talents' && row.contactVisible && !['merged', 'archived', 'disabled'].includes(row.status))
  )
    all.push({ key: 'edit', label: '修改', permission: 'edit' })
  if (key === 'customers') all.push({ key: 'track', label: '新增跟进', permission: 'track' })
  if (key === 'jobs') {
    all.push({ key: 'status', label: '调整招聘状态', permission: 'status' })
    if (['draft', 'rejected', 'withdrawn'].includes(row.demandStatus))
      all.push({ key: 'demand', label: '提交需求确认', permission: 'demand' })
    if (row.demandStatus === 'approved') all.push({ key: 'change', label: '提交职位变更', permission: 'change' })
  }
  if (key === 'talents')
    all.push({ key: 'contact', label: '登记沟通', permission: 'contact' }, { key: 'status', label: '调整人才状态', permission: 'status' })
  if (key === 'applications') {
    const supported = [
      'feedback',
      'interview',
      'interviewResult',
      'offer',
      'issueOffer',
      'acceptOffer',
      'rejectOffer',
      'withdrawOffer',
      'entry',
      'correctEntry',
      'expectedEntry',
      'entryDelay',
      'guarantee',
      'postGuaranteeExit',
      'replacement',
      'followupManage',
      'withdrawRecommendation',
      'recommend',
      'finishRecommendation',
      'candidateWithdraw',
      'customerCancel'
    ]
    all.push(...applicationActions(row).filter((a) => supported.includes(a.key)))
  }
  if (['receipt', 'invoice'].includes(key)) {
    if (['draft', 'rejected'].includes(row.status))
      all.push({ key: 'submit', label: '提交确认 / 审批', permission: key === 'receipt' ? 'receipt_confirm' : 'invoice_submit' })
    if (key === 'invoice' && row.status === 'approved') all.push({ key: 'issue', label: '登记实际开票', permission: 'invoice_issue' })
  }
  return all.filter((a) => can(key, a.permission) && (a.key !== 'replacement' || can(key, 'add')))
}
export async function saveBusiness(key, action, row, values, opts, definition) {
  if (key === 'contracts' && ['submit', 'sign'].includes(action))
    return submitAction(key, action, values || { contractId: row.id, revision: row.revision, title: '合同审批：' + row.name })
  if (key === 'customers' && action === 'track') return submitAction(key, 'track', values)
  if (key === 'jobs' && action === 'demand') return submitAction(key, 'demand', values)
  if (key === 'jobs' && action === 'change')
    return submitAction(key, 'change', {
      entityId: row.id,
      revision: row.revision,
      reason: values.reason,
      fields: values,
      title: '职位变更：' + row.title
    })
  if (['receipt', 'invoice'].includes(key) && action === 'submit')
    return submitAction(key, key === 'receipt' ? 'receipt_confirm' : 'invoice_submit', { recordId: row.id, revision: row.revision })
  if (['add', 'edit'].includes(action)) {
    if (key === 'customers') return request('/project/merchant', action === 'edit' ? 'PUT' : 'POST', values)
    if (['jobs', 'talents', 'applications'].includes(key))
      return request('/project/recruitment/' + key + (action === 'edit' ? '/' + row.id : ''), action === 'edit' ? 'PUT' : 'POST', values)
    if (['receipt', 'invoice'].includes(key)) {
      const source = (opts.sources || []).find((s) => s.sourceKey === values.sourceKey)
      if (!source) throw new Error('应收节点已失效，请重新选择')
      const data = {
        ...values,
        customerId: source.customerId,
        contractId: source.contractId,
        termKey: source.termKey,
        receivableId: source.receivableId
      }
      delete data.sourceKey
      return request('/project/crm/finance/' + key, 'POST', data)
    }
  }
  if (key === 'applications') {
    const base = '/project/recruitment/applications/' + row.id
    if (action === 'replacement') return request(base + '/replacement', 'POST', values)
    if (action === 'interview') return request(base + '/interviews', 'POST', values)
    if (action === 'interviewResult') return request(base + '/interviews/' + values.interviewId, 'PUT', values)
    return request(
      base + '/actions/' + (definition.endpoint || (['correctEntry', 'expectedEntry'].includes(action) ? 'entry' : action)),
      'POST',
      values
    )
  }
  if (['jobs', 'talents'].includes(key)) return request('/project/recruitment/' + key + '/' + row.id + '/actions/' + action, 'POST', values)
  if (action === 'issue') return request('/project/crm/finance/' + key + '/' + row.id + '/issue', 'POST', values)
  throw new Error('不支持的操作')
}
