import request from '/src/utils/axios/request.js'

export const listWorkflowConfigurations = params => request({ url: '/system/workflows', method: 'get', params })
export const getWorkflowConfiguration = params => request({ url: '/system/workflows/detail', method: 'get', params })

export const getWorkflowCatalogOptions = params => request({ url: '/system/workflows/catalog/options', method: 'get', params })
export const createWorkflowCatalog = data => request({ url: '/system/workflows/catalog', method: 'post', data })
export const getWorkflowCatalog = id => request({ url: '/system/workflows/catalog/' + encodeURIComponent(id), method: 'get' })
export const saveWorkflowCatalog = (id, data) => request({ url: '/system/workflows/catalog/' + encodeURIComponent(id), method: 'put', data })
export const associateWorkflowCatalog = (id, data) => request({ url: '/system/workflows/catalog/' + encodeURIComponent(id) + '/associate', method: 'post', data })
