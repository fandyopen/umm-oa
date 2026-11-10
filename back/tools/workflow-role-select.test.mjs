import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import compiler from 'vue-template-compiler'

const require = createRequire(import.meta.url)
const Vue = require('vue')
const file = new URL('../../src/views/lowCode/components/Process/index.vue', import.meta.url)
const parsed = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
const template = compiler.compile(parsed.template.content)
assert.deepEqual(template.errors, [])

function designer(conf = {}, getWorkflowRoles = async () => ({ data: [
  { roleId: 1, roleName: '财务主管', roleKey: 'finance_manager' },
  { roleId: 2, roleName: '销售经理', roleKey: 'sales_manager' }
] })) {
  const source = parsed.script.content.replace(/^import .*$/gm, '').replace('export default', 'return')
  const options = new Function('getWorkflowRoles', source)(getWorkflowRoles)
  const vm = new Vue({
    ...options, propsData: { conf },
    render: new Function(template.render),
    staticRenderFns: template.staticRenderFns.map(fn => new Function(fn))
  })
  vm.selectedId = vm.nodes.find(node => node.type === 'approval')?.id
  return vm
}

const option = value => ({ componentOptions: { propsData: { value } } })

test('role selector searches partial names and case-insensitive role keys', async () => {
  const vm = designer()
  await vm.loadRoles()
  assert.equal(vm.filterRole('财务', option('finance_manager')), true)
  assert.equal(vm.filterRole(' MANAGER ', option('sales_manager')), true)
  assert.equal(vm.filterRole('财务', option('sales_manager')), false)
  assert.equal(vm.roleOptions.length, 2)
  vm.$destroy()
})

test('selected roles survive process save and reload using the role key', async () => {
  const vm = designer()
  await vm.loadRoles()
  vm.selectRole('finance_manager')
  const saved = JSON.parse(JSON.stringify((await vm.getData()).formData))
  const restored = designer(saved)
  await restored.loadRoles()
  assert.equal(restored.selectedNode.approver, 'finance_manager')
  assert.equal(restored.approverValue, 'finance_manager')
  assert.equal(restored.legacyApprover, '')
  assert.equal(saved.edges.length, saved.nodes.length - 1)
  restored.selectRole(undefined)
  assert.equal(restored.approverValue, undefined)
  vm.$destroy()
  restored.$destroy()
})

test('legacy names display matching roles while old user or supervisor settings remain intact', async () => {
  for (const approver of ['财务主管', '部门负责人', '17600000001']) {
    const vm = designer({ nodes: [
      { id: 'start', type: 'start' },
      { id: 'review', type: 'approval', approver },
      { id: 'end', type: 'end' }
    ] })
    await vm.loadRoles()
    assert.equal(vm.approverValue, approver === '财务主管' ? 'finance_manager' : approver)
    assert.equal((await vm.getData()).formData.nodes[1].approver, approver)
    vm.selectRole('sales_manager')
    assert.equal(vm.selectedNode.approver, 'sales_manager')
    vm.$destroy()
  }
})

test('unavailable roles are excluded and loading failures can be retried without losing the configuration', async () => {
  let attempts = 0
  const vm = designer({}, async () => {
    if (++attempts === 1) throw new Error('network unavailable')
    return { data: [
      { roleName: '销售经理', roleKey: 'sales_manager' },
      { roleName: '已停用', roleKey: 'disabled', status: '1' },
      { roleName: '已删除', roleKey: 'deleted', delFlag: '2' }
    ] }
  })
  vm.selectRole('sales_manager')
  await vm.loadRoles()
  assert.equal(vm.rolesError, true)
  assert.equal(vm.rolesLoading, false)
  assert.equal(vm.selectedNode.approver, 'sales_manager')
  await vm.loadRoles()
  assert.equal(vm.rolesError, false)
  assert.deepEqual(vm.roleOptions.map(role => role.value), ['sales_manager'])
  vm.$destroy()
})
