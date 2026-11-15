const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const babel = require('@babel/core')

test('workflow API display configuration controls which payload values are shown', () => {
  const env = environment(),
    business = env.load(path.resolve(__dirname, '../src/services/business.js'))
  const groups = business.approvalGroups({
    _workflowApi: true,
    _workflowDisplay: [{ label: '申请内容', value: '展示内容' }],
    internalCode: 'should-not-display',
    snapshot: { name: 'hidden' }
  })
  assert.equal(groups.length, 1)
  assert.equal(groups[0].rows.length, 0)
  assert.equal(groups[0].configured[0].text, '展示内容')
})

test('frozen nested approval content is flattened for H5 and native mini-program rendering', () => {
  const env = environment(),
    business = env.load(path.resolve(__dirname, '../src/services/business.js'))
  const groups = business.approvalGroups({
    title: '审批',
    _workflowDefinition: { processData: 'internal' },
    snapshot: { jobTitle: '原职位' },
    fields: { jobTitle: '新职位' },
    feeTerms: [{ title: '入职服务费', amount: 3000 }],
    _workflowDisplay: [{ label: '跟进结果', value: '待二次沟通' }]
  })
  const values = groups.flatMap((g) => g.rows.map((row) => row.value))
  assert.ok(values.includes('原职位'))
  assert.ok(values.includes('新职位'))
  assert.ok(values.includes('3000'))
  assert.equal(JSON.stringify(groups).includes('processData'), false)
  assert.equal(groups[0].configured[0].text, '待二次沟通')
})

