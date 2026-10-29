import request from '/src/utils/axios/request.js'

export function listCustomers(query) {
  return request({
    url: '/project/merchant/list',
    method: 'get',
    params: query
  })
}

export function exportCustomers(query) {
  return request({
    url: '/project/merchant/export',
    method: 'get',
    params: query
  })
}

export function importCustomers(rows) {
  return request({
    url: '/project/merchant/import',
    method: 'post',
    data: { rows }
  })
}

export function listCustomerOptions(query) {
  return request({
    url: '/project/merchant/options',
    method: 'get',
    params: query
  })
}

export function listCustomerOwners(query) {
  return request({
    url: '/project/merchant/owner/options',
    method: 'get',
    params: query
  })
}

export function getCustomer(id) {
  return request({
    url: `/project/merchant/${id}`,
    method: 'get'
  })
}

export function addCustomer(data) {
  return request({
    url: '/project/merchant',
    method: 'post',
    data
  })
}

export function updateCustomer(data) {
  return request({
    url: '/project/merchant',
    method: 'put',
    data
  })
}

export function deleteCustomer(id) {
  return request({
    url: `/project/merchant/${id}`,
    method: 'delete'
  })
}

export function deleteCustomerContact(id) {
  return request({
    url: `/project/merchant/user/${id}`,
    method: 'delete'
  })
}

export function listFollowups(customerId) {
  return request({
    url: `/project/merchant/${customerId}/followups`,
    method: 'get'
  })
}

export function addFollowup(customerId, data) {
  return request({
    url: `/project/merchant/${customerId}/followups`,
    method: 'post',
    data
  })
}

export function deleteFollowup(id) {
  return request({
    url: `/project/merchant/followup/${id}`,
    method: 'delete'
  })
}

export function assignCustomer(id, data) {
  return request({
    url: `/project/merchant/${id}/assign`,
    method: 'post',
    data
  })
}

export function releaseCustomer(id) {
  return request({
    url: `/project/merchant/${id}/release`,
    method: 'post'
  })
}

export function claimCustomer(id) {
  return request({
    url: `/project/merchant/${id}/claim`,
    method: 'post'
  })
}

export function handoverCustomers(data) {
  return request({
    url: '/project/merchant/handover',
    method: 'post',
    data
  })
}

export function getCustomerAnalysis() {
  return request({
    url: '/project/crm/analysis/overview',
    method: 'get'
  })
}

export function listCustomerRisks() {
  return request({
    url: '/project/crm/risk/list',
    method: 'get'
  })
}

export function syncCustomerRisks() {
  return request({
    url: '/project/crm/risk/sync',
    method: 'post'
  })
}
