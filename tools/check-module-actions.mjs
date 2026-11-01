import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import { transformSync } from 'esbuild'
import compiler from 'vue-template-compiler'

const require = createRequire(import.meta.url)
function script(source, dependencies = {}) {
  const module = { exports: {} }
  const output = transformSync(source, { format: 'cjs', target: 'es2020', logLevel: 'silent' }).code
  new Function('require', 'module', 'exports', output)(name => dependencies[name] || {}, module, module.exports)
  return module.exports
}
function component(file, dependencies = {}) {
  const parsed = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
  assert.deepEqual(compiler.compile(parsed.template.content).errors, [], file)
  return script(parsed.script.content, dependencies).default
}
const config = script(fs.readFileSync('src/utils/moduleActionConfig.js', 'utf8'), { vue: require('vue') })
const permissions = script(fs.readFileSync('src/utils/modulePermission.js', 'utf8'), { './moduleActionConfig.js': config })
const meta = { moduleId: 341001, perms: 'hr:member:list' }
config.setModuleActionConfigurations([{ moduleId: 341001, actionCode: 'invite', actionName: '成员入职申请', permissionCode: 'custom:invite', status: '0' }])
assert.equal(permissions.hasModuleAction(['module:341001:invite'], meta, 'invite'), true)
assert.equal(permissions.hasModuleAction(['custom:invite'], meta, 'invite'), true)
assert.equal(config.moduleActionName(341001, 'invite', '邀请成员'), '成员入职申请')
config.setModuleActionConfigurations([{ moduleId: 341001, actionCode: 'invite', status: '1' }])
assert.equal(permissions.hasModuleAction(['*:*:*'], meta, 'invite'), false)
config.setModuleActionConfigurations([])

const saved = []
const created = []
const refreshed = []
const routes = []
const menuApi = {
  updateModuleAction: async (...args) => saved.push(args),
  createModuleAction: async (...args) => { created.push(args); return { data: { actionCode: 'action_007' } } },
  getNextModuleActionCode: async () => ({ data: { actionCode: 'action_007' } })
}
const drawer = component('src/views/system/module/modules/ModuleActionForm.vue', { '/src/api/system/menu.js': menuApi })
assert.ok(!fs.readFileSync('src/views/system/module/modules/ModuleActionForm.vue', 'utf8').includes('保存并打开流程设计'))
const vm = { ...drawer.data.call({ emptyForm: drawer.methods.emptyForm }), ...drawer.methods,
  canManageStructure: true, canManageWorkflow: true, handlerKeys: ['hr.member.invite'],
  $nextTick: callback => callback(),
  $store: { getters: { tenantAppId: 1 }, dispatch: async () => {} },
  $router: { push: value => routes.push(value) },
  $message: { success() {}, warning() {}, error() {} },
  $emit: (event, data) => refreshed.push({ event, data }),
  $refs: { actionForm: { validate: callback => callback(true), clearValidate() {} } }
}
const row = Object.freeze({ moduleId: 341001, moduleName: '成员管理', actionCode: 'invite', actionName: '邀请成员', handlerKey: 'hr.member.invite', builtin: 1, status: '0', flowDesignEnabled: false })
const tree = component('src/views/system/module/index.vue')
let addedPage
const inlineEditor = { $refs: { actionForm: { handleUpdate: value => vm.handleUpdate(value), handleAdd: value => { addedPage = value } } },
  $router: { push: () => { throw new Error('Editing must stay on the module management page') } } }
tree.methods.openActionForm.call(inlineEditor, row)
assert.equal(vm.open, true)
assert.equal(vm.moduleId, row.moduleId)
assert.equal(vm.form.flowDesignEnabled, false)
vm.form.flowDesignEnabled = true
vm.submit()
await new Promise(resolve => setImmediate(resolve))
assert.equal(saved[0][2].flowDesignEnabled, true)
assert.equal(routes.length, 0, 'Normal save must not navigate')
assert.equal(vm.open, false)
assert.deepEqual(refreshed[0], { event: 'ok', data: { moduleId: 341001, actionCode: 'invite' } })
let refreshOptions
tree.methods.handleActionSaved.call({ loadTree: options => { refreshOptions = options } }, refreshed[0].data)
assert.deepEqual(refreshOptions, { preserveExpansion: true, expandModuleId: 341001 })
tree.methods.openActionForm.call(inlineEditor, { moduleId: 341201, moduleName: '合同列表' }, true)
assert.equal(addedPage.moduleId, 341201)

vm.handleUpdate(row)
vm.canManageStructure = false
vm.form.flowDesignEnabled = false
vm.submit()
await new Promise(resolve => setImmediate(resolve))
assert.deepEqual(saved[1][2], { flowDesignEnabled: false }, 'Workflow-only admins cannot submit structure fields')
vm.handleUpdate(row)
vm.form.flowDesignEnabled = true
vm.submit()
await new Promise(resolve => setImmediate(resolve))
assert.equal(saved[2][2].flowDesignEnabled, true)
assert.equal(routes.length, 0, 'Enabling workflow design and saving must stay on the current page')
vm.handleUpdate(row)
vm.form.actionName = '未保存修改'
vm.cancel()
assert.equal(vm.open, false)
assert.equal(row.actionName, '邀请成员', 'Cancel must not change the tree record')
assert.equal(saved.length, 3)

