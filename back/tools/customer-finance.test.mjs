import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import compiler from 'vue-template-compiler'

const Vue = createRequire(import.meta.url)('vue')
const read = file => fs.readFileSync(new URL('../../' + file, import.meta.url), 'utf8')
const base = 'src/views/eos/crm/finance/'

function component(file, props = {}, overrides = {}) {
  const parsed = compiler.parseComponent(read(file))
  const requests = [], messages = []
  const dependencies = {
    withModuleContexts: () => ({}), storage: { get: () => '' }, ACCESS_TOKEN: 'token',
    today: () => '2026-11-04', window: { crypto: { randomUUID: () => 'finance-test-token' } },
    handleProcessHistory: () => {},
    listFinanceRecords: async () => ({ data: { rows: [], total: 0 } }),
    listFinanceOptions: async () => ({ data: { sources: [], customers: [], jobs: [], owners: [] } }),
    listFinanceReceipts: async () => ({ data: [] }), listCollections: async () => ({ data: [] }),
    getFinanceRecord: async () => ({ data: {} }), executeWorkflowAction: async () => ({ data: {} }),
    createFinanceRecord: async (...args) => { requests.push(args) },
    updateFinanceRecord: async (...args) => { requests.push(args) },
    financeRecordAction: async (...args) => { requests.push(args) },
    createCollection: async data => { requests.push(data) },
    SDescriptions: {}, ContractDetail: {}, normalizeContractId: value => Number(value) || null,
    ...overrides
  }
  const source = parsed.script.content.replace(/^import .*$/gm, '').replaceAll('import.meta.env.VITE_APP_BASE_API', "'/api-qs'").replace('export default', 'return')
  const options = new Function(...Object.keys(dependencies), source)(...Object.values(dependencies))
  const template = compiler.compile(parsed.template.content)
  assert.deepEqual(template.errors, [])
  const vm = new Vue({ ...options, propsData: props, render: new Function(template.render), staticRenderFns: template.staticRenderFns.map(fn => new Function(fn)) })
  vm.$message = Object.fromEntries(['success', 'error', 'warning'].map(type => [type, text => messages.push({ type, text })]))
  vm.showModuleAction = () => true
  vm.moduleActionBinding = () => ({})
  vm.$store = { getters: { userInfo: { userId: 1 } } }
  return { vm, requests, messages }
}

const record = overrides => component(base + 'modules/FinanceRecords.vue', { recordType: 'invoice' }, overrides)
const proof = () => ({ name: 'receipt.pdf', status: 'done', fileName: '/profile/receipt.pdf' })
const textOf = node => node ? (node.text || '') + (node.children || []).map(textOf).join('') : ''

test('adjustment list offers both writeoff and reversal and keeps their record types distinct', async () => {
  const { vm } = component(base + 'modules/FinanceRecords.vue', { recordType: 'adjustment' })
  const rendered = textOf(vm._render())
  assert.match(rendered, /登记核销或冲销/)
  assert.match(rendered, /到账冲销/)
  assert.doesNotMatch(rendered, /新建应收核销/)
  await vm.openForm(null, 'reversal')
  assert.equal(vm.type, 'reversal')
  await vm.openForm(null, 'writeoff')
  assert.equal(vm.type, 'writeoff')
  vm.$destroy()
})

test('actual settlement carries record revision and complete proof', async () => {
  const { vm, requests } = record()
  vm.openSettle({ id: 8, revision: 4, recordType: 'invoice' }, 'issue')
  vm.settleForm.invoiceNo = 'INV-8'
  vm.settleFiles = [proof()]
  await vm.settleNow()
  assert.equal(requests.length, 1)
  assert.deepEqual(requests[0].slice(0, 3), ['invoice', 8, 'issue'])
  assert.equal(requests[0][3].revision, 4)
  assert.equal(JSON.parse(requests[0][3].attachment)[0].fileName, '/profile/receipt.pdf')
  assert.equal(vm.settle, null)
  vm.$destroy()
})

test('unfinished or failed vouchers block settlement even when another upload succeeded', async () => {
  for (const status of ['uploading', 'error']) {
    const { vm, requests } = record()
    vm.openSettle({ id: 8, revision: 4, recordType: 'invoice' }, 'issue')
    vm.settleForm.invoiceNo = 'INV-8'
    vm.settleFiles = [proof(), { name: 'pending.pdf', status }]
    await vm.settleNow()
    assert.equal(requests.length, 0)
    assert.equal(vm.settle.id, 8)
    vm.$destroy()
  }
})

