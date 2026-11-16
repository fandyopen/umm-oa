
export const statuses = { draft: '草稿', pending: '审批中', rejected: '已退回', approved: '待签署', active: '履约中', completed: '已结案', void: '已作废', terminated: '已终止', withdrawn: '已撤回' }
export const contractStatuses = Object.fromEntries(Object.entries(statuses).filter(([key]) => key !== 'withdrawn'))
// 默认标签不传 color；传入 'default' 会被组件当作自定义色并使用白字。
export const statusColors = { draft: undefined, pending: 'blue', rejected: 'red', approved: 'cyan', active: 'green', completed: 'purple', void: undefined, terminated: 'orange', withdrawn: undefined }
export const displayContractStatus = record => {
  if (record.deleted) return { label: '已删除', color: undefined }
  if (record.lifecyclePending) return { label: '作废 / 终止审批中', color: 'orange' }
  if (record.signingPending) return { label: '签署审批中', color: 'blue' }
  return { label: statuses[record.status] || '—', color: statusColors[record.status] }
}
export const workflowStatus = status => ({ running: '审批中', pending: '审批中', approved: '已通过', rejected: '已退回', withdrawn: '已撤回', cancelled: '已取消' }[status] || '未发起')
export const money = value => `￥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
export const today = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
export const newTerm = () => ({ key: `fee-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`, title: '', chargeMode: 'fixed', baseAmount: 0, rate: 20, amount: 0, trigger: '候选人入职', dueDate: today(), guaranteeDays: 90, remark: '' })
