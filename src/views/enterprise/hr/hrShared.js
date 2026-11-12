export const statusNames = {
  active: '在职',
  leaving: '待离职',
  departed: '已离职',
  draft: '草稿',
  approving: '审批中',
  approved: '已批准',
  confirmed: '已确认',
  rejected: '已退回',
  superseded: '已有更新版本',
  scheduled: '待生效',
  effective: '已生效',
  cancelled: '已取消',
  monthly: '月度汇总',
  daily: '每日考勤',
  leave: '请假记录',
  regular: '常规工资',
  supplement: '差额批次',
  commission: '提成批次',
  todo: '待交接',
  done: '已完成',
  paid: '成功',
  failed: '失败',
  entry: '入职登记',
  rehire: '重新入职',
  transfer: '调整部门',
  dismiss: '离职',
  salary: '薪资调整'
}
export const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const dateText = value => (value ? String(value).slice(0, 10) : '—')
export const newToken = () => (window.crypto?.randomUUID ? window.crypto.randomUUID() : String(Date.now()) + '-' + Math.random().toString(36).slice(2))
export async function fileBody(file) {
  return {
    fileName: file.name,
    fileContent: await new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result).split(',')[1])
      reader.onerror = reject
      reader.readAsDataURL(file)
    })
  }
}
export const parseObject = value => {
  try {
    return typeof value === 'object' ? value : JSON.parse(value)
  } catch (error) {
    return {}
  }
}

export const eventNames = { entry: '入职登记', regular: '转正', rehire: '重新入职', transfer: '调整部门', dismiss: '离职', salary: '薪资调整' }
