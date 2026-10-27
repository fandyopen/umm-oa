import store from '/src/store/index.js'
import { hasModuleAction } from '/src/utils/modulePermission.js'

export default {
  inserted(el, binding, vnode) {
    const value = Array.isArray(binding.value) ? binding.value[0] : binding.value
    if (!value) {
      throw new Error('请设置模块动作权限值')
    }
    const permissions = store.getters.permissions || []
    const meta = vnode.context && vnode.context.$route ? vnode.context.$route.meta : null
    if (!hasModuleAction(permissions, meta, value)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}
