import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import compiler from 'vue-template-compiler'

const Vue = createRequire(import.meta.url)('vue')
const source = fs.readFileSync(new URL('../../src/views/system/module/modules/ModuleActionForm.vue', import.meta.url), 'utf8')
const parsed = compiler.parseComponent(source)
const template = compiler.compile(parsed.template.content)
assert.deepEqual(template.errors, [])

function editor(overrides = {}) {
  const calls = [], messages = []
  const dependencies = {
    WorkflowApiConfig: {},
    getWorkflowCatalogOptions: async () => ({ data: { workflows: [], buttons: [] } }),
    getNextModuleActionCode: async () => ({ data: { actionCode: 'action_002' } }),
    getWorkflowActionHandlers: async () => ({ data: [] }),
    createModuleAction: async (...args) => { calls.push(args); return { data: { actionCode: args[1].actionCode, restored: true } } },
    updateModuleAction: async (...args) => { calls.push(args) },
    ...overrides
  }
  const options = new Function(...Object.keys(dependencies), parsed.script.content.replace(/^import .*$/gm, '').replace('export default', 'return'))(...Object.values(dependencies))
  const vm = new Vue({
    ...options, propsData: { canManageStructure: true, canManageWorkflow: true },
    render: new Function(template.render), staticRenderFns: template.staticRenderFns.map(fn => new Function(fn))
  })
  vm.$message = Object.fromEntries(['success', 'error', 'warning'].map(type => [type, text => messages.push(text)]))
  vm.$store = { dispatch: async action => calls.push(action) }
  return { vm, calls, messages }
}

function findInput(node) {
  if (node?.tag === 'a-input') return node
  for (const child of node?.children || []) {
    const found = findInput(child)
    if (found) return found
  }
}

test('new action codes are editable but existing action identities remain read-only', () => {
  const { vm } = editor()
  vm.handleAdd({ moduleId: 340352, moduleName: '客户档案' })
  assert.equal(findInput(vm._render()).data.attrs['read-only'], false)
  vm.handleUpdate({ moduleId: 340352, actionCode: 'track', actionName: '新增跟进' })
  assert.equal(findInput(vm._render()).data.attrs['read-only'], true)
  vm.$destroy()
})

test('late automatic code suggestions never overwrite a manually entered track code', async () => {
  let release
  const { vm } = editor({ getNextModuleActionCode: () => new Promise(resolve => { release = resolve }) })
  vm.moduleId = 340352
  const pending = vm.loadNextActionCode(vm.editorSequence)
  vm.form.actionCode = 'track'
  findInput(vm._render()).data.on.change({ target: { value: 'track' } })
  release({ data: { actionCode: 'action_002' } })
  await pending
  assert.equal(vm.form.actionCode, 'track')
  assert.equal(vm.generatingCode, false)
  vm.$destroy()
})

test('manual track creation uses its original identity, preserves blank permissions for defaults, and refreshes the session', async () => {
  const { vm, calls, messages } = editor()
  let pending
  vm.$refs.actionForm = { validate: callback => { pending = callback(true) } }
  vm.moduleId = 340352
  vm.form.actionCode = 'track'
  vm.form.actionName = '新增跟进'
  vm.submit()
  await pending
  assert.equal(calls[0][0], 340352)
  assert.equal(calls[0][1].actionCode, 'track')
  assert.equal(calls[0][1].permissionCode, '')
  assert.equal(calls[1], 'BootstrapSession')
  assert.match(messages[0], /按钮已恢复/)
  vm.$destroy()
})

test('action code validation accepts track and generated defaults and rejects reserved or invalid codes', () => {
  const { vm } = editor()
  for (const value of ['', 'track', 'action_002']) vm.validateActionCode(null, value, error => assert.equal(error, undefined))
  for (const value of ['page', 'Track', '9track', '跟进', 'track-delete']) vm.validateActionCode(null, value, error => assert.ok(error))
  vm.$destroy()
})
