import request from '/src/utils/axios/request.js'
export const listPublishedNotices = params => request({ url: '/notices', method: 'get', params })
export const getPublishedNotice = id => request({ url: '/notices/' + id, method: 'get' })
export const publishNotice = id => request({ url: '/system/notice/' + id + '/publish', method: 'put' })
export const withdrawNotice = id => request({ url: '/system/notice/' + id + '/withdraw', method: 'put' })

// 查询公告列表
export function listNotice(query) {
  return request({
    url: '/system/notice/list',
    method: 'get',
    params: query
  })
}

// 查询公告详细
export function getNotice(noticeId) {
  return request({
    url: '/system/notice/' + noticeId,
    method: 'get'
  })
}

// 新增公告
export function addNotice(data) {
  return request({
    url: '/system/notice',
    method: 'post',
    data: data
  })
}

// 修改公告
export function updateNotice(data) {
  return request({
    url: '/system/notice',
    method: 'put',
    data: data
  })
}

// 删除公告
export function delNotice(noticeId) {
  return request({
    url: '/system/notice/' + noticeId,
    method: 'delete'
  })
}
