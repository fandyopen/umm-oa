import Vue from 'vue'
import App from './App'
import Shell from './components/Shell.vue'
import { go, toast } from './services/runtime'
Vue.config.productionTip = false
Vue.component('app-shell', Shell)
Vue.prototype.$go = go
Vue.prototype.$toast = toast
App.mpType = 'app'
new Vue({ ...App }).$mount()
