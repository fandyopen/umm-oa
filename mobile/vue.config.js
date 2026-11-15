const path = require('path')
module.exports = {
  configureWebpack: {
    resolve: {
      alias: {
        'core-js': path.resolve(__dirname, 'node_modules/core-js'),
        '@babel/runtime': path.resolve(__dirname, 'node_modules/@babel/runtime')
      }
    }
  },
  transpileDependencies: ['@dcloudio/uni-ui'],
  devServer: {
    host: '0.0.0.0',
    port: 3001,
    proxy: {
      '/api': { target: process.env.MOBILE_PROXY_TARGET || 'http://127.0.0.1:8080', changeOrigin: true, pathRewrite: { '^/api': '' } }
    }
  }
}
