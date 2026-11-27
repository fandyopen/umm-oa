<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-card :bordered="false">
      <div class="notice-toolbar">
        <a-input-search v-model="query.noticeTitle" placeholder="搜索公告标题" class="search" @search="search" />
        <a-input v-model="query.createBy" placeholder="创建人" class="author" allow-clear @pressEnter="search" />
        <a-select v-model="query.publishStatus" placeholder="发布状态" allow-clear class="state" @change="search">
          <a-select-option value="draft">草稿</a-select-option><a-select-option value="published">已发布</a-select-option><a-select-option value="withdrawn">已撤下</a-select-option>
        </a-select>
        <a-button icon="reload" @click="load">刷新</a-button>
        <a-button v-if="showModuleAction('notices', 'add')" type="primary" icon="plus" @click="edit()">新增通知</a-button>
        <a-button @click="$router.push('/notices')">员工阅读列表</a-button>
      </div>
      <a-alert class="notice-tip" type="info" show-icon message="授权人员可直接发布，无需审批。发布和撤下使用修改权限；新增并发布还需新增权限。" />
      <a-alert v-if="error" type="error" :message="error" show-icon />
      <a-table :columns="columns" :data-source="rows" :loading="loading" row-key="noticeId" :pagination="pagination" :scroll="{ x: 1050 }" @change="changePage">
        <a slot="noticeTitle" slot-scope="title, row" @click="selectedId = row.noticeId">{{ title }}</a>
        <a-tag slot="state" slot-scope="value" :color="value === 'published' ? 'green' : value === 'withdrawn' ? 'orange' : 'default'">{{ names[value] || value }}</a-tag>
        <span slot="type" slot-scope="value">{{ value === '2' ? '公告' : '通知' }}</span>
        <span slot="operations" slot-scope="_, row" class="operations">
          <a @click="selectedId = row.noticeId">详情</a>
          <a v-if="showModuleAction('notices', 'edit')" @click="edit(row)">修改</a>
          <a v-if="showModuleAction('notices', 'edit') && row.publishStatus !== 'published'" @click="changeState(row, true)">发布</a>
          <a v-if="showModuleAction('notices', 'edit') && row.publishStatus === 'published'" @click="changeState(row, false)">撤下</a>
          <a v-if="showModuleAction('notices', 'remove')" class="danger" @click="remove(row)">删除</a>
        </span>
      </a-table>
      <notice-detail :notice-id="selectedId" management @close="selectedId = null" />
    </a-card>
  </page-header-wrapper>
</template>
<script>
import { listNotice, delNotice, publishNotice, withdrawNotice } from '/src/api/system/notice.js'
import { withModuleContexts } from '/src/utils/moduleContext.js'
import NoticeDetail from './NoticeDetail.vue'
export default {
  name: 'Notice',
  components: { NoticeDetail },
  mixins: [withModuleContexts({ notices: { component: 'system/notice/index', label: '通知公告' } })],
  data: () => ({ rows: [], total: 0, loading: false, error: '', selectedId: null, busy: false,
    query: { noticeTitle: '', createBy: '', publishStatus: undefined, pageNum: 1, pageSize: 10 },
    names: { draft: '草稿', published: '已发布', withdrawn: '已撤下' },
    columns: [
      { title: '公告标题', dataIndex: 'noticeTitle', scopedSlots: { customRender: 'noticeTitle' }, width: 250 },
      { title: '类型', dataIndex: 'noticeType', scopedSlots: { customRender: 'type' }, width: 75 },
      { title: '状态', dataIndex: 'publishStatus', scopedSlots: { customRender: 'state' }, width: 100 },
      { title: '创建人', dataIndex: 'createBy', width: 120 },
      { title: '发布人', dataIndex: 'publishedBy', width: 120 },
      { title: '发布时间', dataIndex: 'publishedTime', width: 190 },
      { title: '操作', key: 'operations', scopedSlots: { customRender: 'operations' }, width: 225, fixed: 'right' }
    ] }),
  computed: { pagination() { return { current: this.query.pageNum, pageSize: this.query.pageSize, total: this.total, showSizeChanger: true } } },
  created() { this.load() },
  activated() { this.load() },
  methods: {
    async load() {
      this.loading = true; this.error = ''
      try { const response = await listNotice(this.query); this.rows = response.rows || []; this.total = response.total }
      catch (error) { this.rows = []; this.error = error.message || '加载失败' }
      finally { this.loading = false }
    },
    search() { this.query.pageNum = 1; this.load() },
    changePage(pagination) { this.query.pageNum = pagination.current; this.query.pageSize = pagination.pageSize; this.load() },
    async edit(row) {
      if (!(await this.checkModuleAction({ module: 'notices', action: row ? 'edit' : 'add', label: row ? '修改通知' : '新增通知' }))) return
      this.$router.push({ name: 'NoticeForm', query: row ? { id: row.noticeId } : {} })
    },
    async changeState(row, publish) {
      const label = publish ? '发布通知' : '撤下通知'
      if (this.busy || !(await this.checkModuleAction({ module: 'notices', action: 'edit', label }))) return
      this.$antdconfirm({ title: publish ? '发布后本企业员工即可查看，确认发布？' : '撤下后员工将无法查看，确认撤下？', onOk: async () => {
        if (this.busy) return
        this.busy = true
        try { await (publish ? publishNotice(row.noticeId) : withdrawNotice(row.noticeId)); this.$message.success(publish ? '已发布' : '已撤下'); await this.load() }
        finally { this.busy = false }
      } })
    },
    async remove(row) {
      if (this.busy || !(await this.checkModuleAction({ module: 'notices', action: 'remove', label: '删除通知' }))) return
      this.$antdconfirm({ title: '确认删除“' + row.noticeTitle + '”？', onOk: async () => {
        if (this.busy) return
        this.busy = true
        try { await delNotice(row.noticeId); this.$message.success('已删除'); this.query.pageNum = 1; await this.load() }
        finally { this.busy = false }
      } })
    }
  }
}
</script>
<style scoped>
.notice-toolbar { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
.search { width: 250px; }.author { width: 160px; }.state { width: 140px; }.notice-tip { margin-bottom: 16px; }
.operations { display: flex; gap: 12px; }.danger { color: #e15e5e; }
</style>
