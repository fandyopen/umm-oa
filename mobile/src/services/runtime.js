import Vue from 'vue'
import { catalog, actionAllowed, moduleVisible } from './catalog'

const tokenKey = 'umm:mobile:token'
export const state = Vue.observable({ session: null, contexts: {}, theme: '#0eb3a2', ready: false })
let bootstrapPromise
let authGeneration = 0
export const toast = (title) => uni.showToast({ title: String(title || '操作失败'), icon: 'none', duration: 2500 })
export function go(path, params = {}) {
  const query = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => encodeURIComponent(k) + '=' + encodeURIComponent(v))
    .join('&')
  uni.navigateTo({ url: '/pages/' + path + '/index' + (query ? '?' + query : '') })
}
export function clearSession() {
  authGeneration++
  uni.removeStorageSync(tokenKey)
  state.session = null
  state.contexts = {}
  state.ready = false
  bootstrapPromise = null
  submissions.clear()
}
export function logout() {
  clearSession()
  uni.reLaunch({ url: '/pages/login/index' })
}
export function baseUrl() {
  // H5 uses the same-origin proxy. Mini programs require an HTTPS server domain.
  let base = '/api'
  // #ifndef H5
  base = process.env.VUE_APP_API_BASE || ''
  // #endif
  if (!base) throw new Error('请先配置小程序 VUE_APP_API_BASE HTTPS 接口地址')
  return base.replace(/\/$/, '')
}
export function request(url, method = 'GET', data) {
  return new Promise((resolve, reject) => {
    if (!/^\/[A-Za-z0-9_/?=&.%:,\-]+$/.test(url)) return reject(new Error('接口地址无效'))
    const payload =
      method === 'GET' && data
        ? Object.fromEntries(Object.entries(data).filter(([, value]) => value !== '' && value !== undefined && value !== null))
        : data
    uni.request({
      url: baseUrl() + url,
      method,
      data: payload,
      header: { Authorization: 'Bearer ' + (uni.getStorageSync(tokenKey) || ''), 'Content-Type': 'application/json' },
      timeout: 25000,
      success(res) {
        const body = res.data || {}
        if (res.statusCode === 401 || Number(body.code) === 401) {
          logout()
          return reject(new Error('登录已失效，请重新登录'))
        }
        if (res.statusCode >= 400 || (body.code !== undefined && Number(body.code) !== 200))
          return reject(new Error(body.msg || '请求失败（' + res.statusCode + '）'))
        resolve(body)
      },
      fail() {
        reject(new Error('网络连接失败，请检查服务地址后重试'))
      }
    })
  })
}
export async function login(values) {
  const result = await request('/login', 'POST', values)
  uni.setStorageSync(tokenKey, result.token)
  try {
    await bootstrap(true)
  } catch (error) {
    clearSession()
    throw error
  }
}
export async function bootstrap(refresh = false) {
  if (!uni.getStorageSync(tokenKey)) {
    logout()
    throw new Error('请先登录')
  }
  if (bootstrapPromise) return bootstrapPromise
  if (state.ready && !refresh) return state.session
  const generation = authGeneration
  bootstrapPromise = (async () => {
    const session = await request('/auth/session')
    const components = [...new Set(Object.values(catalog).map((item) => item.component))]
    const contexts = await request('/system/module-context', 'GET', { components: components.join(',') })
    if (generation !== authGeneration) throw new Error('登录状态已变更')
    state.session = session
    state.contexts = contexts.data || {}
    state.ready = true
    state.theme = uni.getStorageSync('umm:mobile:theme:' + session.user.userName) || '#0eb3a2'
    return session
  })().finally(() => {
    bootstrapPromise = null
  })
  return bootstrapPromise
}
export const context = (key) => state.contexts[catalog[key] && catalog[key].component]
export const visible = (key) => !!state.session && moduleVisible(state.session, context(key))
export const can = (key, action) => visible(key) && actionAllowed(state.session, context(key), action)
export async function requireModule(key, action) {
  await bootstrap(true)
  if (!catalog[key] || !visible(key) || (action && !can(key, action))) throw new Error('没有该模块或操作的权限，请联系管理员授权')
}
export function setTheme(color) {
  if (!/^#[0-9a-f]{6}$/i.test(color)) return
  state.theme = color
  uni.setStorageSync('umm:mobile:theme:' + state.session.user.userName, color)
}
const submissions = new Map()
export async function submitAction(key, actionCode, payload) {
  await requireModule(key, actionCode)
  const options = { moduleId: context(key).moduleId, actionCode, payload }
  const fingerprint = JSON.stringify(options)
  let submission = submissions.get(fingerprint)
  if (submission && submission.promise) return submission.promise
  if (!submission) {
    submission = { submissionKey: 'mobile_' + Date.now().toString(36) + Math.random().toString(36).slice(2) }
    submissions.set(fingerprint, submission)
  }
  submission.promise = request('/workflow/action/execute', 'POST', { ...options, submissionKey: submission.submissionKey })
    .then((response) => {
      if (!response.data || !['completed', 'pendingApproval'].includes(response.data.status)) throw new Error('提交结果异常，请重试')
      submissions.delete(fingerprint)
      return response
    })
    .finally(() => {
      submission.promise = null
    })
  return submission.promise
}
export function chooseUpload(url = '/common/upload') {
  return new Promise((resolve, reject) =>
    uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      success(selection) {
        uni.uploadFile({
          url: baseUrl() + url,
          filePath: selection.tempFilePaths[0],
          name: 'file',
          header: { Authorization: 'Bearer ' + uni.getStorageSync(tokenKey) },
          success(result) {
            try {
              const data = JSON.parse(result.data)
              if (result.statusCode === 401 || Number(data.code) === 401) {
                logout()
                throw new Error('登录已失效，请重新登录')
              }
              if (result.statusCode !== 200 || Number(data.code) !== 200) throw new Error(data.msg || '上传失败')
              resolve(data)
            } catch (error) {
              reject(error)
            }
          },
          fail: () => reject(new Error('上传失败，请重试'))
        })
      },
      fail: () => reject(new Error('未选择文件'))
    })
  )
}

export function openFile(path, name = '业务附件') {
  if (!/^\/(?:profile\/|project\/(?:recruitment|hr)\/)/.test(path) || path.includes('..') || /[?#\\]/.test(path))
    return toast('文件地址无效')
  uni.showLoading({ title: '加载附件' })
  uni.downloadFile({
    url: baseUrl() + path,
    header: { Authorization: 'Bearer ' + uni.getStorageSync(tokenKey) },
    success(result) {
      if (result.statusCode !== 200) {
        toast('附件不存在或无访问权限')
        return
      }
      // #ifdef H5
      const link = document.createElement('a')
      link.href = result.tempFilePath
      link.download = name
      link.click()
      // #endif
      // #ifndef H5
      if (/\.(png|jpe?g|gif|webp)$/i.test(name)) uni.previewImage({ urls: [result.tempFilePath] })
      else uni.openDocument({ filePath: result.tempFilePath, showMenu: true, fail: () => toast('无法预览此格式，请在平台端下载') })
      // #endif
    },
    fail: () => toast('附件加载失败，请重试'),
    complete: () => uni.hideLoading()
  })
}
