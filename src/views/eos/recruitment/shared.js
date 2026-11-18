export const recruitmentModules = {
  jobs: { component: 'eos/recruitment/jobs/index', label: '职位管理' },
  talents: { component: 'eos/recruitment/talents/index', label: '人才库' },
  applications: { component: 'eos/recruitment/pipeline/index', label: '招聘流程' }
}
export const jobStates = { draft: '待启动', recruiting: '招聘中', paused: '暂停', completed: '已完成', closed: '已关闭' }
export const talentStates = { draft: '草稿', uncontacted: '待联系', contacting: '沟通中', available: '可推荐', not_now: '暂不考虑', dormant: '休眠', archived: '已归档', disabled: '已停用', merged: '已合并' }
export const stages = { recommended: '已推荐', feedback: '客户反馈', interview: '面试中', offer: 'Offer', entry: '待入职', guarantee: '保证期', completed: '已结案', rejected: '已淘汰', withdrawn: '已撤回', exited: '已离职 / 退出', ended: '已结束' }
export const approvalStates = { draft: '未提交', pending: '审批中', approved: '已通过', rejected: '已退回', withdrawn: '已撤回' }
export const feedbackStates = { pending: '待反馈', interested: '有意向 / 邀约面试', rejected: '不合适', need_info: '需补充资料' }
export const offerStates = { draft: 'Offer草稿', issued: '已发放', accepted: '已接受', rejected: '已拒绝', withdrawn: '已撤回' }
export const guaranteeStates = { active: '保证期中', completed: '期满结案', exit: '已离职', replacement: '替换处理中', refund: '已登记退费' }
export const recruitmentAvailabilityStates = { free: '未被占用', in_process: '招聘中锁定', employed: '已入职锁定' }
export const talentRecommendationOptions = (rows = [], excludeId) => rows
  .filter(row => ['available', 'contacting'].includes(row.status) && Number(row.id) !== Number(excludeId))
  .map(row => ({ value: row.id, label: row.name + (row.recruitmentLocked ? '（' + row.recruitmentAvailabilityLabel + '）' : ''), disabled: row.canRecommend !== true, disabledReason: row.recommendationBlockReason || '请刷新候选人状态后重试' }))
export const followupTypes = { candidate_withdraw: '候选人退出', customer_cancel: '客户取消招聘', offer_reject: 'Offer拒绝', offer_withdraw: 'Offer撤回', entry_delay: '入职延期', guarantee_exit: '保证期离职', guarantee_refund: '保证期退费', guarantee_replacement: '保证期替换', other: '历史其他事项' }
export const applicationSubstate = row => {
  if (row.stage === 'exited' && row.guaranteeStatus === 'completed') return '保证期已结案，后续离职'
  if (['recommended', 'feedback'].includes(row.stage)) return feedbackStates[row.feedbackStatus] || ''
  if (row.stage === 'interview') {
    if (row.interviewStatus === 'completed') return { pass: '面试通过', fail: '面试未通过', pending: '结果待定' }[row.interviewResult] || '已完成'
    return { scheduled: '待面试', cancelled: '已取消' }[row.interviewStatus] || ''
  }
  if (['offer', 'entry'].includes(row.stage)) return offerStates[row.offerStatus] || ''
  if (['guarantee', 'completed', 'exited'].includes(row.stage)) return guaranteeStates[row.guaranteeStatus] || ''
  if (row.stage === 'rejected' && row.offerStatus === 'rejected') return 'Offer已拒绝'
  if (row.stage === 'rejected' && row.interviewResult === 'fail') return '面试未通过'
  if (row.stage === 'ended') return '已确认不再推进'
  return ''
}
export const applicationActions = row => {
  const definitions = [
    ['feedback', row.stage === 'recommended' ? '登记客户反馈' : row.stage === 'interview' ? '更正客户反馈' : '更新反馈', 'feedback', row.stage === 'interview'],
    ['interviewResult', row.interviewStatus === 'completed' && row.interviewResult === 'pending' ? '补充面试结果' : '登记面试结果', 'interview'],
    ['interview', row.stage === 'interview' && row.interviewStatus === 'completed' ? '安排下一轮面试' : '安排面试', 'interview', row.stage === 'interview' && row.interviewStatus === 'completed' && row.interviewResult === 'pass'],
    ['offer', row.stage === 'offer' ? '编辑Offer' : '创建Offer', 'offer'],
    ['issueOffer', '登记发放Offer', 'offer'], ['acceptOffer', '登记接受Offer', 'offer'], ['rejectOffer', '登记拒绝Offer', 'offer'],
    ['withdrawOffer', row.offerStatus === 'draft' ? '取消Offer草稿' : '撤回Offer', 'offer'],
    ['entry', '确认实际入职', 'entry'], ['expectedEntry', '调整预计入职', 'entry'], ['entryDelay', '登记入职延期', 'exception'],
    ['guarantee', '保证期处理', 'guarantee'], ['postGuaranteeExit', '登记离职（保证期已结案）', 'guarantee'], ['correctEntry', '纠正入职登记', 'entry', true],
    ['replacement', '创建关联替换推荐', 'guarantee'], ['replacementManage', '查看替换进度', 'guarantee'],
    ['withdrawRecommendation', '撤回推荐', 'recommend'], ['recommend', '重新提交推荐', 'recommend'],
    ['finishRecommendation', '结束本次推荐', 'exception'],
    ['candidateWithdraw', row.stage === 'entry' ? '登记放弃入职' : '候选人退出', 'exception', true],
    ['customerCancel', '客户取消招聘', 'exception', true], ['followupManage', '处理跟进事项', 'exception']
  ]
  const available = new Set(row.availableOperations || [])
  return definitions.filter(([key]) => available.has(key)).map(([key, label, permission, detailsOnly = false]) => ({ key, label, permission, detailsOnly }))
}

