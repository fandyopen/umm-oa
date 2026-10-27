import Vue from 'vue'
import App from './App.vue'
import router from './router/index.js'
import store from './store/index.js'
import i18n from './locales/index.js'
import bootstrap from './plugins/global/bootstrap.js'
import { loadApplicationRuntime } from './plugins/runtime/application.js'

Vue.config.productionTip = false

async function startApplication() {
  const isLoginPage = /\/user\/login(?:\/|$)/.test(window.location.pathname)
  if (!isLoginPage) {
    await loadApplicationRuntime()
  }

  await import('./router/permission.js')

  new Vue({
    router,
    store,
    i18n,
    created: bootstrap,
    render: h => h(App)
  }).$mount('#app')
}

startApplication()
