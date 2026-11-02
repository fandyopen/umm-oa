import request from '/src/utils/axios/request.js'

// 查询客商管理详细
export function getMerchant(id) {
  return request({
    url: '/project/merchant/' + id,
    method: 'get'
  })
}

// 新增客商管理
export function addMerchant(data) {
  return request({
    url: '/project/merchant',
    method: 'post',
    data: data
  })
}

// 修改客商管理
export function updateMerchant(data) {
  return request({
    url: '/project/merchant',
    method: 'put',
    data: data
  })
}

// 删除客商联系人
export function deleteMerchatUser(id) {
  return request({
    url: '/project/merchant/user/' + id,
    method: 'delete'
  })
}

// 删除客商银行账户
export function deleteMerchatAccount(id) {
  return request({
    url: '/project/merchant/account/' + id,
    method: 'delete'
  })
}
