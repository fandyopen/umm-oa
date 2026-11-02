import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import { parse } from '@babel/parser'
import compiler from 'vue-template-compiler'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const require = createRequire(import.meta.url)
const Vue = require('vue')
const importSource = source => import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'))
const { createModuleContextResolver, normalizeModuleContext } = await importSource(read('src/utils/moduleContextResolver.js'))
const { withModuleActionGuards } = await importSource(read('src/utils/moduleActionGuard.js'))
const permissionSource = read('src/utils/modulePermission.js').replace(/^import .*$/gm, '')
const { hasModuleAction } = await importSource('const getModuleActionConfiguration = () => null;\n' + permissionSource)
const customer = 'eos/crm/customer/index'
const contract = 'eos/crm/contract/index'
const fee = 'eos/crm/contract/fees'
const context = (component, moduleId, perms = 'business:item:list') => ({ component, moduleId, perms, status: '0' })

function environment(fetchModules, permissions = ['*:*:*'], routeMeta = {}, moduleMeta = null, bindings = { contract, customer, fee }, actions = {}, methods = {}) {
  const source = read('src/utils/moduleContext.js')
    .replace(/^import .*$/gm, '')
    .replace(/export /g, '')
  const api = new Function('Vue', 'resolveModuleContexts', 'hasModuleAction', 'createModuleContextResolver', 'withModuleActionGuards', source + '\nreturn { withModuleContexts, resetModuleContexts }')(
    Vue,
    async selectors => ({ data: await fetchModules(selectors) }),
    hasModuleAction,
    createModuleContextResolver,
    withModuleActionGuards
  )
  const messages = []
  const vm = new Vue({
    mixins: [api.withModuleContexts(bindings, actions)],
    methods,
    propsData: { moduleMeta },
    beforeCreate() {
      this.$route = { meta: routeMeta }
      this.$store = { getters: { permissions } }
      this.$message = { warning: message => messages.push(message), error: message => messages.push(message) }
    }
  })
  return { vm, messages, reset: api.resetModuleContexts }
}

function componentMethod(file, section, name, names, values) {
  const script = compiler.parseComponent(read(file)).script.content
  const ast = parse(script, { sourceType: 'module', plugins: ['jsx'] })
  const definition = ast.program.body.find(node => node.type === 'ExportDefaultDeclaration').declaration
  const block = definition.properties.find(node => node.key.name === section).value
  const method = block.properties.find(node => node.key.name === name)
  return new Function(...names, 'return ({' + script.slice(method.start, method.end) + '}).' + name)(...values)
}

test('normalization rejects missing, unrelated, disabled and invalid identifiers', () => {
  for (const value of [null, {}, context(customer, 4), ...[0, 'bad', 1.5, true, [], '', '0x10', '1e3'].map(id => context(contract, id)), { ...context(contract, 3), status: '1' }]) {
    assert.equal(normalizeModuleContext(value, contract), null)
  }
  assert.equal(normalizeModuleContext(context(contract, '992345'), contract).moduleId, 992345)
})

test('explicit owner metadata takes priority over route and needs no lookup', async () => {
  const resolver = createModuleContextResolver(() => assert.fail('unexpected lookup'))
  const owner = context(contract, 9001)
  const result = await resolver.ensure([contract], [owner, context(contract, 9002)])
  assert.equal(result[contract].moduleId, 9001)
})

test('an unrelated route cannot substitute for the requested cross module', async () => {
  const resolver = createModuleContextResolver(async selectors => {
    assert.deepEqual(selectors, [contract])
    return { [contract]: context(contract, 9003) }
  })
  assert.equal(resolver.get(contract, [context(customer, 9004)]), null)
  const result = await resolver.ensure([contract], [context(customer, 9004)])
  assert.equal(result[contract].moduleId, 9003)
})

test('overlapping consumers share one pending lookup and reuse cached metadata', async () => {
  let release
  let calls = 0
  const resolver = createModuleContextResolver(async () => {
    calls++
    return await new Promise(resolve => {
      release = resolve
    })
  })
  const first = resolver.ensure([contract, fee, contract])
  const second = resolver.ensure([fee])
  await Promise.resolve()
  assert.equal(calls, 1)
  release({ [contract]: context(contract, 9011), [fee]: context(fee, 9012) })
  await Promise.all([first, second])
  assert.equal((await resolver.ensure([fee]))[fee].moduleId, 9012)
  assert.equal(calls, 1)
})

