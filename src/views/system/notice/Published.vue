<template>
  <page-header-wrapper>
    <a-card :bordered="false">
      <div class="notice-toolbar">
        <h2>通知公告</h2>
        <a-input-search v-model="keyword" placeholder="搜索通知标题" class="notice-search" @search="search" />
        <a-button icon="reload" @click="load">刷新</a-button>
        <a-button v-if="moduleContexts.notices && canViewModule('notices')" @click="$router.push('/system/notice')">管理通知</a-button>
      </div>
      <a-alert v-if="error" type="error" :message="error" show-icon />
      <a-table :columns="columns" :data-source="rows" :loading="loading" row-key="noticeId" :pagination="pagination" @change="changePage">
        <a slot="noticeTitle" slot-scope="title, row" @click="selectedId = row.noticeId">{{ title }}</a>
      </a-table>
      <notice-detail :notice-id="selectedId" @close="selectedId = null" />
    </a-card>
  </page-header-wrapper>
</template>
<script>
import { listPublishedNotices } from '/src/api/system/notice.js'
import { withModuleContexts } from '/src/utils/moduleContext.js'
import NoticeDetail from './NoticeDetail.vue'
export default {
  components: { NoticeDetail },
  mixins: [withModuleContexts({ notices: { component: 'system/notice/index', label: '通知公告' } })],
  data: () => ({ keyword: '', rows: [], loading: false, error: '', selectedId: null, page: 1, size: 10, total: 0,
    columns: [{ title: '标题', dataIndex: 'noticeTitle', scopedSlots: { customRender: 'noticeTitle' } }, { title: '发布人', dataIndex: 'publishedBy', width: 160 }, { title: '发布时间', dataIndex: 'publishedTime', width: 200 }] }),
  computed: { pagination() { return { current: this.page, pageSize: this.size, total: this.total, showSizeChanger: true } } },
  created() { this.load() },
  activated() { this.load() },
  methods: {
    search() { this.page = 1; this.load() },
    changePage(pagination) { this.page = pagination.current; this.size = pagination.pageSize; this.load() },
    async load() {
      this.loading = true; this.error = ''
      try { const response = await listPublishedNotices({ noticeTitle: this.keyword, pageNum: this.page, pageSize: this.size }); this.rows = response.rows || []; this.total = response.total }
      catch (error) { this.rows = []; this.error = error.message || '通知加载失败' }
      finally { this.loading = false }
    }
  }
}
</script>
<style scoped>
.notice-toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 20px; }
.notice-toolbar h2 { margin: 0 auto 0 0; }
.notice-search { width: 260px; }
</style>
