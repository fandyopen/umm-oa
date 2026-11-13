<template>
  <div class="theme-switcher">
    <a-dropdown :trigger="['click']" placement="bottomRight">
      <button class="theme-trigger" type="button" aria-label="切换风格" title="切换风格" aria-haspopup="menu"><a-icon type="skin" /></button>
      <a-menu slot="overlay" class="theme-menu" :selectedKeys="[preferences.mode]" @click="selectMode">
        <a-menu-item key="blue"><span class="theme-option"><span class="theme-dot theme-dot--blue"></span>蓝色系<a-icon v-if="preferences.mode === 'blue'" type="check" /></span></a-menu-item>
        <a-menu-item key="light"><span class="theme-option"><a-icon type="bulb" />浅色系<a-icon v-if="preferences.mode === 'light'" type="check" /></span></a-menu-item>
        <a-menu-item key="custom"><span class="theme-option"><a-icon type="highlight" />自定义…<a-icon v-if="preferences.mode === 'custom'" type="check" /></span></a-menu-item>
      </a-menu>
    </a-dropdown>

    <a-drawer title="自定义风格" :visible="visible" :width="drawerWidth" placement="right" wrapClassName="appearance-drawer" @close="cancel">
      <p class="appearance-intro">按你的习惯，打造专属工作台。调整后可即时预览。</p>
      <fieldset class="appearance-field">
        <legend>主题主色</legend>
        <div class="appearance-swatches"><button v-for="color in colors" :key="color" type="button" :style="{ background: color }" :aria-label="'使用主色 ' + color" :aria-pressed="draft.primary === color" @click="setPrimary(color)"><a-icon v-if="draft.primary === color" type="check" :style="{ color: textColor(color) }" /></button></div>
        <label class="appearance-color-row"><input v-model="draft.primary" type="color" aria-label="选择主题主色" @input="preview" /><span>主色值</span><input v-model.trim="draft.primary" class="appearance-hex" aria-label="主题主色值" maxlength="7" spellcheck="false" @input="preview" /></label>
        <p v-if="!validPrimary" class="appearance-error" role="alert">请输入六位十六进制颜色，例如 #0eb3a2</p>
      </fieldset>
      <fieldset class="appearance-field">
        <legend>头部样式</legend>
        <div class="appearance-choices"><label :class="{ selected: draft.header === 'light' }"><input v-model="draft.header" type="radio" value="light" @change="preview" /><span>浅色头部</span></label><label :class="{ selected: draft.header === 'primary' }"><input v-model="draft.header" type="radio" value="primary" @change="preview" /><span>主色头部</span></label></div>
      </fieldset>
      <fieldset class="appearance-field">
        <legend>页面背景</legend>
        <div class="appearance-choices"><label :class="{ selected: draft.background === 'gradient' }"><input v-model="draft.background" type="radio" value="gradient" @change="preview" /><span>柔和渐变</span></label><label :class="{ selected: draft.background === 'solid' }"><input v-model="draft.background" type="radio" value="solid" @change="preview" /><span>纯色背景</span></label></div>
        <label v-if="draft.background === 'solid'" class="appearance-color-row"><input v-model="draft.backgroundColor" type="color" aria-label="选择背景颜色" @input="preview" /><span>背景色值</span><input v-model.trim="draft.backgroundColor" class="appearance-hex" aria-label="背景色值" maxlength="7" spellcheck="false" @input="preview" /></label>
        <p v-if="draft.background === 'solid' && !validBackground" class="appearance-error" role="alert">请输入六位十六进制颜色</p>
      </fieldset>
      <div class="appearance-preview" aria-label="风格预览" :style="previewStyle"><div class="preview-header"><span></span><span></span><span></span></div><div class="preview-body"><div class="preview-sidebar"><i></i><i></i><i></i></div><div class="preview-content"><b>我的工作台</b><div class="preview-line"></div><div class="preview-line short"></div><span class="preview-button">开始协作</span></div></div></div>
      <p class="appearance-note">仅保存在当前浏览器，按账号分别记住。</p>
      <div class="appearance-footer"><a-button @click="reset">恢复默认</a-button><div><a-button @click="cancel">取消</a-button><a-button type="primary" :disabled="!valid" @click="save">保存应用</a-button></div></div>
    </a-drawer>
  </div>
</template>

<script>
import { DEFAULT_CUSTOM, PRESET_COLORS, isHexColor, normalizeAppearance, applyAppearance, appearanceTokens, contrastText } from '/src/utils/appearance.mjs'

export default {
  name: 'ThemeSwitcher',
  data() {
    return { visible: false, draft: { ...DEFAULT_CUSTOM }, colors: PRESET_COLORS }
  },
  computed: {
    preferences() { return this.$store.state.appearance.preferences },
    drawerWidth() { return this.$store.state.app.isMobile ? '100%' : 380 },
    validPrimary() { return isHexColor(this.draft.primary) },
    validBackground() { return isHexColor(this.draft.backgroundColor) },
    valid() { return this.validPrimary && (this.draft.background !== 'solid' || this.validBackground) },
    previewStyle() { return appearanceTokens({ mode: 'custom', custom: this.draft }) }
  },
  beforeDestroy() { if (this.visible) this.restore() },
  methods: {
    textColor: contrastText,
    selectMode({ key }) {
      if (key === 'custom') {
        this.draft = { ...this.preferences.custom }
        this.visible = true
        this.preview()
      } else this.$store.dispatch('appearance/save', { ...this.preferences, mode: key })
    },
    setPrimary(color) { this.draft.primary = color; this.preview() },
    preview() { if (this.valid) applyAppearance({ mode: 'custom', custom: this.draft }) },
    restore() {
      if (this.$route.path.startsWith('/user/login')) delete document.documentElement.dataset.appearance
      else applyAppearance(this.preferences)
    },
    cancel() { this.visible = false; this.restore() },
    reset() { this.draft = { ...DEFAULT_CUSTOM }; this.preview() },
    save() {
      if (!this.valid) return
      this.$store.dispatch('appearance/save', normalizeAppearance({ mode: 'custom', custom: this.draft }))
      this.visible = false
    }
  }
}
</script>

