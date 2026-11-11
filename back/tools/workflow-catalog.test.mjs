import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import { createRequire } from 'node:module'
import compiler from 'vue-template-compiler'

const Vue = createRequire(import.meta.url)('vue')
function options(file, dependencies) {
  const parsed = compiler.parseComponent(fs.readFileSync(new URL('../../' + file, import.meta.url), 'utf8'))
  assert.deepEqual(compiler.compile(parsed.template.content).errors, [])
  return new Function(...Object.keys(dependencies), parsed.script.content.replace(/^import .*$/gm, '').replace('export default', 'return'))(...Object.values(dependencies))
}
function designer(query, overrides = {}) {
  const calls = []
  const dependencies = {
    Process: {}, GET_MOCK_CONF: async () => ({ processData: { nodes: [{ id: 'start' }] } }),
    getModuleActions: async () => ({ data: [{ actionCode: 'add', flowDesignEnabled: true, status: '0', handlerKey: 'workflow.business-api' }] }),
    detailProcess: async () => ({ _id: 'legacy', processData: { nodes: [] }, approvalEnabled: true }),
    setProcess: async body => { calls.push(body); return { code: 200, workflowRevision: body.workflowRevision + 1 } },
    getWorkflowCatalog: async () => ({ data: { _id: 'catalog-1', flowName: '新增审批', workflowRevision: 0, approvalEnabled: true } }),
    saveWorkflowCatalog: async (id, body) => { calls.push({ id, ...body }); return { code: 200, data: { workflowRevision: body.workflowRevision + 1 } } },
    ...overrides
  }
  const config = options('src/views/lowCode/processDesign/index.vue', dependencies)
  const vm = new Vue({ ...config, watch: {} })
  vm.$route = { query }
  vm.$store = { commit() {} }
  vm.$message = { success() {}, warning() {}, error() {} }
  vm.$router = { go() {} }
  return { vm, calls }
}

test('independent workflow starts with the default graph without requiring a button', async () => {
  const { vm, calls } = designer({ workflowId: 'catalog-1', menuId: 340590 })
  await vm.initFlowData()
  assert.equal(vm.loadError, '')
  assert.equal(vm.selectedAction, undefined)
  assert.equal(vm.mockData.flowName, '新增审批')
  assert.equal(vm.mockData.processData.nodes[0].id, 'start')
  const param = { draft: true, processData: { nodes: [] }, approvalEnabled: true }
  await vm.sendToServer(param)
  await vm.sendToServer(param)
  assert.equal(calls[0].workflowRevision, 0)
  assert.equal(calls[1].workflowRevision, 1)
  assert.equal(vm.mockData.workflowRevision, 2)
  vm.$destroy()
})

test('original button designer forwards catalog revisions and keeps its original button identity', async () => {
  const { vm, calls } = designer({ menuId: 340590, actionCode: 'add' }, {
    detailProcess: async () => ({ _id: 'catalog-1', workflowRevision: 8, processData: { nodes: [] }, approvalEnabled: true })
  })
  await vm.initFlowData()
  assert.equal(vm.catalogMode, false)
  assert.equal(vm.selectedAction.actionCode, 'add')
  await vm.sendToServer({ actionCode: 'add', draft: true, processData: {}, approvalEnabled: true })
  assert.equal(calls[0]._id, 'catalog-1')
  assert.equal(calls[0].actionCode, 'add')
  assert.equal(calls[0].workflowRevision, 8)
  assert.equal(vm.mockData.workflowRevision, 9)
  vm.$destroy()
})

test('failed catalog save preserves its revision and draft so a stale page cannot pretend success', async () => {
  const { vm } = designer({ workflowId: 'catalog-1' }, { saveWorkflowCatalog: async () => { throw new Error('流程已被修改') } })
  await vm.initFlowData()
  await assert.rejects(vm.sendToServer({ draft: true, processData: {} }), /流程已被修改/)
  assert.equal(vm.mockData.workflowRevision, 0)
  assert.notEqual(vm.mockData.draft, true)
  vm.$destroy()
})

test('opportunity add and edit use configured actions and only report the returned completion status', async () => {
  for (const isUpdate of [false, true]) {
    for (const status of ['completed', 'pendingApproval', 'denied']) {
      const calls = [], messages = [], events = []
      const config = options('src/views/eos/business/modules/BusinessForm.vue', {
        getBusiness() {}, listBusinessOwners() {}, listCustomerOptions() {}, crmModules: { opportunity: {} },
        withModuleContexts: () => ({ methods: { async submitConfiguredAction(...args) { calls.push(args); return status === 'denied' ? null : { data: { status } } } } })
      })
      const vm = new Vue({ ...config, propsData: { isUpdate } })
      vm.$refs.form = { validate: callback => callback(true) }
      vm.$message = { success: message => messages.push(message), error: message => messages.push(message) }
      vm.$on('close', () => events.push('close'))
      vm.form.name = '测试商机'
      vm.handleSubmit()
      await new Promise(resolve => setImmediate(resolve))
      assert.equal(calls[0][0], isUpdate ? 'edit' : 'add')
      assert.equal(calls[0][1].name, '测试商机')
      assert.equal(vm.saving, false)
      if (status === 'denied') {
        assert.equal(events.length, 0)
        assert.equal(messages.length, 0)
      } else {
        assert.equal(events.length, 1)
        assert.match(messages[0], status === 'pendingApproval' ? /提交审批/ : /已创建|已更新/)
      }
      vm.$destroy()
    }
  }
})
