<template>
  <div class="main">
    <template v-if="isNext">
      <form v-if="isLogin" class="loginForm" novalidate @submit.prevent="handleSubmit">
        <p class="form-eyebrow">WELCOME TO UMM</p>
        <h2 class="title">登录协同工作台</h2>
        <p class="user-login-text">欢迎回来，继续推进每一份招聘托付。</p>
        <div v-if="isLoginError" class="login-error" role="alert">{{ loginErrorInfo }}</div>

        <div class="form-field">
          <label class="formTitle" for="login-username">手机号 / 账户</label>
          <div class="input-wrap"><svg class="input-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="4" /><path d="M4 21v-2a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v2" /></svg><input id="login-username" v-model.trim="loginForm.username" class="login-input" autocomplete="username" placeholder="请输入手机号或账户" /></div>
        </div>
        <div class="form-field">
          <label class="formTitle" for="login-password">登录密码</label>
          <div class="input-wrap"><svg class="input-icon" viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="10" width="16" height="11" rx="2" /><path d="M8 10V6a4 4 0 0 1 8 0v4M12 14v3" /></svg><input id="login-password" v-model="loginForm.password" class="login-input password-input" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入登录密码" /><button class="password-toggle" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword" @click="showPassword = !showPassword"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" /><circle cx="12" cy="12" r="3" /><path v-if="!showPassword" d="m3 3 18 18" /></svg></button></div>
        </div>
        <div class="form-field">
          <label class="formTitle" for="login-code">验证码</label>
          <div class="codeContent">
            <div class="input-wrap"><svg class="input-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z" /><path d="m8.5 12 2.5 2.5 4.5-5" /></svg><input id="login-code" v-model.trim="loginForm.code" class="login-input codeInput" autocomplete="off" placeholder="请输入验证码" /></div>
            <button class="captcha-button" type="button" aria-label="刷新验证码" title="点击换一张" @click="getCode"><img v-if="codeUrl" :src="codeUrl" class="codeImg" alt="图形验证码" /><span v-else>点击获取验证码</span></button>
          </div>
        </div>
        <div class="form-options"><label class="remember-option"><input v-model="loginForm.rememberMe" type="checkbox" /><span>记住账号</span></label><span class="password-help">忘记密码请联系管理员</span></div>
        <button class="confirmBtn" type="submit" :disabled="logining">{{ logining ? '正在登录…' : '进入工作台' }}<svg v-if="!logining" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14m-5-5 5 5-5 5" /></svg></button>
        <p class="form-bottom">还没有企业账号？<button class="toggle" type="button" @click="registered">注册企业</button></p>
      </form>

      <form v-else class="loginForm" novalidate @submit.prevent="nextTick">
        <p class="form-eyebrow">JOIN UMM WORKSPACE</p>
        <h2 class="title">开启团队协作</h2>
        <p class="user-login-text">创建企业账号，让招聘与交付有序衔接。</p>
        <div v-if="isLoginError" class="login-error" role="alert">{{ loginErrorInfo }}</div>
        <div class="form-field"><label class="formTitle" for="register-phone">手机号</label><div class="p-a-c"><countryCodeSelector :countryCode.sync="areaCode" /><input id="register-phone" v-model.trim="RegForm.username" class="login-input regPhoneInput" autocomplete="tel" placeholder="请输入手机号" /></div></div>
        <div class="form-field"><label class="formTitle" for="register-password">设置密码</label><input id="register-password" v-model="RegForm.password" class="login-input" type="password" autocomplete="new-password" placeholder="至少8位，包含字母和数字" /></div>
        <div class="form-field"><label class="formTitle" for="register-confirm">确认密码</label><input id="register-confirm" v-model="RegForm.checkPass" class="login-input" type="password" autocomplete="new-password" placeholder="请再次输入密码" /></div>
        <button class="confirmBtn" type="submit" :disabled="logining">下一步</button>
        <p class="form-bottom">已有账号？<button class="toggle" type="button" @click="goLogin">返回登录</button></p>
      </form>
    </template>

    <form v-else class="loginForm" novalidate @submit.prevent="handleRegistered">
      <p class="form-eyebrow">YOUR TEAM STARTS HERE</p>
      <h2 class="title">完善企业信息</h2>
      <p class="user-login-text">填写企业名称，建立团队的协作空间。</p>
      <div v-if="isLoginError" class="login-error" role="alert">{{ loginErrorInfo }}</div>
      <div class="form-field"><label class="formTitle" for="firm-name">企业名称</label><input id="firm-name" v-model.trim="firmForm.firmName" class="login-input" placeholder="请输入营业执照上的企业名称" /></div>
      <button class="confirmBtn" type="submit" :disabled="logining">{{ logining ? '提交中…' : '完成注册' }}</button>
      <p class="form-bottom"><button class="toggle" type="button" @click="goback_">返回上一步</button></p>
    </form>
  </div>
</template>

<script>
import storage from 'store'
import { mapActions } from 'vuex'
import { timeFix } from '/src/utils/common/util.js'
import { getCodeImg } from '/src/api/user/login.js'
import { loadApplicationRuntime } from '/src/plugins/runtime/application.js'
import { setDocumentTitle } from '/src/utils/common/domUtil.js'

const phonePattern = /^1[3-9]\d{9}$/
const passwordPattern = /^(?![0-9]+$)(?![a-zA-Z]+$)[0-9A-Za-z]{8,}$/

export default {
  components: {
    countryCodeSelector: () => import('/src/components/CountryCodeSelector/index.vue')
  },
  data() {
    return {
      codeUrl: '',
      showPassword: false,
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
  mounted() {
    setDocumentTitle('登录协同工作台 - UMM 加木企服')
    const preloadRuntime = () => loadApplicationRuntime().catch(() => {})
    if (window.requestIdleCallback) {
      window.requestIdleCallback(preloadRuntime, { timeout: 1500 })
    } else {
      setTimeout(preloadRuntime, 500)
    }
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
