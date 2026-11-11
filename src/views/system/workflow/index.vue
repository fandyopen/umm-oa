<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-card :bordered="false" class="workflow-management">
      <div class="workflow-heading">
        <div>
          <h2>审批流程</h2>
          <p>集中查看各模块和表单已配置的审批流程、启用情况及未发布修改。</p>
        </div>
        <a-button icon="reload" :loading="loading" @click="load">刷新</a-button>
      </div>
      <div class="workflow-stats">
        <span>全部配置 <strong>{{ statistics.total }}</strong></span>
        <span>已生效 <strong class="workflow-stats__active">{{ statistics.active }}</strong></span>
        <span>含未发布草稿 <strong>{{ statistics.draft }}</strong></span>
        <span>已关闭 <strong>{{ statistics.closed }}</strong></span>
      </div>
      <div class="workflow-filters">
        <a-input v-model="query.keyword" allow-clear placeholder="搜索模块、审批动作或动作编码" class="workflow-search" @pressEnter="search" />
        <a-select v-model="query.module" allow-clear show-search option-filter-prop="children" placeholder="所属模块" class="workflow-module-filter" @change="search">
          <a-select-option v-for="item in modules" :key="item.value" :value="item.value">{{ item.label }}</a-select-option>
        </a-select>
        <a-select v-model="query.source" allow-clear placeholder="流程来源" class="workflow-select" @change="search">
          <a-select-option value="button">模块按钮流程</a-select-option>
          <a-select-option value="form">低代码表单流程</a-select-option>
        </a-select>
        <a-select v-model="query.state" allow-clear placeholder="流程状态" class="workflow-select" @change="search">
          <a-select-option v-for="item in states" :key="item.value" :value="item.value">{{ item.label }}</a-select-option>
        </a-select>
        <a-button type="primary" icon="search" @click="search">查询</a-button>
        <a-button @click="reset">重置</a-button>
      </div>
      <a-alert v-if="loadError" type="error" show-icon :message="loadError" class="workflow-error" />
      <a-table
        row-key="key"
        size="small"
        :loading="loading"
        :columns="columns"
        :data-source="rows"
        :scroll="{ x: 1280 }"
        :pagination="{ current: page, pageSize: 15, showTotal: total => '共 ' + total + ' 条配置', onChange: value => page = value }"
      >
        <span slot="module" slot-scope="text"><a-tooltip :title="text">{{ text }}</a-tooltip></span>
        <code slot="code" slot-scope="text">{{ text }}</code>
        <span slot="source" slot-scope="text">{{ text === 'form' ? '低代码表单流程' : '模块按钮流程' }}</span>
        <span slot="state" slot-scope="text, row">
          <a-tag :color="stateColors[row.state]">{{ row.stateLabel }}</a-tag>
          <a-tag v-if="row.hasDraft" color="orange">{{ row.published ? '有未发布修改' : '草稿' }}</a-tag>
        </span>
        <span slot="time" slot-scope="text">{{ dateTime(text) }}</span>
        <span slot="operation" slot-scope="text, row">
          <a @click="view(row)">查看</a>
          <template v-if="canDesign && row.flowDesignEnabled && row.moduleEnabled && row.actionStatus === '0'">
            <a-divider type="vertical" /><a @click="design(row)">流程设计</a>
          </template>
        </span>
        <div slot="emptyText" class="workflow-empty">暂无符合条件的流程配置</div>
      </a-table>
    </a-card>
    <a-drawer title="审批流程详情" width="min(880px, 100vw)" :visible="detailVisible" :destroy-on-close="true" @close="closeDetail">
      <a-spin :spinning="detailLoading">
        <a-alert v-if="detailError" type="error" show-icon :message="detailError" />
        <template v-if="detail">
          <a-descriptions bordered size="small" :column="2" class="workflow-detail-info">
            <a-descriptions-item label="流程名称" :span="2">{{ detail.flowName }}</a-descriptions-item>
            <a-descriptions-item label="所属模块" :span="2">{{ detail.modulePath }}</a-descriptions-item>
            <a-descriptions-item label="流程来源">{{ detail.source === 'form' ? '低代码表单流程' : '模块按钮流程' }}</a-descriptions-item>
            <a-descriptions-item label="动作编码"><code>{{ detail.actionCode }}</code></a-descriptions-item>
            <a-descriptions-item label="流程状态"><a-tag :color="stateColors[detail.state]">{{ detail.stateLabel }}</a-tag></a-descriptions-item>
            <a-descriptions-item label="流程设计">{{ detail.flowDesignEnabled ? '已开启' : '已关闭' }}</a-descriptions-item>
            <a-descriptions-item label="发布状态">{{ detail.published ? '已发布' : '未发布' }}</a-descriptions-item>
            <a-descriptions-item label="已发布版本审批">{{ detail.published ? detail.approvalEnabled ? '已启用' : '未启用' : '—' }}</a-descriptions-item>
            <a-descriptions-item label="最近修改" :span="2">{{ dateTime(detail.updateTime) }}</a-descriptions-item>
          </a-descriptions>
          <a-alert v-if="detail.hasDraft" type="info" show-icon message="存在未发布草稿" description="草稿不改变当前已发布流程；发布后的新申请使用新配置，进行中的审批继续按发起时的配置处理。" class="workflow-detail-hint" />
          <a-tabs v-model="previewVersion">
            <a-tab-pane key="published" tab="当前发布版本" :disabled="!detail.published">
              <workflow-preview :nodes="detail.publishedNodes || []" />
            </a-tab-pane>
            <a-tab-pane v-if="detail.hasDraft" key="draft" tab="未发布草稿">
              <p class="workflow-draft-setting">草稿审批开关：{{ detail.draftApprovalEnabled ? '启用审批' : '未启用审批' }}</p>
              <workflow-preview :nodes="detail.draftNodes || []" />
            </a-tab-pane>
          </a-tabs>
          <a-empty v-if="!detail.published && !detail.hasDraft" description="尚未保存或发布流程，可从流程设计开始配置" />
        </template>
      </a-spin>
    </a-drawer>
  </page-header-wrapper>
