import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import compiler from 'vue-template-compiler'

const require = createRequire(import.meta.url)
const Vue = require('vue')
const source = fs.readFileSync(new URL('../../src/views/eos/crm/contract/modules/ContractApprovalButton.vue', import.meta.url), 'utf8')
const parsed = compiler.parseComponent(source)
function button(fetchTask) {
  const drawers = [], events = [], errors = []
  const BusDetail = {}
  const options = new Function('getContractApprovalTask', 'loadBusDetail', parsed.script.content
    .replace(/^import .*$/gm, '')
    .replace("import('/src/views/eos/todoItem/todo/busDetail.vue')", 'loadBusDetail()')
    .replace('export default', 'return'))(fetchTask, async () => ({ default: BusDetail }))
  const vm = { ...options.data(), record: { id: 3, name: '后来修改的名称', approvalTask: { taskId: 'old-task' } },
    $createAntdDrawer: value => drawers.push(value), $emit: (...args) => events.push(args), $message: { error: text => errors.push(text) } }
  Object.assign(vm, options.methods)
  return { vm, options, drawers, events, errors, BusDetail }
}

test('entry visibility uses the server task and the shared approval component renders', () => {
  assert.deepEqual(compiler.compile(parsed.template.content).errors, [])
  const { options } = button(async () => ({}))
  const render = compiler.compileToFunctions(parsed.template.content)
  const vm = new Vue({ ...options, ...render, propsData: { record: { id: 3, approvalTask: {} } } })
  assert.equal(vm._render().isComment, true)
  vm.record = { id: 3, approvalTask: { taskId: 'current-task' } }
  assert.equal(vm._render().tag, 'a-button')
  vm.$destroy()
})

test('opening re-fetches the exact task and uses its frozen application in the existing approval drawer', async () => {
  let release, calls = 0
  const { vm, drawers, events, BusDetail } = button(id => { assert.equal(id, 3); calls++; return new Promise(resolve => { release = resolve }) })
  const first = vm.open()
  await vm.open()
  assert.equal(calls, 1)
  const task = { type: 'todo', taskId: 'current-task', instanceId: 'instance', businessType: 'workflowAction', formId: 22, data: { contractId: 3, name: '申请时冻结的名称' } }
  release({ data: task })
  await first
  assert.equal(drawers.length, 1)
  assert.equal(drawers[0].content.template, BusDetail)
  assert.equal(drawers[0].content.props.todoItem, task)
  assert.equal(drawers[0].content.props.row.name, '申请时冻结的名称')
  assert.equal(drawers[0].content.props.detailCompName, 'WorkflowActionTodoDetail')
  assert.equal(drawers[0].content.props.isOnlyShowProcess, false)
  drawers[0].content.on.getList()
  assert.deepEqual(events, [['changed']])
  assert.equal(vm.opening, false)
})

test('a stale or forbidden task does not open an approval form and refreshes the originating contract', async () => {
  for (const fetchTask of [async () => { throw new Error('当前任务已处理或不属于您') }, async () => ({ data: { type: 'done', taskId: 'finished-task' } })]) {
    const { vm, drawers, events, errors } = button(fetchTask)
    await vm.open()
    assert.equal(drawers.length, 0)
    assert.deepEqual(events, [['changed']])
    assert.equal(errors.length, 1)
    assert.equal(vm.opening, false)
  }
})
