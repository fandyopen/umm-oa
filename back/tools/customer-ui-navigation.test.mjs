import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import { parse } from '@babel/parser'
import compiler from 'vue-template-compiler'

const require = createRequire(import.meta.url)
const Vue = require('vue')
const Router = require('vue-router')
Vue.use(Router)
const read = file => fs.readFileSync(new URL('../../' + file, import.meta.url), 'utf8')
const config = new Function('UserLayout', read('src/config/router.config.js')
  .replace(/^import .*$/gm, '').replaceAll('export const ', 'const ') + '\nreturn { indexRouterMap, personalTaskRouterMap }')({})
const viewModules = Object.fromEntries(config.personalTaskRouterMap.map(route => {
  assert.ok(fs.existsSync(new URL('../../src/views/' + route.component + '.vue', import.meta.url)))
  return ['../views/' + route.component + '.vue', { render: h => h('div') }]
}))

function generator(routesFromServer = []) {
  const source = read('src/router/generator-routers.js').replace(/^import .*$/gm, '')
    .replace("import.meta.glob('../views/**/*.vue')", 'viewModules')
    .replaceAll('export const ', 'const ')
  const dependencies = {
    ...config, viewModules, getRouters: async () => ({ data: routesFromServer }),
    UserLayout: {}, BasicLayout: {}, BlankLayout: {}, PageView: {}, RouteView: {},
    validURL: value => /^https?:\/\//.test(value || '')
  }
  return new Function(...Object.keys(dependencies), source + '\nreturn generatorDynamicRouter')(...Object.values(dependencies))
}

function componentMethod(file, name) {
  const source = compiler.parseComponent(read(file)).script.content
  const component = parse(source, { sourceType: 'module', plugins: ['jsx'] }).program.body
    .find(node => node.type === 'ExportDefaultDeclaration').declaration
  const methods = component.properties.find(node => node.key.name === 'methods').value
  const method = methods.properties.find(node => node.key?.name === name)
  return new Function('FollowupForm', 'AssignForm', 'HandoverForm', 'return ({' + source.slice(method.start, method.end) + '})')({}, {}, {})[name]
}

for (const fromApi of [false, true]) {
  test('personal task links resolve without task menu permissions (' + (fromApi ? 'router API' : 'session bootstrap') + ')', async () => {
    const build = generator()
    const routes = await build(fromApi ? undefined : [])
    const router = new Router({ mode: 'abstract', routes })
    for (const expected of config.personalTaskRouterMap) {
      const matched = router.match(expected.path)
      assert.equal(matched.name, expected.name)
      assert.notEqual(matched.path, '/404')
      assert.equal(matched.meta.title, expected.meta.title)
      assert.equal(routes[0].children.find(route => route.path === expected.path).hidden, true)
    }
    let destination
    componentMethod('src/components/layouts/GlobalHeader/RightContent.vue', 'gotodo').call({
      $router: { push: path => { destination = path } }
    })
    assert.equal(router.match(destination).name, 'PersonalTodo')
    for (const noTitleKey of ['todo', 'done', 'myProcess', 'myCopy']) {
      componentMethod('src/views/dashboard/components/todo/index.vue', 'handleTabTodoMore').call({
        noTitleKey, $router: { push: path => { destination = path } }
      })
      assert.ok(config.personalTaskRouterMap.some(route => route.name === router.match(destination).name))
    }
  })
}

test('an authorized task menu keeps its module metadata without duplicate routes', async () => {
  const routes = await generator()([{
    path: '/todoItem', name: 'TodoItem', component: 'Layout', meta: { title: '待办' },
    children: [{ path: 'todo', name: 'Todo', moduleId: 340341, component: 'eos/todoItem/todo/index', meta: { title: '我的待办' } }]
  }])
  const router = new Router({ mode: 'abstract', routes })
  const match = router.match('/todoItem/todo')
  assert.equal(match.name, 'Todo')
  assert.equal(match.meta.moduleId, 340341)
  assert.equal(routes[0].children.some(route => route.name === 'PersonalTodo'), false)
  assert.equal(router.match('/todoItem/done').name, 'PersonalDone')
})

test('a customized task menu can coexist with the personal notification entry', async () => {
  const routes = await generator()([{
    path: '/work', name: 'Work', component: 'Layout',
    children: [{ path: 'inbox', name: 'CustomInbox', component: 'eos/todoItem/todo/index' }]
  }])
  const router = new Router({ mode: 'abstract', routes })
  assert.equal(router.match('/work/inbox').name, 'CustomInbox')
  assert.equal(router.match('/todoItem/todo').name, 'PersonalTodo')
})

for (const [file, method, reload] of [
  ['src/views/eos/crm/customer/index.vue', 'openFollowup', 'refresh'],
  ['src/views/eos/crm/customer/modules/CustomerDetail.vue', 'openFollowup', 'loadCustomer'],
  ['src/views/eos/crm/customer/index.vue', 'openAssign', 'refresh'],
  ['src/views/eos/crm/customer/index.vue', 'openHandover', 'refresh']
]) {
  test(file + ' ' + method + ' renders only form actions and refreshes after success', async () => {
    let options, refreshed = 0
    const vm = {
      customer: { name: '客户甲' }, formId: 12,
      moduleContexts: { customer: { moduleId: 340352, component: 'eos/crm/customer/index' } },
      $createAntdModal: value => { options = value },
      [reload]: () => { refreshed++ }
    }
    componentMethod(file, method).call(vm, { id: 12, name: '客户甲' })
    assert.equal(options.modalProps.footer, null)
    assert.equal(refreshed, 0)
    await options.onOk()
    assert.equal(refreshed, 1)
  })
}
