export function moduleActionPermission(meta, action) {
  if (!meta || !meta.moduleId || !action) {
    return ''
  }
  const perms = meta.perms || ''
  if (perms.endsWith(':list')) {
    return perms.slice(0, -5) + ':' + action
  }
  if (action === 'manage' && perms) {
    return perms
  }
  return `module:${meta.moduleId}:${action}`
}

export function hasModuleAction(permissions, meta, action) {
  const permission = moduleActionPermission(meta, action)
  if (!permission) {
    return false
  }
  const granted = permissions || []
  const moduleManage = `module:${meta.moduleId}:manage`
  return granted.includes('*:*:*')
    || granted.includes(permission)
    || granted.includes(moduleManage)
}
