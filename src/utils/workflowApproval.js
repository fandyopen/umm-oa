export const followupDisplayFields = [
  { field: 'customerName', label: '客户名称' },
  { field: 'followupTime', label: '跟进时间' },
  { field: 'method', label: '跟进方式' },
  { field: 'content', label: '沟通内容' },
  { field: 'nextPlan', label: '下一步计划' },
  { field: 'nextFollowupTime', label: '下次跟进时间' }
]

// The task's data is the submitted snapshot; form.title may contain the flow name.
export function approvalPayload(todoItem = {}, fallback = {}) {
  const candidates = [todoItem.data, fallback, todoItem.form]
  return candidates.find(value => value && typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length) || {}
}

export function formatApprovalValue(value) {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value === 'object') return JSON.stringify(value, null, 2)
  return String(value)
}
