export const metrics = {
  customers: '当前负责客户', newCustomers: '本期新增客户', contacted: '本期接触客户', followups: '本期跟进次数',
  activeOpportunities: '开放商机', newOpportunities: '本期新增商机', demand: '需求确认', quotation: '方案报价', negotiation: '商务谈判', won: '赢单',
  pendingContracts: '待签合同', signedContracts: '本期签约合同', signedAmount: '本期签约金额', receivedAmount: '本期净回款',
  stagnant: '3 天未跟进客户', overdue: '跟进超期客户', stagnantOpportunities: '停滞商机'
}
export const salesRankings = [
  { metric: 'newCustomers', title: '新增客户档案榜', countLabel: '新增客户数', description: '按所选时间内新建的客户档案数量统计。' },
  { metric: 'signedContracts', title: '新增签约合同榜', countLabel: '签约合同数', description: '按所选时间内首次签署的合同数量统计。' }
]
export const moneyMetrics = ['signedAmount', 'receivedAmount']
export const currentMetrics = ['customers', 'activeOpportunities', 'pendingContracts', 'stagnant', 'overdue', 'stagnantOpportunities']
export const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 19) : '—'
export const money = value => '￥' + Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export function rankedPeople(people, metric) {
  let rank = 0
  let previous
  return people.slice().sort((a, b) => Number(b[metric] || 0) - Number(a[metric] || 0) || Number(a.userId) - Number(b.userId)).map((person, index) => {
    const value = Number(person[metric] || 0)
    if (value !== previous) rank = index + 1
    previous = value
    return { ...person, rank }
  })
}
