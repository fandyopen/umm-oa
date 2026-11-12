import request from '/src/utils/axios/request.js'

export function commission(path, method = 'get', data) {
  return request({
    url: '/project/commission' + path,
    method,
    ...(method === 'get' ? { params: data } : { data })
  })
}