test('unknown module stays unavailable without fallback numeric IDs', async () => {
  const resolver = createModuleContextResolver(async () => ({}))
  const result = await resolver.ensure([contract])
  assert.equal(result[contract], null)
  assert.equal(resolver.get(contract), null)
})

test('failed requests are retryable and never install fallback permissions', async () => {
  let calls = 0
  const resolver = createModuleContextResolver(async () => {
    if (++calls === 1) throw new Error('offline')
    return { [contract]: context(contract, 9021) }
  })
  await assert.rejects(resolver.ensure([contract]), /offline/)
  assert.equal(resolver.get(contract), null)
  assert.equal((await resolver.ensure([contract]))[contract].moduleId, 9021)
})

test('invalid or disabled API metadata is not cached as an active module', async () => {
  for (const payload of [null, undefined, []]) {
    const resolver = createModuleContextResolver(async () => payload)
    await assert.rejects(resolver.ensure([contract]), /模块信息无效/)
    assert.equal(resolver.get(contract), null)
  }
  for (const row of [{ ...context(contract, 9), status: '1' }, context(customer, 9)]) {
    const resolver = createModuleContextResolver(async () => ({ [contract]: row }))
    await assert.rejects(resolver.ensure([contract]), /模块信息无效/)
    assert.equal(resolver.get(contract), null)
  }
})

test('reset invalidates cached context and rejects late replies from the previous session', async () => {
  let release
  let calls = 0
  const resolver = createModuleContextResolver(async () => {
    if (++calls === 1)
      return await new Promise(resolve => {
        release = resolve
      })
    return { [contract]: context(contract, 9032) }
  })
  const old = resolver.ensure([contract])
  await Promise.resolve()
  resolver.reset()
  assert.equal((await resolver.ensure([contract]))[contract].moduleId, 9032)
  release({ [contract]: context(contract, 9031) })
  await assert.rejects(old, /会话已变化/)
  assert.equal(resolver.get(contract).moduleId, 9032)
})

test('Vue context permissions update after async metadata arrives from another business page', async () => {
  const { vm } = environment(async selectors => Object.fromEntries(selectors.map((key, index) => [key, context(key, 9100 + index)])), ['*:*:*'], context(customer, 9199))
  assert.equal(vm.canModule('contract', 'submit'), false)
  await vm.ensureModuleContexts()
  await Vue.nextTick()
  assert.equal(vm.moduleContexts.customer.moduleId, 9199)
  assert.notEqual(vm.moduleContexts.contract.moduleId, 9199)
  assert.equal(vm.canModule('contract', 'submit'), true)
  assert.equal(vm.moduleActionBinding('contract', 'submit').moduleId, vm.moduleContexts.contract.moduleId)
  vm.$destroy()
})

test('owner prop is forwarded while cross module permissions resolve independently', async () => {
  let requested
  const { vm } = environment(
    async selectors => {
      requested = selectors
      return { [customer]: context(customer, 9202), [fee]: context(fee, 9203) }
    },
    ['module:9201:submit'],
    context(customer, 9299),
    { moduleId: 9201, perms: 'crm:contract:list' }
  )
  await vm.ensureModuleContexts()
  assert.deepEqual(requested, [fee])
  assert.equal(vm.moduleContexts.contract.moduleId, 9201)
  assert.equal(vm.canModule('contract', 'submit'), true)
  assert.equal(vm.canModule('fee', 'receive'), false)
  vm.$destroy()
})

test('missing module denies authorization silently during page initialization', async () => {
  const { vm, messages } = environment(async () => ({}))
  await vm.ensureModuleContexts()
  assert.equal(vm.canModule('contract', 'submit'), false)
  assert.equal(vm.canViewModule('contract'), false)
  assert.equal(messages.length, 0)
  vm.$destroy()
})

test('prefetch failure never displays a page-wide notification', async () => {
  const { vm, messages } = environment(async () => {
    throw new Error('offline')
  })
  await assert.rejects(vm.ensureModuleContexts(), /offline/)
  assert.equal(vm.canModule('fee', 'receive'), false)
  assert.equal(messages.length, 0)
  vm.$destroy()
})

