import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import compiler from 'vue-template-compiler'

const require = createRequire(import.meta.url)
const Vue = require('vue')
const read = file => fs.readFileSync(new URL('../../' + file, import.meta.url), 'utf8')
const optionsFrom = (file, dependencies) => {
  const source = compiler
    .parseComponent(read(file))
    .script.content.replace(/^import .*$/gm, '')
    .replace('export default', 'return')
    .replaceAll('import.meta.env.VITE_APP_BASE_API', "'/api'")
  return new Function(...Object.keys(dependencies), source)(...Object.values(dependencies))
}
const normalizeContractId = new Function(
  'request',
  read('src/api/eos/crm/contracts.js')
    .replace(/^import .*$/gm, '')
    .replaceAll('export const ', 'const ') + '\nreturn normalizeContractId'
)(() => {})

function form({ contractId = 0, save = async () => ({ data: { id: 88 } }), valid = true } = {}) {
  const calls = [],
    messages = [],
    events = []
  const options = optionsFrom('src/views/eos/crm/contract/modules/ContractForm.vue', {
    storage: { get: () => '' },
    ACCESS_TOKEN: 'token',
    listContractCustomers: async () => ({ data: [] }),
    getContract: async () => ({}),
    createContract: async data => {
      calls.push({ type: 'create', data })
      return await save(data)
    },
    updateContract: async (id, data) => {
      calls.push({ type: 'update', id, data })
      return await save(data)
    },
    normalizeContractId,
    newTerm: () => ({}),
    money: String,
    today: () => '2026-11-03'
  })
  const vm = new Vue({ ...options, propsData: { contractId, initial: { customerId: 42, opportunityId: 123, name: '商机来源合同', amount: 1000 } } })
  vm.$refs.form = { validate: done => done(valid) }
  vm.$message = Object.fromEntries(['success', 'error', 'warning'].map(type => [type, text => messages.push({ type, text })]))
  vm.$on('ok', value => events.push(value))
  return { vm, calls, messages, events }
}

test('creation keeps customer and opportunity references and emits the new contract for detail navigation', async () => {
  const { vm, calls, messages, events } = form()
  assert.equal(await vm.save(), true)
  assert.equal(calls.length, 1)
  assert.equal(calls[0].type, 'create')
  assert.equal(calls[0].data.customerId, 42)
  assert.equal(calls[0].data.opportunityId, 123)
  assert.deepEqual(events, [{ id: 88 }])
  assert.deepEqual(messages, [{ type: 'success', text: '合同已创建，可继续完善并提交审批' }])
  vm.$destroy()
})

test('editing updates the existing contract and uses a distinct save message', async () => {
  const { vm, calls, messages } = form({ contractId: 88 })
  await vm.save()
  assert.equal(calls[0].type, 'update')
  assert.equal(calls[0].id, 88)
  assert.equal(messages[0].text, '合同修改已保存')
  vm.$destroy()
})

test('rejected creation keeps entered data and attachments without emitting a success event', async () => {
  const { vm, events, messages } = form({
    save: async () => {
      throw new Error('商机关联客户与合同客户不一致')
    }
  })
  vm.form.remark = '保留已填写内容'
  vm.fileList = [{ name: '合同.pdf', status: 'done', url: '/profile/contract.pdf' }]
  await assert.rejects(vm.save(), /客户不一致/)
  assert.equal(vm.form.remark, '保留已填写内容')
  assert.equal(vm.fileList.length, 1)
  assert.equal(vm.saving, false)
  assert.deepEqual(events, [])
  assert.deepEqual(messages, [])
  vm.$destroy()
})

test('creation rejects invalid forms and incomplete uploads', async () => {
  const invalid = form({ valid: false })
  assert.equal(await invalid.vm.save(), false)
  assert.equal(invalid.calls.length, 0)
  invalid.vm.$destroy()
  for (const status of ['uploading', 'error']) {
    const { vm, calls, messages } = form()
    vm.fileList = [{ status }]
    await vm.save()
    assert.equal(calls.length, 0)
    assert.equal(messages[0].type, 'warning')
    vm.$destroy()
  }
})

test('pending creation prevents repeated requests and loading blocks submission', async () => {
  let release
  const { vm, calls } = form({
    save: () =>
      new Promise(resolve => {
        release = resolve
      })
  })
  vm.loading = true
  assert.equal(await vm.save(), false)
  vm.loading = false
  const pending = vm.save()
  await Promise.resolve()
  assert.equal(await vm.save(), false)
  assert.equal(calls.length, 1)
  release({ data: { id: 88 } })
  await pending
  vm.$destroy()
})

test('invalid create responses do not show success or navigate to a missing contract', async () => {
  const { vm, messages, events } = form({ save: async () => ({ data: {} }) })
  await vm.save()
  assert.equal(messages[0].type, 'error')
  assert.deepEqual(events, [])
  vm.$destroy()
})

test('opportunity conversion opens the created contract with the resolved contract module context', () => {
  const options = optionsFrom('src/views/eos/business/modules/BusinessDetail.vue', {
    withModuleContexts: () => ({}),
    crmModules: { opportunity: {}, contract: {} },
    getBusiness: () => {},
    ContractForm: {},
    normalizeContractId
  })
  let modal
  const drawers = [],
    errors = []
  const context = {
    data: { customerId: '42', customerName: '合同客户', name: '商机名称', expectedAmount: 1000 },
    formId: 123,
    moduleContexts: { contract: { moduleId: 9123 } },
    $createAntdModal: value => {
      modal = value
    },
    $createAntdDrawer: value => drawers.push(value),
    $message: { error: text => errors.push(text) }
  }
  options.methods.createContract.call(context)
  assert.equal(modal.modalProps.footer, null)
  assert.equal(modal.content.props.initial.opportunityId, 123)
  assert.equal(modal.content.props.initial.customerId, 42)
  assert.equal(modal.onOk({ payload: {} }), false)
  assert.equal(drawers.length, 0)
  assert.equal(errors.length, 1)
  modal.onOk({ payload: { id: '88' } })
  assert.equal(drawers.length, 1)
  assert.equal(drawers[0].content.props.contractId, 88)
  assert.equal(drawers[0].content.props.moduleMeta.moduleId, 9123)
})