</template>

<script>
import moment from 'moment'
import { listWorkflowConfigurations, getWorkflowConfiguration } from '/src/api/system/workflow.js'
import WorkflowPreview from './WorkflowPreview.vue'

export default {
  name: 'WorkflowManage',
  components: { WorkflowPreview },
  data: () => ({
    query: { keyword: '', module: undefined, source: undefined, state: undefined },
    rows: [], modules: [], statistics: { total: 0, active: 0, draft: 0, closed: 0 },
    canDesign: false, loading: false, loadError: '', sequence: 0, page: 1,
    detail: null, detailVisible: false, detailLoading: false, detailError: '', detailSequence: 0, previewVersion: 'published',
    states: [
      { value: 'active', label: '已生效' }, { value: 'unconfigured', label: '待配置' }, { value: 'integration', label: '待接入' },
      { value: 'unpublished', label: '未发布' }, { value: 'disabled', label: '未启用' }, { value: 'closed', label: '已关闭' }
    ],
    stateColors: { active: 'green', unconfigured: undefined, integration: 'orange', unpublished: 'blue', disabled: 'orange', closed: undefined },
    columns: [
      { title: '所属模块', dataIndex: 'modulePath', width: 300, ellipsis: true, scopedSlots: { customRender: 'module' } },
      { title: '审批动作 / 表单', dataIndex: 'actionName', width: 180, ellipsis: true },
      { title: '动作编码', dataIndex: 'actionCode', width: 130, scopedSlots: { customRender: 'code' } },
      { title: '流程来源', dataIndex: 'source', width: 140, scopedSlots: { customRender: 'source' } },
      { title: '流程状态', dataIndex: 'state', width: 210, scopedSlots: { customRender: 'state' } },
      { title: '最近修改时间', dataIndex: 'updateTime', width: 180, scopedSlots: { customRender: 'time' } },
      { title: '操作', key: 'operation', width: 150, align: 'center', fixed: 'right', scopedSlots: { customRender: 'operation' } }
    ]
  }),
  created() { this.load() },
  activated() { if (this.rows.length) this.load() },
  beforeDestroy() { this.sequence++; this.detailSequence++ },
  methods: {
    async load() {
      const sequence = ++this.sequence
      this.loading = true
      this.loadError = ''
      try {
        const response = await listWorkflowConfigurations({ ...this.query })
        if (sequence !== this.sequence) return
        this.rows = response.data.rows
        this.modules = response.data.modules
        this.statistics = response.data.statistics
        this.canDesign = response.data.canDesign === true
      } catch (error) {
        if (sequence !== this.sequence) return
        this.rows = []
        this.loadError = error.message || '流程配置加载失败，请点击刷新重试'
      } finally {
        if (sequence === this.sequence) this.loading = false
      }
    },
    search() { this.page = 1; return this.load() },
    reset() {
      this.query = { keyword: '', module: undefined, source: undefined, state: undefined }
      return this.search()
    },
    dateTime(value) { return value && moment(value).isValid() ? moment(value).format('YYYY-MM-DD HH:mm:ss') : '—' },
    async view(row) {
      const sequence = ++this.detailSequence
      this.detail = null
      this.detailVisible = true
      this.detailLoading = true
      this.detailError = ''
      try {
        const response = await getWorkflowConfiguration({ key: row.key })
        if (sequence !== this.detailSequence) return
        this.detail = response.data
        this.previewVersion = this.detail.published ? 'published' : this.detail.hasDraft ? 'draft' : 'none'
      } catch (error) {
        if (sequence === this.detailSequence) this.detailError = error.message || '流程详情加载失败，请重试'
      } finally {
        if (sequence === this.detailSequence) this.detailLoading = false
      }
    },
    closeDetail() { this.detailVisible = false; this.detailSequence++ },
    design(row) {
      if (!this.canDesign || !row.flowDesignEnabled || !row.moduleEnabled || row.actionStatus !== '0') return
      if (row.source === 'form') {
        return this.$router.push({ name: 'processFormDesign', params: { id: row.tenantAppId || 1, formid: row.formId } })
      }
      return this.$router.push({ path: '/processDesign', query: { tenantAppId: row.tenantAppId, menuId: row.moduleId, menuName: row.moduleName, actionCode: row.actionCode } })
    }
  }
}
</script>

