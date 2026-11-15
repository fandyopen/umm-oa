export const payrollRuleFields = [
  { key: 'hoursPerDay', label: '每日计薪工时', min: 0.0001, max: 24 },
  { key: 'personalPaidRate', label: '事假计薪比例（0至1）', max: 1 },
  { key: 'sickPaidRate', label: '病假计薪比例（0至1）', max: 1 },
  { key: 'absencePaidRate', label: '缺勤计薪比例（0至1）', max: 1 },
  { key: 'overtimeMultiplier', label: '加班计薪倍率' },
  { key: 'latePenalty', label: '每次迟到扣款（元）' },
  { key: 'earlyPenalty', label: '每次早退扣款（元）' }
]

export const payrollRuleLabel = rules => {
  const source = rules?._rule
  return source ? `${source.name} · V${source.versionNo} · ${source.effectiveMonth} 起生效` : '原批次计薪参数（历史快照）'
}
