import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import compiler from 'vue-template-compiler'
import { financeRecordTypes, financeStatuses, financeStatusColors, financeMoney, financeFiles, financeHistoryEntry } from '../../src/views/eos/crm/finance/history.js'

const Vue = createRequire(import.meta.url)('vue')
const read = file => fs.readFileSync(new URL('../../' + file, import.meta.url), 'utf8')
const { followupDisplayFields, formatApprovalValue } = await import('data:text/javascript;base64,' + Buffer.from(read('src/utils/workflowApproval.js')).toString('base64'))
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
    SDescriptions: {}, HrApprovalDetail: {}, ContractDetail: {}, normalizeContractId: value => Number(value) || null,
    followupDisplayFields, formatApprovalValue,
    getFinanceReport: async () => ({ data: {} }), exportFinanceRows: async () => ({ data: [] }),
    XLSX: { utils: { book_new: () => ({}), json_to_sheet: rows => rows, book_append_sheet: () => {} }, write: () => [] },
    saveAs: () => {},
    FinanceRecordHistory: {}, FinanceHistoryTimeline: {}, Timeline: { Item: {} },
    financeRecordTypes, financeStatuses, financeStatusColors, financeMoney, financeFiles, financeHistoryEntry,
    ...overrides
  }
  const source = parsed.script.content.replace(/^import .*$/gm, '').replaceAll('import.meta.env.VITE_APP_BASE_API', "'/api-qs'").replace('export default', 'return')
  const options = new Function(...Object.keys(dependencies), source)(...Object.values(dependencies))
  if (parsed.template) {
    const template = compiler.compile(parsed.template.content)
    assert.deepEqual(template.errors, [])
    options.render = new Function(template.render)
    options.staticRenderFns = template.staticRenderFns.map(fn => new Function(fn))
  }
  const vm = new Vue({ ...options, propsData: props })
  vm.$message = Object.fromEntries(['success', 'error', 'warning'].map(type => [type, text => messages.push({ type, text })]))
  vm.showModuleAction = () => true
  vm.moduleActionBinding = () => ({})
  vm.$store = { getters: { userInfo: { userId: 1 } } }
  return { vm, requests, messages }
}

const record = overrides => component(base + 'modules/FinanceRecords.vue', { recordType: 'invoice' }, overrides)
const proof = () => ({ name: 'receipt.pdf', status: 'done', fileName: '/profile/receipt.pdf' })
const textOf = node => node ? (node.text || '') + (node.children || []).map(textOf).join('') : ''

test('financial history shows business fields without exposing internal snapshots or stale status', () => {
  const { vm } = component(base + 'modules/FinanceHistoryTimeline.vue', { history: [{
    id: 2, action: '财务确认', createBy: '13610316814', createTime: '2026-11-05T08:40:53.734Z',
    content: JSON.stringify({ amount: 12222, businessDate: '2026-11-05', channel: '银行转账', referenceNo: 'PAY-1',
      remark: '收到款项\n凭证已核对', attachment: JSON.stringify([proof()]), status: 'draft', revision: 1, createToken: 'internal-token', customerId: 2 })
  }] })
  const entry = vm.entries[0]
  assert.equal(entry.action, '财务确认')
  assert.equal(entry.time, '2026-11-05 16:40:53')
  assert.deepEqual(entry.items.find(item => item.key === 'amount'), { key: 'amount', label: '金额', value: '￥12,222.00' })
  assert.equal(entry.items.find(item => item.key === 'remark').value, '收到款项\n凭证已核对')
  assert.equal(entry.files[0].url, '/profile/receipt.pdf')
  assert.ok(!JSON.stringify(entry).includes('internal-token'))
  assert.ok(!entry.items.some(item => ['status', 'revision', 'customerId'].includes(item.key)))
  assert.ok(vm.$options.components.ATimeline)
  assert.ok(vm.$options.components.ATimelineItem)
  vm.$destroy()
})

test('history tolerates empty and old notes while attachments only use valid local upload links', () => {
  for (const content of ['{}', null, '[1,2]', '{broken', { revision: 2 }]) {
    const entry = financeHistoryEntry({ action: '提交审批', content })
    assert.deepEqual(entry.items, [])
    assert.deepEqual(entry.files, [])
    assert.equal(entry.note, '')
  }
  assert.equal(financeHistoryEntry({ content: '客户确认收到发票' }).note, '客户确认收到发票')
  assert.deepEqual(financeFiles(JSON.stringify([
    { name: '有效凭证', url: '/profile/proof.pdf' }, { name: '外部链接', url: 'https://example.com/file.pdf' },
    { name: '脚本', fileName: 'javascript:alert(1)' }, { name: '目录穿越', fileName: '/profile/%2e%2e/config' }, null
  ])), [{ name: '有效凭证', url: '/profile/proof.pdf' }])
})

test('record history summary keeps attachments and handles a record without history', () => {
  const { vm } = component(base + 'modules/FinanceRecordHistory.vue', { record: {
    id: 2, recordType: 'receipt', amount: 12222, status: 'confirmed', customerName: '测试客户',
    businessDate: '2026-11-05', remark: '收到回款', attachment: JSON.stringify([proof()])
  } })
  const rendered = textOf(vm._render())
  assert.match(rendered, /回款申请/)
  assert.match(rendered, /已确认/)
  assert.match(rendered, /12,222\.00/)
  assert.equal(vm.files.length, 1)
  assert.equal(vm.files[0].name, 'receipt.pdf')
  vm.$destroy()
  const timeline = component(base + 'modules/FinanceHistoryTimeline.vue').vm
  assert.match(textOf(timeline._render()), /暂无操作记录/)
  timeline.$destroy()
})

