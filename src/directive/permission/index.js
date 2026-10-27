import hasRole from './hasRole'
import hasPermi from './hasPermi'
import hasModuleAction from './hasModuleAction'

const install = function (Vue) {
  Vue.directive('hasRole', hasRole)
  Vue.directive('hasPermi', hasPermi)
  Vue.directive('hasModuleAction', hasModuleAction)
}

if (window.Vue) {
  window['hasRole'] = hasRole
  window['hasPermi'] = hasPermi
  window['hasModuleAction'] = hasModuleAction
  Vue.use(install); // eslint-disable-line
}

export default install
