import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import compiler from 'vue-template-compiler'

const read = file => fs.readFileSync(new URL('../../' + file, import.meta.url), 'utf8')
const load = (file, dependencies) => new Function(...Object.keys(dependencies), compiler.parseComponent(read(file)).script.content.replace(/^import .*$/gm, '').replace('export default', 'return'))(...Object.values(dependencies))
const stub = { withModuleContexts: () => ({}) }
const dialog = overrides => {
  const calls = [], events = [], messages = []
  const options = load('src/views/eos/crm/contract/modules/ContractLifecycleDialog.vue', { ...stub, crmModules: {}, contractAction: async (...args) => { calls.push(args); return {} }, deleteContract: async (...args) => { calls.push(args); return {} }, ...overrides })
  const vm = { ...options.data(), record: { id: 42, revision: 3, name: '测试合同' }, action: 'void', title: '作废合同', $emit: (...args) => events.push(args), $message: { warning: text => messages.push(text), success: text => messages.push(text) }, submitConfiguredAction: async (...args) => { calls.push(args); return { data: { status: 'pendingApproval' } } } }
  Object.assign(vm, options.methods)
  return { vm, calls, events, messages }
}

test('contract lifecycle templates compile', () => {
  for (const name of ['ContractLifecycleDialog', 'ContractWorkspace', 'ContractDetail']) {
    const template = compiler.parseComponent(read('src/views/eos/crm/contract/modules/' + name + '.vue')).template.content
    assert.deepEqual(compiler.compile(template).errors, [])
  }
})

test('void requires reason and forwards the exact revision through unified submission', async () => {
  const { vm, calls, events, messages } = dialog()
  await vm.save()
  assert.equal(calls.length, 0)
  vm.reason = '  不再签约  '
  await vm.save()
  assert.equal(calls[0][0], 'void')
  assert.deepEqual(calls[0][1], { contractId: 42, revision: 3, reason: '不再签约', title: '作废合同：测试合同' })
  assert.ok(messages.includes('申请已提交，审批通过后生效'))
  assert.deepEqual(events, [['success', { deleted: false }], ['close']])
})

test('deletion sends the revision and only closes after successful deletion', async () => {
  const { vm, calls, events } = dialog()
  vm.action = 'remove'
  await vm.save()
  assert.deepEqual(calls, [[42, { revision: 3 }]])
  assert.deepEqual(events, [['success', { deleted: true }], ['close']])
})

test('a pending operation cannot be repeated or cancelled', async () => {
  const { vm, events } = dialog()
  let resolve, count = 0
  vm.reason = '原因'
  vm.submitConfiguredAction = () => { count++; return new Promise(done => { resolve = done }) }
  const first = vm.save()
  await vm.save()
  vm.cancel()
  assert.equal(count, 1)
  assert.equal(events.length, 0)
  resolve({})
  await first
  assert.equal(vm.saving, false)
})

test('a failed request preserves the reason and leaves confirmation open', async () => {
  const { vm, events } = dialog()
  vm.reason = '保留原因'
  vm.submitConfiguredAction = async () => { throw new Error('合同已变更') }
  await assert.rejects(vm.save(), /合同已变更/)
  assert.equal(vm.reason, '保留原因')
  assert.equal(vm.saving, false)
  assert.equal(events.length, 0)
})
