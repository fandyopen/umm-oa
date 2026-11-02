import request from '/src/utils/axios/request.js'

// 查询部门下拉树结构（无权限）
export function allTree() {
  return request({
    url: '/system/dept/allTree',
    method: 'get'
  })
}

// 删除部门
export function delDept(deptId) {
  return request({
    url: '/system/dept/' + deptId,
    method: 'delete'
  })
}
