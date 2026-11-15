const path = require('path')
module.exports = {
  parser: require('postcss-comment'),
  plugins: [
    require('@dcloudio/vue-cli-plugin-uni/packages/postcss')({
      uniApp: require(path.resolve(__dirname, 'src/manifest.json'))
    })
  ]
}
