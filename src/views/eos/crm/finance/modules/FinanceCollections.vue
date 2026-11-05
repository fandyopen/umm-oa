<template>
  <div>
    <div class="toolbar">
      <a-input-search v-model="query.keyword" allow-clear placeholder="搜索客户或催收内容" style="width: 260px" @search="load" />
      <a-select v-model="query.status" allow-clear placeholder="全部任务" style="width: 130px" @change="load"
        ><a-select-option value="open">待跟进</a-select-option><a-select-option value="closed">已关闭</a-select-option></a-select
      >
      <a-button icon="reload" @click="load">刷新</a-button>
      <a-button v-if="showModuleAction('finance', 'collect')" type="primary" icon="plus" @click="openPlan" v-hasModuleAction="moduleActionBinding('finance', 'collect')">新增催收计划</a-button>
    </div>
    <a-table :loading="loading" :data-source="rows" row-key="id" :pagination="false" :scroll="{ x: 1100 }" size="small">
      <a-table-column title="客户" data-index="customerName" /><a-table-column title="负责人" data-index="ownerName" /><a-table-column title="下次催收" data-index="nextDate" /><a-table-column
        title="跟进内容"
        data-index="content"
      />
      <a-table-column title="状态" data-index="status"
        ><template slot-scope="s"
          ><a-tag :color="s === 'open' ? 'orange' : 'green'">{{ s === 'open' ? '待跟进' : '已关闭' }}</a-tag></template
        ></a-table-column
      >
      <a-table-column title="操作"
        ><template slot-scope="r"
          ><a-button v-if="r.status === 'open' && showModuleAction('finance', 'collect')" type="link" @click="updatePlan(r)">更新计划</a-button
          ><a-button v-if="r.status === 'open' && showModuleAction('finance', 'collect')" type="link" @click="closePlan(r)">关闭任务</a-button
          ><a-button type="link" @click="historyPlan = r">历史</a-button
          ></template
        ></a-table-column
      >
    </a-table>
    <a-modal :visible="!!historyPlan" title="催收记录历史" :footer="null" width="800px" :body-style="{ maxHeight: '72vh', overflowY: 'auto' }" @cancel="historyPlan = null">
      <template v-if="historyPlan">
        <a-descriptions size="small" bordered :column="{ xs: 1, sm: 2 }"><a-descriptions-item label="客户">{{ historyPlan.customerName || '—' }}</a-descriptions-item><a-descriptions-item label="负责人">{{ historyPlan.ownerName || '—' }}</a-descriptions-item></a-descriptions>
        <finance-history-timeline :history="historyPlan.history || []" />
      </template>
    </a-modal>
    <a-modal :visible="visible" title="催收计划" :confirm-loading="saving" @cancel="visible = false" @ok="save">
      <a-form-model :model="form" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-model-item label="应收项目"
          ><a-select v-model="form.sourceKey" :disabled="!!form.collectionId" show-search option-filter-prop="children" @change="sourceChange"
            ><a-select-option v-for="s in sources" :key="s.sourceKey" :value="s.sourceKey">{{ sourceLabel(s) }}</a-select-option></a-select
          ></a-form-model-item
        >
        <a-form-model-item label="负责人"
          ><a-select v-model="form.ownerUserId" show-search option-filter-prop="children"
            ><a-select-option v-for="u in owners" :key="u.id" :value="u.id">{{ u.name }}</a-select-option></a-select
          ></a-form-model-item
        >
        <a-form-model-item label="下次催收"><a-date-picker v-model="form.nextDate" value-format="YYYY-MM-DD" /></a-form-model-item>
        <a-form-model-item label="跟进内容"><a-textarea v-model="form.content" :max-length="2000" /></a-form-model-item>
      </a-form-model>
    </a-modal>
  </div>
</template>
<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { listFinanceOptions, listCollections, createCollection } from '/src/api/eos/crm/finance.js'
import FinanceHistoryTimeline from './FinanceHistoryTimeline.vue'
const context = { component: 'eos/crm/finance/index', label: '财务与回款管理' }
export default {
  name: 'FinanceCollections',
  components: { FinanceHistoryTimeline },
  mixins: [
    withModuleContexts(
      { finance: context },
      {
        openPlan: { module: 'finance', action: 'collect', label: '新增催收计划' },
        updatePlan: { module: 'finance', action: 'collect', label: '更新催收计划' },
        save: { module: 'finance', action: 'collect', label: '保存催收计划' },
        closePlan: { module: 'finance', action: 'collect', label: '关闭催收任务' }
      }
    )
  ],
  data: () => ({ rows: [], sources: [], owners: [], loading: false, saving: false, visible: false, historyPlan: null, query: { keyword: '', status: 'open' }, form: {} }),
  mounted() {
    this.load()
    this.loadOptions()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const r = await listCollections(this.query)
        this.rows = r.data || []
      } finally {
        this.loading = false
      }
    },
    async loadOptions() {
      const r = await listFinanceOptions()
      this.sources = r.data?.sources || []
      this.owners = r.data?.owners || []
    },
    sourceLabel(s) {
      return s.customerName + ' · ' + (s.contractName || s.title) + ' · ' + (s.title || s.termTitle) + ' · 待收 ' + Number(s.outstandingAmount || 0).toFixed(2)
    },
    selected() {
      return this.sources.find(s => s.sourceKey === this.form.sourceKey)
    },
    sourceChange() {
      const s = this.selected()
      if (s) Object.assign(this.form, { customerId: s.customerId, contractId: s.contractId, termKey: s.termKey, receivableId: s.receivableId })
    },
    async openPlan() {
      await this.loadOptions()
      this.form = { ownerUserId: this.$store.getters.userInfo?.userId }
      this.visible = true
    },
    updatePlan(r) {
      const source = r.receivableId ? 'manual:' + r.receivableId : 'contract:' + r.contractId + ':' + r.termKey
      this.form = { ...r, collectionId: r.id, sourceKey: source }
      this.visible = true
    },
    async save() {
      if (this.saving) return
      const s = this.selected()
      if (!s) return this.$message.warning('请选择应收项目')
      if (!this.form.ownerUserId || !this.form.nextDate || !this.form.content?.trim()) return this.$message.warning('请填写负责人、下次催收日期和跟进内容')
      this.saving = true
      try {
        await createCollection({ ...this.form, sourceKey: undefined })
        this.visible = false
        this.$message.success('催收计划已保存')
        await this.load()
      } finally {
        this.saving = false
      }
    },
    async closePlan(r) {
      let answer
      try {
        answer = await this.$prompt('关闭原因', '关闭催收任务', { inputValidator: v => !!v?.trim() && v.trim().length <= 1000, message: '请填写关闭原因' })
      } catch (e) {
        return
      }
      await createCollection({ ...r, collectionId: r.id, close: true, reason: answer.value, content: answer.value })
      this.$message.success('催收任务已关闭')
      await this.load()
    }
  }
}
</script>
<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.toolbar .ant-btn-primary {
  margin-left: auto;
}
</style>
