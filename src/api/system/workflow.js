import request from '/src/utils/axios/request.js'

export const listWorkflowConfigurations = params => request({ url: '/system/workflows', method: 'get', params })
export const getWorkflowConfiguration = params => request({ url: '/system/workflows/detail', method: 'get', params })
