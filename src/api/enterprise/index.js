import request from '/src/utils/axios/request.js'

// 通讯录 部门树
export function getTreeMenu(query) {
  return request({
    url: '/system/dept/admin/tree',
    method: 'get',
    params: query
  })
}
// 全部成员
export function getUserList(query) {
  return request({
    url: '/system/user/list',
    method: 'get',
    params: query
  })
}
// 离职成员
export function getDismissList(params) {
  return request({
    url: '/system/user/dismiss_list',
    method: 'get',
    params
  })
}

// 拖拽接口
export function deptsort(params) {
  return request({
    url: '/system/dept/sort',
    method: 'get',
    params
  })
}
// 修改部门名称
export function editDeptName(data) {
  return request({
    url: '/system/dept',
    method: 'put',
    data
  })
}

// 添加子部门
export function addSystemDepchild(data) {
  return request({
    url: '/system/dept',
    method: 'post',
    data
  })
}

// 设置部门主管
export function setDeptPrincipal(deptId, userId) {
  return request({
    url: '/system/dept/' + deptId + '/principal',
    method: 'put',
    data: { userId }
  })
}

// 取消部门主管
export function clearDeptPrincipal(deptId, userId) {
  return request({
    url: '/system/dept/' + deptId + '/principal',
    method: 'delete',
    params: { userId }
  })
}

// 根据权限查询角色
export function getRoleByAdmin() {
  return request({
    url: '/system/role/admin/list',
    method: 'get'
  })
}
// 查询角色组
export function getRoleListClass(params) {
  return request({
    url: '/system/role_group/group_list',
    method: 'get',
    params
  })
}

// 查询角色下有那些成员
export function getRoleMemberList(params) {
  return request({
    url: '/system/role/list_member',
    method: 'get',
    params
  })
}

// 角色添加成员
export function addRoleinMember(data) {
  return request({
    url: '/system/role/add_member',
    method: 'post',
    data
  })
}
// 角色删除成员
export function deleteRoleinMember(data) {
  return request({
    url: '/system/role/remove_member',
    method: 'delete',
    data
  })
}

// 角色组新增/修改
export function updateRoleGroup(data) {
  return request({
    url: '/system/role_group/group_update',
    method: 'post',
    data
  })
}

// 角色修改
export function updateRole(data) {
  return request({
    url: '/system/role/update',
    method: 'post',
    data
  })
}

// 角色新增
export function insertRole(data) {
  return request({
    url: '/system/role/insert',
    method: 'post',
    data
  })
}

// 查询固定超级管理员
export function queryStstemAdmin() {
  return request({
    url: '/system/admin/query/admin',
    method: 'get'
  })
}

// 创建空白应用
export function createdNewApp(data) {
  return request({
    url: '/app/user/self/app',
    method: 'post',
    data
  })
}

// 用户修改应用
export function updateApp(data) {
  return request({
    url: '/app/user/update',
    method: 'put',
    data
  })
}

// 用户删除应用
export function deleteAppItem(params) {
  return request({
    url: '/app/user/delete/' + params,
    method: 'delete'
  })
}

// 添加模块权限
export function addMenuPerm(data) {
  return request({
    url: '/app/user/addModule',
    method: 'post',
    data
  })
}
