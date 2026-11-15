<template>
  <view class="app" :style="themeStyle">
    <view class="top">
      <view class="topline">
        <text v-if="back" class="back" @click="backPage">‹</text>
        <view class="brand">
          <text class="brandmark">U</text>
          <text>{{ title }}</text>
        </view>
        <text class="toplabel">移动办公</text>
      </view>
    </view>
    <view class="content"><slot /></view>
    <view v-if="tab" class="bottom">
      <view v-for="item in tabs" :key="item.key" class="tab" :class="{ selected: tab === item.key }" @click="openTab(item.key)">
        <text class="tabicon">{{ item.icon }}</text>
        <text>{{ item.label }}</text>
      </view>
    </view>
  </view>
</template>
<script>
import { state } from '../services/runtime'
export default {
  props: { title: String, tab: String, back: Boolean },
  data: () => ({
    state,
    tabs: [
      { key: 'home', label: '工作台', icon: '▦' },
      { key: 'apps', label: '应用', icon: '▤' },
      { key: 'messages', label: '消息', icon: '♧' },
      { key: 'my', label: '我的', icon: '◎' }
    ]
  }),
  computed: {
    themeStyle() {
      return { '--primary': this.state.theme }
    }
  },
  methods: {
    openTab(key) {
      if (this.tab !== key) uni.reLaunch({ url: '/pages/' + key + '/index' })
    },
    backPage() {
      if (getCurrentPages().length > 1) uni.navigateBack()
      else uni.reLaunch({ url: '/pages/home/index' })
    }
  }
}
</script>
