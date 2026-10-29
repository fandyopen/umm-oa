import Vue from 'vue'
import Vuex from 'vuex'

import app from './modules/app'
import user from './modules/user'
import createdForm from './modules/created-form'
import permission from './modules/async-router'
import getters from './getters'

Vue.use(Vuex)

export default new Vuex.Store({
  modules: {
    app,
    user,
    permission,
    createdForm
  },
  state: {},
  mutations: {},
  actions: {},
  getters
})
