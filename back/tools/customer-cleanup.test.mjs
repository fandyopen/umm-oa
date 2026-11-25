import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import Vue from 'vue'
import compiler from 'vue-template-compiler'

const source = fs.readFileSync(new URL('../../src/views/system/customerCleanup/index.vue', import.meta.url), 'utf8')
const parsed = compiler.parseComponent(source)
const preview = total => ({ customer: { id: 25, name: '清理测试客户' }, signature: 'scope-' + total, total, tables: [], amounts: { netReceipts: 50 }, payroll: { items: [], sharedBatches: 0 } })
function page(overrides = {}) {
  const calls = [], messages = []
  const dependencies = {
    saveAs: () => {}, searchCleanupCustomers: async () => ({ data: [{ id: 25, name: '清理测试客户' }] }),
    previewCustomerCleanup: async () => ({ data: preview(0) }), listCustomerCleanups: async () => ({ data: [] }),
    cleanCustomerRelations: async (id, data) => { calls.push({ id, data, operation: 'cleanup' }); return { data: { id: 'operation-1', customerDeleted: false, deletedCount: 3, remainingCount: 0, deleted: [] } } },
    deleteCleanedCustomer: async (id, data) => { calls.push({ id, data, operation: 'delete' }); return { data: { id: 'operation-2', customerDeleted: true, deletedCount: 0, remainingCount: 0, deleted: [] } } },
    downloadCustomerCleanupBackup: async () => new Blob(['backup']), ...overrides
  }
  const options = new Function(...Object.keys(dependencies), parsed.script.content.replace(/^import .*$/gm, '').replace('export default', 'return'))(...Object.values(dependencies))
  const vm = new Vue(options)
  vm.$message = Object.fromEntries(['error', 'success', 'warning'].map(type => [type, text => messages.push({ type, text })]))
  return { vm, calls, messages }
}
function confirm(vm, type = 'cleanup') {
  vm.confirm(type)
  vm.form = { confirmId: '25', confirmName: '清理测试客户', reason: '删除测试业务' }
}

test('cleanup page template compiles and requires explicit customer selection', async () => {
  assert.deepEqual(compiler.compile(parsed.template.content).errors, [])
  const { vm, calls } = page()
  vm.keyword = '测试'
  await vm.search()
  assert.equal(vm.customers.length, 1)
  assert.equal(vm.preview, null)
  await vm.execute()
  assert.equal(calls.length, 0)
  vm.$destroy()
})
test('exact customer confirmation and reason are required before destructive requests', async () => {
  const { vm, calls } = page()
  vm.preview = preview(3)
  confirm(vm)
  vm.form.confirmName = '另一客户'
  await vm.execute()
  vm.form.confirmName = '清理测试客户'
  vm.form.reason = ' '
  await vm.execute()
  assert.equal(calls.length, 0)
  vm.confirm('delete')
  assert.equal(vm.confirmation, 'cleanup')
  vm.$destroy()
})
test('cleanup keeps customer and refreshes preview before a separately confirmed final deletion', async () => {
  const { vm, calls } = page()
  vm.customers = [{ id: 25 }]
  vm.preview = preview(3)
  confirm(vm)
  await vm.execute()
  assert.equal(calls[0].data.signature, 'scope-3')
  assert.equal(calls[0].operation, 'cleanup')
  assert.equal(vm.preview.total, 0)
  assert.equal(vm.customers.length, 1)
  confirm(vm, 'delete')
  await vm.execute()
  assert.equal(calls[1].operation, 'delete')
  assert.equal(calls[1].data.signature, 'scope-0')
  assert.equal(vm.customers.length, 0)
  assert.equal(vm.result.customerDeleted, true)
  vm.$destroy()
})
test('stale preview forces another review without automatically repeating a cleanup', async () => {
  let attempts = 0
  const { vm, messages } = page({ cleanCustomerRelations: async () => { attempts++; throw Object.assign(new Error('关联数据已变化'), { code: 409 }) } })
  vm.preview = preview(3)
  confirm(vm)
  await vm.execute()
  assert.equal(attempts, 1)
  assert.equal(vm.confirmation, '')
  assert.equal(vm.preview.total, 0)
  assert.equal(vm.busy, false)
  assert.match(messages[0].text, /关联数据已变化/)
  vm.$destroy()
})
test('in-flight cleanup blocks duplicate execution and cancellation', async () => {
  let finish, attempts = 0
  const { vm } = page({ cleanCustomerRelations: () => { attempts++; return new Promise(resolve => { finish = resolve }) } })
  vm.preview = preview(3)
  confirm(vm)
  const running = vm.execute()
  await vm.execute()
  vm.cancel()
  assert.equal(attempts, 1)
  assert.equal(vm.confirmation, 'cleanup')
  finish({ data: { id: '1', deleted: [], customerDeleted: false } })
  await running
  assert.equal(vm.busy, false)
  vm.$destroy()
})
test('failed cleanup preserves confirmation input and does not claim success', async () => {
  const { vm, messages } = page({ cleanCustomerRelations: async () => { throw new Error('备份失败') } })
  vm.preview = preview(3)
  confirm(vm)
  await vm.execute()
  assert.equal(vm.result, null)
  assert.equal(vm.form.reason, '删除测试业务')
  assert.equal(vm.confirmation, 'cleanup')
  assert.equal(messages.filter(message => message.type === 'success').length, 0)
  vm.$destroy()
})
