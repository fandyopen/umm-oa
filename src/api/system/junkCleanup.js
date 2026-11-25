import http from '/src/utils/axios/request.js'

const base = '/system/junk-cleanup'
const request = options => http({ silentError: true, ...options })
export const scanJunk = () => request({ url: base + '/scan', method: 'get' })
export const cleanJunk = data => request({ url: base + '/clean', method: 'post', data, timeout: 180000 })
export const listJunkCleanupHistory = () => request({ url: base + '/history', method: 'get' })
