import moment from 'moment'

const fields = {
  amount: '金额', businessDate: '业务日期', customerName: '客户', contractName: '合同', contractCode: '合同编号',
  projectName: '项目', jobTitle: '职位', title: '应收项目', sourceEvent: '来源事件',
  invoiceTitle: '发票抬头', taxNo: '税号', invoiceNo: '发票号码', channel: '付款渠道', referenceNo: '业务流水号',
  category: '成本类别', payee: '收款对象', originalRecordId: '原到账记录编号', originalReceiptId: '原合同到账编号',
  reason: '办理原因', remark: '备注', nextDate: '下次催收日期', content: '跟进内容', previousContent: '原跟进内容'
}

export const financeRecordTypes = { invoice: '开票申请', receipt: '回款申请', refund: '退款申请', reversal: '到账冲销', writeoff: '应收核销', cost: '直接成本' }
export const financeStatuses = { draft: '草稿', approving: '审批中', approved: '待实际办理', confirmed: '已确认', rejected: '已驳回', void: '已作废' }
export const financeStatusColors = { approving: 'blue', approved: 'gold', confirmed: 'green', rejected: 'red' }
export const financeMoney = value => Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const financeTime = value => value && moment(value).isValid() ? moment(value).utcOffset(8).format('YYYY-MM-DD HH:mm:ss') : '未记录时间'

function parse(value) {
  if (typeof value !== 'string') return value
  try { return JSON.parse(value) } catch (_) { return null }
}

export function financeFiles(value) {
  const files = parse(value)
  if (!Array.isArray(files)) return []
  return files.flatMap(file => {
    if (!file || typeof file !== 'object') return []
    const url = file.fileName || file.url
    if (typeof url !== 'string' || !url.startsWith('/profile/') || /[\\\s?#]/.test(url)) return []
    try {
      if (decodeURIComponent(url).split(/[\\/]/).includes('..')) return []
    } catch (_) { return [] }
    return [{ name: typeof file.name === 'string' && file.name.trim() ? file.name : '查看凭证', url }]
  })
}

export function financeHistoryEntry(entry) {
  const content = parse(entry.content)
  const data = content && typeof content === 'object' && !Array.isArray(content) ? content : {}
  const items = Object.entries(fields).flatMap(([key, label]) => {
    const value = data[key]
    if (!['string', 'number'].includes(typeof value) || !String(value).trim()) return []
    if (key === 'amount' && !Number.isFinite(Number(value))) return []
    return [{ key, label, value: key === 'amount' ? '￥' + financeMoney(value) : String(value) }]
  })
  const raw = typeof entry.content === 'string' ? entry.content.trim() : ''
  // Old plain-text notes remain readable; malformed snapshots never expose raw JSON.
  const note = typeof content === 'string' ? content : !content && raw && !/^[\[{]/.test(raw) ? raw : ''
  return {
    id: entry.id, action: entry.action || '操作记录', operator: entry.createByName || entry.createBy || '未记录操作人',
    time: financeTime(entry.createTime), items, files: financeFiles(data.attachment), note,
    color: /驳回|作废/.test(entry.action) ? 'red' : /确认|通过|实际|结清/.test(entry.action) ? 'green' : 'blue'
  }
}
