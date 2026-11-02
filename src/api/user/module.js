import request from '/src/utils/axios/request.js'

export const resolveModuleContexts = components =>
  request({
    url: '/system/module-context',
    method: 'get',
    silentError: true,
    params: { components: components.join(',') }
  })

// 获取路由
export const getRouters = () => {
  return request({
    url: '/getRouters',
    method: 'get'
  })
}
