import { statusNames, eventNames } from './hrShared.js'

export function employeeRecords(rows) {
  return rows.map(row => ({ ...row, employmentType: row.employmentTypeName || row.employmentType, employmentStatus: statusNames[row.employmentStatus] || row.employmentStatus }))
}
export function salaryStandardRecords(rows) {
  return rows.map(row => ({
    ...row,
    status: statusNames[row.status] || row.status
  }))
}
export function changeRecords(rows) {
  return rows.map(row => ({ ...row, eventType: eventNames[row.eventType] || row.eventType, status: statusNames[row.status] || row.status }))
}
export function payrollRecords(batch) {
  return (batch.rows || []).map(row => ({
    ...row,
    ...row.snapshot,
    employeeName: row.snapshot.nickName,
    batchId: batch.id,
    period: batch.period,
    revision: batch.revision,
    remainingAmount: Number(row.netAmount) - Number(row.paidAmount)
  }))
}
export function payrollSummary(rows) {
  return rows.map(row => ({
    ...row,
    batchType: statusNames[row.batchType] || row.batchType,
    status: statusNames[row.status] || row.status,
    published: row.published ? '已发布' : '未发布',
    remainingAmount: Number(row.netAmount) - Number(row.paidAmount)
  }))
}
export function payrollReportEntries(batch, finance = false) {
  const rows = payrollRecords(batch)
  const payments = rows.flatMap(row =>
    (row.payments || []).map(p => ({ ...p, batchId: batch.id, period: batch.period, employeeNo: row.employeeNo, employeeName: row.employeeName, deptName: row.deptName, paymentId: p.id, status: statusNames[p.status] || p.status }))
  )
  const departments = new Map()
  const amounts = ['baseAmount', 'allowance', 'overtime', 'bonus', 'commission', 'adjustment', 'grossAmount', 'deduction', 'tax', 'contribution', 'netAmount', 'paidAmount', 'remainingAmount']
  rows.forEach(row => {
    const key = String(row.snapshot.deptId || row.deptName || '未分配部门')
    if (!departments.has(key)) departments.set(key, { period: batch.period, deptName: row.deptName || '未分配部门', employeeCount: 0, ...Object.fromEntries(amounts.map(key => [key, 0])) })
    const group = departments.get(key)
    group.employeeCount++
    amounts.forEach(key => {
      group[key] += Number(row[key] || 0)
    })
  })
  const paymentRows = rows.map(row => {
    const successful = (row.payments || []).filter(p => p.status === 'paid')
    return {
      ...row,
      status: row.remainingAmount === 0 ? '已付清' : Number(row.paidAmount) !== 0 ? '部分发放' : (row.payments || []).some(p => p.status === 'failed') ? '发放失败待重试' : '未发放',
      referenceNo: successful.map(p => p.referenceNo).join('；'),
      paymentDate: successful.map(p => String(p.paymentDate).slice(0, 10)).join('；')
    }
  })
  return [
    ...(!finance ? [{ type: 'payroll', records: rows, sheetName: '工资表（草稿可回传）' }] : []),
    { type: 'payslip', records: rows, sheetName: '员工工资明细' },
    { type: 'departmentPayroll', records: [...departments.values()] },
    { type: 'payment', records: paymentRows, sheetName: '发放对账与银行账户' },
    { type: 'paymentDetail', records: payments }
  ]
}
