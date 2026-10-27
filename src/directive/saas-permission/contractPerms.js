/**
 * saas通讯录权限
 */
import store from '/src/store/index.js'

const contractPerms = {
  inserted(el) {
    const perms = store.getters.userPermission || {}
    const roles = store.getters.roles || []
    const globalPermissions = store.getters.permissions || []
    const isSuperAdmin = roles.includes('admin') || globalPermissions.includes('*:*:*')

    // 只要组织架构 角色其一可见 通讯录就可见
    const allowed = isSuperAdmin || perms.has_team_read_perms || perms.has_role_read_perms

    if (!allowed) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}

export default contractPerms
