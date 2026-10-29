import request from '/src/utils/axios/request.js'

const userApi = {
  Login: '/login',
  Logout: '/logout'
}

/**
 * login func
 * @param parameter
 * @returns {*}
 */
export async function login(parameter) {
  const { username, password, code, uuid, rememberMe } = parameter
  const { encrypt } = await import('/src/utils/encrypt/jsencrypt.js')
  return request({
    url: userApi.Login,
    method: 'post',
    data: {
      username,
      password: encrypt(password),
      code,
      uuid,
      rememberMe
    }
  })
}

export function getSession() {
  return request({
    url: '/auth/session',
    method: 'get'
  })
}

export function logout() {
  return request({
    url: userApi.Logout,
    method: 'delete',
    headers: {
      'Content-Type': 'application/json;charset=UTF-8'
    }
  })
}

// 获取验证码
export function getCodeImg() {
  return request({
    url: '/captchaImage',
    method: 'get'
  })
}
