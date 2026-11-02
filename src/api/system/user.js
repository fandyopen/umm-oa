import request from '/src/utils/axios/request.js'

// 上传文件
export function commonUpload(data) {
  return request({
    url: '/common/upload',
    method: 'post',
    data
  })
}

// 不根据权限查询所有用户列表
export function queryAll(query) {
  return request({
    url: '/system/user/list/queryAll',
    method: 'get',
    params: query
  })
}

// 删除用户
export function delUser(userId) {
  return request({
    url: '/system/user/' + userId,
    method: 'delete'
  })
}

// 查询用户个人信息
export function getUserProfile() {
  return request({
    url: '/system/user/profile',
    method: 'get'
  })
}

// 修改用户个人信息
export function updateUserProfile(data) {
  return request({
    url: '/system/user/profile',
    method: 'put',
    data: data
  })
}

// 用户密码重置
export function updateUserPwd(oldPassword, newPassword) {
  const data = {
    oldPassword,
    newPassword
  }
  return request({
    url: '/system/user/profile/updatePwd',
    method: 'put',
    params: data
  })
}

// 用户头像上传
export function uploadAvatar(data) {
  return request({
    url: '/system/user/profile/avatar',
    method: 'post',
    data: data
  })
}

// 修改成员
export function editUser(data) {
  return request({
    url: '/system/user/edit',
    method: 'put',
    data: data
  })
}

// excel 导入成员
export function excelImport(data) {
  return request({
    url: '/system/user/excel_import',
    method: 'post',
    data: data
  })
}