test('failed actual settlement preserves user input and enables retry', async () => {
  const { vm } = record({ financeRecordAction: async () => { throw new Error('stale revision') } })
  vm.openSettle({ id: 9, revision: 3, recordType: 'refund' }, 'pay')
  vm.settleFiles = [proof()]
  await assert.rejects(vm.settleNow(), /stale revision/)
  assert.equal(vm.saving, false)
  assert.equal(vm.settle.id, 9)
  assert.equal(vm.settleFiles.length, 1)
  vm.$destroy()
})

test('changing receivable clears original receipt links before another refund is saved', () => {
  const { vm } = record()
  vm.form = { originalKey: 'record:7', originalRecordId: 7, originalReceiptId: 8 }
  vm.sources = [{ sourceKey: 'manual:2', customerId: 3, receivableId: 2 }]
  vm.sourceChanged('manual:2')
  assert.equal(vm.form.originalKey, undefined)
  assert.equal(vm.form.originalRecordId, undefined)
  assert.equal(vm.form.originalReceiptId, undefined)
  assert.equal(vm.form.customerId, 3)
  assert.equal(vm.form.receivableId, 2)
  vm.$destroy()
})

test('editing a collection sends the existing task identifier', async () => {
  const { vm, requests } = component(base + 'modules/FinanceCollections.vue')
  vm.sources = [{ sourceKey: 'manual:2', receivableId: 2, customerId: 3 }]
  vm.updatePlan({ id: 4, receivableId: 2, customerId: 3, ownerUserId: 1, nextDate: '2026-11-11', content: '下周回款' })
  await vm.save()
  assert.equal(requests.length, 1)
  assert.equal(requests[0].collectionId, 4)
  assert.equal(requests[0].receivableId, 2)
  vm.$destroy()
})

test('financial approval displays the transaction amount and vouchers instead of contract approval wording', () => {
  const { vm } = component('src/views/workflow/todoDetail.vue', { row: { financeRecord: true, contractId: 7, amount: 50, recordType: 'refund', attachment: JSON.stringify([proof()]) } })
  assert.equal(vm.normalizedRow.recordType, '退款申请')
  assert.equal(vm.formItems.find(item => item.prop === 'amount').label, '本次金额（元）')
  assert.equal(vm.financeFiles.length, 1)
  assert.doesNotMatch(textOf(vm._render()), /冻结的合同版本/)
  vm.$destroy()
})

test('voucher preview keeps every uploaded proof and ignores unsafe paths', () => {
  const { vm } = record()
  vm.preview({ attachment: JSON.stringify([
    { name: '到账凭证.pdf', fileName: '/profile/receipt.pdf' },
    { name: '银行流水.pdf', fileName: '/profile/bank.pdf' },
    { name: '无效文件', fileName: 'javascript:alert(1)' },
    { name: '无效路径', fileName: '/profile/../config' }
  ]) })
  assert.deepEqual(vm.previewFile.map(file => file.name), ['到账凭证.pdf', '银行流水.pdf'])
  vm.$destroy()
})

test('collection close preserves the task when the server rejects the operation', async () => {
  const { vm } = component(base + 'modules/FinanceCollections.vue', {}, {
    createCollection: async () => { throw new Error('task changed') }
  })
  vm.$prompt = async () => ({ value: '客户已确认' })
  await assert.rejects(vm.closePlan({ id: 4, receivableId: 2, customerId: 3, ownerUserId: 1 }), /task changed/)
  vm.$destroy()
})

test('changing cost contract clears an incompatible job but retains an unbound job', () => {
  const { vm } = component(base + 'modules/FinanceRecords.vue', { recordType: 'cost' })
  vm.jobs = [{ id: 1, customerId: 3, contractId: 7 }, { id: 2, customerId: 3, contractId: null }]
  vm.form = { customerId: 3, contractId: 8, jobId: 1 }
  vm.contractChanged()
  assert.equal(vm.form.jobId, undefined)
  vm.form.jobId = 2
  vm.contractChanged()
  assert.equal(vm.form.jobId, 2)
  vm.$destroy()
})