<style lang="less" scoped>
.workflow-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.workflow-heading h2 { margin: 0 0 8px; font-size: 20px; color: #263445; }
.workflow-heading p { margin: 0; color: #8a94a2; font-size: 13px; }
.workflow-stats { display: flex; flex-wrap: wrap; gap: 16px 32px; padding: 18px 0; margin-bottom: 18px; border-bottom: 1px solid #edf0f5; color: #758195; font-size: 13px; }
.workflow-stats strong { margin-left: 8px; color: #263445; font-size: 19px; font-weight: 600; }
.workflow-stats .workflow-stats__active { color: #389e68; }
.workflow-filters { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-bottom: 20px; }
.workflow-search { width: 280px; }
.workflow-module-filter { width: 240px; }
.workflow-select { width: 145px; }
.workflow-management code { font-size: 12px; overflow-wrap: anywhere; }
.workflow-error, .workflow-detail-hint { margin-bottom: 18px; }
.workflow-empty { padding: 32px 0; color: #8a94a2; }
.workflow-detail-info { margin-bottom: 20px; }
.workflow-draft-setting { color: #758195; font-size: 13px; }
@media (max-width: 768px) {
  .workflow-search, .workflow-module-filter { width: 100%; }
  .workflow-select { flex: 1; min-width: 130px; }
  .workflow-heading { align-items: flex-start; }
}
</style>
