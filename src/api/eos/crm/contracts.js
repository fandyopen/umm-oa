import request from '/src/utils/axios/request.js'

const base = '/project/crm/contracts'
export const normalizeContractId = value => {
  if (typeof value !== 'number' && typeof value !== 'string') return null
  if (typeof value === 'string' && !/^[0-9]+$/.test(value.trim())) return null
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}
const requestContract = async (id, config, suffix = '') => {
  const contractId = normalizeContractId(id)
  if (!contractId) throw new Error('合同编号无效，请刷新合同列表后重试')
  return request({ ...config, url: `${base}/${contractId}${suffix}` })
}
export const listContracts = params => request({ url: base, method: 'get', params })
export const listContractCustomers = params => request({ url: `${base}/customers`, method: 'get', params })
export const getContract = id => requestContract(id, { method: 'get' })
export const createContract = data => request({ url: base, method: 'post', data })
export const updateContract = (id, data) => requestContract(id, { method: 'put', data })
export const deleteContract = id => requestContract(id, { method: 'delete' })
export const contractAction = (id, action, data) => requestContract(id, { method: 'post', data }, `/${action}`)
// Only pre-workflow pending tasks use this compatibility endpoint.
export const approveLegacyContract = (id, data) => requestContract(id, { method: 'post', data }, '/approve')
