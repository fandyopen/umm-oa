import contractPerms from './contractPerms'
import deptPerms from './deptPerms'
import userPerms from './userPerms'

const install = function (Vue) {
  Vue.directive('contractPerms', contractPerms)
  Vue.directive('deptPerms', deptPerms)
  Vue.directive('userPerms', userPerms)
}

if (window.Vue) {
  window['contractPerms'] = contractPerms
  window['deptPerms'] = deptPerms
  window['userPerms'] = userPerms
  Vue.use(install) // eslint-disable-line
}

export default install