test('HR workflow dispatch uses the passed module context even on another route', async () => {
  let body
  const executeAction = componentMethod(
    'src/views/enterprise/components/memberElement/index.vue',
    'methods',
    'executeAction',
    ['executeWorkflowAction', 'hasModuleAction'],
    [
      async input => {
        body = input
      },
      hasModuleAction
    ]
  )
  const vm = {
    moduleMeta: context('enterprise/hr/index', 934567),
    $route: { meta: context(customer, 999999) },
    $store: { getters: { permissions: ['module:934567:transfer'] } },
    $message: { error: () => assert.fail('unexpected error') }
  }
  await executeAction.call(vm, 'transfer', { userIds: [7], deptId: 8 })
  assert.deepEqual(body, { moduleId: 934567, actionCode: 'transfer', payload: { userIds: [7], deptId: 8 } })
})

test('HR cannot send an action without resolved metadata or granted permission', async () => {
  const executeAction = componentMethod(
    'src/views/enterprise/components/memberElement/index.vue',
    'methods',
    'executeAction',
    ['executeWorkflowAction', 'hasModuleAction'],
    [() => assert.fail('unexpected dispatch'), hasModuleAction]
  )
  for (const moduleMeta of [null, context('enterprise/hr/index', 934567)]) {
    const vm = { moduleMeta, $store: { getters: { permissions: [] } }, $message: { error() {} } }
    await assert.rejects(executeAction.call(vm, 'invite', {}), /业务模块不可用/)
  }
})

test('action table treats explicit unresolved metadata as unavailable', () => {
  const readContext = componentMethod('src/components/Table/ActionColumns.vue', 'computed', 'actionModuleMeta', [], [])
  const route = context(customer, 888)
  assert.equal(readContext.call({ moduleMeta: null, $route: { meta: route } }), null)
  assert.equal(readContext.call({ moduleMeta: undefined, $route: { meta: route } }), route)
})

const feeBinding = { fee: { component: fee, label: '合同回款' } }
const receiveCheck = { module: 'fee', action: 'receive', label: '登记回款' }

test('unresolved action remains visible but cannot authorize or run a business request', async () => {
  let dispatched = 0
  const { vm, messages } = environment(
    async () => ({}),
    ['*:*:*'],
    {},
    null,
    feeBinding,
    { receive: receiveCheck },
    {
      receive() {
        dispatched++
      }
    }
  )
  await vm.ensureModuleContexts()
  assert.deepEqual(messages, [])
  assert.equal(vm.showModuleAction('fee', 'receive'), true)
  assert.equal(vm.canModule('fee', 'receive'), false)
  assert.equal(await vm.receive(), false)
  assert.equal(dispatched, 0)
  assert.deepEqual(messages, ['登记回款暂不可用：合同回款模块未配置或已停用，请联系管理员。'])
  assert.equal(vm.moduleActionBusy.receive, false)
  vm.$destroy()
})

test('click retries a missing module and queries only the selected business dependency', async () => {
  const requests = []
  const { vm, messages } = environment(
    async selectors => {
      requests.push(selectors)
      return {}
    },
    ['*:*:*'],
    {},
    null,
    { contract: { component: contract, label: '合同列表' }, ...feeBinding }
  )
  await vm.ensureModuleContexts()
  await vm.checkModuleAction(receiveCheck)
  assert.deepEqual(requests, [[contract, fee], [fee]])
  assert.equal(messages.length, 1)
  assert.ok(messages[0].includes('登记回款'))
  assert.ok(!messages[0].includes('合同列表'))
  vm.$destroy()
})

test('metadata lookup failure is silent until click and uses a retryable specific message', async () => {
  const { vm, messages } = environment(
    async () => {
      throw new Error('offline')
    },
    ['*:*:*'],
    {},
    null,
    feeBinding
  )
  await vm.ensureModuleContexts().catch(() => {})
  assert.deepEqual(messages, [])
  assert.equal(await vm.checkModuleAction(receiveCheck), false)
  assert.deepEqual(messages, ['登记回款暂不可用：合同回款模块信息加载失败，请重试。'])
  vm.$destroy()
})

test('duplicate module configuration names the failing action and business module', async () => {
  const { vm, messages } = environment(
    async () => {
      throw Object.assign(new Error('duplicate'), { code: 409 })
    },
    ['*:*:*'],
    {},
    null,
    feeBinding
  )
  await vm.ensureModuleContexts().catch(() => {})
  await vm.checkModuleAction(receiveCheck)
  assert.deepEqual(messages, ['登记回款暂不可用：合同回款模块配置重复，请联系管理员。'])
  vm.$destroy()
})

