<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-card :bordered="false">
      <div class="head">
        <div>
          <h2>财务与回款管理</h2>
          <span>合同应收按签署生效版本计算，到账、退款和核销分别留痕。</span>
        </div>
        <a-button icon="download" :disabled="!showModuleAction('finance', 'export')" v-hasModuleAction="moduleActionBinding('finance', 'export')" @click="exportCurrent">导出当前工作区</a-button>
      </div>
      <a-row :gutter="16" class="metrics">
        <a-col v-for="metric in metrics" :key="metric.key" :span="4"
          ><div>
            <span>{{ metric.name }}</span
            ><strong>{{ money(summary[metric.key]) }}</strong>
          </div></a-col
        >
      </a-row>
      <a-tabs v-model="tab" @change="changeTab">
        <a-tab-pane key="ledger" tab="应收台账"><finance-ledger ref="ledger" @changed="loadSummary" /></a-tab-pane>
        <a-tab-pane key="invoice" tab="开票管理"><finance-records ref="invoice" record-type="invoice" @changed="loadSummary" /></a-tab-pane>
        <a-tab-pane key="receipt" tab="回款登记"><finance-records ref="receipt" record-type="receipt" @changed="loadSummary" /></a-tab-pane>
        <a-tab-pane key="refund" tab="退款管理"><finance-records ref="refund" record-type="refund" @changed="loadSummary" /></a-tab-pane>
        <a-tab-pane key="adjustment" tab="核销与冲销"><finance-records ref="adjustment" record-type="adjustment" @changed="loadSummary" /></a-tab-pane>
        <a-tab-pane key="cost" tab="成本记录"><finance-records ref="cost" record-type="cost" @changed="loadSummary" /></a-tab-pane>
        <a-tab-pane key="collections" tab="逾期催收"><finance-collections ref="collections" /></a-tab-pane>
        <a-tab-pane key="reconcile" tab="对账报表"><finance-reconcile ref="reconcile" /></a-tab-pane>
      </a-tabs>
    </a-card>
  </page-header-wrapper>
</template>
<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import FinanceLedger from './modules/FinanceLedger.vue'
import FinanceRecords from './modules/FinanceRecords.vue'
import FinanceCollections from './modules/FinanceCollections.vue'
import FinanceReconcile from './modules/FinanceReconcile.vue'
import { getFinanceReport, exportFinanceRows } from '/src/api/eos/crm/finance.js'
import * as XLSX from 'xlsx'
import { saveAs } from 'file-saver'

const module = { component: 'eos/crm/finance/index', label: '财务与回款管理' }
export default {
  name: 'CustomerFinance',
  components: { FinanceLedger, FinanceRecords, FinanceCollections, FinanceReconcile },
  mixins: [withModuleContexts({ finance: module })],
  data: () => ({
    tab: 'ledger',
    summary: {},
    metrics: [
      { key: 'receivableAmount', name: '应收' },
      { key: 'invoicedAmount', name: '已开票' },
      { key: 'netReceivedAmount', name: '净回款' },
      { key: 'refundedAmount', name: '已退款' },
      { key: 'outstandingAmount', name: '未收' },
      { key: 'costAmount', name: '直接成本' }
    ]
  }),
  mounted() {
    this.loadSummary()
  },
  methods: {
    money(value) {
      return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
    },
    async loadSummary() {
      const res = await getFinanceReport({})
      this.summary = res.data?.summary || {}
    },
    async changeTab() {
      await this.$nextTick()
      const view = this.$refs[this.tab]
      await Promise.all([this.loadSummary(), view?.load(), view?.loadOptions?.()])
    },
    async exportCurrent() {
      if (!(await this.checkModuleAction({ module: 'finance', action: 'export', label: '导出财务数据' }))) return
      const filters = this.$refs[this.tab]?.query || {}
      const exports =
        this.tab === 'reconcile' ? await Promise.all(['ledger', 'receipts', 'invoice', 'refund', 'adjustment', 'cost', 'reconcile'].map(type => exportFinanceRows(type, filters))) : [await exportFinanceRows(this.tab, filters)]
      const names = this.tab === 'reconcile' ? ['应收与账龄', '实际到账', '发票', '退款', '核销冲销', '直接成本', '客户对账'] : ['财务数据']
      const book = XLSX.utils.book_new()
      for (let i = 0; i < exports.length; i++) {
        const rows = exports[i].data || []
        if (rows.length > 5000) return this.$message.warning('单个工作表最多导出5000条，请缩小筛选范围')
        XLSX.utils.book_append_sheet(book, XLSX.utils.json_to_sheet(rows), names[i])
      }
      const bytes = XLSX.write(book, { bookType: 'xlsx', type: 'array' })
      saveAs(new Blob([bytes], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), '财务与回款明细.xlsx')
    }
  }
}
</script>
<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.head h2 {
  margin: 0 0 4px;
  font-size: 20px;
}
.head span {
  color: #7b8190;
}
.metrics {
  margin: 24px 0 12px;
  border-bottom: 1px solid #edf0f3;
  padding-bottom: 16px;
}
.metrics > .ant-col {
  border-right: 1px solid #edf0f3;
}
.metrics span,
.metrics strong {
  display: block;
}
.metrics span {
  color: #8a9099;
  margin-bottom: 7px;
}
.metrics strong {
  color: #30343b;
  font-size: 20px;
  font-weight: 600;
}
@media (max-width: 768px) {
  .head {
    align-items: flex-start;
    flex-direction: column;
  }
  .metrics > .ant-col {
    width: 50%;
    margin-bottom: 14px;
  }
}
</style>
