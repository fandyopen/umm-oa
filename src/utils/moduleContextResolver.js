// The component path identifies a business module. Numeric IDs come only from server metadata.
export function normalizeModuleContext(value, component) {
  if (!value || value.component !== component) return null
  if (!['number', 'string'].includes(typeof value.moduleId)) return null
  if (typeof value.moduleId === 'string' && !/^[0-9]+$/.test(value.moduleId.trim())) return null
  const moduleId = Number(value.moduleId)
  if (!Number.isSafeInteger(moduleId) || moduleId <= 0) return null
  if (value.status != null && String(value.status) !== '0') return null
  return { ...value, moduleId, perms: value.perms || '' }
}

export function createModuleContextResolver(fetchModules, state = { modules: {} }) {
  let generation = 0
  const pending = new Map()
  const get = (component, candidates = []) => {
    for (const candidate of candidates) {
      const meta = normalizeModuleContext(candidate, component)
      if (meta) return meta
    }
    return state.modules[component] || null
  }
  const ensure = async (components, candidates = [], { retryMissing = false } = {}) => {
    const selectors = [...new Set(components)]
    const missing = selectors.filter(component => !get(component, candidates) && (!Object.prototype.hasOwnProperty.call(state.modules, component) || (retryMissing && state.modules[component] === null)) && !pending.has(component))
    if (missing.length) {
      const currentGeneration = generation
      const request = Promise.resolve()
        .then(() => fetchModules(missing))
        .then(rows => {
          if (currentGeneration !== generation) throw new Error('会话已变化，请重新加载模块信息')
          if (!rows || typeof rows !== 'object' || Array.isArray(rows)) throw new Error('服务端返回的模块信息无效')
          const resolved = {}
          for (const component of missing) {
            const row = rows && rows[component]
            const context = normalizeModuleContext(row, component)
            if (row && !context) throw new Error('服务端返回的模块信息无效：' + component)
            resolved[component] = context
          }
          state.modules = { ...state.modules, ...resolved }
        })
        .finally(() => {
          for (const component of missing) {
            if (pending.get(component) === request) pending.delete(component)
          }
        })
      missing.forEach(component => pending.set(component, request))
    }
    await Promise.all([
      ...new Set(
        selectors
          .filter(component => !get(component, candidates))
          .map(component => pending.get(component))
          .filter(Boolean)
      )
    ])
    return Object.fromEntries(selectors.map(component => [component, get(component, candidates)]))
  }
  return {
    get,
    ensure,
    reset() {
      generation++
      pending.clear()
      state.modules = {}
    }
  }
}
