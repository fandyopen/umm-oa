import Vue from 'vue'
import Vuex from 'vuex'

import app from './modules/app'
import user from './modules/user'
import createdForm from './modules/created-form'
import permission from './modules/async-router'
import getters from './getters'
import appearance from './modules/appearance'

Vue.use(Vuex)

export default new Vuex.Store({
  modules: {
    app,
    appearance,
    user,
    permission,
    createdForm
  },
  state: {},
  mutations: {},
  actions: {},
  getters
})
