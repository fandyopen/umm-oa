import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import { createRequire } from 'node:module'

const read = file => fs.readFileSync(new URL(file, import.meta.url), 'utf8')
const { docsLocation } = await import('data:text/javascript;base64,' + Buffer.from(read('../../src/utils/docsNavigation.js')).toString('base64'))
const root = children => [{ moduleId: 341100, path: '/handbook', component: 'Layout', children }]
const page = (path, component, hidden = false) => ({ path, component: 'docs/pages/' + component, hidden, name: component, meta: { title: component } })

test('documentation links use authorized components and configured paths', () => {
  const routes = root([page('guide', 'ModuleDocs'), page('commission', 'CommissionRules')])
  assert.deepEqual(docsLocation(routes), { path: '/handbook/guide' })
  assert.deepEqual(docsLocation(routes, 'commission-rules'), { path: '/handbook/commission' })
  assert.equal(docsLocation(routes, 'api-docs'), null)
  assert.equal(docsLocation([]), null)
  assert.deepEqual(docsLocation([{ path: '/', children: [{ path: '/handbook', children: [{ path: '/handbook/new-rules', meta: { component: 'docs/pages/CommissionRules' } }] }] }], 'commission-rules'), { path: '/handbook/new-rules' })
})

test('default navigation skips hidden pages but explicit authorized links remain valid', () => {
  const routes = root([page('technical', 'ApiDocs', true), page('guide', 'ModuleDocs')])
  assert.deepEqual(docsLocation(routes), { path: '/handbook/guide' })
  assert.deepEqual(docsLocation(routes, 'api-docs'), { path: '/handbook/technical' })
  assert.equal(docsLocation(root([page('technical', 'ApiDocs', true)])), null)
})

test('generated menus resolve root and legacy links without restoring unauthorized sections', async () => {
  const source = read('../../src/router/generator-routers.js').replace(/^import .*$/gm, '')
    .replace("const modules = import.meta.glob('../views/**/*.vue')", 'const modules = {}').replace(/export const /g, 'const ')
  const stub = { render: h => h('div') }
  const generate = new Function('docsLocation', 'indexRouterMap', 'personalTaskRouterMap', 'UserLayout', 'BasicLayout', 'BlankLayout', 'PageView', 'RouteView', 'validURL', source + '\nreturn generatorDynamicRouter')(
    docsLocation, [{ path: '/docs/access-denied', name: 'Denied', component: 'RouteView', hidden: true }], [], stub, stub, stub, stub, stub, () => false)
  const routes = await generate(root([page('guide', 'ModuleDocs'), page('pay-rules', 'CommissionRules')]))
  const require = createRequire(import.meta.url)
  const Vue = require('vue'), Router = require('vue-router')
  Vue.use(Router)
  const router = new Router({ mode: 'abstract', routes })
  assert.equal(router.match('/handbook').path, '/handbook/guide')
  assert.equal(router.match('/system/docs?tab=commission-rules').path, '/handbook/pay-rules')
  assert.equal(router.match('/system/docs?tab=api-docs').path, '/docs/access-denied')
  assert.equal(router.match('/system/docs').path, '/handbook/guide')
})