// Guidance describes the next business step; executable actions still use server operations and permissions.
export const recruitmentGuidance = (kind, row, today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' })) => {
  const step = (title, description, action) => ({ title, description, action })
  if (kind === 'jobs') {
    if (row.status === 'deleted') return step('职位已删除', '仅供查阅历史资料，已有招聘流程仍可继续办理。', 'pipeline')
    if (row.demandStatus === 'pending') return step('等待需求审批', '审批通过后才能开始招聘，可查看当前审批进度。', 'approvalProgress')
    if (row.status === 'closed') return step('职位已关闭', '如需继续招聘，请先核对需求并调整职位状态。', 'status')
    if (row.status === 'completed') return step('职位招聘已完成', '可查看入职人员及后续保证期进度。', 'pipeline')
    if (['draft', 'rejected', 'withdrawn'].includes(row.demandStatus)) return step(row.demandStatus === 'rejected' ? '需求已退回，请修改后重提' : '请先确认招聘需求', '核对岗位要求、人数及负责人后提交需求确认。', 'demand')
    if (row.status === 'paused') return step('职位暂停招聘', '恢复招聘后可继续推荐人才；已有流程可查看跟进。', 'resume')
    if (row.demandStatus === 'approved' && Number(row.hired) >= Number(row.headcount)) return step('招聘名额已完成', '请核对入职人员，确认后完成职位。', 'completeJob')
    if (row.status !== 'recruiting') return step('需求已确认，请启动招聘', '将职位调整为招聘中后，即可推荐人才。', 'resume')
    return Number(row.recommendations) > 0
      ? step('继续推进候选人', `已推荐 ${row.recommendations} 人，请跟进反馈、面试及入职；还可继续推荐。`, 'pipeline')
      : step('下一步：推荐人才', `需求已确认，剩余 ${row.vacancies ?? Math.max(0, Number(row.headcount) - Number(row.hired || 0))} 个名额，请选择人才创建推荐。`, 'recommendTalent')
  }
  if (kind === 'talents') {
    if (row.recruitmentLocked) return step('查看当前招聘进度', row.recommendationBlockReason || '人才正在招聘流程中或已入职，请在关联流程继续办理。', 'pipeline')
    if (Number(row.pendingApprovalCount) > 0 || (row.approvals || []).some(a => a.status === 'pending')) return step('等待档案审批', '当前存在审批申请，请先查看审批进度。', 'approvalProgress')
    if (row.canRecommend === true) return step('下一步：推荐到职位', '核对求职意向，选择匹配的招聘职位创建推荐。', 'recommendTalent')
    if (['draft', 'uncontacted', 'contacting'].includes(row.status)) return step('下一步：联系候选人', row.recommendationBlockReason || '确认求职意向并记录联系情况，再调整为沟通中或可推荐。', 'contact')
    if (row.status === 'merged') return step('档案已合并', '请使用合并后的保留档案继续跟进。')
    return step('先核实求职意向', row.recommendationBlockReason || '联系候选人确认意向后，再调整状态继续推荐。', 'contact')
  }
  const available = new Set(row.availableOperations || [])
  if (['recommended', 'feedback'].includes(row.stage)) {
    if (row.feedbackStatus === 'rejected') return step('客户认为不合适', '核实反馈后结束本次推荐；反馈有误时可更正。', 'finishRecommendation')
    if (row.feedbackStatus === 'interested' && available.has('interview')) return step('下一步：安排面试', '请与客户、候选人确认面试时间和地点。', 'interview')
    if (row.feedbackStatus === 'need_info') return step('请补充候选人资料', '联系候选人补充资料后，继续更新客户反馈。', 'feedback')
    return step('下一步：登记客户反馈', '请联系客户确认推荐结果，并登记是否进入面试。', 'feedback')
  }
  if (row.stage === 'interview') {
    if (available.has('offer')) return step('面试已通过，可创建 Offer', '如需继续考察，也可安排下一轮面试。', 'offer')
    if (available.has('interviewResult')) return step(row.interviewResult === 'pending' ? '请补充最终面试结果' : '下一步：登记面试结果', '面试实际完成后登记结果；待定结果需继续跟进。', 'interviewResult')
    return step('继续跟进面试', '请核对面试轮次、客户反馈及待补充结果。', available.has('interview') ? 'interview' : 'interviews')
  }
  if (row.stage === 'offer') return row.offerStatus === 'draft'
    ? step('下一步：登记发放 Offer', '核对 Offer 内容，实际发放给候选人后再登记。', 'issueOffer')
    : step('等待候选人确认 Offer', '联系候选人，按实际情况登记接受或拒绝。', 'acceptOffer')
  if (row.stage === 'entry') {
    if (!available.has('entry')) return step('请先核实职位状态及名额', '当前暂不能确认入职，请联系职位负责人处理；可登记入职延期。', 'job')
    if (!row.contractId) return step('入职前请先关联服务合同', '在职位变更中选择该客户已签署的合同，确认保证期条款。', 'linkContract')
    if (Array.isArray(row.attachments) && !row.attachments.length) return step('入职前请先上传凭证', '上传入职证明后，再登记实际入职日期及保证期条款。', 'evidence')
    return step('下一步：确认实际入职', '预计入职不等于实际到岗；请备好入职凭证，实际到岗后再确认。', 'entry')
  }
  if (row.stage === 'guarantee' && row.guaranteeStatus === 'active') {
    if (available.has('completeGuarantee')) return step('保证期已到，请办理结案', '请核实履约情况后办理期满结案。', 'completeGuarantee')
    const days = Math.max(0, Math.ceil((Date.parse(row.guaranteeEnd) - Date.parse(today)) / 86400000))
    return step('保证期跟进中', row.guaranteeEnd ? `保证期截止 ${String(row.guaranteeEnd).slice(0, 10)}，剩余 ${days} 天；离职、替换或退费可登记处理。` : '持续跟进入职情况，异常情况请及时登记。', 'guarantee')
  }
  if (row.stage === 'exited' && row.guaranteeStatus === 'replacement') return step('请跟进保证期替换', '已有替换人选请查看进度，符合条件时可创建关联替换推荐。', available.has('replacement') ? 'replacement' : 'replacementManage')
  if (available.has('recommend')) return step('可重新提交推荐', '先核实候选人意向及职位需求，再重新提交。', 'recommend')
  if (available.has('followupManage')) return step('还有跟进事项待处理', '本次推进已结束，请完成遗留跟进并记录结果。', 'followupManage')
  return step(row.stage === 'completed' ? '本次招聘已结案' : '本次招聘推进已结束', '可查看招聘历史及办理记录。')
}
export const statuses = { ...jobStates, ...talentStates, ...stages, ...approvalStates, deleted: '已删除', active: '有效', disabled: '停用', scheduled: '待面试', cancelled: '已取消', issued: '已发放', accepted: '已接受', pass: '通过', fail: '未通过', interested: '有意向', need_info: '待补充', open: '待处理', closed: '已关闭', todo: '待跟进', done: '已完成', refund: '退费', replacement: '替换', exit: '离职' }
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
  field('city', '工作地点'), field('industry', '所属行业', 'industry'), field('experience', '经验要求'),
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
