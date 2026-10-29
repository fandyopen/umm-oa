<template>
  <div v-if="isLoginPage" id="app"><router-view /></div>
  <a-config-provider v-else :locale="locale"><div id="app"><router-view /></div></a-config-provider>
</template>

<script>
import { mapGetters } from 'vuex'
import defaultSettings from '/src/config/defaultSettings.js'
import { domTitle, setDocumentTitle } from '/src/utils/common/domUtil.js'
import { i18nRender } from '/src/locales/index.js'

export default {
  data() { return { watermarkTimer: null } },
  computed: {
    ...mapGetters(['nickname', 'defaultCompany']),
    isLoginPage() { return /\/user\/login(?:\/|$)/.test(this.$route.path) },
    locale() {
      const { title } = this.$route.meta
      title && setDocumentTitle(`${i18nRender(title)} - ${domTitle}`)
      return this.$i18n.getLocaleMessage(this.$store.getters.lang).antLocale || undefined
    }
  },
  async mounted() {
    if (this.isLoginPage || !defaultSettings.watermark) return
    const watermark = (await import('/src/utils/common/watermark.js')).default
    const renderWatermark = () => {
      const now = new Date().toLocaleString('zh-CN', { hour12: false })
      watermark(`${this.defaultCompany || defaultSettings.comany}<br>${now}<br>${this.nickname || ''}`)
    }
    renderWatermark()
    this.watermarkTimer = setInterval(renderWatermark, 60000)
  },
  beforeDestroy() { clearInterval(this.watermarkTimer) }
}
</script>

<style lang="less">
.water-mark-wrap { position: absolute; width: 100%; height: 100%; z-index: 9999; pointer-events: none; top: 0; left: 0; display: flex; overflow: hidden; flex-wrap: wrap; }
.water-word { display: flex; align-items: center; justify-content: center; font-size: 18px; color: rgba(191, 191, 191, 0.15); transform: rotate(-15deg); user-select: none; }
.ant-form-item-children { display: inline-block; width: 100%; .ant-calendar-picker { display: inline-block; width: 100%; } }
</style>