vm.canManageStructure = true
vm.handleAdd({ moduleId: 341201, moduleName: '合同列表' })
await new Promise(resolve => setImmediate(resolve))
assert.equal(vm.form.actionCode, 'action_007')
vm.form.actionName = '新按钮'
vm.submit()
await new Promise(resolve => setImmediate(resolve))
assert.equal(created.length, 1)
assert.equal(created[0][0], 341201)
assert.equal(refreshed.at(-1).data.actionCode, 'action_007')
let finishPreview
menuApi.getNextModuleActionCode = () => new Promise(resolve => { finishPreview = resolve })
vm.handleAdd({ moduleId: 341201, moduleName: '合同列表' })
vm.handleUpdate(row)
finishPreview({ data: { actionCode: 'action_008' } })
await new Promise(resolve => setImmediate(resolve))
assert.equal(vm.form.actionCode, 'invite', 'A stale code preview must not overwrite the edited button')
assert.equal(vm.generatingCode, false)
const editor = component('src/views/system/moduleAction/index.vue')
let delegatedRow
editor.methods.openRequestedEditor.call({ actions: [row], canManageStructure: true,
  $route: { query: { actionCode: 'invite' } }, openEdit: value => { delegatedRow = value } })
assert.equal(delegatedRow, row)

let detailCalls = 0
let actionRows = [{ actionCode: 'submit', actionName: '提交审批', status: '0', flowDesignEnabled: true, handlerKey: 'crm.contract.submit' }]
const design = component('src/views/lowCode/processDesign/index.vue', {
  '/src/api/system/menu.js': { getModuleActions: async () => ({ data: actionRows }) },
  '/src/api/processDesign/index.js': { detailProcess: async body => { detailCalls++; assert.equal(body.actionCode, 'submit'); return { _id: 'process-submit', approvalEnabled: true, processData: {} } } }
})
const flow = { ...design.data(), ...design.methods, handleFormItem() {}, $route: { query: { menuId: '341201', tenantAppId: '1', actionCode: 'page' } } }
Object.entries(design.computed).forEach(([name, get]) => Object.defineProperty(flow, name, { get: () => get.call(flow) }))
await flow.initFlowData()
assert.match(flow.loadError, /按钮/)
assert.equal(detailCalls, 0)
flow.$route.query.actionCode = 'submit'
await flow.initFlowData()
assert.equal(flow.loadError, '')
assert.equal(flow.mockData._id, 'process-submit')
assert.equal(detailCalls, 1)
actionRows[0].flowDesignEnabled = false
await flow.initFlowData()
assert.match(flow.loadError, /尚未开启/)
assert.equal(flow.mockData, null)
assert.equal(flow.spinning, false)
assert.equal(detailCalls, 1, 'Closed design cannot load or publish through an old URL')
assert.equal(flow.actionOptions.some(action => action.code === 'page'), false)
tree.methods.openFlowDesign.call({ $store: { getters: {} }, $router: { push: value => routes.push(value) } }, { moduleId: 341201, moduleName: '合同列表', actionCode: 'submit' })
assert.equal(routes.at(-1).query.actionCode, 'submit')
const legacy = component('src/views/system/menu/index.vue')
legacy.beforeRouteEnter({ query: {} }, {}, target => assert.equal(target.path, '/system/module'))
component('src/views/eos/crm/contract/modules/ContractDetail.vue')
component('src/views/eos/crm/contract/modules/ContractWorkspace.vue')
vm.canManageStructure = true
vm.handleUpdate({ moduleId: 341201, actionCode: 'sign', actionName: '签署生效', handlerKey: 'crm.contract.sign', handlerManaged: true, businessActionName: '签署生效', flowDesignEnabled: false, status: '0' })
assert.equal(vm.handlerManaged, true)
assert.equal(vm.businessActionName, '签署生效')
const drawerSource = fs.readFileSync('src/views/system/module/modules/ModuleActionForm.vue', 'utf8')
assert(!drawerSource.includes('!canManageWorkflow || !form.handlerKey'), 'Missing executors must not block opening the designer')
let draftBody
let navigated = false
const draftDesign = component('src/views/lowCode/processDesign/index.vue', {
  '/src/api/processDesign/index.js': { setProcess: async body => { draftBody = body; return { code: 200 } } }
})
const draftVm = { ...draftDesign.data(), ...draftDesign.methods, selectedAction: { actionCode: 'custom' }, actionCode: 'custom', approvalEnabled: true,
  mockData: { _id: 'published-flow' }, $route: { query: { menuId: '341201', tenantAppId: '1', menuName: '合同列表' } },
  _: { isNil: value => value === null || value === undefined }, $message: { success() {}, warning() {}, error(message) { throw new Error(message) } },
  $router: { go() { navigated = true } }, $refs: { processDesign: { getData: async () => ({ formData: { nodes: [] } }) } }
}
await draftVm.saveDraft()
assert.equal(draftBody.draft, true)
assert.equal(draftBody.actionCode, 'custom')
assert.equal(draftVm.mockData.draft, true)
assert.equal(navigated, false, 'Draft saving must stay in the designer')
assert.equal(draftVm.saving, false)
await draftVm.publish()
assert.equal(draftBody.draft, false)
assert.equal(navigated, true)
console.log('Inline button drawer, save/cancel, creation, workflow switch, permissions and designer checks passed')
