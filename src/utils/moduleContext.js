import Vue from 'vue'
import { resolveModuleContexts } from '/src/api/user/module.js'
import { hasModuleAction } from './modulePermission.js'
import { createModuleContextResolver } from './moduleContextResolver.js'
import { withModuleActionGuards } from './moduleActionGuard.js'
import { executeWorkflowAction } from '/src/api/workflow/action.js'
import { submitModuleWorkflowAction } from './workflowActionSubmit.js'

const state = Vue.observable({ modules: {} })
const resolver = createModuleContextResolver(async components => {
  const response = await resolveModuleContexts(components)
  return response.data
}, state)

export const resetModuleContexts = () => resolver.reset()

// First binding is the owning module. Other bindings are explicit cross-module dependencies.
export function withModuleContexts(definitions, actions = {}) {
  const bindings = Object.fromEntries(Object.entries(definitions).map(([key, value]) => [key, typeof value === 'string' ? value : value.component]))
  const primary = Object.keys(bindings)[0]
  return {
    mixins: [withModuleActionGuards(actions)],
    props: { moduleMeta: { type: Object, default: null } },
    computed: {
      moduleContexts() {
        return Object.fromEntries(Object.entries(bindings).map(([key, component]) => [key, resolver.get(component, this.moduleContextCandidates(key))]))
      }
    },
    created() {
      this.ensureModuleContexts().catch(() => {})
    },
    methods: {
      moduleContextCandidates(key) {
        // A passed context belongs to the owning module, never a cross-module dependency.
        const supplied = key === primary && this.moduleMeta ? { component: bindings[key], ...this.moduleMeta } : null
        return [supplied, this.$route && this.$route.meta]
      },
      async ensureModuleContexts(keys = Object.keys(bindings)) {
        const candidates = keys.flatMap(key => this.moduleContextCandidates(key))
        await resolver.ensure(
          keys.map(key => bindings[key]),
          candidates
        )
        return this.moduleContexts
      },
      canModule(key, action) {
        return hasModuleAction(this.$store.getters.permissions || [], this.moduleContexts[key], action)
      },
      canViewModule(key) {
        const meta = this.moduleContexts[key]
        const permissions = this.$store.getters.permissions || []
        return !!meta && (permissions.includes('*:*:*') || (!!meta.perms && permissions.includes(meta.perms)))
      },
      moduleActionBinding(key, actionCode) {
        return { ...this.moduleContexts[key], actionCode, allowUnresolved: true }
      },
      showModuleAction(key, action = 'view') {
        // An unresolved context is not a permission denial. Clicking still requires a successful check.
        return !this.moduleContexts[key] || (action === 'view' ? this.canViewModule(key) : this.canModule(key, action))
      },
      async checkModuleAction({ module: key, action = 'view', label }) {
        const component = bindings[key]
        const moduleName = definitions[key].label || this.moduleContexts[key]?.moduleName || key
        const unavailable = reason => {
          this.$message.warning(label + '暂不可用：' + reason)
          return false
        }
        try {
          await resolver.ensure([component], this.moduleContextCandidates(key), { retryMissing: true })
        } catch (error) {
          if (this._isDestroyed) return false
          return unavailable(error.code === 409 ? moduleName + '模块配置重复，请联系管理员。' : moduleName + '模块信息加载失败，请重试。')
        }
        if (this._isDestroyed) return false
        if (!this.moduleContexts[key]) return unavailable(moduleName + '模块未配置或已停用，请联系管理员。')
        const allowed = action === 'view' ? this.canViewModule(key) : this.canModule(key, action)
        if (!allowed) return unavailable('你没有该操作的权限。')
        return true
      },
      submitConfiguredAction(actionCode, payload, label, module = primary) {
        return submitModuleWorkflowAction(this, executeWorkflowAction, { primaryModule: primary, module, actionCode, payload, label })
      }
    }
  }
}
