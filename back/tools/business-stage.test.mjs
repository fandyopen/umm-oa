import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import compiler from 'vue-template-compiler'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const require = createRequire(import.meta.url)
const Vue = require('vue')
const moment = require('moment')
const script = compiler.parseComponent(fs.readFileSync(path.join(root, 'src/views/eos/business/modules/BusinessStageForm.vue'), 'utf8')).script.content
const componentFactory = new Function(
  'moment',
  'storage',
  'ACCESS_TOKEN',
  'getBusiness',
  'addBusinessStage',
  'previewDoc',
  script
    .replace(/^import .*$/gm, '')
    .replace('import.meta.env.VITE_APP_BASE_API', "'/api'")
    .replace('export default', 'return')
)

function createForm(data = { stage: '初步接洽' }, save = async () => ({ code: 200 })) {
  const payloads = [],
    events = [],
    messages = []
  const component = componentFactory(
    moment,
    { get: () => '' },
    'token',
    async () => ({ data }),
    async payload => {
      payloads.push(payload)
      return await save(payload)
    },
    () => {}
  )
  const vm = new Vue({ ...component, propsData: { formId: 901999, businessCode: 'PREVIEW-OPPORTUNITY' } })
  vm.$message = { success: message => messages.push(message), warning: message => messages.push(message) }
  vm.$on('close', () => events.push('close'))
  vm.$on('saveSuccess', value => events.push(value))
  vm.$refs.form = { validate: done => done(!!vm.formData.remark.trim()), clearValidate() {} }
  return { vm, payloads, events, messages }
}

for (const [current, next] of [
  ['初步接洽', '需求确认'],
  ['需求确认', '方案报价'],
  ['方案报价', '商务谈判'],
  ['商务谈判', '赢单']
]) {
  test('defaults to the next stage after ' + current, async () => {
    const { vm } = createForm({ stage: current })
    await vm.loadBusiness()
    assert.equal(vm.currentStage, current)
    assert.equal(vm.formData.state, next)
    assert.equal(vm.stepStatus(vm.currentIndex), 'finish')
    assert.equal(vm.stepStatus(vm.selectedIndex), 'process')
    vm.$destroy()
  })
}

test('current opportunity state takes precedence over an outdated historical entry', async () => {
  const { vm } = createForm({ stage: '方案报价', sealBusinessStages: [{ state: '需求确认' }] })
  await vm.loadBusiness()
  assert.equal(vm.formData.state, '商务谈判')
  vm.$destroy()
})

test('supports businessState and historical fallbacks when stage is absent', async () => {
  for (const data of [{ businessState: '需求确认' }, { sealBusinessStages: [{ state: '初步接洽' }, { state: '需求确认' }] }]) {
    const { vm } = createForm(data)
    await vm.loadBusiness()
    assert.equal(vm.formData.state, '方案报价')
    vm.$destroy()
  }
})

test('completed stages can be viewed without rolling back and future stages cannot be skipped', async () => {
  const { vm } = createForm({ stage: '需求确认' })
  await vm.loadBusiness()
  assert.deepEqual(
    vm.stages.map((_, index) => vm.stepStatus(index)),
    ['finish', 'finish', 'process', 'wait', 'wait', 'wait']
  )
  vm.handleStepChange(0)
  assert.equal(vm.viewingStage, '初步接洽')
  assert.equal(vm.viewingHistory, true)
  assert.equal(vm.currentStage, '需求确认')
  assert.equal(vm.formData.state, '方案报价')
  vm.handleStepChange(3)
  assert.equal(vm.viewingStage, '初步接洽')
  assert.equal(vm.formData.state, '方案报价')
  assert.equal(vm.stepStatus(2), 'process')
  vm.returnToNextStage()
  assert.equal(vm.viewingStage, '方案报价')
  vm.$destroy()
})

test('successful confirmation persists the selected stage, selects the next, and leaves the modal open', async () => {
  const { vm, payloads, events } = createForm({ stage: '需求确认' })
  await vm.loadBusiness()
  vm.formData.remark = '完成方案报价'
  vm.formData.recordTime = moment('2026-11-03')
  vm.handleUploadChange({ fileList: [{ name: '方案.pdf', status: 'done', response: { code: 200, fileName: '/profile/plan.pdf' } }] })
  assert.equal(await vm.handleSubmit(), true)
  assert.equal(payloads.length, 1)
  assert.equal(payloads[0].state, '方案报价')
  assert.equal(payloads[0].recordTime, '2026-11-03 00:00:00')
  assert.equal(JSON.parse(payloads[0].attachment)[0].name, '方案.pdf')
  assert.equal(vm.currentStage, '方案报价')
  assert.equal(vm.formData.state, '商务谈判')
  assert.equal(vm.formData.remark, '')
  assert.deepEqual(vm.fileList, [])
  assert.deepEqual(events, [{ stage: '方案报价', nextStage: '商务谈判' }])
  assert.equal(vm.saving, false)
  vm.$destroy()
})

