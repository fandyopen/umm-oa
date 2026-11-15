import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import Vue from 'vue'
import compiler from 'vue-template-compiler'

const source = fs.readFileSync(new URL('../../src/views/system/dataTable/index.vue', import.meta.url), 'utf8')
const parsed = compiler.parseComponent(source)
const tables = [{ name: 'sys_user', label: '成员', readable: true }, { name: 'oa_finance_record', label: '财务记录', readable: true }, { name: 'oa_finance_history', label: '财务历史', readable: true }]
const profile = { key: 'finance-receipt', title: '财务与回款管理 / 回款登记', currentTitle: '财务与回款管理 / 实际到账登记', table: 'oa_finance_record', filters: [{ field: 'record_type', op: 'eq', values: ['receipt'] }], sources: [] }
function page(overrides = {}) {
  const calls = [], messages = []
  const dependencies = {
    Empty: {}, listDataTables: async () => ({ data: tables }), listDataPages: async () => ({ data: [profile] }),
    getDataTableStructure: async () => ({ data: { columns: [{ name: 'id', label: 'ID', editable: true }, { name: 'remark', editable: true }], editAllowed: true, deleteAllowed: true } }),
    getDataTableRows: async (table, params) => { calls.push({ operation: 'rows', table, params }); return { data: { rows: [], total: 0 } } },
    getDataTableHistory: async () => ({ data: { rows: [], total: 0 } }),
    getDataTableRelated: async (table, data) => { calls.push({ operation: 'related', table, data }); return { data: [] } },
    getDataTableImpact: async (table, data) => { calls.push({ operation: 'impact', table, data }); return { data: [] } },
    saveDataTableRow: async (table, data, create) => { calls.push({ operation: 'save', table, data, create }); return { data: {} } },
    deleteDataTableRow: async (table, data) => { calls.push({ operation: 'delete', table, data }); return {} }, ...overrides
  }
  const options = new Function(...Object.keys(dependencies), parsed.script.content.replace(/^import .*$/gm, '').replace('export default', 'return'))(...Object.values(dependencies))
  delete options.created
  const vm = new Vue(options)
  vm.$message = Object.fromEntries(['warning', 'error', 'success'].map(type => [type, text => messages.push({ type, text })]))
  vm.tables = tables
  vm.businessPages = [profile]
  return { vm, calls, messages }
}
const row = { key: { id: '8' }, version: 'v1', values: { id: '8', remark: '原值' } }

test('business page and related drawer templates compile', () => {
  assert.deepEqual(compiler.compile(parsed.template.content).errors, [])
})
test('page selection, reset, pagination and refresh preserve the server page scope', async () => {
  const { vm, calls } = page()
  await vm.selectPage(profile.key)
  vm.filterField = 'remark'
  vm.filterValue = '到账'
  vm.search()
  await Vue.nextTick()
  vm.reset()
  await Vue.nextTick()
  vm.tableChanged({ current: 1, pageSize: 50 }, {}, {})
  await Vue.nextTick()
  await vm.refresh()
  assert.ok(calls.filter(c => c.operation === 'rows').every(c => c.params.pageKey === profile.key))
  assert.equal(vm.activePage.key, profile.key)
  assert.equal(vm.size, 50)
  assert.equal(vm.filterPage('实际到账登记', { key: profile.key }), true)
  assert.equal(vm.filterPage('回款登记', { key: profile.key }), true)
  vm.$destroy()
})
test('direct table selection explicitly exits the business page scope', async () => {
  const { vm, calls } = page()
  await vm.selectPage(profile.key)
  await vm.selectTable(tables[1])
  assert.equal(vm.activePage, null)
  assert.equal(calls.at(-1).params.pageKey, undefined)
  vm.$destroy()
})
test('related group navigation and reset retain exact origin key, version and relation', async () => {
  const { vm, calls } = page()
  const context = { table: 'oa_finance_record', ...row, relation: 'finance-history' }
  delete context.values
  await vm.locateRelated({ table: 'oa_finance_history', context, label: '历史', note: 'entity_type=record' })
  vm.reset()
  await Vue.nextTick()
  assert.deepEqual(JSON.parse(calls.at(-1).params.relationContext), context)
  assert.equal(vm.activePage, null)
  await vm.selectTable(tables[1])
  assert.equal(vm.activeRelation, null)
  vm.$destroy()
})
test('delete preview and commit carry page scope and keep selection after success', async () => {
  const { vm, calls } = page()
  await vm.selectPage(profile.key)
  await vm.openDelete(row)
  vm.reason = '删除无效草稿'
  await vm.commit()
  const writes = calls.filter(c => ['impact', 'delete'].includes(c.operation))
  assert.equal(writes.length, 2)
  assert.ok(writes.every(c => c.data.pageKey === profile.key && c.data.key.id === '8' && c.data.version === 'v1'))
  assert.equal(vm.activePage.key, profile.key)
  assert.equal(vm.confirmVisible, false)
  vm.$destroy()
})
test('financial maintenance blocks prevent accidental editor and delete requests', async () => {
  const { vm, calls, messages } = page()
  await vm.selectPage(profile.key)
  const confirmed = { ...row, maintenanceBlock: '已入账请冲销' }
  vm.openEditor(confirmed)
  await vm.openDelete(confirmed)
  assert.equal(vm.editorVisible, false)
  assert.equal(calls.filter(c => c.operation === 'impact').length, 0)
  assert.ok(messages.every(m => m.text === confirmed.maintenanceBlock))
  vm.$destroy()
})
test('late association response cannot replace data for a newer row or closed drawer', async () => {
  const pending = []
  const { vm } = page({ getDataTableRelated: () => new Promise(resolve => pending.push(resolve)) })
  await vm.selectTable(tables[1])
  const first = vm.viewRelated(row)
  const second = vm.viewRelated({ ...row, key: { id: '9' } })
  pending[1]({ data: [{ id: 'new' }] })
  await second
  pending[0]({ data: [{ id: 'old' }] })
  await first
  assert.equal(vm.relatedGroups[0].id, 'new')
  const third = vm.viewRelated(row)
  vm.closeRelated()
  pending[2]({ data: [{ id: 'closed' }] })
  await third
  assert.equal(vm.relatedGroups.length, 0)
  vm.$destroy()
})
test('stale association navigation surfaces error without falling back to the entire table', async () => {
  const { vm, calls } = page({ getDataTableRows: async () => { throw new Error('数据已被修改') } })
  await vm.locateRelated({ table: 'oa_finance_history', context: { table: 'oa_finance_record', key: row.key, version: 'stale', relation: 'finance-history' }, label: '历史', note: '单条历史' })
  assert.equal(vm.error, '数据已被修改')
  assert.ok(vm.activeRelation)
  assert.equal(vm.rows.length, 0)
  assert.equal(calls.filter(c => c.operation === 'rows').length, 0)
  vm.$destroy()
})
test('removing the last row on a page returns to the last valid page within the same scope', async () => {
  const calls = []
  const { vm } = page({ getDataTableRows: async (table, params) => { calls.push(params); return { data: { total: 20, rows: params.page === 2 ? [] : [row] } } } })
  await vm.selectPage(profile.key)
  vm.page = 2
  await vm.loadRows()
  assert.equal(vm.page, 1)
  assert.equal(vm.rows.length, 1)
  assert.ok(calls.every(c => c.pageKey === profile.key))
  vm.$destroy()
})
