import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import { createRequire } from 'node:module'
import compiler from 'vue-template-compiler'

const require = createRequire(import.meta.url)
const Vue = require('vue')
const moment = require('moment')
const read = file => fs.readFileSync(new URL('../../' + file, import.meta.url), 'utf8')
const page = compiler.parseComponent(read('src/views/system/workflow/index.vue'))
assert.deepEqual(compiler.compile(page.template.content).errors, [])

function workspace(list = async () => ({ data: { rows: [], modules: [], statistics: {}, canDesign: false } }), get = async () => ({ data: {} })) {
  const options = new Function('moment', 'listWorkflowConfigurations', 'getWorkflowConfiguration', 'WorkflowPreview',
    page.script.content.replace(/^import .*$/gm, '').replace('export default', 'return'))(moment, list, get, {})
  const vm = new Vue({ ...options, created: [], activated: [] })
  const routes = []
  vm.$router = { push: value => routes.push(value) }
  return { vm, routes }
}

test('overview submits all selected filters and resets them without changing configurations', async () => {
  const calls = []
  const { vm } = workspace(async query => {
    calls.push(query)
    return { data: { rows: [{ key: 'button:1:7:track' }], modules: [{ label: '客户档案', value: 'module:7' }], statistics: { total: 3 }, canDesign: false } }
  })
  vm.query = { keyword: '新增跟进', module: 'module:7', source: 'button', state: 'active' }
  vm.page = 3
  await vm.search()
  assert.deepEqual(calls[0], { keyword: '新增跟进', module: 'module:7', source: 'button', state: 'active' })
  assert.equal(vm.page, 1)
  assert.equal(vm.rows[0].key, 'button:1:7:track')
  assert.equal(vm.canDesign, false)
  await vm.reset()
  assert.deepEqual(calls[1], { keyword: '', module: undefined, source: undefined, state: undefined })
  vm.$destroy()
})

test('newer search results cannot be overwritten by a slower previous request', async () => {
  const finish = []
  const { vm } = workspace(() => new Promise(resolve => finish.push(resolve)))
  const first = vm.load()
  const second = vm.search()
  finish[1]({ data: { rows: [{ key: 'new' }], modules: [], statistics: { total: 1 }, canDesign: true } })
  await second
  finish[0]({ data: { rows: [{ key: 'old' }], modules: [], statistics: { total: 5 }, canDesign: false } })
  await first
  assert.equal(vm.rows[0].key, 'new')
  assert.equal(vm.canDesign, true)
  assert.equal(vm.loading, false)
  vm.$destroy()
})

test('details show the published version by default and preserve a separate draft preview', async () => {
  const detail = { key: 'button:1:7:track', published: true, hasDraft: true, publishedNodes: [{ name: '生效节点' }], draftNodes: [{ name: '草稿节点' }] }
  const requests = []
  const { vm } = workspace(undefined, async input => { requests.push(input); return { data: detail } })
  await vm.view({ key: detail.key })
  assert.deepEqual(requests, [{ key: detail.key }])
  assert.equal(vm.previewVersion, 'published')
  assert.equal(vm.detail.publishedNodes[0].name, '生效节点')
  assert.equal(vm.detail.draftNodes[0].name, '草稿节点')
  assert.equal(vm.detailLoading, false)
  vm.$destroy()
})

test('draft-only configuration opens its draft and closed drawers ignore late responses', async () => {
  let finish
  const { vm } = workspace(undefined, () => new Promise(resolve => { finish = resolve }))
  const first = vm.view({ key: 'draft' })
  finish({ data: { published: false, hasDraft: true, draftNodes: [] } })
  await first
  assert.equal(vm.previewVersion, 'draft')
  const second = vm.view({ key: 'closed' })
  vm.closeDetail()
  finish({ data: { flowName: 'late response' } })
  await second
  assert.equal(vm.detailVisible, false)
  assert.equal(vm.detail, null)
  vm.$destroy()
})

test('design navigation requires its separate grant and preserves the exact owning module and action', () => {
  const { vm, routes } = workspace()
  const row = { source: 'button', moduleId: 340352, moduleName: '客户档案', tenantAppId: 2, actionCode: 'track', flowDesignEnabled: true, moduleEnabled: true, actionStatus: '0' }
  vm.design(row)
  assert.equal(routes.length, 0)
  vm.canDesign = true
  for (const disabled of [{ flowDesignEnabled: false }, { moduleEnabled: false }, { actionStatus: '1' }]) vm.design({ ...row, ...disabled })
  assert.equal(routes.length, 0)
  vm.design(row)
  assert.deepEqual(routes[0], { path: '/processDesign', query: { tenantAppId: 2, menuId: 340352, menuName: '客户档案', actionCode: 'track' } })
  vm.design({ ...row, source: 'form', formId: 'expense' })
  assert.deepEqual(routes[1], { name: 'processFormDesign', params: { id: 2, formid: 'expense' } })
  vm.$destroy()
})

test('failed configuration reads expose a retryable error and clear old details', async () => {
  const failure = async () => { throw new Error('没有查看权限') }
  const { vm } = workspace(failure, failure)
  vm.rows = [{ key: 'stale' }]
  await vm.load()
  assert.equal(vm.rows.length, 0)
  assert.equal(vm.loadError, '没有查看权限')
  await vm.view({ key: 'denied' })
  assert.equal(vm.detailError, '没有查看权限')
  assert.equal(vm.detail, null)
  assert.equal(vm.detailLoading, false)
  vm.$destroy()
})

test('preview renders a readonly graph and approval rules without loading the editor', () => {
  const preview = compiler.parseComponent(read('src/views/system/workflow/WorkflowPreview.vue'))
  const template = compiler.compile(preview.template.content)
  assert.deepEqual(template.errors, [])
  const options = new Function('Empty', preview.script.content.replace(/^import .*$/gm, '').replace('export default', 'return'))({ PRESENTED_IMAGE_SIMPLE: '' })
  const vm = new Vue({ ...options, propsData: { nodes: [{ id: 'approval', type: 'approval', name: '经理审批', approverLabel: '角色：人事经理', approvalMode: 'all' }] }, render: new Function(template.render), staticRenderFns: template.staticRenderFns.map(fn => new Function(fn)) })
  const text = node => typeof node?.text === 'string' ? node.text : (node?.children || []).map(text).join(' ')
  const rendered = text(vm._render())
  assert.match(rendered, /经理审批/)
  assert.match(rendered, /角色：人事经理/)
  assert.match(rendered, /会签/)
  assert.doesNotMatch(preview.script.content, /getWorkflowRoles|setProcess|updateForm/)
  vm.$destroy()
})
