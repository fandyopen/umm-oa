import request from '/src/utils/axios/request.js'

/**
 * 根据id更新待办任务已读列表
 * @param {Number} id 业务流程单号
 * @param {String} taskId 任务ID
 * @param {String} read 是否设置已读，1，已读；其他，未读
 * @returns True or False
 */
export function updateReadUsers(id, taskId, read) {
  return request({
    url: `/activiti/workflow/read/${id}/${taskId}/${read}`,
    method: 'get'
  })
}
