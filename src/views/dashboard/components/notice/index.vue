<template>
  <a-card class="card-container card-container-height" :bordered="false" :headStyle="{ border: 'none' }" :bodyStyle="{ padding: '0 20px', overflow: 'auto' }">
    <h1 slot="title" class="card-title">重要通知</h1>
    <a slot="extra" @click="$router.push('/notices')">更多</a>
    <a-spin :spinning="loading">
      <a-alert v-if="error" type="error" :message="error"><a slot="description" @click="load">重新加载</a></a-alert>
      <div v-else>
        <div v-for="item in rows" :key="item.noticeId" class="notice-item">
          <a class="notice-link" @click="selectedId = item.noticeId">{{ item.noticeTitle }}<small>{{ item.publishedTime }}</small></a>
        </div>
        <p v-if="!loading && !rows.length" class="notice-empty">暂无已发布通知</p>
      </div>
    </a-spin>
    <notice-detail :notice-id="selectedId" @close="selectedId = null" />
  </a-card>
</template>
<script>
import { listPublishedNotices } from '/src/api/system/notice.js'
import NoticeDetail from '/src/views/system/notice/NoticeDetail.vue'
export default {
  components: { NoticeDetail },
  data: () => ({ rows: [], loading: false, error: '', selectedId: null }),
  created() { this.load() },
  activated() { this.load() },
  methods: {
    async load() {
      if (this.loading) return
      this.loading = true
      this.error = ''
      try { this.rows = (await listPublishedNotices({ pageNum: 1, pageSize: 5 })).rows || [] }
      catch (error) { this.rows = []; this.error = error.message || '通知加载失败' }
      finally { this.loading = false }
    }
  }
}
</script>
<style scoped>
.notice-link { display: block; width: 100%; overflow-wrap: anywhere; }
.notice-item { padding: 12px 0; border-bottom: 1px solid #f0f0f0; }
.notice-empty { padding: 25px 0; text-align: center; color: #8a97a5; }
.notice-link small { display: block; color: #8a97a5; font-size: 12px; margin-top: 4px; }
</style>
