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
    async GenerateRoutes({ commit }, data = {}) {
      const { generatorDynamicRouter } = await import('/src/router/generator-routers.js')
      const routers = await generatorDynamicRouter(data.routers)
      commit('SET_ROUTERS', routers)
      return routers
    },
    async RefreshNavigation({ dispatch }) {
      const session = await dispatch('BootstrapSession')
      const routes = await dispatch('GenerateRoutes', { routers: session.routers })
      const { default: router } = await import('/src/router/index.js')
      routes.forEach(route => router.addRoute(route))
      return routes
    }
  }
}

export default permission
