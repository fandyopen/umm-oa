<template>
  <div class="main">
    <template v-if="isNext">
      <form v-if="isLogin" class="loginForm" novalidate @submit.prevent="handleSubmit">
        <div class="title">登录</div>
        <div class="user-login-text">
          通过帐号密码登录，或者切换为<span class="toggle" @click="registered">注册</span>
        </div>
        <div v-if="isLoginError" class="login-error">{{ loginErrorInfo }}</div>

        <div class="formTitle">手机号/账户</div>
        <input v-model.trim="loginForm.username" class="login-input" autocomplete="username" placeholder="手机号/账户" />

        <div class="formTitle">密码</div>
        <input v-model="loginForm.password" class="login-input" type="password" autocomplete="current-password" placeholder="密码" />

        <div class="formTitle">验证码</div>
        <div class="codeContent">
          <input v-model.trim="loginForm.code" class="login-input codeInput" autocomplete="off" placeholder="验证码" />
          <img :src="codeUrl" class="codeImg" alt="验证码" @click="getCode" />
        </div>

        <button class="confirmBtn" type="submit" :disabled="logining">
          {{ logining ? '登录中...' : '登 录' }}
        </button>
      </form>

      <form v-else class="loginForm" novalidate @submit.prevent="nextTick">
        <div class="title">登录</div>
        <div class="user-login-text">
          通过手机号注册，或者切换为<span class="toggle" @click="goLogin">账号密码登录</span>
        </div>
        <div v-if="isLoginError" class="login-error">{{ loginErrorInfo }}</div>

        <div class="formTitle">手机号</div>
        <div class="p-a-c">
          <countryCodeSelector :countryCode.sync="areaCode" />
          <input v-model.trim="RegForm.username" class="login-input regPhoneInput" autocomplete="tel" placeholder="手机号" />
        </div>

        <div class="formTitle">密码</div>
        <input v-model="RegForm.password" class="login-input" type="password" autocomplete="new-password" placeholder="请输入密码" />

        <div class="formTitle">确认密码</div>
        <input v-model="RegForm.checkPass" class="login-input" type="password" autocomplete="new-password" placeholder="请再次输入密码" />

        <button class="confirmBtn" type="submit" :disabled="logining">下一步</button>
      </form>
    </template>

    <form v-else class="loginForm" novalidate @submit.prevent="handleRegistered">
      <div class="title">填写企业信息</div>
      <div class="user-login-text">
        请填写营业执照上的企业名称<span class="toggle" @click="goback_">返回</span>
      </div>
      <div v-if="isLoginError" class="login-error">{{ loginErrorInfo }}</div>

      <div class="formTitle">企业名称</div>
      <input v-model.trim="firmForm.firmName" class="login-input" placeholder="请填写企业名称" />

      <button class="confirmBtn" type="submit" :disabled="logining">
        {{ logining ? '提交中...' : '完 成' }}
      </button>
    </form>
  </div>
</template>

<script>
import storage from 'store'
import { mapActions } from 'vuex'
import { timeFix } from '/src/utils/common/util.js'
import { getCodeImg } from '/src/api/user/login.js'
import { loadApplicationRuntime } from '/src/plugins/runtime/application.js'

const phonePattern = /^1[3-9]\d{9}$/
const passwordPattern = /^(?![0-9]+$)(?![a-zA-Z]+$)[0-9A-Za-z]{8,}$/

export default {
  components: {
    countryCodeSelector: () => import('/src/components/CountryCodeSelector/index.vue')
  },
  data() {
    return {
      codeUrl: '',
      isLoginError: false,
      loginErrorInfo: '',
      loginForm: {
        username: '',
        password: '',
        rememberMe: true,
        code: ''
      },
      logining: false,
      isLogin: true,
      isNext: true,
      RegForm: {
        username: '',
        password: '',
        checkPass: ''
      },
      firmForm: {
        firmName: ''
      },
      areaCode: 86
    }
  },
  created() {
    this.getCode()
    this.getCookie()
  },
  methods: {
    ...mapActions(['Login', 'Logout', '_registered']),
    getCode() {
      getCodeImg()
        .then(res => {
          this.codeUrl = 'data:image/gif;base64,' + res.img
          this.loginForm.uuid = res.uuid
        })
        .catch(() => {})
    },
    getCookie() {
      const username = storage.get('username')
      const rememberMe = storage.get('rememberMe')
      storage.remove('password')
      this.loginForm = {
        ...this.loginForm,
        username: username === undefined ? this.loginForm.username : username,
        rememberMe: rememberMe === undefined ? true : Boolean(rememberMe)
      }
    },
    saveLoginCredentials() {
      if (this.loginForm.rememberMe) {
        storage.set('username', this.loginForm.username, { expires: 30 })
        storage.set('rememberMe', this.loginForm.rememberMe, { expires: 30 })
      } else {
        storage.remove('username')
        storage.remove('password')
        storage.remove('rememberMe')
      }
    },
    validateLogin() {
      if (!this.loginForm.username || !this.loginForm.password || !this.loginForm.code) {
        this.showError('请输入手机号/账户、密码和验证码')
        return false
      }
      return true
    },
    handleSubmit() {
      if (!this.validateLogin()) {
        return
      }
      this.logining = true
      this.saveLoginCredentials()
      this.Login(this.loginForm)
        .then(() => this.loginSuccess())
        .catch(err => this.requestFailed(err))
        .finally(() => {
          this.logining = false
        })
    },
    async loginSuccess() {
      await loadApplicationRuntime()
      this.$store.dispatch('getTenantAppId')
      this.$router.push({ path: '/' })
      setTimeout(() => {
        this.$notification.success({
          message: '欢迎',
          description: `${timeFix()}，欢迎回来`
        })
      }, 1000)
      this.handleCloseLoginError()
    },
    requestFailed(err) {
      this.showError(String(err || '登录失败'))
      this.loginForm.code = ''
      this.getCode()
    },
    showError(message) {
      this.isLoginError = true
      this.loginErrorInfo = message
    },
    handleCloseLoginError() {
      this.isLoginError = false
      this.loginErrorInfo = ''
    },
    registered() {
      this.handleCloseLoginError()
      this.isLogin = false
    },
    goLogin() {
      this.handleCloseLoginError()
      this.isLogin = true
    },
    goback_() {
      this.isNext = true
    },
    nextTick() {
      if (!phonePattern.test(this.RegForm.username)) {
        this.showError('请输入正确的手机号')
        return
      }
      if (!passwordPattern.test(this.RegForm.password)) {
        this.showError('密码至少8位，且必须包含字母和数字')
        return
      }
      if (this.RegForm.password !== this.RegForm.checkPass) {
        this.showError('两次输入的密码不一致')
        return
      }
      this.handleCloseLoginError()
      this.isNext = false
    },
    handleRegistered() {
      if (!this.firmForm.firmName) {
        this.showError('请输入企业名称')
        return
      }
      this.logining = true
      const params = {
        name: this.firmForm.firmName,
        sysUser: {
          userName: this.RegForm.username,
          password: this.RegForm.password,
          countryCode: this.areaCode
        }
      }
      this._registered(params)
        .then(() => this.loginSuccess())
        .catch(err => this.requestFailed(err))
        .finally(() => {
          this.logining = false
        })
    }
  }
}
</script>

<style lang="less" scoped>
@import './user.less';
</style>
