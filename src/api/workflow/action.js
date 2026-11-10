import request from '/src/utils/axios/request.js'

export function getWorkflowRoles() {
  return request({
    url: '/workflow/roles',
    method: 'get'
  })
}

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

export function getWorkflowActionApis(moduleId) {
  return request({ url: '/workflow/action/apis', method: 'get', params: { moduleId } })
}
