<template>
  <app-shell title="我的审批" back>
    <view class="tabs">
      <text v-for="t in tabs" :key="t.key" class="pill" :class="{ active: kind === t.key }" @click="change(t.key)">{{ t.title }}</text>
    </view>
    <view v-if="error" class="error" @click="load">{{ error }} · 点击重试</view>
    <view v-for="r in rows" :key="r.mobileKey" class="card listcard" @click="$go('task', { kind, key: r.mobileKey })">
      <view class="row">
        <text class="h3">{{ r.title || (r.form && r.form.title) || r.processName }}</text>
        <text class="tag">{{ r.taskName || '查看' }}</text>
      </view>
      <view class="muted">{{ r.applyUserName }} · {{ r.createTime }}</view>
    </view>
    <view v-if="!rows.length" class="card empty">{{ loading ? '正在加载…' : '暂无记录' }}</view>
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
import { pageRows, taskPaths } from '../../services/catalog'
export default {
  data: () => ({
    kind: 'todo',
    tabs: [
      { key: 'todo', title: '待办' },
      { key: 'done', title: '已办' },
      { key: 'start', title: '我发起的' },
      { key: 'copy', title: '抄送' }
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
  onLoad(q) {
    this.kind = taskPaths[q.kind] ? q.kind : 'todo'
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
    }
  }
}
</script>
