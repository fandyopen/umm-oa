import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import compiler from 'vue-template-compiler'

const read = file => compiler.parseComponent(fs.readFileSync(new URL('../../src/' + file, import.meta.url), 'utf8'))
const verify = read('components/Activiti/ViewVerify/index.vue')

function approval(delegateRequest, personalRequest = async () => {}) {
  const requests = [], events = [], errors = []
  const options = new Function('decideApproval', 'completeTask', 'rejectTask', 'finishTask', 'busFlowState', 'ProcessHistory',
    verify.script.content.replace(/^import .*$/gm, '').replace('export default', 'return'))(
    body => { requests.push(body); return delegateRequest(body) }, personalRequest, personalRequest, personalRequest, {}, {})
  const vm = { ...options.data(), ...options.methods,
    todoItem: { approvalInbox: true, taskId: 'task-1', instanceId: 'instance-1', businessType: 'workflowAction', formId: 3 },
    isEmpty: value => value == null || String(value).trim() === '',
    $emit: (...args) => events.push(args), $message: { error: text => errors.push(text), success: () => {} } }
  return { vm, requests, events, errors }
}

test('approval inbox, audit detail and shared decision templates compile', () => {
  for (const file of ['views/eos/todoItem/approvals/index.vue', 'views/eos/todoItem/approvals/ApprovalDetail.vue', 'components/Activiti/ViewVerify/index.vue']) {
    assert.deepEqual(compiler.compile(read(file).template.content).errors, [], file)
  }
})

test('inbox decision submits task, decision and comment without impersonating an assignee', async () => {
  const { vm, requests, events } = approval(async () => {})
  vm.comment = '核对无误'
  await vm.complete(true)
  assert.deepEqual(requests, [{ taskId: 'task-1', pass: true, comment: '核对无误' }])
  assert.deepEqual(events, [['getList'], ['ok'], ['close']])
  assert.equal(vm.submitting, false)
})

test('rejection requires a reason and failure keeps the drawer and entered opinion', async () => {
  const { vm, requests, events, errors } = approval(async () => { throw new Error('任务已变更') })
  vm.handleRejectTask()
  assert.equal(requests.length, 0)
  assert.equal(errors.length, 1)
  vm.comment = '需要补充凭证'
  await assert.rejects(vm.handleRejectTask(), /任务已变更/)
  assert.deepEqual(requests, [{ taskId: 'task-1', pass: false, comment: '需要补充凭证' }])
  assert.deepEqual(events, [])
  assert.equal(vm.comment, '需要补充凭证')
  assert.equal(vm.submitting, false)
})

test('personal approvals continue to use the original completion API', async () => {
  const personal = []
  const { vm, requests } = approval(async () => { throw new Error('不应调用代审批') }, async body => personal.push(body))
  vm.todoItem.approvalInbox = false
  await vm.complete(true)
  assert.equal(requests.length, 0)
  assert.equal(personal[0].instanceId, 'instance-1')
  assert.equal(JSON.parse(personal[0].variablesStr).pass, true)
})
