<template>
  <a-modal :visible="!!noticeId" title="通知详情" :width="850" :footer="null" destroy-on-close @cancel="$emit('close')">
    <a-spin :spinning="loading">
      <a-alert v-if="error" type="error" :message="error" show-icon />
      <template v-else-if="notice">
        <h2 class="notice-title">{{ notice.noticeTitle }}</h2>
        <p class="notice-meta">{{ notice.publishedBy ? '发布人：' + notice.publishedBy : '创建人：' + notice.createBy }} · {{ notice.publishedTime || notice.createTime }}</p>
        <iframe class="notice-body" title="通知正文" sandbox="" :srcdoc="documentHtml" />
      </template>
    </a-spin>
  </a-modal>
</template>
<script>
import { getNotice, getPublishedNotice } from '/src/api/system/notice.js'
export default {
  props: { noticeId: { type: [Number, String], default: null }, management: Boolean },
  data: () => ({ loading: false, error: '', notice: null, version: 0 }),
  computed: {
    documentHtml() {
      return '<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; img-src \'self\' https: data:; style-src \'unsafe-inline\';"><style>body{font:15px/1.9 sans-serif;color:#24374b;margin:12px;overflow-wrap:anywhere}img{max-width:100%;height:auto}table{max-width:100%;border-collapse:collapse}td,th{border:1px solid #ddd;padding:6px}</style></head><body>' + (this.notice?.noticeContent || '') + '</body></html>'
    }
  },
  watch: { noticeId: { immediate: true, handler() { this.load() } } },
  methods: {
    async load() {
      const version = ++this.version
      this.notice = null; this.error = ''; this.loading = false
      if (!this.noticeId) return
      this.loading = true
      try {
        const response = await (this.management ? getNotice(this.noticeId) : getPublishedNotice(this.noticeId))
        if (version === this.version) this.notice = response.data
      } catch (error) {
        if (version === this.version) this.error = error.message || '通知无法查看，可能已撤下'
      } finally { if (version === this.version) this.loading = false }
    }
  }
}
</script>
<style scoped>
.notice-title { overflow-wrap: anywhere; }
.notice-meta { color: #7b8798; font-size: 13px; }
.notice-body { width: 100%; height: 55vh; border: 0; }
</style>
