/**
 * saas组织架构权限
 *
 * v-dept-perms="'read'"    组织架构可见
 * v-dept-perms="'manage'"  组织架构可维护（新增/修改/删除成员与部门）
 *
 * 判定字段统一来自 /crm/user/get_user_info 的 perms，
 * 由后端 OrganizationPermissionService 按 app.permission.org-strategy 产出。
 */
import store from '/src/store/index.js'

const deptPerms = {
  inserted(el, binding) {
    const value =
      typeof binding.value === 'string' ? binding.value : binding.value && binding.value.bindingPerms
    const perms = store.getters.userPermission || {}
    const roles = store.getters.roles || []
    const globalPermissions = store.getters.permissions || []
    const isSuperAdmin = roles.includes('admin') || globalPermissions.includes('*:*:*')

    const teamRead = isSuperAdmin || perms.has_team_read_perms
    const teamManage = isSuperAdmin || perms.has_team_manage_perms
    const roleRead = isSuperAdmin || perms.has_role_read_perms

    const allowed = value === 'manage' ? teamManage : teamRead || roleRead

    if (!allowed) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}

export default deptPerms