test('known permission denial stays hidden and never runs the action', async () => {
  let dispatched = 0
  const { vm, messages } = environment(
    async () => ({ [fee]: context(fee, 7201) }),
    [],
    {},
    null,
    feeBinding,
    { receive: receiveCheck },
    {
      receive() {
        dispatched++
      }
    }
  )
  await vm.ensureModuleContexts()
  assert.equal(vm.showModuleAction('fee', 'receive'), false)
  assert.equal(await vm.receive(), false)
  assert.equal(dispatched, 0)
  assert.deepEqual(messages, ['登记回款暂不可用：你没有该操作的权限。'])
  vm.$destroy()
})

test('missing module can recover on the next click without refreshing the entire page', async () => {
  let ready = false
  let dispatched = 0
  const { vm, messages } = environment(
    async () => (ready ? { [fee]: context(fee, 7202) } : {}),
    ['module:7202:receive'],
    {},
    null,
    feeBinding,
    { receive: receiveCheck },
    {
      receive(value) {
        dispatched++
        return value
      }
    }
  )
  await vm.ensureModuleContexts()
  assert.equal(await vm.receive('first'), false)
  ready = true
  assert.equal(await vm.receive('saved'), 'saved')
  assert.equal(dispatched, 1)
  assert.equal(messages.length, 1)
  vm.$destroy()
})

test('a failing unrelated module does not block an already resolved action', async () => {
  let dispatched = 0
  let lookups = 0
  const { vm, messages } = environment(
    async () => {
      lookups++
      throw new Error('unrelated lookup fails')
    },
    ['module:7301:submit'],
    context(contract, 7301),
    null,
    { contract: { component: contract, label: '合同列表' }, ...feeBinding },
    { submit: { module: 'contract', action: 'submit', label: '提交审批' } },
    {
      submit() {
        dispatched++
        return true
      }
    }
  )
  await vm.ensureModuleContexts().catch(() => {})
  assert.equal(await vm.submit(), true)
  assert.equal(dispatched, 1)
  assert.equal(lookups, 1)
  assert.deepEqual(messages, [])
  vm.$destroy()
})

test('rapid duplicate clicks share validation and expose a loading state until completion', async () => {
  let release
  let dispatched = 0
  const { vm } = environment(
    async () =>
      await new Promise(resolve => {
        release = resolve
      }),
    ['module:7401:receive'],
    {},
    null,
    feeBinding,
    { receive: receiveCheck },
    {
      receive(value) {
        dispatched++
        return value
      }
    }
  )
  const first = vm.receive('accepted')
  const duplicate = vm.receive('duplicate')
  assert.equal(vm.moduleActionBusy.receive, true)
  await Promise.resolve()
  assert.equal(await duplicate, false)
  release({ [fee]: context(fee, 7401) })
  assert.equal(await first, 'accepted')
  assert.equal(dispatched, 1)
  assert.equal(vm.moduleActionBusy.receive, false)
  vm.$destroy()
})

test('action rejection clears the loading state and the button can retry', async () => {
  let dispatched = 0
  const { vm } = environment(
    async () => ({ [fee]: context(fee, 7402) }),
    ['module:7402:receive'],
    {},
    null,
    feeBinding,
    { receive: receiveCheck },
    {
      receive() {
        if (++dispatched === 1) throw new Error('business failed')
        return 'ok'
      }
    }
  )
  await assert.rejects(vm.receive(), /business failed/)
  assert.equal(vm.moduleActionBusy.receive, false)
  assert.equal(await vm.receive(), 'ok')
  vm.$destroy()
})

test('guard wrappers do not leak or stack across component instances', async () => {
  let checks = 0
  let calls = 0
  const options = {
    mixins: [withModuleActionGuards({ save: receiveCheck })],
    methods: {
      async checkModuleAction() {
        checks++
        return true
      },
      save(value) {
        calls++
        return value
      }
    }
  }
  const first = new Vue(options)
  const second = new Vue(options)
  assert.equal(await first.save(1), 1)
  assert.equal(await second.save(2), 2)
  assert.equal(checks, 2)
  assert.equal(calls, 2)
  first.$destroy()
  second.$destroy()
})

