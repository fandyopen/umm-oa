<template>
  <app-shell title="我的" tab="my">
    <view v-if="error" class="error" @click="load">{{ error }} · 点击重试</view>
    <view class="card profile">
      <view class="avatar">{{ (user.nickName || user.userName || '我').slice(0, 1) }}</view>
      <view>
        <view class="h2">{{ user.nickName || user.userName }}</view>
        <view class="muted">{{ (user.dept && user.dept.deptName) || user.deptName || '企业成员' }}</view>
        <view class="muted">{{ roles }}</view>
      </view>
    </view>
    <view class="card">
      <view v-for="item in links" :key="item.kind" class="kv" @click="$go('tasks', { kind: item.kind })">
        <text class="kvvalue">{{ item.title }}</text>
        <text class="muted">查看 →</text>
      </view>
      <view v-for="item in selfApps" :key="item.key" class="kv" @click="$go('list', { module: item.key })">
        <text class="kvvalue">{{ item.title }}</text>
        <text class="muted">查看 →</text>
      </view>
    </view>
    <view class="card">
      <view class="h3">外观设置</view>
      <view class="muted">沿用平台配色，可为当前账号选择主题色</view>
      <view class="colors">
        <view
          v-for="color in colors"
          :key="color"
          class="color"
          :style="{ background: color, outline: state.theme === color ? '2px solid ' + color : 'none' }"
          @click="setTheme(color)"
        />
      </view>
    </view>
    <view class="notice">
      当前版本：移动办公 1.0
      <br />
      审批设计、角色授权、提成规则维护请在平台端配置。
    </view>
    <button class="danger" @click="exit">退出登录</button>
  </app-shell>
</template>
<script>
import { bootstrap, state, visible, setTheme, logout } from '../../services/runtime'
import { catalog } from '../../services/catalog'
export default {
  data: () => ({
    state,
    error: '',
    colors: ['#0eb3a2', '#348ee4', '#6366f1', '#8b5cf6', '#db2777', '#c2410c'],
    links: [
      { kind: 'todo', title: '待我审批' },
      { kind: 'done', title: '我的已办' },
      { kind: 'start', title: '我的发起' },
      { kind: 'copy', title: '抄送给我' }
    ]
  }),
  computed: {
    user() {
      return (this.state.session && this.state.session.user) || {}
    },
    roles() {
      const names = (this.user.roles || []).map((role) => role.roleName || role.roleKey).filter(Boolean)
      return (names.length ? names : (this.state.session && this.state.session.roles) || []).join(' / ')
    },
    selfApps() {
      return Object.entries(catalog)
        .filter(([key, v]) => v.self && visible(key))
        .map(([key, v]) => ({ ...v, key }))
    }
  },
  onShow() {
    this.load()
  },
  methods: {
    setTheme,
    async load() {
      try {
        this.error = ''
        await bootstrap(true)
      } catch (e) {
        this.error = e.message
      }
    },
    exit() {
      uni.showModal({
        confirmText: '确定',
        cancelText: '取消',
        title: '退出登录',
        content: '确认退出当前账号？',
        success: (r) => {
          if (r.confirm) logout()
        }
      })
    }
  }
}
</script>
