export const monitorMetrics = {
  active: '推进中人选', feedback: '待客户反馈', interview: '面试中', entry: '待入职', guarantee: '保证期中',
  pending: '待处理事项', overdue: '超期事项', stagnant: '停滞事项', followups: '本期跟进人选',
  recommended: '正式推荐', interviewed: '完成面试', offered: '发放 Offer', entered: '实际入职', completed: '保证期结案'
}

export const rankingMetrics = ['recommended', 'interviewed', 'offered', 'entered', 'completed']

export const formatMonitorTime = value => value ? String(value).replace('T', ' ').slice(0, 19) : '—'

export const rankedPeople = (people, metric) => {
  const rows = people.filter(row => Number(row[metric]) > 0).map(row => ({ ...row }))
    .sort((a, b) => Number(b[metric]) - Number(a[metric]) || String(a.userName).localeCompare(String(b.userName), 'zh-CN') || Number(a.userId) - Number(b.userId))
  let rank = 0
  return rows.map((row, index) => {
    if (index === 0 || Number(row[metric]) !== Number(rows[index - 1][metric])) rank = index + 1
    return { ...row, rank }
  })
}
