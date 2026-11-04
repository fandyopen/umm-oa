import request from '/src/utils/axios/request.js'

const base = '/project/crm/finance'
const call = (url, method = 'get', data) => request({ url: base + url, method, ...(method === 'get' ? { params: data } : { data }) })
export const listFinanceLedger = params => call('/ledger', 'get', params)
export const getFinanceReport = params => call('/report', 'get', params)
export const listFinanceRecords = (type, params) => call('/' + (type === 'receivable' ? 'receivables' : type), 'get', params)
export const getFinanceRecord = id => call('/records/' + id)
export const listFinanceOptions = () => call('/options')
export const listFinanceReceipts = params => call('/receipts', 'get', params)
export const listCollections = params => call('/collections', 'get', params)
export const createFinanceRecord = (type, data) => call('/' + type, 'post', data)
export const updateFinanceRecord = (type, id, data) => call('/' + type + '/' + id, 'put', data)
export const createReceivable = data => call('/receivables', 'post', data)
export const updateReceivable = (id, data) => call('/receivables/' + id, 'put', data)
export const financeReceivableAction = (id, action, data) => call('/receivables/' + id + '/' + action, 'post', data)
export const financeRecordAction = (type, id, action, data) => call('/' + type + '/' + id + '/' + action, 'post', data)
export const createCollection = data => call('/collections', 'post', data)
export const exportFinanceRows = (type, params) => call('/export/' + type, 'get', params)
