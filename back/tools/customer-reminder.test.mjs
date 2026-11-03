import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import { parse } from '@babel/parser'
import compiler from 'vue-template-compiler'

const read = file => fs.readFileSync(new URL('../../' + file, import.meta.url), 'utf8')
const source = read('src/views/eos/todoItem/customerReminder.js')
  .replace(/^import .*$/gm, '')
  .replace('export const ', 'const ')
  .replace('export default', 'return')
const reminder = new Function('withModuleContexts', 'crmModules', source)(() => ({}), { customer: {} })
const type = 'crmCustomerFollowup'

function method(file, block, name) {
  const script = compiler.parseComponent(read(file)).script.content
  const definition = parse(script, { sourceType: 'module', plugins: ['jsx'] }).program.body.find(node => node.type === 'ExportDefaultDeclaration').declaration
  const container = definition.properties.find(node => node.key.name === block).value
  const node = container.properties.find(node => node.key.name === name || node.key.value === name)
  return new Function('CUSTOMER_FOLLOWUP_TODO', 'return ({' + script.slice(node.start, node.end) + '})')(type)[name]
}

function navigation(allowed = true) {
  const destinations = [], events = [], warnings = [], checks = []
  const vm = {
    $route: { path: '/todoItem/todo', query: {} },
    $router: { push: async value => destinations.push(value) },
    $message: { warning: value => warnings.push(value) },
    $emit: value => events.push(value),
    checkModuleAction: async value => { checks.push(value); return allowed }
  }
  return { vm, destinations, events, warnings, checks }
}

test('customer reminder opens a filtered customer list after checking module access', async () => {
  const { vm, destinations, events, checks } = navigation()
  await reminder.methods.openCustomerReminder.call(vm, { form: { customerId: 782 } })
  assert.deepEqual(destinations, [{ path: '/crm/customers', query: { customerId: '782', source: 'followupReminder' } }])
  assert.equal(checks[0].module, 'customer')
  assert.deepEqual(events, ['read'])
})

test('denied customer access does not navigate or mark the reminder handled', async () => {
  const { vm, destinations, events } = navigation(false)
  await reminder.methods.openCustomerReminder.call(vm, { businessId: 782 })
  assert.deepEqual(destinations, [])
  assert.deepEqual(events, [])
})

test('invalid customer references do not invoke navigation', async () => {
  for (const customerId of [null, -1, 'bad', '1.5', Number.MAX_SAFE_INTEGER + 1]) {
    const { vm, destinations, warnings } = navigation()
    await reminder.methods.openCustomerReminder.call(vm, { form: { customerId } })
    assert.deepEqual(destinations, [])
    assert.equal(warnings.length, 1)
  }
})

for (const file of ['todo/todo.vue', 'done/done.vue']) {
  test(file + ' row clicks use customer navigation without opening approval drawers', async () => {
    let opened = 0
    const vm = { openCustomerReminder: async row => { assert.equal(row.businessId, 782); opened++ } }
    await method('src/views/eos/todoItem/' + file, 'methods', 'handleDetail').call(vm, { businessType: type, businessId: 782 })
    assert.equal(opened, 1)
  })
}

test('opening the customer reminder filters the list and avoids an automatic detail drawer', () => {
  const watcher = method('src/views/eos/crm/customer/index.vue', 'watch', '$route.query.customerId')
  let refreshed = 0
  const vm = {
    activePool: 'public', queryParam: { name: 'old' },
    $route: { query: { source: 'followupReminder' } },
    $refs: { table: { refresh: reset => { assert.equal(reset, true); refreshed++ } } },
    $nextTick: fn => fn(),
    handleDetail: () => assert.fail('should enter customer list')
  }
  watcher.handler.call(vm, '782')
  assert.equal(vm.activePool, 'mine')
  assert.deepEqual(vm.queryParam, { customerId: 782 })
  assert.equal(refreshed, 1)
  watcher.handler.call(vm, undefined)
  assert.deepEqual(vm.queryParam, {})
})

test('customer reminders are registered and do not render generic approval buttons', () => {
  const todo = compiler.parseComponent(read('src/views/eos/todoItem/todo/todo.vue')).template.content
  assert.match(todo, /item.businessType === 'crmCustomerFollowup'/)
  assert.ok(todo.includes('@click.stop="openCustomerReminder(item)"'))
  assert.match(read('src/views/eos/config/busUtil.js'), /crmCustomerFollowup:/)
  const detail = compiler.parseComponent(read('src/views/eos/todoItem/todo/busDetail.vue')).template.content
  assert.ok(detail.includes("!['crmRisk', 'crmContractApproval', 'crmCustomerFollowup']"))
})
