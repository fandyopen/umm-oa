import request from '/src/utils/axios/request.js'

export const listApprovals = params => request({ url: '/project/approvals', method: 'get', params })
export const approvalOptions = () => request({ url: '/project/approvals/options', method: 'get' })
export const approvalDetail = taskId => request({ url: '/project/approvals/detail', method: 'get', params: { taskId } })
export const decideApproval = data => request({ url: '/project/approvals/decision', method: 'post', data })
