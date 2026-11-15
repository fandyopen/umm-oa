<template>
  <view>
    <view v-for="(file, index) in files" :key="index" class="kv">
      <text class="link" @click="open(file)">{{ file.name || file.originalName || '查看业务凭证' }} ↗</text>
    </view>
  </view>
</template>
<script>
import { openFile } from '../services/runtime'
export default {
  props: { value: [String, Array] },
  computed: {
    files() {
      try {
        const rows = typeof this.value === 'string' ? JSON.parse(this.value || '[]') : this.value || []
        return Array.isArray(rows) ? rows.filter((f) => f && typeof f.fileName === 'string' && f.fileName.startsWith('/profile/')) : []
      } catch (e) {
        return []
      }
    }
  },
  methods: {
    open(f) {
      openFile(f.fileName, f.name || f.originalName)
    }
  }
}
</script>
