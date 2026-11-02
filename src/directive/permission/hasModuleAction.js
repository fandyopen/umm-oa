import store from '/src/store/index.js'
import { hasModuleAction } from '/src/utils/modulePermission.js'
import { getModuleActionConfiguration } from '/src/utils/moduleActionConfig.js'

function updateAction(el, binding, vnode) {
  const value = Array.isArray(binding.value) ? binding.value[0] : binding.value
  if (!value) throw new Error('请设置模块动作权限值')
  const routeMeta = vnode.context && vnode.context.$route ? vnode.context.$route.meta : null
  const action = typeof value === 'object' ? value.actionCode : value
  const meta = typeof value === 'object' ? { moduleId: value.moduleId, perms: value.perms || '' } : routeMeta
  const allowed = (!meta?.moduleId && typeof value === 'object' && value.allowUnresolved === true) || hasModuleAction(store.getters.permissions || [], meta, action)
  el.style.display = allowed ? el.__actionDisplay || '' : 'none'
  if (!allowed || !meta) return
  const configuration = getModuleActionConfiguration(meta.moduleId, action)
  if (!configuration || !configuration.actionName) return
  // Replace only the existing label; preserve icons and component listeners.
  const label = Array.from(el.childNodes).find(node => (node.nodeType === 3 && node.textContent.trim()) || (node.nodeType === 1 && node.tagName === 'SPAN' && !node.children.length && node.textContent.trim()))
  if (label) label.textContent = configuration.actionName
}

export default {
  inserted(el, binding, vnode) {
    el.__actionDisplay = el.style.display
    updateAction(el, binding, vnode)
  },
  componentUpdated: updateAction
}
