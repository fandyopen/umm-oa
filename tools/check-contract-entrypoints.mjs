import assert from 'node:assert/strict'
import fs from 'node:fs'
import { transformSync } from 'esbuild'
import compiler from 'vue-template-compiler'

const requests = []
function loadScript(source, dependencies) {
  const module = { exports: {} }
  const output = transformSync(source, { format: 'cjs', target: 'es2020', logLevel: 'silent' }).code
  new Function('require', 'module', 'exports', output)(name => dependencies[name] || {}, module, module.exports)
  return module.exports
}
const api = loadScript(fs.readFileSync('src/api/eos/crm/contracts.js', 'utf8'), {
  '/src/utils/axios/request.js': config => { requests.push(config); return Promise.resolve({ data: { id: 1 } }) }
})
const invalidIds = [undefined, null, NaN, Infinity, true, false, {}, [], [1], 0, -1, 1.5, '', ' ', 'NaN', 'undefined', 'Infinity', '1.5', '1e3', '1/receipts', '9007199254740992']
for (const id of invalidIds) {
  assert.equal(api.normalizeContractId(id), null)
  await assert.rejects(api.getContract(id), /合同编号无效/)
  await assert.rejects(api.updateContract(id, {}), /合同编号无效/)
  await assert.rejects(api.deleteContract(id), /合同编号无效/)
  await assert.rejects(api.contractAction(id, 'submit', {}), /合同编号无效/)
}
assert.equal(requests.length, 0, 'Invalid identifiers must never reach the network')
await api.getContract(' 12 ')
await api.updateContract(13, { name: '修订' })
await api.deleteContract('14')
await api.contractAction(15, 'submit', { revision: 0 })
assert.deepEqual(requests.map(item => [item.method, item.url]), [
  ['get', '/project/crm/contracts/12'], ['put', '/project/crm/contracts/13'],
  ['delete', '/project/crm/contracts/14'], ['post', '/project/crm/contracts/15/submit']
])

function component(file) {
  const parsed = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
  assert.deepEqual(compiler.compile(parsed.template.content).errors, [])
  return loadScript(parsed.script.content, { '/src/api/eos/crm/contracts.js': api, '../shared.js': { today: () => '2026-11-01' } }).default
}
const cases = [
  ['src/views/eos/crm/contract/modules/ContractWorkspace.vue', 'openForm', {}],
  ['src/views/eos/crm/contract/modules/ContractDetail.vue', 'openForm', { contractId: 12 }],
  ['src/views/eos/crm/customer/modules/CustomerDetail.vue', 'createContract'],
  ['src/views/eos/business/modules/BusinessDetail.vue', 'createContract']
]
for (const [file, method, argument] of cases) {
  const view = component(file)
  let options
  let openedId
  let refreshes = 0
  const context = {
    $createAntdModal: value => { options = value },
    $message: { error() {}, success() {}, warning() {} },
    $emit() {}, customer: { name: '客户' }, data: { customerId: 1 }, formId: 1,
    load: () => { refreshes++ }, loadCustomer: () => { refreshes++ },
    openDetail: id => { openedId = id }, openContract: id => { openedId = id }
  }
  view.methods[method].call(context, argument)
  assert.equal(options.modalProps.footer, null, `${file}: the default confirm button must be disabled`)
  if (method === 'openForm' && argument.contractId || file.includes('BusinessDetail')) continue
  for (const payload of [undefined, {}, { id: NaN }, { id: 'undefined' }]) {
    assert.equal(options.onOk({ payload }), false, `${file}: malformed save callback must be rejected`)
    assert.equal(openedId, undefined)
    assert.equal(refreshes, 0)
  }
  options.onOk({ payload: { id: 12 } })
  assert.equal(openedId, 12)
  assert.equal(refreshes, 1)
  let drawerCount = 0
  const open = view.methods.openDetail || view.methods.openContract
  for (const id of invalidIds) open.call({ ...context, $createAntdDrawer: () => { drawerCount++ } }, id)
  assert.equal(drawerCount, 0, `${file}: invalid identifiers must not open drawers`)
}
console.log('PASS: invalid IDs blocked before network, valid contract requests preserved, four custom form footers disabled, save callbacks and detail entry points validated.')

const detail = component('src/views/eos/crm/contract/modules/ContractDetail.vue')
const submitContext = {
  contractId: 12, contract: { revision: 7 }, saving: false,
  load: async () => {}, $emit() {}, $message: { success() {}, warning() {} }
}
const beforeSubmit = requests.length
await detail.methods.startAction.call(submitContext, 'submit')
assert.equal(requests.length, beforeSubmit, 'Opening submission must not fetch a manually selected approver')
assert.equal(submitContext.operation, 'submit')
await detail.methods.runAction.call(submitContext)
assert.equal(requests.at(-1).url, '/project/crm/contracts/12/submit')
assert.equal(requests.at(-1).data.revision, 7)
assert.equal(requests.at(-1).data.approvalUserId, undefined)

const actionManager = component('src/views/system/moduleAction/index.vue')
const actions = [{ actionCode: 'submit', actionName: '提交审批', permissionCode: 'crm:contract:submit' }, { actionCode: 'add', actionName: '新增' }]
for (const keyword of ['提交审批', 'submit', 'crm:contract:submit']) {
  assert.deepEqual(actionManager.computed.filteredActions.call({ actionKeyword: keyword, actions }), [actions[0]])
}
let destination
actionManager.methods.configureWorkflow.call({
  moduleId: 341201, modules: [{ moduleId: 341201, moduleName: '合同列表' }],
  $store: { getters: { tenantAppId: 1 } }, $router: { push: value => { destination = value } }
}, actions[0])
assert.equal(destination.path, '/processDesign')
assert.equal(destination.query.menuId, 341201)
assert.equal(destination.query.actionCode, 'submit')

const designer = component('src/views/lowCode/processDesign/index.vue')
const buttonOptions = designer.computed.actionOptions.call({ moduleActions: actions.slice(0, 1) })
assert.equal(buttonOptions.length, 1)
assert.equal(buttonOptions[0].code, 'submit')
assert.equal(buttonOptions[0].name, '提交审批')
assert.equal(buttonOptions.some(option => option.code === 'page'), false)
const todoDetail = component('src/views/workflow/todoDetail.vue')
let contractDrawer
let todoChanges = 0
todoDetail.methods.openContract.call({ row: { contractId: 12 }, $createAntdDrawer: value => { contractDrawer = value }, $emit: () => { todoChanges++ } })
assert.equal(contractDrawer.content.props.contractId, 12)
assert.equal(contractDrawer.content.props.initialTab, 'approvals')
contractDrawer.content.on.changed()
assert.equal(todoChanges, 1, 'Approving inside the linked contract must refresh the unified todo list')
const verify = component('src/components/Activiti/ViewVerify/index.vue')
let delegates = 0
verify.methods.handleDelegateTaskClick.call({ allowDelegate: false, $emit: () => { delegates++ } })
assert.equal(delegates, 0, 'Contract tasks must follow the published approval assignments')
component('src/views/eos/todoItem/todo/busDetail.vue')
console.log('PASS: contract submission uses workflow configuration, action codes are searchable, configuration opens the exact module/action, and unified todos link to the matching contract.')
