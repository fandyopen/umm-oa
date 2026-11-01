import { getModuleActionConfiguration } from './moduleActionConfig.js'

export function moduleActionPermission(meta, action) {
  if (!meta || !meta.moduleId || !action) {
    return ''
  }
  const perms = meta.perms || ''
  const configuration = getModuleActionConfiguration(meta.moduleId, action)
  if (configuration && configuration.permissionCode) return configuration.permissionCode
  if (perms.endsWith(':list')) {
    return perms.slice(0, -5) + ':' + action
  }
  if (action === 'manage' && perms) {
    return perms
  }
  return `module:${meta.moduleId}:${action}`
}

export function hasModuleAction(permissions, meta, action) {
  const configuration = meta && getModuleActionConfiguration(meta.moduleId, action)
  if (configuration && String(configuration.status) !== '0') return false
  const permission = moduleActionPermission(meta, action)
  if (!permission) {
    return false
  }
  const granted = permissions || []
  const moduleManage = `module:${meta.moduleId}:manage`
  return granted.includes('*:*:*')
    || granted.includes(permission)
    || granted.includes(`module:${meta.moduleId}:${action}`)
    || granted.includes(moduleManage)
}
