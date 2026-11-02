import request from '/src/utils/axios/request.js'

// 查询模块管理树（包含页面和按钮动作）
export function getModuleManagementTree() {
  return request({
    url: '/system/module/management-tree',
    method: 'get'
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

export function getNextModuleActionCode(moduleId) {
  return request({
    url: '/system/module/' + moduleId + '/actions/next-code',
    method: 'get'
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

// 根据角色ID查询菜单下拉树结构
export function roleMenuTree(params) {
  return request({
    url: '/app/user/roleMenuTree',
    method: 'get',
    params
  })
}