test('cancel and other explicitly unguarded branches do not look up modules', async () => {
  const vm = new Vue({
    mixins: [withModuleActionGuards({ close: value => (value ? receiveCheck : null) })],
    methods: {
      checkModuleAction() {
        assert.fail('cancel must not resolve module')
      },
      close(value) {
        return value
      }
    }
  })
  assert.equal(await vm.close(false), false)
  vm.$destroy()
})

test('unmounted component does not open a form after a delayed lookup', async () => {
  let release
  let dispatched = 0
  const { vm } = environment(
    async () =>
      await new Promise(resolve => {
        release = resolve
      }),
    ['*:*:*'],
    {},
    null,
    feeBinding,
    { receive: receiveCheck },
    {
      receive() {
        dispatched++
      }
    }
  )
  const result = vm.receive()
  await Promise.resolve()
  vm.$destroy()
  release({ [fee]: context(fee, 7501) })
  assert.equal(await result, false)
  assert.equal(dispatched, 0)
})

test('all declared business action checks map to existing methods and module dependencies', () => {
  const files = [
    'src/views/eos/crm/contract/modules/ContractWorkspace.vue',
    'src/views/eos/crm/contract/modules/ContractDetail.vue',
    'src/views/eos/crm/customer/modules/CustomerDetail.vue',
    'src/views/eos/crm/customer/index.vue',
    'src/views/eos/business/index.vue',
    'src/views/eos/business/modules/BusinessDetail.vue',
    'src/views/eos/crm/risk/index.vue',
    'src/views/eos/crm/contract/modules/CrmTodoDetail.vue',
    'src/views/enterprise/components/memberElement/index.vue'
  ]
  for (const file of files) {
    const script = compiler.parseComponent(read(file)).script.content
    const definition = parse(script, { sourceType: 'module', plugins: ['jsx'] }).program.body.find(node => node.type === 'ExportDefaultDeclaration').declaration
    const mixin = definition.properties.find(node => node.key.name === 'mixins').value.elements[0]
    const methods = definition.properties.find(node => node.key.name === 'methods').value.properties.map(node => node.key.name)
    const config = mixin.arguments[mixin.callee.name === 'withModuleContexts' ? 1 : 0]
    const actions = new Function('return (' + script.slice(config.start, config.end) + ')')()
    const keys = mixin.callee.name === 'withModuleContexts' ? mixin.arguments[0].properties.map(node => node.key.name) : ['members']
    for (const [name, action] of Object.entries(actions)) {
      assert.ok(methods.includes(name), file + ': ' + name)
      const args =
        name === 'startAction'
          ? ['submit']
          : name === 'executeAction'
          ? ['invite']
          : name === 'confirmAction'
          ? ['revision', '创建新版本']
          : name === 'handleTabChange'
          ? ['contracts']
          : name === 'openForm' || name === 'openCustomerForm'
          ? [{}]
          : [1]
      const check = typeof action === 'function' ? action.apply({ operation: 'receipts', operationTitle: '登记回款' }, args) : action
      assert.ok(check && keys.includes(check.module), file + ': missing dependency for ' + name)
      assert.ok(check.label, file + ': missing label for ' + name)
    }
  }
})

test('async member guard waits for parent metadata propagation before dispatch', async () => {
  const check = componentMethod('src/views/enterprise/components/memberElement/index.vue', 'methods', 'checkModuleAction', [], [])
  const order = []
  const allowed = await check.call(
    {
      moduleActionGuard: async value => {
        order.push(value.label)
        return true
      },
      $nextTick: async () => {
        order.push('updated')
      }
    },
    receiveCheck
  )
  assert.equal(allowed, true)
  assert.deepEqual(order, ['登记回款', 'updated'])
})

test('permission directive exposes unresolved controls only when on-click checking is explicit', () => {
  const source = read('src/directive/permission/hasModuleAction.js')
    .replace(/^import .*$/gm, '')
    .replace('export default', 'return')
  const directive = new Function('store', 'hasModuleAction', 'getModuleActionConfiguration', source)({ getters: { permissions: [] } }, hasModuleAction, () => null)
  const el = { style: { display: '' }, childNodes: [] }
  const vnode = { context: { $route: { meta: {} } } }
  directive.inserted(el, { value: { actionCode: 'add', allowUnresolved: true } }, vnode)
  assert.equal(el.style.display, '')
  directive.componentUpdated(el, { value: { actionCode: 'add' } }, vnode)
  assert.equal(el.style.display, 'none')
  directive.componentUpdated(el, { value: { moduleId: 12, actionCode: 'add', allowUnresolved: true } }, vnode)
  assert.equal(el.style.display, 'none')
})

