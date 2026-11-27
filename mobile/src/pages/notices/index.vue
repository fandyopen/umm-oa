<template>
  <app-shell title="通知公告" back>
    <view class="h2">企业通知公告</view>
    <view class="muted">查看已发布的通知与公告</view>
    <view class="spacer" />
    <view class="card row"><input v-model="keyword" placeholder="搜索通知标题" confirm-type="search" @confirm="search" /><text class="link" @click="search">搜索</text></view>
    <view v-if="error" class="error" @click="load">{{ error }} · 点击重试</view>
    <view v-for="notice in rows" :key="notice.noticeId" class="card listcard" @click="$go('notice', { id: notice.noticeId })">
      <view class="h3">{{ notice.noticeTitle }}</view>
      <view class="muted">{{ notice.publishedBy }} · {{ notice.publishedTime }}</view>
    </view>
    <view v-if="!rows.length && !error" class="card empty">{{ loading ? '正在加载…' : '暂无已发布通知' }}</view>
    <view class="pagination"><button class="secondary" :disabled="page <= 1 || loading" @click="changePage(-1)">上一页</button><text>{{ page }} / {{ pages }}</text><button class="secondary" :disabled="page >= pages || loading" @click="changePage(1)">下一页</button></view>
  </app-shell>
</template>
<script>
import { bootstrap, request } from '../../services/runtime'
export default {
  data: () => ({ rows: [], page: 1, total: 0, keyword: '', loading: false, error: '', version: 0 }),
  computed: { pages() { return Math.max(1, Math.ceil(this.total / 20)) } },
  onShow() { this.load() },
  methods: {
    search() { this.page = 1; this.load() },
    changePage(amount) { this.page += amount; this.load() },
    async load() {
      const version = ++this.version
      this.loading = true; this.error = ''
      try {
        await bootstrap()
        const response = await request('/notices', 'GET', { pageNum: this.page, pageSize: 20, noticeTitle: this.keyword })
        if (version === this.version) { this.rows = response.rows || []; this.total = response.total }
      } catch (error) { if (version === this.version) { this.rows = []; this.error = error.message } }
      finally { if (version === this.version) this.loading = false }
    }
  }
}
</script>
