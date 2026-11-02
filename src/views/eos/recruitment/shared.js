export const recruitmentModules = {
  jobs: { component: 'eos/recruitment/jobs/index', label: '职位管理' },
  talents: { component: 'eos/recruitment/talents/index', label: '人才库' },
  applications: { component: 'eos/recruitment/pipeline/index', label: '招聘流程' }
}
export const jobStates = { draft: '待启动', recruiting: '招聘中', paused: '暂停', completed: '已完成', closed: '已关闭' }
export const talentStates = { draft: '草稿', uncontacted: '待联系', contacting: '沟通中', available: '可推荐', not_now: '暂不考虑', dormant: '休眠', archived: '已归档', disabled: '已停用', merged: '已合并' }
export const stages = { draft: '推荐草稿', recommended: '已推荐', feedback: '客户反馈', interview: '面试中', offer: 'Offer', entry: '待入职', guarantee: '保证期', completed: '已结案', rejected: '已淘汰', withdrawn: '已撤回', exited: '已离职 / 退出' }
export const approvalStates = { draft: '未提交', pending: '审批中', approved: '已通过', rejected: '已退回', withdrawn: '已撤回' }
export const statuses = { ...jobStates, ...talentStates, ...stages, ...approvalStates, active: '有效', disabled: '停用', scheduled: '待面试', cancelled: '已取消', issued: '已发放', accepted: '已接受', pass: '通过', fail: '未通过', interested: '有意向', need_info: '待补充', open: '待处理', closed: '已关闭', todo: '待跟进', done: '已完成', refund: '退费', replacement: '替换', exit: '离职' }
export const label = value => statuses[value] || value || '—'
export const choices = obj => Object.entries(obj).map(([value, label]) => ({ value, label }))
export const optionRows = rows => (rows || []).map(row => ({ value: row.id, label: row.name }))
export const field = (key, label, type = 'text', extra = {}) => ({ key, label, type, ...extra })
export const reasonField = field('reason', '说明 / 原因', 'textarea', { required: true, max: 2000 })

export const jobFields = options => [
  field('title', '职位名称', 'text', { required: true, max: 200 }),
  field('customerId', '所属客户', 'select', { required: true, options: optionRows(options.customers) }),
  field('contractId', '服务合同', 'contract', { options: options.contracts || [], hint: '须为所属客户的已签署合同；实际入职前必须关联，以生成合同保证期。' }),
  field('ownerUserId', '职位负责人', 'select', { required: true, options: optionRows(options.users) }),
  field('collaborators', '协作顾问', 'multiple', { options: optionRows(options.users) }),
  field('city', '工作地点'), field('industry', '所属行业'), field('experience', '经验要求'),
  field('salaryMin', '月薪下限（元）', 'number'), field('salaryMax', '月薪上限（元）', 'number'),
  field('headcount', '招聘人数', 'number', { required: true, min: 1, precision: 0 }),
  field('requirements', '岗位要求', 'textarea', { required: true, max: 16000 }),
  field('feeTerms', '职位收费条件', 'textarea', { max: 2000, hint: '作为职位补充约定，保证期仍以签署合同条款为准。' }),
  field('targetDate', '目标完成日期', 'date'),
  field('cycleDays', '招聘周期（天）', 'number', { min: 1, precision: 0 }),
  field('minRecommendations', '最低推荐数量', 'number', { precision: 0 }),
  field('feedbackDays', '客户反馈期限（天）', 'number', { min: 1, precision: 0 })
]
export const talentFields = (options, retainedTags = []) => [
  field('name', '候选人姓名', 'text', { required: true, max: 100 }),
  field('phone', '手机号', 'text', { max: 40 }), field('email', '邮箱', 'text', { max: 200 }),
  field('ownerUserId', '负责人', 'select', { required: true, options: optionRows(options.users) }),
  field('collaborators', '协作人', 'multiple', { options: optionRows(options.users) }),
  field('city', '所在城市'), field('industry', '行业'), field('skills', '技能', 'text', { max: 1000 }),
  field('experienceYears', '工作经验（年）', 'number', { max: 80, precision: 0 }),
  field('expectedSalary', '期望月薪（元）', 'number'), field('source', '人才来源'),
  field('tags', '标签', 'multiple', { options: (options.tags || []).filter(t => t.status === 'active' || retainedTags.includes(t.name)).map(t => ({ value: t.name, label: t.name + (t.status === 'active' ? '' : '（已停用）'), disabled: t.status !== 'active' })) }),
  field('remark', '备注', 'textarea', { max: 2000 })
]
