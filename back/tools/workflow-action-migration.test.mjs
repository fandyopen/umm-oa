import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import { parse } from '@babel/parser'
import compiler from 'vue-template-compiler'

const read = file => fs.readFileSync(new URL('../../' + file, import.meta.url), 'utf8')
function method(file, name, dependencies = {}) {
  const component = compiler.parseComponent(read(file))
  assert.deepEqual(compiler.compile(component.template.content).errors, [], file)
  const script = component.script.content
  const ast = parse(script, { sourceType: 'module' })
  const definition = ast.program.body.find(node => node.type === 'ExportDefaultDeclaration').declaration
  const methods = definition.properties.find(node => node.key.name === 'methods').value
  const node = methods.properties.find(node => node.key.name === name)
  return new Function(...Object.keys(dependencies), 'return ({' + script.slice(node.start, node.end) + '}).' + name)(...Object.values(dependencies))
}
const hrRoot = 'src/views/enterprise/hr/'
const financeFile = 'src/views/eos/crm/finance/modules/FinanceRecords.vue'
const contractFile = 'src/views/eos/crm/contract/modules/ContractDetail.vue'
const recruitmentFile = 'src/views/eos/recruitment/components/RecruitmentWorkspace.vue'
const memberFile = 'src/views/enterprise/components/memberElement/index.vue'
const fields = await import('data:text/javascript;base64,' + Buffer.from(read('src/views/eos/recruitment/shared.js')).toString('base64'))
const normalizeContractId = new Function('request', read('src/api/eos/crm/contracts.js').replace(/^import .*$/gm, '').replaceAll('export const ', 'const ') + ';return normalizeContractId')(() => assert.fail('unexpected request'))

for (const scenario of [
  { file: hrRoot + 'changes.vue', name: 'submit', row: { id: 7, revision: 3, nickName: '张三' }, action: 'submit', payload: { eventId: 7, revision: 3, title: '人事异动：张三' } },
  { file: hrRoot + 'employees.vue', name: 'submitSalary', row: { id: 8, revision: 4 }, action: 'salary_submit', payload: { salaryId: 8, revision: 4, title: '员工定薪 / 调薪审批' } },
  { file: hrRoot + 'PayrollWorkspace.vue', name: 'submit', action: 'submit', payload: { payrollId: 9, revision: 5, title: '2026-11工资审批' } },
  { file: financeFile, name: 'submitWorkflow', row: { id: 10, revision: 6 }, action: 'refund_submit', payload: { recordId: 10, revision: 6 } }
]) {
  test(scenario.name + ' preserves business identifiers and stops refresh after permission denial', async () => {
    let refreshed = 0
    const submit = method(scenario.file, scenario.name, { hr: async path => { assert.equal(path, '/employees/11'); return { data: { userId: 11 } } } })
    for (const response of [null, { data: { status: 'pendingApproval' } }, { data: { status: 'completed' } }]) {
      const sent = [], messages = []
      const vm = {
        detail: { id: 9, revision: 5, period: '2026-11' }, employee: { userId: 11 },
        submitConfiguredAction: async (...args) => { sent.push(args); return response },
        submitAction: () => 'refund_submit', needsApproval: () => true,
        load: () => { refreshed++ }, open: async id => assert.equal(id, 9),
        setEmployee: data => { assert.equal(data.userId, 11); refreshed++ },
        changed: async () => { refreshed++ }, $message: { success: text => messages.push(text) }
      }
      const before = refreshed
      await submit.call(vm, scenario.row)
      assert.equal(sent[0][0], scenario.action)
      assert.deepEqual(sent[0][1], scenario.payload)
      assert.equal(messages.length, response ? 1 : 0)
      assert.equal(refreshed - before, response ? 1 : 0)
    }
  })
}

test('member invite transfer and dismissal keep dialogs and selection when submission is denied', async () => {
  const calls = []
  const vm = {
    selectedRowKeys: [7], adjus_rData: 8, form: { nickName: '张三' }, countryCode: 86, rData: 9,
    isIMmodal: true, adjusDepVisible: true,
    submitConfiguredAction: async (...args) => { calls.push(args); return null },
    $refs: { ruleForm: { validate: done => done(true) }, table: { refresh: () => assert.fail('unexpected refresh') } },
    $message: { success: () => assert.fail('unexpected success') }
  }
  method(memberFile, 'onSubmit').call(vm)
  method(memberFile, 'adjusting').call(vm, 1)
  method(memberFile, 'mitosis').call(vm)
  await new Promise(resolve => setImmediate(resolve))
  assert.deepEqual(calls.map(call => call[0]), ['invite', 'transfer', 'dismiss'])
  assert.deepEqual(calls[0][1], { nickName: '张三', countryCode: 86, deptId: 9 })
  assert.deepEqual(calls[1][1], { deptId: 8, userIds: [7] })
  assert.deepEqual(calls[2][1], { userIds: [7] })
  assert.equal(vm.isIMmodal, true)
  assert.equal(vm.adjusDepVisible, true)
  assert.deepEqual(vm.selectedRowKeys, [7])
})

