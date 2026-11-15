<template>
  <app-shell title="工作台" tab="home">
    <view v-if="error" class="error" @click="load">{{ error }} · 点击重试</view>
    <view class="card hero">
      <view class="muted">{{ company }}</view>
      <view class="h1">{{ greeting }}，{{ user.nickName || user.userName || '同事' }}</view>
      <view class="muted">让每一次沟通，都有下一步。</view>
      <view class="spacer" />
      <view class="row">
        <view @click="$go('tasks', { kind: 'todo' })">
          <view class="stat">{{ total }}</view>
          <text>待办事项 →</text>
        </view>
        <text>{{ today }}</text>
      </view>
    </view>
    <view class="card">
      <view class="grid">
        <view class="appitem" v-for="item in shortcuts" :key="item.key" @click="$go(item.path, item.params)">
          <view class="appicon">{{ item.icon }}</view>
          {{ item.title }}
        </view>
      </view>
    </view>
    <view class="section">
      <text class="h2">常用应用</text>
      <text class="link" @click="$go('apps')">全部应用 →</text>
    </view>
    <view class="card">
      <view class="grid">
        <view class="appitem" v-for="item in apps" :key="item.key" @click="$go('list', { module: item.key })">
          <view class="appicon">{{ item.icon }}</view>
          {{ item.title }}
        </view>
      </view>
      <view v-if="!apps.length" class="empty">暂无已授权应用</view>
    </view>
    <view class="section">
      <text class="h2">待我处理</text>
      <text class="link" @click="$go('tasks', { kind: 'todo' })">查看全部 →</text>
    </view>
    <view v-for="task in tasks" :key="task.mobileKey" class="card listcard" @click="$go('task', { kind: 'todo', key: task.mobileKey })">
      <view class="row">
        <text class="h3">{{ task.title || (task.form && task.form.title) || task.processName }}</text>
        <text class="tag badge">待处理</text>
      </view>
      <view class="muted">{{ task.taskName || task.businessType }} · {{ task.applyUserName || '' }}</view>
      <view class="muted">{{ task.createTime }}</view>
    </view>
    <view v-if="!tasks.length" class="card empty">{{ loading ? '正在加载…' : '暂时没有待办，工作安排井井有条' }}</view>
  </app-shell>
</template>
<script>
import { bootstrap, state, visible, request } from '../../services/runtime'
import { catalog, pageRows } from '../../services/catalog'
export default {
  data: () => ({
    state,
    tasks: [],
    total: 0,
    loading: false,
    error: '',
    today: new Date().toLocaleDateString(),
    shortcuts: [
      { key: 'todo', title: '待我审批', icon: '审', path: 'tasks', params: { kind: 'todo' } },
      { key: 'start', title: '我的发起', icon: '发', path: 'tasks', params: { kind: 'start' } },
      { key: 'done', title: '已办事项', icon: '办', path: 'tasks', params: { kind: 'done' } },
      { key: 'copy', title: '抄送给我', icon: '抄', path: 'tasks', params: { kind: 'copy' } }
    ]
  }),
  computed: {
    user() {
      return (this.state.session && this.state.session.user) || {}
    },
    company() {
      const c = this.state.session && this.state.session.currentCompany
      return (typeof c === 'string' ? c : c && (c.companyName || c.name)) || '企业协同工作空间'
    },
    greeting() {
      return new Date().getHours() < 12 ? '上午好' : '下午好'
    },
    apps() {
      return Object.entries(catalog)
        .filter(([k]) => visible(k))
        .slice(0, 8)
        .map(([key, v]) => ({ ...v, key }))
    }
  },
  onShow() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        await bootstrap(true)
        const r = pageRows(await request('/mobile/tasks/todo', 'GET', { pageSize: 4 }))
        this.tasks = r.rows
        this.total = r.total
      } catch (e) {
        this.tasks = []
        this.error = e.message
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
