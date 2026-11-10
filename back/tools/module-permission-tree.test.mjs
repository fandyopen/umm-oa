import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import compiler from 'vue-template-compiler'

const require = createRequire(import.meta.url)
const Vue = require('vue')
const read = file => fs.readFileSync(new URL('../../' + file, import.meta.url), 'utf8')
const helpers = await import('data:text/javascript;base64,' + Buffer.from(read('src/utils/modulePermissionTree.js')).toString('base64'))
const { allPermissionKeys, changePermissionSelection, permissionTreeState } = helpers
const track = 'action:340352:track'
const action = code => ({ id: 'action:340352:' + code, moduleId: 340352, actionCode: code, isAction: true, children: [] })
const tree = [{ id: 340000, children: [{ id: 340352, children: ['add', 'edit', 'remove', 'export', 'track'].map(action) }] }]
const saved = [340000, 340352, ...['add', 'edit', 'remove', 'export'].map(code => action(code).id)]
const parsed = compiler.parseComponent(read('src/views/enterprise/modulePerms/modules/menuTree.vue'))
const template = compiler.compile(parsed.template.content)
assert.deepEqual(template.errors, [])

function editor(initial = saved) {
  const calls = []
  let persisted = [...initial]
  const options = new Function('addMenuPerm', 'roleMenuTree', ...Object.keys(helpers),
    parsed.script.content.replace(/^import .*$/gm, '').replace('export default', 'return'))(
    async body => { calls.push(JSON.parse(JSON.stringify(body))); persisted = [...body.tenantModuleIds] },
    async () => ({ menus: tree, checkedKeys: [...persisted] }), ...Object.values(helpers))
  const vm = new Vue({ ...options, render: new Function(template.render), staticRenderFns: template.staticRenderFns.map(source => new Function(source)) })
  vm.$store = { getters: { tenantAppId: 1 } }
  vm.$message = { success() {} }
  vm.$refs.form = { validate: callback => callback(true) }
  return { vm, calls }
}

function findTree(node) {
  if (node?.tag === 'a-tree') return node
  for (const child of node?.children || []) {
    const found = findTree(child)
    if (found) return found
  }
}

test('saved parent grants do not select a newly added or restored track button', () => {
  const state = permissionTreeState(tree, saved)
  assert.deepEqual(state.checked, saved)
  assert.equal(state.checked.includes(track), false)
  assert.deepEqual(state.halfChecked, [])
})

test('installed Ant Design tree reproduces linked false checks and renders the repaired exact state', () => {
  const Tree = Vue.extend(require('ant-design-vue/lib/vc-tree/src/Tree.js').Tree)
  const nativeData = nodes => nodes.map(node => ({ key: node.id, title: String(node.id), children: nativeData(node.children || []) }))
  for (const checkStrictly of [false, true]) {
    const vm = new Tree({ propsData: {
      __propsSymbol__: {}, children: [], treeData: nativeData(tree), checkable: true,
      checkStrictly, checkedKeys: permissionTreeState(tree, saved)
    } })
    assert.equal(vm.$data._checkedKeys.includes(track), !checkStrictly)
    vm.$destroy()
  }
})

test('opening and saving without edits preserves exact grants despite linked selection being enabled', async () => {
  const { vm, calls } = editor()
  await vm.handleUpdate({ roleId: 22 })
  const rendered = findTree(vm._render())
  assert.ok(Object.hasOwn(rendered.data.attrs, 'checkStrictly'))
  assert.equal(rendered.data.model, undefined)
  assert.deepEqual(rendered.data.attrs['checked-keys'].checked, saved)
  vm.submitForm()
  await Promise.resolve()
  assert.deepEqual(calls[0].tenantModuleIds, saved)
  await vm.handleUpdate({ roleId: 22 })
  assert.equal(vm.treeCheckedKeys.checked.includes(track), false)
  vm.$destroy()
})

test('explicitly checking track persists it and reopens as checked', async () => {
  const { vm, calls } = editor()
  await vm.handleUpdate({ roleId: 22 })
  vm.onCheck({ checked: [...saved, track], halfChecked: [] }, { node: { eventKey: track }, checked: true })
  assert.equal(vm.treeCheckedKeys.checked.includes(track), true)
  vm.submitForm()
  await Promise.resolve()
  assert.deepEqual(calls[0].tenantModuleIds, [...saved, track])
  await vm.handleUpdate({ roleId: 22 })
  assert.equal(vm.treeCheckedKeys.checked.includes(track), true)
  vm.$destroy()
})

test('unchecking track keeps page access and all other button grants', () => {
  assert.deepEqual(changePermissionSelection(tree, [...saved, track], track, false, true), saved)
})

test('linked clicks on a module select and revoke its subtree only', () => {
  assert.deepEqual(changePermissionSelection(tree, [], 340352, true, true).sort(), allPermissionKeys(tree).sort())
  assert.deepEqual(changePermissionSelection(tree, allPermissionKeys(tree), 340352, false, true), [340000])
})

test('unlinked clicks do not grant other buttons', () => {
  assert.deepEqual(changePermissionSelection(tree, [], 340352, true, false), [340352])
  assert.deepEqual(changePermissionSelection(tree, [340352], track, true, false), [340352, track])
})

test('changing linkage mode never changes saved selection', async () => {
  const { vm } = editor()
  await vm.handleUpdate({ roleId: 22 })
  for (const checked of [false, true, true]) {
    vm.handleCheckedTreeConnect({ target: { checked } })
    assert.equal(vm.form.menuCheckStrictly, checked)
    assert.deepEqual(vm.getMenuAllCheckedKeys(), saved)
  }
  vm.$destroy()
})

test('half checked ancestors are visual only and never saved as extra grants', async () => {
  const { vm } = editor([340352, track])
  await vm.handleUpdate({ roleId: 22 })
  assert.deepEqual(vm.treeCheckedKeys.halfChecked, [340000])
  assert.deepEqual(vm.getMenuAllCheckedKeys(), [340352, track])
  vm.$destroy()
})

test('select all, clear all and reopening a different role do not retain stale selections', async () => {
  const { vm } = editor()
  await vm.handleUpdate({ roleId: 22 })
  vm.handleCheckedTreeNodeAll({ target: { checked: true } })
  assert.deepEqual(vm.getMenuAllCheckedKeys(), allPermissionKeys(tree))
  vm.handleCheckedTreeNodeAll({ target: { checked: false } })
  assert.deepEqual(vm.treeCheckedKeys, { checked: [], halfChecked: [] })
  await vm.handleUpdate({ roleId: 22 })
  assert.deepEqual(vm.getMenuAllCheckedKeys(), saved)
  vm.$destroy()
})

test('numeric and string IDs are normalized without duplicate grants', () => {
  assert.deepEqual(permissionTreeState(tree, ['340352', 340352, track]).checked, [340352, track])
})

test('temporarily hidden module grants survive edits to a visible button', async () => {
  const hiddenModule = 999999
  const hiddenGrant = 'action:999999:edit'
  const { vm, calls } = editor([...saved, hiddenModule, hiddenGrant])
  await vm.handleUpdate({ roleId: 22 })
  vm.onCheck({}, { node: { eventKey: track }, checked: true })
  vm.submitForm()
  await Promise.resolve()
  assert.deepEqual(calls[0].tenantModuleIds, [...saved, hiddenModule, hiddenGrant, track])
  vm.$destroy()
})
