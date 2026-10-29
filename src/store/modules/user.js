import storage from 'store'
import { login, getSession, logout } from '/src/api/user/login.js'
import { registered } from '/src/api/user/registered.js'
import { ACCESS_TOKEN, TENANT_ID } from '/src/store/mutation-types.js'
import { getEnterprise, addEnterprise, toggleEnterprise } from '/src/api/user/enterprise.js'
import { getAppList } from '/src/api/user/app.js'
import { isEmpty } from '/src/utils/common/util.js'

const user = {
  state: {
    token: '',
    name: '',
    userName: '',
    welcome: '',
    avatar: '',
    roles: [],
    permissions: [],
    info: {},
    sex: '',
    posts: '',
    postGroup: '',
    todoTotal: '',
    userPermission: {},
    tenantAppId: null,
    defaultCompany: '',
    docsEnabled: true,
    docsTitle: '使用文档'
  },

  mutations: {
    SET_TOKEN: (state, token) => { state.token = token },
    SET_NAME: (state, name) => { state.name = name },
    SET_USER_NAME: (state, userName) => { state.userName = userName },
    SET_AVATAR: (state, avatar) => { state.avatar = avatar },
    SET_ROLES: (state, roles) => { state.roles = roles },
    SET_INFO: (state, info) => { state.info = info },
    SET_PERMISSIONS: (state, permissions) => { state.permissions = permissions },
    SET_SEX: (state, sex) => { state.sex = sex },
    SET_POSTS: (state, posts) => { state.posts = posts },
    SET_POSTSGROUP: (state, postGroup) => { state.postGroup = postGroup },
    SET_TODOTOTAL: (state, todoTotal) => {
      state.todoTotal = todoTotal
      storage.set('todoTotal', todoTotal)
    },
    SET_USERPERMISSION: (state, userPermission) => { state.userPermission = userPermission },
    SET_TENANT_APP_ID: (state, tenantAppId) => {
      state.tenantAppId = tenantAppId
      storage.set('TENANT_APP_ID', tenantAppId, 0)
    },
    SET_DEFAULT_COMPANY: (state, defaultCompany) => {
      state.defaultCompany = defaultCompany
      storage.set('DEFAULT_COMPANY', defaultCompany, 0)
    },
    SET_DOCS_CONFIG: (state, docs = {}) => {
      const enabled = String(docs.enabled ?? 'true').trim().toLowerCase()
      state.docsEnabled = !['false', '0', 'n', 'disabled'].includes(enabled)
      state.docsTitle = String(docs.title || '使用文档')
    }
  },

  actions: {
    BootstrapSession({ commit }) {
      return getSession().then(res => {
        const currentUser = res.user || {}
        let avatar = null
        if (!isEmpty(currentUser.avatar)) {
          avatar = currentUser.avatar.slice(0, 5) === 'https'
            ? currentUser.avatar
            : import.meta.env.VITE_APP_BASE_API + currentUser.avatar
        }
        commit('SET_ROLES', res.roles && res.roles.length ? res.roles : ['ROLE_DEFAULT'])
        commit('SET_PERMISSIONS', res.permissions || [])
        commit('SET_NAME', currentUser.nickName || '')
        commit('SET_USER_NAME', currentUser.userName || '')
        commit('SET_AVATAR', avatar)
        commit('SET_SEX', currentUser.sex || '')
        commit('SET_POSTS', (res.posts || []).includes('ptyg') ? '0' : '1')
        commit('SET_DEFAULT_COMPANY', res.currentCompany || '')
        commit('SET_DOCS_CONFIG', res.docs || {})
        commit('SET_USERPERMISSION', res.organizationPermissions || {})
        storage.set(TENANT_ID, currentUser.tenantId_, 7 * 24 * 60 * 60 * 1000)
        return res
      })
    },
    Login({ commit }, userInfo) {
      return login(userInfo).then(res => {
        storage.set(ACCESS_TOKEN, res.token, 7 * 24 * 60 * 60 * 1000)
        commit('SET_TOKEN', res.token)
      })
    },
    _registered({ commit }, params) {
      return registered(params).then(res => {
        storage.set(ACCESS_TOKEN, res.token, 7 * 24 * 60 * 60 * 1000)
        commit('SET_TOKEN', res.token)
      })
    },
    _enterprise({ commit }) {
      return getEnterprise().then(res => {
        if (!isEmpty(res.data) && !storage.get('DEFAULT_COMPANY')) {
          commit('SET_DEFAULT_COMPANY', res.data[0].name)
        }
        return res
      })
    },
    _addEnterprise(context, data) {
      return addEnterprise(data)
    },
    _toggleEnterprise(context, id) {
      return toggleEnterprise(id)
    },
    Logout({ commit, state }) {
      return logout(state.token).finally(() => {
        commit('SET_TOKEN', '')
        commit('SET_ROLES', [])
        commit('SET_PERMISSIONS', [])
        commit('SET_USERPERMISSION', {})
        commit('SET_SEX', '')
        commit('SET_POSTS', '')
        commit('SET_TENANT_APP_ID', null)
        storage.remove(ACCESS_TOKEN)
        storage.remove('mima')
        storage.remove('todoTotal')
        storage.remove('TENANT_APP_ID')
        storage.remove(TENANT_ID)
      })
    },
    SetAppId() {
      return getAppList()
    }
  }
}

export default user
