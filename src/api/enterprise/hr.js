import request from '/src/utils/axios/request.js'
export const hr = (path, method = 'get', data) => request({ url: '/project/hr' + path, method, ...(method === 'get' ? { params: data } : { data }) })
