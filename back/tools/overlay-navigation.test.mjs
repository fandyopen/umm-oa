import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import { createRequire } from 'node:module'

const source = fs.readFileSync(new URL('../../src/utils/overlayNavigation.js', import.meta.url), 'utf8')
const { createOverlayNavigation, installOverlayNavigation } = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'))
const Vue = createRequire(import.meta.url)('vue')

test('nested overlays suspend their predecessors, preserve explicit layers and restore in reverse order', () => {
  const navigation = createOverlayNavigation()
  const states = new Map()
  const entry = zIndex => { const item = { zIndex, update: state => states.set(item, state) }; return item }
  const detail = entry(1200), edit = entry(999), confirm = entry()
  navigation.open(detail)
  navigation.open(edit)
  navigation.open(confirm)
  assert.deepEqual(states.get(detail), { suspended: true, layer: 1200 })
  assert.deepEqual(states.get(edit), { suspended: true, layer: 1210 })
  assert.deepEqual(states.get(confirm), { suspended: false, layer: 1220 })
  navigation.close(confirm)
  assert.equal(states.get(edit).suspended, false)
  navigation.close(edit)
  assert.equal(states.get(detail).suspended, false)
})

test('updating an open parent does not bring it forward and removed parents never reopen', () => {
  const navigation = createOverlayNavigation()
  const states = new Map()
  const parent = { update: state => states.set('parent', state) }
  const child = { update: state => states.set('child', state) }
  navigation.open(parent)
  navigation.open(child)
  navigation.open(parent)
  assert.equal(states.get('parent').suspended, true)
  navigation.close(parent)
  assert.equal(states.get('child').suspended, false)
  navigation.close(child)
  assert.equal(states.get('parent').suspended, true)
})

function setup() {
  const Surface = { props: ['visible', 'className', 'wrapClassName', 'maskStyle', 'wrapStyle', 'keyboard', 'zIndex'], render: h => h('div') }
  const Drawer = { name: 'ADrawer', props: Surface.props, render(h) { return h(Surface, { props: { ...this.$props, className: this.wrapClassName } }, this.$slots.default) } }
  const Modal = { name: 'AModal', props: Surface.props, render(h) { return h(Surface, { props: this.$props }, this.$slots.default) } }
  installOverlayNavigation(Vue, [Drawer, Modal])
  return { Drawer, Modal }
}

test('drawers and modals suspend without changing visible, destroying content or emitting close', async () => {
  const { Drawer, Modal } = setup()
  const parent = new Vue({ ...Drawer, propsData: { visible: true, wrapClassName: 'original', wrapStyle: { color: 'red' }, keyboard: true } })
  let closes = 0
  parent.$on('close', () => closes++)
  const child = new Vue({ ...Modal, propsData: { visible: true } })
  await Vue.nextTick()
  const vnode = parent._render()
  assert.equal(parent.visible, true)
  assert.equal(parent._isDestroyed, false)
  assert.equal(vnode.componentOptions.propsData.keyboard, false)
  assert.match(vnode.componentOptions.propsData.className, /original umm-overlay-suspended/)
  assert.equal(vnode.componentOptions.propsData.maskStyle.display, 'none')
  assert.equal(vnode.componentOptions.propsData.wrapStyle.color, 'red')
  child.$destroy()
  await Vue.nextTick()
  const restored = parent._render().componentOptions.propsData
  assert.equal(restored.className, 'original')
  assert.equal(restored.keyboard, true)
  assert.equal(parent.visible, true)
  assert.equal(closes, 0)
  parent.$destroy()
})

test('modal presentation preserves classes and styles and deactivated pages leave the stack', async () => {
  const { Drawer, Modal } = setup()
  const parent = new Vue({ ...Modal, propsData: { visible: true, wrapClassName: 'centered', maskStyle: { opacity: 0.5 } } })
  const child = new Vue({ ...Drawer, propsData: { visible: true } })
  assert.match(parent._render().componentOptions.propsData.wrapClassName, /centered umm-overlay-suspended/)
  assert.equal(parent._render().componentOptions.propsData.maskStyle.opacity, 0.5)
  child.$options.deactivated.forEach(hook => hook.call(child))
  assert.equal(parent.overlaySuspended, false)
  assert.equal(child.overlaySuspended, true)
  child.$options.activated.forEach(hook => hook.call(child))
  assert.equal(parent.overlaySuspended, true)
  parent.$destroy()
  child.$destroy()
  const next = new Vue({ ...Modal, propsData: { visible: true } })
  assert.equal(next.overlaySuspended, false)
  next.$destroy()
})
