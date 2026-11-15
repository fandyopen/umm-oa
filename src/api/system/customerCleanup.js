import http from '/src/utils/axios/request.js'

const base = '/system/customer-cleanup'
const request = options => http({ silentError: true, ...options })
export const searchCleanupCustomers = keyword => request({ url: base + '/customers', method: 'get', params: { keyword } })
export const previewCustomerCleanup = id => request({ url: base + '/customers/' + id + '/preview', method: 'get' })
export const cleanCustomerRelations = (id, data) => request({ url: base + '/customers/' + id + '/cleanup', method: 'post', data, timeout: 180000 })
export const deleteCleanedCustomer = (id, data) => request({ url: base + '/customers/' + id, method: 'delete', data, timeout: 180000 })
export const listCustomerCleanups = () => request({ url: base + '/history', method: 'get' })
export const downloadCustomerCleanupBackup = id => request({ url: base + '/history/' + encodeURIComponent(id) + '/backup', method: 'get', responseType: 'blob' })
