import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import { createRequire } from 'node:module'
import compiler from 'vue-template-compiler'

const Vue = createRequire(import.meta.url)('vue')
const moment = createRequire(import.meta.url)('moment')
const { followupDisplayFields, approvalPayload, formatApprovalValue } = await import('data:text/javascript;base64,' + Buffer.from(fs.readFileSync(new URL('../../src/utils/workflowApproval.js', import.meta.url), 'utf8')).toString('base64'))
const helperSource = fs.readFileSync(new URL('../../src/utils/workflowActionSubmit.js', import.meta.url), 'utf8')
const { createWorkflowActionSubmitter, submitModuleWorkflowAction } = new Function(helperSource.replaceAll('export function', 'function') + '\nreturn { createWorkflowActionSubmitter, submitModuleWorkflowAction }')()
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

test('different records submit independently while each retry keeps its own identity', async () => {
  const sent = []
  const resolvers = []
  const submitter = createWorkflowActionSubmitter(input => {
    sent.push(input)
    return new Promise((resolve, reject) => resolvers.push({ resolve, reject }))
  })
  const firstInput = { moduleId: 12, actionCode: 'submit', payload: { recordId: 1 } }
  const secondInput = { ...firstInput, payload: { recordId: 2 } }
  const first = submitter.submit(firstInput)
  const second = submitter.submit(secondInput)
  assert.notEqual(first, second)
  assert.equal(submitter.submit(firstInput), first)
  await Promise.resolve()
  assert.deepEqual(sent.map(input => input.payload.recordId), [1, 2])
  assert.notEqual(sent[0].submissionKey, sent[1].submissionKey)
  const failed = assert.rejects(first, /网络失败/)
  resolvers[0].reject(new Error('网络失败'))
  resolvers[1].resolve({ data: { status: 'completed', recordId: 2 } })
  await failed
  assert.equal((await second).data.recordId, 2)
  const retry = submitter.submit(firstInput)
  await Promise.resolve()
  assert.equal(sent[2].submissionKey, sent[0].submissionKey)
  resolvers[2].resolve({ data: { status: 'pendingApproval', recordId: 1 } })
  assert.equal((await retry).data.recordId, 1)
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

test('configured module actions resolve the module, check its button permission, and reuse idempotent submission', async () => {
  let checks = 0, calls = 0, finish
  const component = {
    moduleContexts: { customer: { moduleId: 12 } },
    checkModuleAction: async input => { checks++; assert.deepEqual(input, { module: 'customer', action: 'track', label: '保存跟进' }); return true }
  }
  const execute = input => { calls++; finish = input; return new Promise(resolve => { component.resolve = resolve }) }
  const options = { primaryModule: 'customer', actionCode: 'track', label: '保存跟进', payload: { customerId: 7, content: '提交内容' } }
  const first = submitModuleWorkflowAction(component, execute, options)
  const second = submitModuleWorkflowAction(component, execute, options)
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(checks, 2)
  assert.equal(calls, 1)
  assert.equal(finish.moduleId, 12)
  assert.equal(finish.actionCode, 'track')
  assert.equal(finish.payload.content, '提交内容')
  assert.ok(finish.submissionKey)
  component.resolve({ data: { status: 'pendingApproval' } })
  assert.equal((await first).data.status, 'pendingApproval')
  assert.equal((await second).data.status, 'pendingApproval')
})

test('configured module actions stop after permission denial and reject unresolved contexts', async () => {
  let calls = 0
  const execute = async () => { calls++; return { data: { status: 'completed' } } }
  const component = { moduleContexts: { customer: { moduleId: 12 } }, checkModuleAction: async () => false }
  const options = { primaryModule: 'customer', actionCode: 'track', payload: {} }
  assert.equal(await submitModuleWorkflowAction(component, execute, options), null)
  component.checkModuleAction = async () => true
  component.moduleContexts.customer.moduleId = undefined
  await assert.rejects(submitModuleWorkflowAction(component, execute, { ...options, label: '保存跟进' }), /模块信息未就绪/)
  assert.equal(calls, 0)
})

function configEditor(getWorkflowActionApis = async () => ({ data: [{ id: 'POST /save/{id}', name: '保存', pathParameters: ['id'], hasBody: true }] })) {
  const events = []
  const vm = new Vue({ ...options('src/views/system/module/modules/WorkflowApiConfig.vue', { getWorkflowActionApis, followupDisplayFields, AutoComplete: {} }), propsData: { moduleId: 12 }, render: h => h('div') })
  vm.$on('input', value => events.push(JSON.parse(value)))
  return { vm, events }
}

test('empty API configuration reacts to the first selection and subsequent interface changes', async () => {
  const apis = [
    { id: 'POST /project/business', pathParameters: [], hasBody: true },
    { id: 'DELETE /project/business/{id}', pathParameters: ['id'], hasBody: false }
  ]
  const { vm, events } = configEditor(async () => ({ data: apis }))
  await Vue.nextTick()
  // Rendering the empty selector evaluates and caches this computed property.
  assert.equal(vm.selected, undefined)
  vm.selectEndpoint(apis[0].id)
  await Vue.nextTick()
  assert.equal(vm.selected.id, apis[0].id)
  assert.doesNotThrow(() => vm.validate())
  assert.equal(events.at(-1).endpoint, apis[0].id)
  vm.selectEndpoint(apis[1].id)
  await Vue.nextTick()
  assert.equal(vm.selected.id, apis[1].id)
  assert.deepEqual(events.at(-1).pathMapping, { id: 'id' })
  assert.doesNotThrow(() => vm.validate())
  vm.$destroy()
})

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
      moment: () => ({ format: () => '2026-11-10 10:00:00' }),
      withModuleContexts: () => ({ methods: {
        async submitConfiguredAction(actionCode, payload, label) {
          const module = Object.keys(this.moduleContexts)[0]
          if (!(await this.checkModuleAction({ module, action: actionCode, label }))) return null
          return createWorkflowActionSubmitter(execute).submit({ moduleId: 12, actionCode, payload })
        }
      } }), crmModules: { customer: {} }
    }), propsData: { customerId: 7, customerName: '客户甲' }, render: h => h('div')
  })
  vm.moduleContexts = { customer: { moduleId: 12 } }
  vm.checkModuleAction = async () => true
  vm.$message = Object.fromEntries(['success', 'error', 'warning'].map(type => [type, text => messages.push([type, text])]))
  vm.$on('ok', value => events.push(value))
  return { vm, messages, events }
}

