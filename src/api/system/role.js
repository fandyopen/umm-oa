import request from '/src/utils/axios/request.js'

// 查询角色列表 权限选着
export function listRoleAll(query) {
  return request({
    url: '/system/role/list/all',
    method: 'get',
    params: query
  })
}

// 删除角色 saas版
export function delSaasRole(roleId) {
  return request({
    url: '/system/role/remove/' + roleId,
    method: 'delete'
  })
}

// 删除角色组 saas版
export function delRoleGroup(groupId) {
  return request({
    url: '/system/role_group/group_remove/' + groupId,
    method: 'delete'
  })
}
