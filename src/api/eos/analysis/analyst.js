import request from '/src/utils/axios/request.js'

// 商机统计
export function businessSale(query) {
  return request({
    url: '/project/analysis/business/sale',
    method: 'get',
    params: query
  })
}
