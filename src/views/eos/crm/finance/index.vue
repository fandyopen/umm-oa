<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-card :bordered="false">
      <div v-if="!isPayroll && !isCommission" class="head">
        <div>
          <h2>{{ $route.meta.title || '财务与回款管理' }}</h2>
          <span>{{ isPayroll ? '工资审批、实际发放与工资条发布分别留痕。' : '合同应收按签署生效版本计算，到账、退款和核销分别留痕。' }}</span>
        </div>
        <a-button v-if="isPayroll" icon="download" @click="exportCurrent">导出工资报表</a-button>
        <a-button v-else icon="download" :disabled="!showModuleAction('finance', 'export')" v-hasModuleAction="moduleActionBinding('finance', 'export')" @click="exportCurrent">导出当前工作区</a-button>
      </div>
      <a-row v-if="!isPayroll && !isCommission" :gutter="16" class="metrics">
        <a-col v-for="metric in metrics" :key="metric.key" :span="4"
          ><div>
            <span>{{ metric.name }}</span
            ><strong>{{ money(summary[metric.key]) }}</strong>
          </div></a-col
        >
      </a-row>
      <router-view ref="workspace" @changed="loadSummary" />
    </a-card>
  </page-header-wrapper>
</template>
<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { getFinanceReport, exportFinanceRows } from '/src/api/eos/crm/finance.js'
import * as XLSX from 'xlsx'
import { saveAs } from 'file-saver'

const module = { component: 'eos/crm/finance/index', label: '财务与回款管理' }
export default {
  name: 'CustomerFinance',
  mixins: [withModuleContexts({ finance: module })],
  data: () => ({
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
  computed: {
    isCommission() { return this.$route?.name === 'FinanceCommission' || this.$route?.path?.endsWith('/finance/commission') || false },
    isPayroll() {
      return this.$route?.name === 'FinancePayroll' || this.$route?.path?.endsWith('/finance/payroll') || false
    }
  },
  watch: {
    isPayroll(value) {
      if (!value) this.loadSummary()
    }
  },
  mounted() {
    this.loadSummary()
  },
  methods: {
    money(value) {
      return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
    },
    async loadSummary() {
      if (this.isPayroll || this.isCommission) return
      const res = await getFinanceReport({})
      this.summary = res.data?.summary || {}
    },
    async exportCurrent() {
      if (this.isPayroll) return this.$refs.workspace?.exportRows()
      if (!(await this.checkModuleAction({ module: 'finance', action: 'export', label: '导出财务数据' }))) return
      const view = this.$refs.workspace
      const workspace = view?.$options.financeWorkspace
      if (!workspace) return
      const filters = view.query || {}
      const exports =
        workspace === 'reconcile' ? await Promise.all(['ledger', 'receipts', 'invoice', 'refund', 'adjustment', 'cost', 'reconcile'].map(type => exportFinanceRows(type, filters))) : [await exportFinanceRows(workspace, filters)]
      const names = workspace === 'reconcile' ? ['应收与账龄', '实际到账', '发票', '退款', '核销冲销', '直接成本', '客户对账'] : ['财务数据']
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
