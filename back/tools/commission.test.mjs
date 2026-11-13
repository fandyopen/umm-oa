import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import compiler from 'vue-template-compiler'
const Vue = createRequire(import.meta.url)('vue')
const read = file => fs.readFileSync(new URL('../../' + file, import.meta.url), 'utf8')
const { createWorkflowActionSubmitter } = await import('data:text/javascript;base64,' + Buffer.from(read('src/utils/workflowActionSubmit.js')).toString('base64'))
function component(api = async () => ({ data: {} }), execute = async () => ({ data: { status: 'pendingApproval' } })) {
  const parsed = compiler.parseComponent(read('src/views/eos/crm/finance/commission.vue'))
  const compiled = compiler.compile(parsed.template.content)
  assert.deepEqual(compiled.errors, [])
  const options = new Function('withModuleContexts', 'commission', 'executeWorkflowAction', 'createWorkflowActionSubmitter', 'money', 'XLSX', parsed.script.content.replace(/^import .*$/gm, '').replace('export default', 'return'))(() => ({ methods: { async submitConfiguredAction(actionCode, payload, label) {
    if (!(await this.checkModuleAction({ module: 'commission', action: actionCode, label }))) return null
    this._submitter ||= createWorkflowActionSubmitter(execute)
    return this._submitter.submit({ moduleId: this.moduleContexts.commission.moduleId, actionCode, payload })
  } } }), api, execute, createWorkflowActionSubmitter, String, {})
  const vm = new Vue(options)
  const messages = []
  vm.$message = { success: message => messages.push(message) }
  vm.$route = { query: {}, path: '/finance/commission' }
  vm.checkModuleAction = async () => true
  vm.showModuleAction = () => true
  vm.moduleContexts = { commission: { moduleId: 341410 } }
  return { vm, messages }
}
test('commission submission uses the shared idempotent workflow result and retains the drawer', async () => {
  const submitted = []
  const detail = { id: 4, revision: 0, period: '2026-11' }
  const { vm, messages } = component(async path => ({ data: path === '/batches/4' ? { ...detail, status: 'approving' } : path === '/options' ? {} : [] }), async body => { submitted.push(body); return { data: { status: 'pendingApproval' } } })
  vm.detail = detail
  await vm.submitBatch()
  assert.equal(submitted.length, 1)
  assert.equal(submitted[0].actionCode, 'submit')
  assert.equal(submitted[0].moduleId, 341410)
  assert.equal(submitted[0].payload.batchId, 4)
  assert.ok(submitted[0].submissionKey)
  assert.equal(vm.detail.status, 'approving')
  assert.ok(messages.some(message => message.includes('已进入审批')))
  assert.equal(vm.busy, false)
  vm.$destroy()
})
test('confirm saves current edited attribution before freezing the new revision', async () => {
  const calls = []
  const attribution = { id: 7, revision: 3, reason: '协作核对', participants: [{ userId: 8, side: 'delivery', share: 100 }] }
  const { vm } = component(async (path, method, body) => {
    calls.push({ path, method, body: body && JSON.parse(JSON.stringify(body)) })
    if (path === '/applications/9') return { data: { application: { id: 9 }, attribution: { ...attribution, revision: 4 } } }
    return { data: path === '/options' ? {} : [] }
  })
  vm.currentApplication = { id: 9 }
  vm.attributionForm = attribution
  await vm.confirmAttribution(true)
  assert.equal(calls[0].path, '/applications/9')
  assert.equal(calls[0].method, 'post')
  assert.equal(calls[0].body.participants[0].userId, 8)
  assert.equal(calls[1].path, '/attributions/7/confirm')
  assert.equal(calls[1].body.revision, 4)
  vm.$destroy()
})
test('a calculation failure does not hide editable business records', async () => {
  const { vm } = component(async path => {
    if (path === '/overview') throw new Error('负向流水尚未关联原到账')
    return { data: path === '/options' ? { applications: [{ id: 1 }], receipts: [{ id: 2 }] } : [{ id: 3 }] }
  })
  await vm.load()
  assert.equal(vm.options.applications[0].id, 1)
  assert.equal(vm.options.receipts[0].id, 2)
  assert.equal(vm.batches[0].id, 3)
  assert.match(vm.calculationError, /原到账/)
  assert.equal(vm.loading, false)
  vm.$destroy()
})
test('allocation dropdown only offers confirmed outcomes under the same contract and term', () => {
  const { vm } = component()
  vm.allocationForm = { receipt: { contractId: 2, termKey: 'fee' }, rows: [] }
  vm.options = { applications: [
    { attributionId: 1, attributionStatus: 'confirmed', contractId: 2, guaranteeTermKey: 'fee' },
    { attributionId: 2, attributionStatus: 'draft', contractId: 2, guaranteeTermKey: 'fee' },
    { attributionId: 3, attributionStatus: 'confirmed', contractId: 3, guaranteeTermKey: 'fee' },
    { attributionId: 4, attributionStatus: 'confirmed', contractId: 2, guaranteeTermKey: 'other' }
  ] }
  assert.deepEqual(vm.allocationCandidates.map(row => row.attributionId), [1])
  vm.$destroy()
})


test('commission permission denial preserves the batch and does not report or submit approval', async () => {
  const submitted = []
  const { vm, messages } = component(undefined, async body => { submitted.push(body) })
  vm.detail = { id: 5, revision: 3, period: '2026-11', status: 'draft' }
  vm.checkModuleAction = async () => false
  await vm.submitBatch()
  assert.equal(submitted.length, 0)
  assert.equal(messages.length, 0)
  assert.equal(vm.detail.status, 'draft')
  assert.equal(vm.busy, false)
  vm.$destroy()
})
