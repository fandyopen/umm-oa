import Vue from 'vue'
import i18n from '/src/locales/index.js'

let runtimePromise

export function loadApplicationRuntime() {
  if (!runtimePromise) {
    runtimePromise = Promise.all([
      import('/src/plugins/ui/index.js'),
      import('/src/plugins/dayjs/index.js'),
      import('/src/plugins/viewerjs/index.js'),
      import('/src/plugins/global/index.js'),
      import('/src/directive/index.js'),
      import('/src/assets/style/global.less'),
      import('moment').then(module =>
        import('moment/dist/locale/zh-cn').then(() => module.default.locale('zh-cn'))
      ),
      import('qs')
    ]).then(async ([, , , , , , , qsModule]) => {
      Vue.prototype.$qs = qsModule.default
      const [antLocale, momentLocale] = await Promise.all([
        import('ant-design-vue/es/locale-provider/zh_CN'),
        import('moment/locale/zh-cn')
      ])
      i18n.setLocaleMessage('zh-CN', {
        ...i18n.getLocaleMessage('zh-CN'),
        antLocale: antLocale.default,
        momentLocale: momentLocale.default
      })
    })
  }
  return runtimePromise
}
