import http from '/src/utils/axios/request.js'

const request = options => http({ ...options, silentError: true })

const base = '/system/data-tables'
const url = (table, suffix) => base + '/' + encodeURIComponent(table) + '/' + suffix
export const listDataTables = () => request({ url: base, method: 'get' })
export const getDataTableStructure = table => request({ url: url(table, 'structure'), method: 'get' })
export const getDataTableRows = (table, params) => request({ url: url(table, 'rows'), method: 'get', params })
export const getDataTableHistory = (table, params) => request({ url: url(table, 'history'), method: 'get', params })
export const getDataTableImpact = (table, data) => request({ url: url(table, 'impact'), method: 'post', data })
export const saveDataTableRow = (table, data, create) => request({ url: url(table, 'rows'), method: create ? 'post' : 'put', data })
export const deleteDataTableRow = (table, data) => request({ url: url(table, 'rows'), method: 'delete', data })