test('metadata request opts out of generic notifications while normal route requests do not', async () => {
  const requests = []
  const source = read('src/api/user/module.js')
    .replace(/^import .*$/gm, '')
    .replace(/export /g, '')
  const api = new Function('request', source + '\nreturn {resolveModuleContexts, getRouters}')(input => {
    requests.push(input)
    return Promise.resolve({})
  })
  await api.resolveModuleContexts([contract])
  await api.getRouters()
  assert.equal(requests[0].silentError, true)
  assert.equal(requests[1].silentError, undefined)
})

test('request interceptors suppress only opted-in errors and preserve session expiration handling', async () => {
  let responseHandler, rejectionHandler
  const notices = []
  let logouts = 0
  let reloads = 0
  let source = read('src/utils/axios/request.js').replace(/^import .*$/gm, '')
  const ast = parse(source, { sourceType: 'module' })
  const notify = ast.program.body.find(node => node.type === 'VariableDeclaration' && node.declarations[0].id.name === 'notifyError')
  source = source.slice(0, notify.start) + source.slice(notify.end)
  source = source.replace('import.meta.env.VITE_APP_BASE_API', "'/api'").replace(/^export .*$/gm, '')
  const instance = {
    interceptors: {
      request: { use() {} },
      response: {
        use(success, failure) {
          responseHandler = success
          rejectionHandler = failure
        }
      }
    }
  }
  new Function('Vue', 'axios', 'storage', 'VueAxios', 'errorCode', 'ACCESS_TOKEN', 'notifyError', 'location', source)(
    {
      prototype: {
        store: {
          dispatch: async () => {
            logouts++
          }
        }
      }
    },
    { create: () => instance },
    {},
    {},
    {},
    'token',
    value => notices.push(value),
    {
      reload() {
        reloads++
      }
    }
  )
  await assert.rejects(responseHandler({ data: { code: 404, msg: 'missing' }, config: { url: '/system/module-context', silentError: true } }), error => error.code === 404)
  await assert.rejects(rejectionHandler({ message: 'Network Error', config: { silentError: true } }))
  assert.equal(notices.length, 0)
  await assert.rejects(Promise.resolve().then(() => responseHandler({ data: { code: 404, msg: 'normal error' }, config: { url: '/normal' } })))
  assert.equal(notices.length, 1)
  await assert.rejects(responseHandler({ data: { code: 401 }, config: { url: '/system/module-context', silentError: true } }))
  await Promise.resolve()
  assert.equal(logouts, 1)
  assert.equal(reloads, 1)
})

test('customer primary data renders while related module queries are still pending', async () => {
  const load = componentMethod(
    'src/views/eos/crm/customer/modules/CustomerDetail.vue',
    'methods',
    'loadCustomer',
    ['getCustomer', 'listFollowups'],
    [async () => ({ data: { name: '主档正常', sealMerchantUsers: [{ id: 1 }] } }), async () => ({ data: [{ id: 2 }] })]
  )
  const releases = []
  const vm = { formId: 9, loading: false, loadRelatedData: () => new Promise(resolve => releases.push(resolve)) }
  const pending = load.call(vm)
  for (let i = 0; i < 8; i++) await Promise.resolve()
  assert.equal(vm.customer.name, '主档正常')
  assert.equal(vm.loading, false)
  assert.deepEqual(vm.contacts, [{ id: 1 }])
  assert.equal(releases.length, 5)
  releases.forEach(resolve => resolve())
  await pending
})

test('contract list loading does not await unrelated metadata prefetch', async () => {
  const readRoute = componentMethod('src/views/eos/crm/contract/modules/ContractWorkspace.vue', 'methods', 'readRoute', ['normalizeContractId'], [() => null])
  let loaded = 0
  const vm = {
    query: {},
    $route: { query: {} },
    detailTab: 'overview',
    load() {
      loaded++
    },
    ensureModuleContexts() {
      assert.fail('list must not await metadata')
    }
  }
  await readRoute.call(vm)
  assert.equal(loaded, 1)
})

