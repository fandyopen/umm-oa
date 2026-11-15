module.exports = {
  presets: [['@vue/cli-plugin-babel/preset', { useBuiltIns: 'usage', corejs: 3 }]],
  plugins: process.env.UNI_PLATFORM === 'h5' ? [] : ['@babel/plugin-transform-runtime']
}
