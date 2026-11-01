import assert from 'node:assert/strict'
import fs from 'node:fs'
import compiler from 'vue-template-compiler'
import { transformSync } from 'esbuild'

function loadScript(source, dependencies = {}) {
  const module = { exports: {} }
  const output = transformSync(source, { format: 'cjs', loader: 'jsx', target: 'es2020', logLevel: 'silent' }).code
  new Function('require', 'module', 'exports', output)(name => dependencies[name] || {}, module, module.exports)
  return module.exports
}
const navigation = loadScript(fs.readFileSync('src/views/eos/crm/contract/navigation.js', 'utf8'))
const requests = []
const api = loadScript(fs.readFileSync('src/api/eos/crm/contracts.js', 'utf8'), {
  '/src/utils/axios/request.js': config => { requests.push(config); return Promise.resolve({ data: {} }) }
})
function component(file, dependencies = {}) {
  const parsed = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
  assert.deepEqual(compiler.compile(parsed.template.content).errors, [], file)
  return loadScript(parsed.script.content, {
    '/src/api/eos/crm/contracts.js': api, '../navigation.js': navigation,
    '../shared.js': { can: () => false }, ...dependencies
  }).default
}

const originalQuery = { customerId: '7', contractId: '19', termKey: 'first' }
for (const [mode, path, expected] of [
  ['approvals', '/todoItem/todo', { businessCategory: 'contract' }],
  ['fees', '/crm/contracts/list', { tab: 'terms' }],
  ['versions', '/crm/contracts/list', { tab: 'versions' }],
  ['reminders', '/crm/risks', {}]
]) {
  const destination = navigation.contractEntryTarget(mode, originalQuery)
  assert.equal(destination.path, path)
  assert.deepEqual(destination.query, { ...originalQuery, ...expected })
  const wrapper = fs.readFileSync(`src/views/eos/crm/contract/${mode}.vue`, 'utf8')
  assert.ok(wrapper.includes('<legacy-contract-entry'))
  component(`src/views/eos/crm/contract/${mode}.vue`)
}
assert.deepEqual(originalQuery, { customerId: '7', contractId: '19', termKey: 'first' })
const legacy = component('src/views/eos/crm/contract/modules/LegacyContractEntry.vue')
let redirected
legacy.mounted.call({ mode: 'versions', $route: { query: originalQuery }, $router: { replace: value => { redirected = value } } })
assert.equal(redirected.query.contractId, '19')
assert.equal(redirected.query.tab, 'versions')

const workspaceFile = 'src/views/eos/crm/contract/modules/ContractWorkspace.vue'
const workspace = component(workspaceFile)
assert.ok(!fs.readFileSync(workspaceFile, 'utf8').includes('<a-tabs'))
const routeContext = { $route: { query: { ...originalQuery, tab: 'versions' } }, query: {}, openedContract: '', load() {}, openDetail: (...args) => { redirected = args } }
routeContext.detailTab = workspace.computed.detailTab.call(routeContext)
workspace.methods.readRoute.call(routeContext)
assert.deepEqual(redirected, ['19', 'versions', 'first'])

const detailSource = fs.readFileSync('src/views/eos/crm/contract/modules/ContractDetail.vue', 'utf8')
for (const tab of navigation.contractDetailTabs) assert.ok(detailSource.includes(`key="${tab}"`), tab)
component('src/views/eos/crm/contract/modules/ContractDetail.vue')
component('src/views/eos/crm/contract/modules/CrmTodoDetail.vue')

const risk = component('src/views/eos/crm/risk/index.vue')
const risks = [
  { taskId: 'first', customerId: 7, contractId: 19, customerName: '甲客户', contractName: '甲合同', riskType: 'payment_overdue', title: '收费逾期' },
  { taskId: 'second', customerId: 7, contractId: 20, customerName: '甲客户', contractName: '乙合同', riskType: 'contract_expiry' },
  { taskId: 'third', customerId: 8, contractId: 21, customerName: '乙客户', contractName: '丙合同', riskType: 'payment_overdue' }
]
const riskContext = { risks, keyword: '', customerId: undefined, contractId: undefined, riskType: '', $route: { query: { customerId: '7', contractId: '19', riskType: 'payment_overdue' } } }
risk.methods.readRoute.call(riskContext)
assert.deepEqual(risk.computed.filteredRisks.call(riskContext).map(item => item.taskId), ['first'])
assert.equal(risk.computed.customerOptions.call(riskContext).length, 2)
assert.equal(risk.computed.contractOptions.call(riskContext).length, 2)
let reloads = 0
const handleContext = { saving: false, handleTarget: risks[0], handleContent: '  已确认付款计划  ', loadData: async () => { reloads++ }, $message: { success() {}, warning() {} } }
await risk.methods.saveHandle.call(handleContext)
assert.equal(requests.at(-1).url, '/project/crm/contracts/19/reminder-log')
assert.deepEqual(requests.at(-1).data, { taskId: 'first', content: '已确认付款计划' })
assert.equal(reloads, 1)
assert.equal(handleContext.saving, false)
assert.equal(risks.length, 3, 'A handling record must not manually dismiss the risk')

let querySent
let badge
const todo = component('src/views/eos/todoItem/todo/todo.vue', {
  '/src/api/activiti/process.js': { listTodo: async query => { querySent = query; return { rows: [], total: 1, unfilteredTotal: 9 } } }
})
const todoContext = { simple: false, $route: { query: { businessCategory: 'contract' } }, $store: { commit: (_, value) => { badge = value } } }
Object.assign(todoContext, todo.data.call(todoContext))
assert.equal(todoContext.businessCategory, 'contract')
await todoContext.loadData({ pageNum: 1, pageSize: 10 })
assert.equal(querySent.businessCategory, 'contract')
assert.equal(badge, 9, 'The header badge must retain the total pending count')
const dashboardContext = { ...todoContext, simple: true }
Object.assign(dashboardContext, todo.data.call(dashboardContext))
assert.deepEqual(dashboardContext.queryParam, {}, 'Dashboard todos must not inherit the route filter')

console.log('PASS: legacy redirects preserve contract context; one list and five detail tabs compile; risk filters and handling work; unified todos retain correct filters and total badges.')