test('followup uses the configured track action and distinguishes approval from completed writes', async () => {
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

test('approval presentation prefers submitted data and falls back to stored legacy forms without mutation', () => {
  const data = { title: '客户跟进：客户甲', content: '提交时内容', _workflowDisplay: [{ label: '沟通内容', value: '提交时内容' }] }
  const task = { data, form: { title: '客户档案审批', content: '其他内容' } }
  assert.equal(approvalPayload(task), data)
  assert.equal(approvalPayload(task).content, '提交时内容')
  assert.equal(approvalPayload({ data: {}, form: data }), data)
  assert.deepEqual(approvalPayload({}, { content: '旧申请内容' }), { content: '旧申请内容' })
  assert.deepEqual(approvalPayload({}), {})
  assert.equal(task.form.title, '客户档案审批')
})

test('approval values preserve zero, false, objects, and missing-field placeholders', () => {
  assert.equal(formatApprovalValue(0), '0')
  assert.equal(formatApprovalValue(false), '否')
  assert.equal(formatApprovalValue(null), '—')
  assert.equal(formatApprovalValue(undefined), '—')
  assert.equal(formatApprovalValue(''), '—')
  assert.equal(formatApprovalValue('原文\n下一行'), '原文\n下一行')
  assert.deepEqual(JSON.parse(formatApprovalValue({ amount: 0 })), { amount: 0 })
})

test('field suggestions fill labels without resetting custom labels or execution mappings', async () => {
  const { vm, events } = configEditor()
  await Vue.nextTick()
  assert.deepEqual(vm.advancedKeys, [])
  assert.ok(vm.$options.components.AAutoComplete, 'field suggestions register their input component')
  vm.config.endpoint = 'POST /project/merchant/{id}/followups'
  vm.config.pathMapping = { id: 'customerId' }
  vm.config.bodyMapping = { content: 'form.content' }
  vm.config.displayFields = [{ label: '', field: '' }]
  assert.ok(vm.displayOptions.some(field => field.value === 'nextPlan'))
  vm.setDisplayField(0, 'content')
  assert.equal(vm.config.displayFields[0].label, '沟通内容')
  vm.config.displayFields[0].label = '经理关注的内容'
  vm.setDisplayField(0, 'nextPlan')
  assert.equal(vm.config.displayFields[0].label, '经理关注的内容')
  vm.setDisplayField(0, 'custom.path')
  const saved = events.at(-1)
  assert.equal(saved.displayFields[0].field, 'custom.path')
  assert.equal(saved.pathMapping.id, 'customerId')
  assert.deepEqual(saved.bodyMapping, { content: 'form.content' })
  vm.$destroy()
})

test('invalid execution mappings expand advanced settings so validation errors remain visible', async () => {
  const { vm } = configEditor()
  await Vue.nextTick()
  vm.selectEndpoint('POST /save/{id}')
  vm.config.pathMapping.id = ''
  assert.throws(() => vm.validate(), /路径参数/)
  assert.deepEqual(vm.advancedKeys, ['api'])
  vm.$destroy()
})

test('processing opens the same business detail component as row details while retaining legacy approval support', () => {
  const BusDetail = {}, ViewVerify = {}
  const component = options('src/components/Activiti/index.vue', { busFlowMap: { workflowAction: { busDetailCompName: 'WorkflowActionTodoDetail' }, lowcode: { busDetailCompName: 'LowCodeTodoDetail' } }, handleProcessHistory() {}, busFlowState: {}, ViewVerify, BusDetail, UserSelect: {}, request() {} })
  for (const businessType of ['workflowAction', 'lowcode', 'legacy']) {
    let opened
    const task = { businessType, form: { content: '申请内容' }, formId: 17, formKey: 'track', taskId: 'task1' }
    component.methods._handleView.call({ todoItem: task, $createAntdDrawer: input => { opened = input } })
    assert.equal(opened.content.template, businessType === 'legacy' ? ViewVerify : BusDetail)
    assert.equal(opened.content.props.todoItem, task)
    if (businessType !== 'legacy') assert.equal(opened.content.props.row.content, '申请内容')
  }
})

test('shared business details use the frozen request title and show the task push time', () => {
  const component = options('src/views/eos/todoItem/todo/busDetail.vue', { moment, approvalPayload, ViewVerify: {}, UserSelect: {}, LowCodeTodoDetail: {}, WorkflowActionTodoDetail: {}, CrmTodoDetail: {} })
  const vm = new Vue({ ...component, propsData: { detailCompName: 'WorkflowActionTodoDetail', row: { title: '流程名' }, todoItem: { businessType: 'workflowAction', applyUserName: '申请人', data: { title: '客户跟进：客户甲' }, createTime: '2026-11-11 11:27:22' } }, render: h => h('div') })
  assert.equal(vm.detailRow.title, '客户跟进：客户甲')
  assert.equal(vm.pushTime, '2026-11-11 11:27:22')
  assert.equal(vm.isApplication, true)
  vm.$destroy()
})

test('configured approval fields keep their frozen values and legacy followups retain readable labels', () => {
  const component = options('src/views/workflow/todoDetail.vue', { SDescriptions: {}, followupDisplayFields, formatApprovalValue, HrApprovalDetail: {}, ContractDetail: {}, normalizeContractId() {} })
  const frozen = [{ label: '沟通内容', value: '旧申请的内容' }, { label: '下一步计划', value: '' }]
  const vm = new Vue({ ...component, propsData: { row: { _workflowApi: true, content: '业务字段', _workflowDisplay: frozen } }, render: h => h('div') })
  assert.deepEqual(vm.displayFields, frozen)
  assert.deepEqual(vm.formItems, [])
  vm.row = { _workflowApi: true, title: '旧申请', _workflowSecret: '内部数据', content: '历史内容', nextPlan: '继续沟通', flag: false }
  assert.equal(vm.formItems.find(field => field.prop === 'content').label, '沟通内容')
  assert.equal(vm.formItems.find(field => field.prop === 'nextPlan').label, '下一步计划')
  assert.equal(vm.formItems.find(field => field.prop === 'flag').formatter(false), '否')
  assert.ok(vm.formItems.every(field => !field.prop.startsWith('_') && field.prop !== 'title'))
  vm.row = { customerName: '旧客户', content: '更早的跟进申请' }
  assert.ok(vm.formItems.some(field => field.prop === 'content' && field.label === '沟通内容'))
  vm.$destroy()
})
