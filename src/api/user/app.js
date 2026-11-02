import request from '/src/utils/axios/request.js'

// 查询App列表
export function getAppList(query) {
  return request({
    url: '/app/user/list',
    method: 'get',
    params: query
  })
}
