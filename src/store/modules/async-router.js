/**
 * 向后端请求用户的菜单，动态生成路由
 */
const permission = {
  state: {
    routers: [],
    addRouters: []
  },
  mutations: {
    SET_ROUTERS: (state, routers) => {
      state.addRouters = routers
      state.routers = routers
    }
  },
  actions: {
    GenerateRoutes({ commit }, data = {}) {
      return new Promise(resolve => {
        import('/src/router/generator-routers.js').then(({ generatorDynamicRouter }) => {
          generatorDynamicRouter(data.routers).then(routers => {
            commit('SET_ROUTERS', routers)
            resolve(routers)
          })
        })
      })
    }
  }
}

export default permission
