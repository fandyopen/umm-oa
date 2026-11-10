import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import { createRequire } from 'node:module'
import compiler from 'vue-template-compiler'

const Vue = createRequire(import.meta.url)('vue')
const helperSource = fs.readFileSync(new URL('../../src/utils/workflowActionSubmit.js', import.meta.url), 'utf8')
const createWorkflowActionSubmitter = new Function(helperSource.replace('export function', 'function') + '\nreturn createWorkflowActionSubmitter')()
const options = (file, dependencies) => {
  const source = fs.readFileSync(new URL('../../' + file, import.meta.url), 'utf8')
  const parsed = compiler.parseComponent(source)
  assert.deepEqual(compiler.compile(parsed.template.content).errors, [])
  return new Function(...Object.keys(dependencies), parsed.script.content.replace(/^import .*$/gm, '').replace('export default', 'return'))(...Object.values(dependencies))
}

test('shared submitter freezes payload and coalesces duplicate clicks on the existing gateway', async () => {
  let resolve, sent
  const submitter = createWorkflowActionSubmitter(input => { sent = input; return new Promise(done => { resolve = done }) })
  const input = { moduleId: 12, actionCode: 'save', payload: { content: '原内容' } }
  const first = submitter.submit(input), second = submitter.submit(input)
  input.payload.content = '修改中'
  assert.equal(first, second)
  await Promise.resolve()
  assert.equal(sent.payload.content, '原内容')
  assert.ok(sent.submissionKey.length >= 8)
  resolve({ data: { status: 'pendingApproval', requestId: 5 } })
  assert.equal((await first).data.requestId, 5)
})

test('network retries retain their submission identity and changed contents use a new identity', async () => {
  const keys = []
  let attempt = 0
  const submitter = createWorkflowActionSubmitter(async input => {
    keys.push(input.submissionKey)
    if (++attempt < 3) throw new Error('网络失败')
    return { data: { status: 'completed' } }
  })
  const input = { moduleId: 12, actionCode: 'save', payload: { content: '内容' } }
  await assert.rejects(submitter.submit(input), /网络失败/)
  await assert.rejects(submitter.submit(input), /网络失败/)
  await submitter.submit({ ...input, payload: { content: '变更内容' } })
  assert.equal(keys[0], keys[1])
  assert.notEqual(keys[1], keys[2])
})

test('successful submissions reset their key and unexpected results do not report success', async () => {
  const keys = []
  const submitter = createWorkflowActionSubmitter(async input => { keys.push(input.submissionKey); return { data: { status: 'completed' } } })
  const input = { moduleId: 12, actionCode: 'save', payload: {} }
  await submitter.submit(input)
  await submitter.submit(input)
  assert.notEqual(keys[0], keys[1])
  await assert.rejects(createWorkflowActionSubmitter(async () => ({ data: {} })).submit(input), /结果异常/)
})

function configEditor(getWorkflowActionApis = async () => ({ data: [{ id: 'POST /save/{id}', name: '保存', pathParameters: ['id'], hasBody: true }] })) {
  const events = []
  const vm = new Vue({ ...options('src/views/system/module/modules/WorkflowApiConfig.vue', { getWorkflowActionApis }), propsData: { moduleId: 12 }, render: h => h('div') })
  vm.$on('input', value => events.push(JSON.parse(value)))
  return { vm, events }
}

test('generic configuration loads module APIs and saves path, body and approval display mappings', async () => {
  const { vm, events } = configEditor()
  await Vue.nextTick()
  vm.selectEndpoint('POST /save/{id}')
  vm.setPath('id', 'record.id')
  vm.bodyRows = [{ target: 'content', source: 'form.content' }]
  vm.config.displayFields = [{ field: 'form.content', label: '沟通内容' }]
  vm.validate()
  const saved = events.at(-1)
  assert.equal(saved.pathMapping.id, 'record.id')
  assert.equal(saved.bodyMapping.content, 'form.content')
  assert.equal(saved.displayFields[0].label, '沟通内容')
  vm.bodyRows.push({ target: 'content', source: 'other' })
  assert.throws(() => vm.validate(), /不能重复/)
  vm.$destroy()
})

test('API directory failures require retry and do not clear the saved configuration', async () => {
  let fail = true
  const { vm } = configEditor(async () => { if (fail) throw new Error('失败'); return { data: [] } })
  await Vue.nextTick()
  vm.config.endpoint = '原接口'
  assert.equal(vm.loadError, true)
  assert.throws(() => vm.validate(), /加载/)
  fail = false
  await vm.loadApis()
  assert.equal(vm.config.endpoint, '原接口')
  assert.equal(vm.loadError, false)
  vm.$destroy()
})

function followup(execute) {
  const messages = [], events = []
  const vm = new Vue({
    ...options('src/views/eos/crm/customer/modules/FollowupForm.vue', {
      moment: () => ({ format: () => '2026-11-10 10:00:00' }), executeWorkflowAction: execute,
      createWorkflowActionSubmitter, withModuleContexts: () => ({}), crmModules: { customer: {} }
    }), propsData: { customerId: 7, customerName: '客户甲' }, render: h => h('div')
  })
  vm.moduleContexts = { customer: { moduleId: 12 } }
  vm.checkModuleAction = async () => true
  vm.$message = Object.fromEntries(['success', 'error', 'warning'].map(type => [type, text => messages.push([type, text])]))
  vm.$on('ok', value => events.push(value))
  return { vm, messages, events }
}

test('followup submits through track only when saving and distinguishes approval from completed writes', async () => {
  const sent = []
  const { vm, messages, events } = followup(async input => { sent.push(input); return { data: { status: 'pendingApproval' } } })
  assert.equal(sent.length, 0)
  vm.form.content = '沟通记录'
  await vm.submit()
  assert.equal(sent[0].actionCode, 'track')
  assert.equal(sent[0].payload.customerId, 7)
  assert.equal(sent[0].moduleId, 12)
  assert.match(messages[0][1], /提交审批/)
  assert.equal(events[0].status, 'pendingApproval')
  vm.$destroy()
  const direct = followup(async () => ({ data: { status: 'completed' } }))
  direct.vm.form.content = '直接保存'
  await direct.vm.submit()
  assert.equal(direct.messages[0][1], '跟进记录已保存')
  direct.vm.$destroy()
})

test('form errors and permission denials retain contents and never close or report success', async () => {
  let calls = 0
  const { vm, messages, events } = followup(async () => { calls++; throw new Error('业务失败') })
  await vm.submit()
  assert.equal(calls, 0)
  vm.form.content = '保留内容'
  vm.checkModuleAction = async () => false
  await vm.submit()
  assert.equal(calls, 0)
  vm.checkModuleAction = async () => true
  await vm.submit()
  assert.equal(vm.form.content, '保留内容')
  assert.equal(events.length, 0)
  assert.equal(vm.saving, false)
  assert.equal(messages.at(-1)[0], 'error')
  vm.$destroy()
})
