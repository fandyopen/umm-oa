import request from '/src/utils/axios/request.js'

// 查询所有岗位列表，不分页
export function listPostAll(query) {
  return request({
    url: '/system/post/list/all',
    method: 'get',
    params: query
  })
}