test('action table exposes unresolved actions only for explicitly guarded callers', () => {
  const visible = componentMethod(
    'src/components/Table/ActionColumns.vue',
    'computed',
    'visibleActions',
    ['store', 'hasModuleAction', 'ACTION_BY_LABEL', 'moduleActionName'],
    [{ getters: { permissions: [] } }, hasModuleAction, {}, () => assert.fail('unknown module has no configured label')]
  )
  const action = { label: '推进阶段', actionCode: 'track' }
  assert.deepEqual(visible.call({ actions: [action], actionModuleMeta: null, allowUnresolved: true }), [action])
  assert.deepEqual(visible.call({ actions: [action], actionModuleMeta: null, allowUnresolved: false }), [])
})

test('member department popover only opens after its operation check succeeds', async () => {
  let lookups = 0
  const open = componentMethod(
    'src/views/enterprise/components/memberElement/index.vue',
    'methods',
    'initAdjusDepTreeData',
    ['getTreeMenu'],
    [
      async () => {
        lookups++
        return { data: [{ id: 1 }] }
      }
    ]
  )
  let allowed = false
  const vm = new Vue({
    mixins: [withModuleActionGuards({ open: { module: 'members', action: 'transfer', label: '调整部门' } })],
    data: () => ({ adjusDepVisible: false }),
    methods: { open, checkModuleAction: async () => allowed }
  })
  assert.equal(await vm.open(), false)
  assert.equal(vm.adjusDepVisible, false)
  assert.equal(lookups, 0)
  allowed = true
  await vm.open()
  assert.equal(vm.adjusDepVisible, true)
  assert.equal(lookups, 1)
  vm.$destroy()
})

test('action table prevents duplicate dispatch while a guarded callback is pending', async () => {
  const dispatch = componentMethod('src/components/Table/ActionColumns.vue', 'methods', 'handleCustomAction', [], [])
  let release
  let calls = 0
  const callback = () => {
    calls++
    return new Promise(resolve => {
      release = resolve
    })
  }
  const vm = { actionPending: false, record: { id: 1 } }
  const first = dispatch.call(vm, callback)
  assert.equal(vm.actionPending, true)
  await dispatch.call(vm, callback)
  assert.equal(calls, 1)
  release('ok')
  assert.equal(await first, 'ok')
  assert.equal(vm.actionPending, false)
})

const recruitmentFile = 'src/views/eos/recruitment/components/RecruitmentWorkspace.vue'
const recruitmentFields = await importSource(read('src/views/eos/recruitment/shared.js'))
const recruitmentMethod = (name, dependencies = {}) => componentMethod(recruitmentFile, 'methods', name, Object.keys(dependencies), Object.values(dependencies))
const recruitmentPath = (kind, id) => '/' + kind + '/' + id

test('recommendation candidate selection only allows contacting and available with an inline label hint', async () => {
  const create = recruitmentMethod('create', recruitmentFields)
  let fields
  const vm = {
    kind: 'applications', query: {}, allowedAction: async () => true, loadOptions: async () => {},
    options: { users: [], jobs: [], talents: ['draft', 'uncontacted', 'contacting', 'available', 'archived'].map((status, index) => ({ id: index + 1, name: status, status })) },
    form: (_, items) => { fields = items }
  }
  await create.call(vm)
  const candidate = fields.find(item => item.key === 'talentId')
  assert.equal(candidate.type, 'select')
  assert.deepEqual(candidate.options.map(option => option.value), [3, 4])
  assert.equal(candidate.labelHint, '只允许「沟通中、可推荐」两种状态可选')
})

test('recruitment menus follow record state and permissions in all three modules', () => {
  const actions = recruitmentMethod('actionsForRecord')
  const vm = { kind: 'jobs', showModuleAction: (_, permission) => permission !== 'assign' }
  let keys = actions.call(vm, { demandStatus: 'draft' }).map(action => action.key)
  assert.ok(keys.includes('demand') && keys.includes('remove') && keys.includes('copy'))
  assert.ok(!keys.includes('change') && !keys.includes('assign'))
  keys = actions.call(vm, { demandStatus: 'approved' }).map(action => action.key)
  assert.ok(keys.includes('change') && !keys.includes('demand') && !keys.includes('remove'))
  vm.kind = 'talents'
  keys = actions.call(vm, { status: 'available', approvals: [{ status: 'pending' }] }).map(action => action.key)
  assert.ok(keys.includes('withdraw') && !keys.includes('copy') && !keys.includes('edit'))
  vm.kind = 'applications'
  keys = actions.call(vm, { stage: 'offer', offerStatus: 'issued' }).map(action => action.key)
  assert.ok(keys.includes('acceptOffer') && keys.includes('rejectOffer'))
  assert.ok(!keys.includes('issueOffer') && !keys.includes('remove') && !keys.includes('copy'))
})