test('continuing in the same modal requires a new activity and advances again only after confirmation', async () => {
  const { vm, payloads, events } = createForm({ stage: '初步接洽' })
  await vm.loadBusiness()
  vm.formData.remark = '已确认需求'
  await vm.handleSubmit()
  assert.equal(vm.currentStage, '需求确认')
  assert.equal(vm.formData.state, '方案报价')
  assert.equal(await vm.handleSubmit(), false)
  assert.equal(payloads.length, 1)
  vm.formData.remark = '已发送报价'
  await vm.handleSubmit()
  assert.deepEqual(
    payloads.map(item => item.state),
    ['需求确认', '方案报价']
  )
  assert.equal(vm.formData.state, '商务谈判')
  assert.ok(!events.includes('close'))
  vm.$destroy()
})

test('a failed save preserves the activity, attachments, selection and current persisted stage', async () => {
  const { vm, events } = createForm({ stage: '需求确认' }, async () => {
    throw new Error('offline')
  })
  await vm.loadBusiness()
  vm.formData.remark = '待保存内容'
  await assert.rejects(vm.handleSubmit(), /offline/)
  assert.equal(vm.formData.remark, '待保存内容')
  assert.equal(vm.formData.state, '方案报价')
  assert.equal(vm.currentStage, '需求确认')
  assert.deepEqual(events, [])
  assert.equal(vm.saving, false)
  vm.$destroy()
})

test('pending save rejects duplicate submits, stage changes and closing from the form', async () => {
  let release
  const { vm, payloads, events } = createForm(
    { stage: '需求确认' },
    () =>
      new Promise(resolve => {
        release = resolve
      })
  )
  await vm.loadBusiness()
  vm.formData.remark = '推进'
  const first = vm.handleSubmit()
  await Promise.resolve()
  assert.equal(vm.saving, true)
  assert.equal(await vm.handleSubmit(), false)
  vm.handleStepChange(5)
  vm.handleCancel()
  assert.equal(vm.formData.state, '方案报价')
  assert.equal(payloads.length, 1)
  assert.ok(!events.includes('close'))
  release({ code: 200 })
  await first
  assert.equal(vm.formData.state, '商务谈判')
  vm.$destroy()
})

test('winning ends progression without automatically transitioning into loss', async () => {
  const { vm, payloads, events } = createForm({ stage: '商务谈判' })
  await vm.loadBusiness()
  vm.formData.remark = '确认赢单'
  await vm.handleSubmit()
  assert.equal(vm.currentStage, '赢单')
  assert.equal(vm.formData.state, '')
  assert.equal(vm.isFinished, true)
  assert.equal(vm.stepStatus(5), 'wait')
  assert.equal(await vm.handleSubmit(), false)
  assert.equal(payloads.length, 1)
  assert.ok(!events.includes('close'))
  vm.$destroy()
})

test('loss is only available after negotiation and never marks win as completed', async () => {
  const { vm, payloads } = createForm({ stage: '商务谈判', sealBusinessStages: [{ state: '商务谈判' }] })
  await vm.loadBusiness()
  vm.handleStepChange(5)
  vm.formData.remark = '客户终止合作'
  await vm.handleSubmit()
  assert.equal(vm.currentStage, '输单')
  assert.equal(vm.isFinished, true)
  assert.equal(vm.stepStatus(0), 'finish')
  assert.equal(vm.stepStatus(1), 'finish')
  assert.equal(vm.stepStatus(2), 'finish')
  assert.equal(vm.stepStatus(4), 'wait')
  assert.equal(vm.stepStatus(5), 'finish')
  assert.equal(await vm.handleSubmit(), false)
  assert.equal(payloads.length, 1)
  vm.$destroy()
})

test('rejects skipped, earlier and terminal targets even when form state is directly tampered with', async () => {
  const { vm, payloads } = createForm({ stage: '需求确认' })
  await vm.loadBusiness()
  vm.formData.remark = '不得越级'
  for (const stage of ['初步接洽', '需求确认', '商务谈判', '赢单', '输单', '未知阶段']) {
    vm.formData.state = stage
    assert.equal(await vm.handleSubmit(), false)
  }
  assert.equal(payloads.length, 0)
  assert.equal(vm.currentStage, '需求确认')
  vm.$destroy()
})

