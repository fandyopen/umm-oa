<template>
  <app-shell title="通知详情" back>
    <view v-if="error" class="error" @click="load">{{ error }} · 点击重试</view>
    <view v-if="loading" class="card empty">正在加载…</view>
    <view v-else-if="notice" class="card">
      <view class="h2">{{ notice.noticeTitle }}</view>
      <view class="muted">发布人：{{ notice.publishedBy }}<br />{{ notice.publishedTime }}</view>
      <view class="spacer" />
      <rich-text :nodes="content" />
    </view>
  </app-shell>
</template>
<script>
import { bootstrap, request, baseUrl } from '../../services/runtime'
import { noticeNodes } from '../../services/noticeContent'
export default {
  data: () => ({ id: '', notice: null, content: [], error: '', loading: false }),
  onLoad(query) { this.id = query.id || '' },
  onShow() { this.load() },
  methods: {
    async load() {
      this.loading = true; this.error = ''; this.notice = null
      try {
        if (!/^\d+$/.test(this.id)) throw new Error('通知编号无效')
        await bootstrap()
        this.notice = (await request('/notices/' + this.id)).data
        this.content = noticeNodes(this.notice.noticeContent, baseUrl())
      } catch (error) { this.error = error.message || '通知无法查看，可能已撤下' }
      finally { this.loading = false }
    }
  }
}
</script>