test('recruitment row menus ignore an earlier record response after switching rows', async () => {
  const pending = new Map()
  const prepare = recruitmentMethod('prepareRowActions', { api: path => new Promise(resolve => pending.set(path, resolve)), recordPath: recruitmentPath })
  const vm = { kind: 'jobs', rowMenuRequest: 0 }
  const first = prepare.call(vm, { id: 1 }, true)
  const second = prepare.call(vm, { id: 2 }, true)
  pending.get('/jobs/2')({ data: { id: 2, demandStatus: 'approved' } })
  await second
  pending.get('/jobs/1')({ data: { id: 1, demandStatus: 'draft' } })
  await first
  assert.equal(vm.rowMenuRecord.id, 2)
  assert.equal(vm.rowMenuLoading, false)
})

test('recruitment row actions recheck current state and never use an unrelated open detail', async () => {
  let latest = { id: 2, demandStatus: 'draft', revision: 4 }
  const execute = recruitmentMethod('runRecordAction', { api: async path => { assert.equal(path, '/jobs/2'); return { data: latest } }, recordPath: recruitmentPath })
  const actions = recruitmentMethod('actionsForRecord')
  const calls = []; const warnings = []
  const vm = { kind: 'jobs', detail: { id: 1 }, actionLoadingId: null, showModuleAction: () => true, actionsForRecord: d => actions.call(vm, d), loadOptions: async () => {}, runAction: async (action, d) => calls.push([action.key, d]), $message: { warning: message => warnings.push(message) } }
  await execute.call(vm, 'demand', { id: 2 })
  assert.equal(calls[0][1], latest)
  assert.equal(vm.detail.id, 1)
  assert.equal(vm.actionLoadingId, null)
  latest = { id: 2, demandStatus: 'approved', revision: 5 }
  await execute.call(vm, 'demand', { id: 2 })
  assert.equal(calls.length, 1)
  assert.equal(warnings.length, 1)
})

test('recruitment offer entry and guarantee use attachments and revisions from the selected row', async () => {
  const requests = []
  const run = recruitmentMethod('runAction', { ...recruitmentFields, recordPath: recruitmentPath, api: async (...args) => requests.push(args) })
  const attachment = recruitmentMethod('attachmentField', recruitmentFields)
  const selected = { id: 2, revision: 7, attachments: [{ id: 21, originalName: 'current.pdf' }], guaranteeTerms: [{ key: 'term', title: '合同', guaranteeDays: 30 }] }
  let fields; let save
  const vm = { kind: 'applications', detail: { id: 1, attachments: [{ id: 11 }] }, allowedAction: async () => true, attachmentField: (required, d) => attachment.call(vm, required, d), form: (_, f, initial, callback) => { fields = f; save = callback } }
  for (const key of ['offer', 'entry', 'guarantee']) {
    await run.call(vm, { key, permission: key, label: key }, selected)
    assert.deepEqual(fields.find(f => f.key === 'attachmentId').options, [{ value: 21, label: 'current.pdf' }])
    await save({ reason: 'test' })
    assert.equal(requests.at(-1)[0], '/applications/2/actions/' + key)
    assert.equal(requests.at(-1)[2].revision, 7)
  }
})

test('recruitment interview replacement and deletion keep the selected row context', async () => {
  const run = recruitmentMethod('runAction', { ...recruitmentFields, recordPath: recruitmentPath })
  const selected = { id: 2 }; const calls = []
  const vm = { kind: 'applications', detail: { id: 1 }, allowedAction: async () => true, editInterview: (_, d) => calls.push(d), createReplacement: d => calls.push(d), remove: id => calls.push(id) }
  for (const key of ['interview', 'replacement', 'remove']) await run.call(vm, { key, permission: key }, selected)
  assert.deepEqual(calls, [selected, selected, 2])
})