test('history displays every record for the viewed node, including its own time, event and attachments, while preserving drafts', async () => {
  const records = [
    { state: '初步接洽', recordTime: '2026-11-01 10:30:00', remark: '首次接洽', attachment: '[{"name":"接洽.pdf","url":"/profile/first.pdf"}]' },
    { state: '初步接洽', recordTime: '2026-11-01 15:00:00', remark: '补充接洽', attachment: '' },
    { state: '需求确认', recordTime: '2026-11-02 09:00:00', remark: '确认需求', attachment: [{ name: '需求.pdf', url: '/profile/needs.pdf' }] }
  ]
  const { vm, payloads } = createForm({ stage: '需求确认', sealBusinessStages: records })
  await vm.loadBusiness()
  vm.formData.remark = '未提交的报价草稿'
  vm.handleUploadChange({ fileList: [{ name: '报价.pdf', status: 'done', response: { code: 200, fileName: '/profile/quote.pdf' } }] })
  const draft = { ...vm.formData }
  vm.handleStepChange(0)
  assert.equal(vm.viewedRecords.length, 2)
  assert.equal(vm.viewedRecords[0].remark, '首次接洽')
  assert.equal(vm.viewedRecords[0].recordTime, '2026-11-01 10:30:00')
  assert.deepEqual(vm.viewedRecords[0].files, [{ name: '接洽.pdf', url: '/profile/first.pdf' }])
  assert.equal(await vm.handleSubmit(), false)
  vm.handleStepChange(1)
  assert.equal(vm.viewedRecords.length, 1)
  assert.equal(vm.viewedRecords[0].files[0].name, '需求.pdf')
  vm.returnToNextStage()
  assert.deepEqual(vm.formData, draft)
  assert.equal(vm.fileList.length, 1)
  assert.equal(payloads.length, 0)
  vm.$destroy()
})

test('saved node can be reviewed immediately without closing and malformed legacy attachments do not break history', async () => {
  const { vm, events } = createForm({ stage: '需求确认' })
  await vm.loadBusiness()
  vm.formData.remark = '本次报价记录'
  await vm.handleSubmit()
  vm.handleStepChange(2)
  assert.equal(vm.viewedRecords[0].remark, '本次报价记录')
  assert.equal(vm.viewingHistory, true)
  assert.deepEqual(vm.parseAttachments('invalid json'), [])
  assert.deepEqual(vm.parseAttachments('[null,{}, {"name":"bad"}]'), [])
  assert.ok(!events.includes('close'))
  vm.$destroy()
})

test('ended opportunities still allow reviewing completed nodes, including early losses from legacy data', async () => {
  for (const stage of ['赢单', '输单']) {
    const { vm, payloads } = createForm({
      stage,
      sealBusinessStages: [
        { state: '需求确认', remark: '历史需求' },
        { state: stage, remark: '历史结束' }
      ]
    })
    await vm.loadBusiness()
    vm.handleStepChange(1)
    assert.equal(vm.viewedRecords[0].remark, '历史需求')
    assert.equal(vm.viewingHistory, true)
    assert.equal(await vm.handleSubmit(), false)
    assert.equal(payloads.length, 0)
    if (stage === '输单') assert.equal(vm.canSelectStep(4), false)
    vm.$destroy()
  }
})

test('unknown and terminal initial states cannot create unintended stage records', async () => {
  for (const stage of ['自定义阶段', '赢单', '输单']) {
    const { vm, payloads } = createForm({ stage })
    await vm.loadBusiness()
    vm.formData.remark = '不应提交'
    assert.equal(await vm.handleSubmit(), false)
    assert.equal(payloads.length, 0)
    vm.$destroy()
  }
})

test('unfinished or failed uploads prevent confirmation and successful upload removal clears the attachment', async () => {
  const { vm, payloads, messages } = createForm()
  await vm.loadBusiness()
  vm.formData.remark = '推进'
  vm.handleUploadChange({ fileList: [{ name: '附件', status: 'uploading' }] })
  assert.equal(await vm.handleSubmit(), false)
  vm.handleUploadChange({ fileList: [{ name: '附件', status: 'done', response: { code: 500 } }] })
  await vm.handleSubmit()
  assert.equal(payloads.length, 0)
  assert.ok(messages[0].includes('上传失败'))
  vm.handleUploadChange({ fileList: [] })
  assert.equal(vm.formData.attachment, '[]')
  vm.$destroy()
})