test('contract submit and signing use configured actions while receipts keep the draft API', async () => {
  const calls = []
  const run = method(contractFile, 'runAction', { normalizeContractId, contractAction: async (...args) => { calls.push(['api', ...args]); return { data: {} } } })
  for (const operation of ['submit', 'sign', 'receipts']) {
    const messages = [], events = []
    const actionForm = { revision: 3, signedDate: '2026-11-11' }
    const vm = {
      saving: false, operation, operationTitle: '提交合同操作', contractId: 7, contract: { name: '合同甲', customerName: '客户甲' }, actionForm,
      submitConfiguredAction: async (...args) => { calls.push(['configured', ...args]); return { data: { status: 'pendingApproval' } } },
      load: async () => events.push('load'), $emit: name => events.push(name), $message: { success: text => messages.push(text) }
    }
    await run.call(vm)
    const sent = calls.at(-1)
    if (operation === 'receipts') {
      assert.deepEqual(sent, ['api', 7, 'receipts', actionForm])
      assert.match(messages[0], /草稿已创建/)
    } else {
      assert.equal(sent[0], 'configured')
      assert.equal(sent[1], operation)
      assert.deepEqual(sent[2], { ...actionForm, contractId: 7, title: (operation === 'sign' ? '签署审批：' : '合同审批：') + '合同甲 - 客户甲' })
      assert.match(messages[0], /已提交/)
    }
    assert.equal(vm.operation, '')
    assert.equal(vm.saving, false)
    assert.deepEqual(events, ['load', 'changed'])
  }
})

test('contract permission denial network errors and invalid identifiers never report success', async () => {
  const run = method(contractFile, 'runAction', { normalizeContractId })
  for (const outcome of ['denied', 'network', 'invalid']) {
    let calls = 0
    const vm = {
      saving: false, operation: 'sign', contractId: outcome === 'invalid' ? 'bad' : 7, contract: {}, actionForm: { revision: 3 },
      submitConfiguredAction: async () => { calls++; if (outcome === 'network') throw new Error('网络失败'); return null },
      load: () => assert.fail('unexpected refresh'), $message: { success: () => assert.fail('unexpected success') }
    }
    if (outcome === 'denied') await run.call(vm)
    else await assert.rejects(run.call(vm), outcome === 'invalid' ? /合同编号无效/ : /网络失败/)
    assert.equal(calls, outcome === 'invalid' ? 0 : 1)
    assert.equal(vm.operation, 'sign')
    assert.equal(vm.saving, false)
  }
})

for (const [kind, action] of [['jobs', 'demand'], ['jobs', 'change'], ['talents', 'merge'], ['talents', 'handover']]) {
  test(kind + ' ' + action + ' submits the selected record through its owning module', async () => {
    let save
    const sent = []
    const run = method(recruitmentFile, 'runAction', { ...fields, recordPath: (kind, id) => '/' + kind + '/' + id, api: () => assert.fail('unexpected legacy approval API') })
    const vm = {
      kind, detail: { id: 99 }, options: { users: [], talents: [] }, allowedAction: async () => true,
      form: (title, items, initial, callback) => { save = callback },
      submitConfiguredAction: async (...args) => { sent.push(args); return { data: { status: 'pendingApproval' } } }
    }
    await run.call(vm, { key: action, permission: action, label: '申请' }, { id: 7, revision: 3 })
    vm.kind = kind === 'jobs' ? 'talents' : 'jobs'
    const body = { reason: '业务调整', ownerUserId: 8, targetId: 9 }
    await save(body)
    assert.deepEqual(sent, [[action, { ...(action === 'change' ? { revision: 3, reason: body.reason, fields: body } : { revision: 3, ...body }), entityId: 7, title: '招聘申请：' + action + ' #7' }, '申请', kind]])
  })
}

test('recruitment dispute handover preserves the dispute proposed owner and revision', async () => {
  let save
  const checks = [], calls = []
  const transfer = method(recruitmentFile, 'transferOwnership', fields)
  await transfer.call({
    detail: { id: 7, revision: 3 }, options: { users: [] }, allowedAction: async action => { checks.push(action); return true },
    form: (title, items, initial, callback) => { save = callback }, submitConfiguredAction: async (...args) => calls.push(args)
  }, { id: 12, proposedOwnerId: 8, proposedOwnerName: '李四' })
  await save({ collaborators: [9], reason: '归属调整' })
  assert.deepEqual(checks, ['dispute', 'handover'])
  assert.deepEqual(calls, [['handover', { collaborators: [9], reason: '归属调整', ownerUserId: 8, disputeId: 12, revision: 3, entityId: 7, title: '招聘申请：handover #7' }, '归属争议：申请转交', 'talents']])
})

test('recruitment denial or failure retains the form and entered contents without success feedback', async () => {
  const submit = method(recruitmentFile, 'submitForm')
  for (const outcome of [null, false, new Error('网络失败')]) {
    const body = { reason: '保留已填写内容' }
    const vm = {
      saving: false, formVisible: true, formSave: async input => { assert.equal(input, body); if (outcome instanceof Error) throw outcome; return outcome },
      $message: { success: () => assert.fail('unexpected success') }, load: () => assert.fail('unexpected refresh')
    }
    if (outcome instanceof Error) await assert.rejects(submit.call(vm, body), /网络失败/)
    else await submit.call(vm, body)
    assert.equal(vm.formVisible, true)
    assert.equal(vm.saving, false)
  }
})
