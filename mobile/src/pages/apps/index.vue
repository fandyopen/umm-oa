<template>
  <app-shell title="全部应用" tab="apps">
    <view class="h1">业务随身办</view>
    <view class="muted">按平台角色展示可使用的应用</view>
    <view v-if="error" class="error" @click="load">{{ error }} · 点击重试</view>
    <view v-for="group in groups" :key="group.name">
      <view class="section">
        <text class="h2">{{ group.name }}</text>
        <text class="muted">{{ group.items.length }} 个应用</text>
      </view>
      <view class="card grid">
        <view class="appitem" v-for="item in group.items" :key="item.key" @click="$go('list', { module: item.key })">
          <view class="appicon">{{ item.icon }}</view>
          {{ item.title }}
        </view>
      </view>
    </view>
    <view v-if="!groups.length && !error" class="empty">暂无已授权应用，请联系管理员配置角色。</view>
  </app-shell>
</template>
<script>
import { catalog } from '../../services/catalog'
import { bootstrap, visible, state } from '../../services/runtime'
export default {
  data: () => ({ state, error: '' }),
  computed: {
    groups() {
      const groups = {}
      Object.entries(catalog)
        .filter(([key]) => visible(key))
        .forEach(([key, v]) => {
          ;(groups[v.group] || (groups[v.group] = [])).push({ ...v, key })
        })
      return Object.entries(groups).map(([name, items]) => ({ name, items }))
    }
  },
  onShow() {
    this.load()
  },
  methods: {
    async load() {
      try {
        this.error = ''
        await bootstrap(true)
      } catch (e) {
        this.error = e.message
      }
    }
  }
}
</script>
