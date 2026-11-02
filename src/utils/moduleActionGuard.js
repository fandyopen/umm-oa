// Declare checks next to a component's module dependencies. Only user action handlers are wrapped.
export function withModuleActionGuards(actions) {
  return {
    data: () => ({ moduleActionBusy: {} }),
    beforeCreate() {
      // Vue binds methods after beforeCreate. Copy the map to keep wrappers instance-local.
      const methods = { ...this.$options.methods }
      for (const [name, definition] of Object.entries(actions)) {
        const original = methods[name]
        if (typeof original !== 'function') throw new Error('未找到模块操作方法：' + name)
        methods[name] = async function (...args) {
          const check = typeof definition === 'function' ? definition.apply(this, args) : definition
          if (!check) return original.apply(this, args)
          if (this.moduleActionBusy[name]) return false
          this.$set(this.moduleActionBusy, name, true)
          try {
            if (!(await this.checkModuleAction(check)) || this._isDestroyed) return false
            return await original.apply(this, args)
          } finally {
            this.$set(this.moduleActionBusy, name, false)
          }
        }
      }
      this.$options.methods = methods
    }
  }
}