<style lang="less">
.theme-switcher { display: flex; align-items: center; margin-right: 16px; }
.theme-trigger { display: flex; align-items: center; justify-content: center; width: 34px; height: 34px; padding: 0; color: var(--umm-header-text, #fff); border: 0; border-radius: 10px; background: transparent; font-size: 20px; cursor: pointer; }
.theme-trigger:hover, .theme-trigger:focus-visible { background: var(--umm-header-selected, rgba(255,255,255,.15)); outline: 2px solid var(--umm-primary-border, #cbe6ff); outline-offset: 2px; }
.theme-menu.ant-dropdown-menu { min-width: 152px; padding: 6px; border: 1px solid #e4eeef; border-radius: 14px; box-shadow: 0 10px 32px rgba(31, 74, 83, .16); }
.theme-menu .ant-dropdown-menu-item { margin: 2px 0; padding: 8px 10px; border-radius: 8px; }
.theme-menu .ant-dropdown-menu-item-selected { color: var(--umm-link, #008e82); background: var(--umm-primary-soft, #e9f8f5); }
.theme-option { display: flex; align-items: center; gap: 9px; font-size: 13px; line-height: 20px; }
.theme-option .anticon { margin: 0; font-size: 14px; }
.theme-option .anticon-check { margin-left: auto; }
.theme-dot { width: 14px; height: 14px; border-radius: 50%; display: inline-block; }
.theme-dot--blue { background: #348ee4; }
.appearance-drawer {
  .ant-drawer-content { border-radius: 18px 0 0 18px; }
  .ant-drawer-body { padding: 24px 24px 100px; }
  .ant-drawer-wrapper-body { height: 100%; }
  .ant-drawer-title { color: #21374a; font-weight: 600; }
}
.appearance-intro { color: #718596; font-size: 13px; line-height: 1.8; margin-bottom: 26px; }
.appearance-field { min-width: 0; margin: 0 0 26px; padding: 0; border: 0; }
.appearance-field legend { width: auto; margin-bottom: 13px; border: 0; color: #30475b; font-size: 14px; font-weight: 600; }
.appearance-swatches { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 15px; }
.appearance-swatches button { display: flex; align-items: center; justify-content: center; width: 32px; height: 32px; padding: 0; border: 2px solid #fff; border-radius: 10px; box-shadow: 0 0 0 1px #e0eaed; cursor: pointer; }
.appearance-swatches button[aria-pressed='true'] { outline: 2px solid var(--umm-link); outline-offset: 3px; }
.appearance-color-row { display: flex; align-items: center; gap: 10px; margin-top: 14px; color: #718596; font-size: 12px; }
.appearance-color-row input[type='color'] { width: 35px; height: 34px; padding: 2px; border: 1px solid #e0eaed; border-radius: 7px; background: #fff; cursor: pointer; }
.appearance-hex { width: 115px; height: 34px; margin-left: auto; padding: 5px 10px; border: 1px solid #dce6e9; border-radius: 8px; color: #30475b; font-size: 13px; }
.appearance-choices { display: flex; gap: 12px; }
.appearance-choices label { display: flex; flex: 1; align-items: center; justify-content: center; gap: 7px; padding: 11px 5px; border: 1px solid #dce6e9; border-radius: 10px; color: #53697c; font-size: 13px; cursor: pointer; }
.appearance-choices label.selected { border-color: var(--umm-primary); background: var(--umm-primary-soft); color: var(--umm-link); }
.appearance-choices input { accent-color: var(--umm-link); }
.appearance-error { margin-top: 8px; font-size: 12px; color: #c0392b; }
.appearance-preview { overflow: hidden; height: 160px; border: 1px solid #e0eaed; border-radius: 12px; background: var(--umm-page-bg); }
.preview-header { display: flex; justify-content: flex-end; gap: 5px; height: 26px; padding: 10px; background: var(--umm-header-bg); border-bottom: 1px solid #e0eaed; }
.preview-header span { width: 6px; height: 6px; border-radius: 50%; background: var(--umm-header-text); opacity: .6; }
.preview-body { display: flex; height: 134px; }
.preview-sidebar { width: 54px; padding: 16px 8px; background: #fff; border-right: 1px solid #e0eaed; }
.preview-sidebar i { display: block; height: 6px; margin-bottom: 12px; border-radius: 3px; background: #e8eef0; }
.preview-sidebar i:first-child { background: var(--umm-primary); }
.preview-content { flex: 1; margin: 12px; padding: 12px; background: #fff; border-radius: 9px; color: #30475b; font-size: 11px; }
.preview-line { height: 5px; margin-top: 8px; background: #e8eef0; border-radius: 4px; }
.preview-line.short { width: 65%; }
.preview-button { display: inline-block; margin-top: 10px; padding: 3px 8px; border-radius: 4px; background: var(--umm-primary); color: var(--umm-primary-text); font-size: 9px; }
.appearance-note { margin: 14px 0; color: #718596; font-size: 12px; }
.appearance-footer { position: absolute; bottom: 0; right: 0; left: 0; display: flex; justify-content: space-between; gap: 8px; padding: 16px 20px; background: #fff; border-top: 1px solid #e0eaed; }
.appearance-footer > div { display: flex; gap: 8px; }
@media (max-width: 550px) { .theme-switcher { margin-right: 10px; } }
</style>
