import request from '/src/utils/axios/request.js'

// 查询菜单列表
export function listMenu(query) {
  return request({
    url: '/system/module/list',
    method: 'get',
    params: query
  })
}

// 查询菜单详细
export function getMenu(menuId) {
  return request({
    url: '/system/module/' + menuId,
    method: 'get'
  })
}

// 查询模块动作
export function getModuleActions(moduleId, query = {}) {
  return request({
    url: '/system/module/' + moduleId + '/actions',
    method: 'get',
    params: query
  })
}

export function createModuleAction(moduleId, data) {
  return request({
    url: '/system/module/' + moduleId + '/actions',
    method: 'post',
    data
  })
}

export function updateModuleAction(moduleId, actionCode, data) {
  return request({
    url: '/system/module/' + moduleId + '/actions/' + actionCode,
    method: 'put',
    data
  })
}

export function deleteModuleAction(moduleId, actionCode) {
  return request({
    url: '/system/module/' + moduleId + '/actions/' + actionCode,
    method: 'delete'
  })
}

// 查询菜单下拉树结构
export function treeselect() {
  return request({
    url: '/system/menu/treeselect',
    method: 'get'
  })
}

// 根据角色ID查询菜单下拉树结构
export function roleMenuTreeselect(roleId) {
  return request({
    url: '/system/menu/roleMenuTreeselect/' + roleId,
    method: 'get'
  })
}

// 新增菜单
export function addMenu(data) {
  return request({
    url: '/system/module',
    method: 'post',
    data: data
  })
}

// 修改菜单
export function updateMenu(data) {
  return request({
    url: '/system/module',
    method: 'put',
    data: data
  })
}

// 删除菜单
export function delMenu(menuId) {
  return request({
    url: '/system/module/' + menuId,
    method: 'delete'
  })
}

// 用户常用菜单API
// 获取用户常用应用(构建菜单之后的)

// 添加用户常用菜单

// 删除常用应用菜单

// 根据角色ID查询菜单下拉树结构
export function roleMenuTree(params) {
  return request({
    url: '/app/user/roleMenuTree',
    method: 'get',
    params
  })
}

// 根据部门ID查询菜单下拉树结构
export function deptMenuTree(params) {
  return request({
    url: '/app/user/deptMenuTree',
    method: 'get',
    params
  })
}

// 根据用户ID查询菜单下拉树结构
export function userMenuTree(params) {
  return request({
    url: '/app/user/userMenuTree',
    method: 'get',
    params
  })
}
