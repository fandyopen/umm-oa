<template>
  <app-shell title="UMM 协同办公">
    <view class="login-logo">
      <view class="h1">随时连接，高效协同</view>
      <view class="muted">使用平台账号，继续今天的工作。</view>
    </view>
    <view class="card">
      <view class="h2">账号登录</view>
      <view class="spacer" />
      <view class="field">
        <text class="fieldlabel">平台账号</text>
        <input class="control" v-model="form.username" placeholder="请输入账号" maxlength="80" />
      </view>
      <view class="field">
        <text class="fieldlabel">密码</text>
        <input class="control" v-model="form.password" password placeholder="请输入密码" @confirm="submit" />
      </view>
      <view v-if="captcha.enabled" class="field">
        <text class="fieldlabel">验证码</text>
        <view class="row">
          <input class="control" v-model="form.code" placeholder="验证码" />
          <image :src="captcha.image" style="width: 110px; height: 42px" @click="loadCaptcha" />
        </view>
      </view>
      <view v-if="error" class="error">{{ error }}</view>
      <button class="btn" :loading="busy" :disabled="busy" @click="submit">登录</button>
      <view class="notice">权限、审批和业务数据与办公平台同步。请使用所属企业分配的账号。</view>
    </view>
  </app-shell>
</template>
<script>
import { request, login } from '../../services/runtime'
export default {
  data: () => ({ form: { username: '', password: '', code: '', uuid: '' }, captcha: {}, error: '', busy: false }),
  onLoad() {
    this.loadCaptcha()
  },
  methods: {
    async loadCaptcha() {
      try {
        const r = await request('/captchaImage')
        this.captcha = { enabled: r.captchaEnabled === true, image: 'data:image/png;base64,' + r.img }
        this.form.uuid = r.uuid || ''
      } catch (e) {
        this.error = e.message
      }
    },
    async submit() {
      if (this.busy) return
      if (!this.form.username || !this.form.password) return this.$toast('请填写账号和密码')
      this.busy = true
      this.error = ''
      try {
        await login(this.form)
        this.form.password = ''
        uni.reLaunch({ url: '/pages/home/index' })
      } catch (e) {
        this.error = e.message
        await this.loadCaptcha()
      } finally {
        this.busy = false
      }
    }
  }
}
</script>
