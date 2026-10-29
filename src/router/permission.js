import router from '/src/router/index.js'
import store from '/src/store/index.js'
import storage from 'store'
import NProgress from 'nprogress'
import '/src/assets/style/nprogress.less'
import { setDocumentTitle, domTitle } from '/src/utils/common/domUtil'
import { ACCESS_TOKEN } from '/src/store/mutation-types.js'

NProgress.configure({ showSpinner: false })

const allowList = ['login']
const loginRoutePath = '/user/login'
const defaultRoutePath = '/index'
let sessionBootstrapPromise = null

function addDynamicRoutes(routes) {
  routes.forEach(route => router.addRoute(route))
}

async function ensureSessionReady() {
  if (store.getters.roles.length > 0 && store.getters.addRouters.length > 0) {
    return
  }
  if (!sessionBootstrapPromise) {
    sessionBootstrapPromise = store.dispatch('BootstrapSession')
      .then(session => store.dispatch('GenerateRoutes', { routers: session.routers }))
      .then(routes => addDynamicRoutes(routes))
      .catch(error => {
        sessionBootstrapPromise = null
        throw error
      })
  }
  return sessionBootstrapPromise
}

router.beforeEach(async (to, from, next) => {
  NProgress.start()
  if (to.meta && typeof to.meta.title !== 'undefined') {
    setDocumentTitle(`${to.meta.title} - ${domTitle}`)
  }

  if (!storage.get(ACCESS_TOKEN)) {
    if (allowList.includes(to.name)) {
      next()
    } else {
      next({ path: loginRoutePath, query: { redirect: to.fullPath } })
      NProgress.done()
    }
    return
  }

  if (to.path === loginRoutePath || to.path === '/') {
    next({ path: defaultRoutePath })
    return
  }

  try {
    const needsBootstrap = store.getters.roles.length === 0 || store.getters.addRouters.length === 0
    await ensureSessionReady()
    next(needsBootstrap ? { ...to, replace: true } : undefined)
  } catch (error) {
    await store.dispatch('Logout').catch(() => {})
    next({ path: loginRoutePath, query: { redirect: to.fullPath } })
  }
})

router.afterEach(() => {
  NProgress.done()
})
