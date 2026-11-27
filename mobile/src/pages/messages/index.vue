<template>
  <app-shell title="消息提醒" tab="messages">
    <view class="h2">与你有关的工作动态</view>
    <view class="muted">待办提醒与抄送通知 · 阅读状态按账号保存</view>
    <view class="spacer" />
    <view class="card row" @click="$go('notices')"><view><view class="h3">通知公告</view><view class="muted">查看企业已发布通知</view></view><text class="link">查看 →</text></view>
    <view class="tabs">
      <text v-for="t in tabs" :key="t.key" class="pill" :class="{ active: kind === t.key }" @click="change(t.key)">{{ t.title }}</text>
    </view>
    <view v-if="error" class="error" @click="load">{{ error }} · 点击重试</view>
    <view v-for="r in rows" :key="r.mobileKey" class="card listcard" @click="open(r)">
      <view class="row">
        <text class="h3">{{ r.title || (r.form && r.form.title) || r.processName }}</text>
        <text class="tag" :class="{ badge: !r.read }">{{ r.read ? '已读' : '未读' }}</text>
      </view>
      <view class="muted">{{ r.taskName || r.businessType }} · {{ r.createTime }}</view>
    </view>
    <view v-if="!rows.length" class="card empty">{{ loading ? '正在加载…' : '暂无消息' }}</view>
    <view class="pagination">
      <button
        class="secondary"
        :disabled="page === 1 || loading"
        @click="
          page--
          load()
        "
      >
        上一页
      </button>
      <text>{{ page }} / {{ pages }}</text>
      <button
        class="secondary"
        :disabled="page >= pages || loading"
        @click="
          page++
          load()
        "
      >
        下一页
      </button>
    </view>
  </app-shell>
</template>
<script>
import { bootstrap, request } from '../../services/runtime'
import { pageRows } from '../../services/catalog'
export default {
  data: () => ({
    kind: 'todo',
    tabs: [
      { key: 'todo', title: '待办提醒' },
      { key: 'copy', title: '抄送通知' }
    ],
    rows: [],
    page: 1,
    total: 0,
    loading: false,
    error: ''
  }),
  computed: {
    pages() {
      return Math.max(1, Math.ceil(this.total / 20))
    }
  },
  onShow() {
    this.load()
  },
  methods: {
    change(k) {
      this.kind = k
      this.page = 1
      this.load()
    },
    async load() {
      this.loading = true
      this.error = ''
      try {
        await bootstrap()
        const r = pageRows(await request('/mobile/tasks/' + this.kind, 'GET', { pageNum: this.page, pageSize: 20 }))
        this.rows = r.rows
        this.total = r.total
      } catch (e) {
        this.rows = []
        this.error = e.message
      } finally {
        this.loading = false
      }
    },
    open(r) {
      this.$go('task', { kind: this.kind, key: r.mobileKey })
    }
  }
}
</script>
