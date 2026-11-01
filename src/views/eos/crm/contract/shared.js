import { hasModuleAction } from '/src/utils/modulePermission.js'
import store from '/src/store/index.js'

export const statuses = { draft: '草稿', pending: '审批中', rejected: '已退回', approved: '待签署', active: '履约中', completed: '已结案' }
export const statusColors = { draft: 'default', pending: 'blue', rejected: 'red', approved: 'cyan', active: 'green', completed: 'purple' }
const prefixes = { 341201: 'crm:contract', 341202: 'crm:approval', 341203: 'crm:fee', 341204: 'crm:version', 341205: 'crm:reminder', 341101: 'project:risk', 340352: 'project:merchant' }
export const can = (moduleId, action) => hasModuleAction(store.getters.permissions || [], { moduleId, perms: `${prefixes[moduleId]}:list` }, action)
export const canView = moduleId => (store.getters.permissions || []).some(permission => permission === '*:*:*' || permission === `${prefixes[moduleId]}:list`)
export const money = value => `￥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
export const today = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
export const newTerm = () => ({ key: `fee-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`, title: '', chargeMode: 'fixed', baseAmount: 0, rate: 20, amount: 0, trigger: '候选人入职', dueDate: today(), guaranteeDays: 90, remark: '' })