test('finance routes render the requested workspace and preserve each record type', async () => {
  const { default: VueRouter } = await import('vue-router')
  const types = ['ledger', 'invoice', 'receipt', 'refund', 'adjustment', 'cost', 'collections', 'reconcile']
  const bases = {
    ledger: 'FinanceLedger', collections: 'FinanceCollections', reconcile: 'FinanceReconcile'
  }
  const pages = {}
  for (const type of types) {
    const parent = bases[type] || 'FinanceRecords'
    const source = component(base + 'modules/' + parent + '.vue', parent === 'FinanceRecords' ? { recordType: type } : {}, {
      listFinanceLedger: async () => ({ data: [] }), getFinanceReport: async () => ({ data: {} }),
      createReceivable: () => {}, updateReceivable: () => {}, financeReceivableAction: () => {}
    }).vm
    const page = component(base + type + '.vue', {}, { [parent]: source.$options }).vm
    assert.equal(typeof page.$options.render, 'function')
    assert.equal(page.$options.financeWorkspace, type)
    if (parent === 'FinanceRecords') {
      assert.equal(page.recordType, type)
      assert.equal(page.type, type === 'adjustment' ? 'writeoff' : type)
      assert.equal(page.$options.props.recordType.required, undefined)
    }
    pages[`../views/eos/crm/finance/${type}.vue`] = () => Promise.resolve(page.$options)
    page.$destroy()
    source.$destroy()
  }
  const shell = component(base + 'index.vue').vm
  pages['../views/eos/crm/finance/index.vue'] = () => Promise.resolve(shell.$options)
  const source = read('src/router/generator-routers.js').replace(/^import .*$/gm, '')
    .replace(/^const modules = import.meta.glob.*$/m, 'const modules = pageModules')
    .replaceAll('export const ', 'const ')
  const generator = new Function('pageModules', 'UserLayout', 'BasicLayout', 'BlankLayout', 'PageView', 'RouteView', 'indexRouterMap', 'validURL', source + '\nreturn generator')(
    pages, {}, {}, {}, {}, {}, [], () => false
  )
  const routes = generator([{ name: 'CustomerFinance', path: '/finance', component: 'eos/crm/finance/index', redirect: '/finance/ledger', children:
    types.map(type => ({ name: 'Finance' + type, path: type, component: 'eos/crm/finance/' + type })) }])
  const router = new VueRouter({ mode: 'abstract', routes })
  assert.equal(router.resolve('/finance').route.path, '/finance/ledger')
  for (const type of types) {
    const route = router.resolve('/finance/' + type).route
    assert.equal(route.matched.length, 2)
    assert.equal(route.matched[1].meta.component, 'eos/crm/finance/' + type)
    assert.equal(typeof route.matched[1].components.default, 'function')
    assert.equal(router.resolve({ name: 'Finance' + type }).route.path, '/finance/' + type)
  }
  shell.$destroy()
})

test('header navigation opens leaf menus even with an empty children array', () => {
  const parsed = compiler.parseComponent(read('src/components/layouts/GlobalHeader/RightContent.vue'))
  const source = parsed.script.content.replace(/^import .*$/gm, '').replace('export default', 'return')
  const options = new Function('AvatarDropdown', 'Ellipsis', 'IconFont', 'mapState', 'mapGetters', 'mapActions', source)(
    {}, {}, {}, () => ({}), () => ({}), () => ({})
  )
  const visits = [], selections = []
  const context = { $router: { push: target => visits.push(target) }, SetCurrentLevelOneMenu: menus => selections.push(menus) }
  options.methods.changeCurrentFirstLevelMenu.call(context, { name: 'Standalone', children: [] })
  assert.deepEqual(visits.pop(), { name: 'Standalone' })
  const children = [{ name: 'Hidden', hidden: true }, { name: 'Ledger', hidden: false, children: [] }]
  options.methods.changeCurrentFirstLevelMenu.call(context, { name: 'Finance', children })
  assert.deepEqual(visits.pop(), { name: 'Ledger' })
  assert.equal(selections.at(-1)[0].name, 'Finance')
})

test('export uses the active route workspace and its filters', async () => {
  const calls = []
  const { vm } = component(base + 'index.vue', {}, { exportFinanceRows: async (type, filters) => { calls.push({ type, filters }); return { data: [] } } })
  vm.checkModuleAction = async () => true
  vm.$refs.workspace = { $options: { financeWorkspace: 'invoice' }, query: { keyword: '客户A' } }
  await vm.exportCurrent()
  assert.deepEqual(calls, [{ type: 'invoice', filters: { keyword: '客户A' } }])
  calls.length = 0
  vm.$refs.workspace = { $options: { financeWorkspace: 'reconcile' }, query: { customerId: 3 } }
  await vm.exportCurrent()
  assert.equal(calls.length, 7)
  assert.ok(calls.every(call => call.filters.customerId === 3))
  vm.$destroy()
})

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
