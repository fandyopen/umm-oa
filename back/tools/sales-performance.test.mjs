import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import compiler from 'vue-template-compiler'

const source = fs.readFileSync(new URL('../../src/views/eos/crm/salesPerformance/index.vue', import.meta.url), 'utf8')
const component = compiler.parseComponent(source)
const page = new Function(component.script.content.replace(/^import .*$/gm, '').replace('export default', 'return'))()

test('clicking a sales warning resets pagination and locates only that person and warning type', () => {
  let scrolled = false
  const vm = {
    attention: [
      { ownerUserId: 47, type: 'No follow-up', customerId: 2 },
      { ownerUserId: 47, type: 'Follow-up overdue', customerId: 4 },
      { ownerUserId: 53, type: 'No follow-up', customerId: 5 },
      { ownerUserId: 47, type: 'Stagnant opportunity', opportunityId: 9 }
    ],
    attentionPage: 3,
    $nextTick: callback => callback(),
    $refs: { attentionSection: { scrollIntoView: () => { scrolled = true } } }
  }
  page.methods.locateAttention.call(vm, { userId: 47, name: '销售1' }, 'customer')
  assert.equal(vm.attentionPage, 1)
  assert.equal(vm.attentionOwnerName, '销售1')
  assert.equal(scrolled, true)
  assert.deepEqual(page.computed.filteredAttention.call(vm).map(row => row.customerId), [2, 4])
  page.methods.locateAttention.call(vm, { userId: 47, name: '销售1' }, 'opportunity')
  assert.deepEqual(page.computed.filteredAttention.call(vm).map(row => row.opportunityId), [9])
  page.methods.clearAttentionFilter.call(vm)
  assert.equal(page.computed.filteredAttention.call(vm).length, 4)
})

test('customer entry navigates to the exact customer and its followup tab', () => {
  let destination
  page.methods.openCustomer.call({ $router: { push: value => { destination = value } } }, { customerId: 4, customerName: '同名客户' })
  assert.deepEqual(destination, { path: '/crm/customers', query: { customerId: '4', tab: 'followups' } })
})

test('sales performance template compiles', () => {
  assert.deepEqual(compiler.compile(component.template.content).errors, [])
})