// Load the actual ES modules in an isolated uni runtime, including PC pure recruitment predicates.
function environment() {
  const cache = new Map(),
    storage = new Map([['umm:mobile:token', 'test-token']]),
    calls = []
  let execute = (options) => options.success({ statusCode: 200, data: { code: 200, data: { status: 'pendingApproval' } } })
  const meta = { moduleId: 12, perms: 'crm:customer:list' }
  const session = {
    user: { userName: 'member' },
    permissions: ['crm:customer:list', 'custom:followup'],
    moduleActions: [{ moduleId: 12, actionCode: 'track', permissionCode: 'custom:followup', status: '0' }]
  }
  const uni = {
    getStorageSync: (key) => storage.get(key),
    setStorageSync: (k, v) => storage.set(k, v),
    removeStorageSync: (k) => storage.delete(k),
    reLaunch: () => {},
    request(options) {
      calls.push(options)
      if (options.url.endsWith('/auth/session')) return options.success({ statusCode: 200, data: { code: 200, ...session } })
      if (options.url.endsWith('/system/module-context'))
        return options.success({ statusCode: 200, data: { code: 200, data: { 'eos/crm/customer/index': meta } } })
      execute(options)
    }
  }
  function load(file) {
    file = path.resolve(file)
    if (!path.extname(file)) file += '.js'
    if (cache.has(file)) return cache.get(file).exports
    const module = { exports: {} }
    cache.set(file, module)
    let source = fs.readFileSync(file, 'utf8').replace(/\/\/ #ifndef H5[\s\S]*?\/\/ #endif/g, '')
    const code = babel.transformSync(source, {
      babelrc: false,
      configFile: false,
      plugins: ['@babel/plugin-transform-modules-commonjs']
    }).code
    vm.runInNewContext(`(function(require,module,exports){${code}\n})`, { uni, process, console, Date, Math, Set, Map, Promise, JSON })(
      (name) => (name === 'vue' ? { observable: (v) => v } : load(path.resolve(path.dirname(file), name))),
      module,
      module.exports
    )
    return module.exports
  }
  return {
    load,
    uni,
    calls,
    session,
    storage,
    setExecute: (fn) => {
      execute = fn
    },
    catalog: load(path.resolve(__dirname, '../src/services/catalog.js'))
  }
}

test('module access, configured permission codes, deleted and disabled buttons are enforced', () => {
  const { catalog: c } = environment(),
    meta = { moduleId: 12, perms: 'crm:customer:list' }
  assert.equal(c.moduleVisible({ permissions: ['some:other:list'] }, meta), false)
  const session = {
    permissions: ['crm:customer:list', 'custom:followup'],
    moduleActions: [{ moduleId: 12, actionCode: 'followup_new', permissionCode: 'custom:followup', status: '0' }]
  }
  assert.equal(c.actionAllowed(session, meta, 'followup_new'), true)
  assert.equal(c.actionAllowed({ ...session, permissions: ['*:*:*'] }, meta, 'track'), false)
  session.moduleActions[0].status = '1'
  assert.equal(c.actionAllowed({ ...session, permissions: ['*:*:*'] }, meta, 'followup_new'), false)
})
test('invalid numeric IDs never form business URLs and both pagination envelopes work', () => {
  const { catalog: c } = environment()
  for (const id of ['NaN', 'undefined', '1.2', '-1', '0', '1e3', '9007199254740992']) assert.equal(c.positiveId(id), null)
  assert.equal(c.positiveId('13'), 13)
  assert.deepEqual(JSON.parse(JSON.stringify(c.pageRows({ code: 200, data: { rows: [{ id: 1 }], total: 40 } }))), {
    rows: [{ id: 1 }],
    total: 40
  })
  assert.equal(c.pageRows({ code: 200, rows: [{ id: 1 }], total: 1 }).total, 1)
})
test('workflow retries keep one submission key and concurrent taps send one business request', async () => {
  const env = environment(),
    runtime = env.load(path.resolve(__dirname, '../src/services/runtime.js'))
  let send, failedKey
  env.setExecute((options) => {
    send = options
    failedKey = options.data.submissionKey
  })
  const first = runtime.submitAction('customers', 'track', { content: '跟进' }),
    second = runtime.submitAction('customers', 'track', { content: '跟进' })
  await new Promise((r) => setImmediate(r))
  assert.equal(env.calls.filter((c) => c.url.endsWith('/workflow/action/execute')).length, 1)
  send.fail()
  await assert.rejects(first, /网络/)
  await assert.rejects(second, /网络/)
  env.setExecute((o) => {
    assert.equal(o.data.submissionKey, failedKey)
    o.success({ statusCode: 200, data: { code: 200, data: { status: 'pendingApproval' } } })
  })
  await runtime.submitAction('customers', 'track', { content: '跟进' })
  assert.equal(env.calls.filter((c) => c.url.endsWith('/workflow/action/execute')).length, 2)
})
test('expired login clears role data, token and module metadata', async () => {
  const env = environment(),
    runtime = env.load(path.resolve(__dirname, '../src/services/runtime.js'))
  await runtime.bootstrap()
  env.setExecute((o) => o.success({ statusCode: 200, data: { code: 401, msg: 'expired' } }))
  await assert.rejects(runtime.request('/private'), /登录已失效/)
  assert.equal(runtime.state.session, null)
  assert.equal(env.storage.has('umm:mobile:token'), false)
  assert.equal(Object.keys(runtime.state.contexts).length, 0)
})
test('recommendations only offer unlocked eligible candidates and guarantee choices follow server rules', () => {
  const env = environment(),
    business = env.load(path.resolve(__dirname, '../src/services/business.js'))
  const definition = business.formDefinition(
    'applications',
    'add',
    {},
    {
      talents: [
        { id: 1, name: '可推荐', status: 'available', canRecommend: true },
        { id: 2, name: '锁定', status: 'available', canRecommend: false },
        { id: 3, name: '未沟通', status: 'uncontacted', canRecommend: true }
      ]
    }
  )
  assert.deepEqual(
    Array.from(definition.fields.find((f) => f.key === 'talentId').options, (o) => o.value),
    [1]
  )
  const guarantee = business.formDefinition('applications', 'guarantee', { revision: 4, availableOperations: ['guaranteeExit'] })
  assert.deepEqual(
    Array.from(guarantee.fields[0].options, (o) => o.value),
    ['exit']
  )
  assert.equal(guarantee.values.revision, 4)
})
test('recruitment action bodies preserve revision and use existing workflow / stage endpoints', async () => {
  const env = environment(),
    business = env.load(path.resolve(__dirname, '../src/services/business.js'))
  const definition = business.formDefinition('applications', 'acceptOffer', { id: 9, revision: 2 })
  await business.saveBusiness(
    'applications',
    'acceptOffer',
    { id: 9 },
    { ...definition.values, expectedEntryDate: '2026-12-01' },
    {},
    definition
  )
  const call = env.calls.at(-1)
  assert.equal(call.url, '/api/project/recruitment/applications/9/actions/offer')
  assert.equal(call.data.revision, 2)
  assert.equal(call.data.operation, 'accept')
})
