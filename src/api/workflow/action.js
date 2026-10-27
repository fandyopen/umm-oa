import request from '/src/utils/axios/request.js'

export function executeWorkflowAction(data) {
  return request({
    url: '/workflow/action/execute',
    method: 'post',
    data
  })
}

export function getWorkflowActionHandlers() {
  return request({
    url: '/workflow/action/handlers',
    method: 'get'
  })
}
