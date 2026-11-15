<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-spin :spinning="loading">
      <div class="sales-header">
        <div>
          <h2>拓展经营</h2>
          <p>{{ department || '客户拓展部' }}<template v-if="manager"> · 负责人：{{ manager }}</template> · 查看销售过程、商机推进与签约回款</p>
        </div>
        <div class="sales-controls">
          <a-range-picker v-model="period" :allow-clear="false" :disabled-date="disabledDate" format="YYYY-MM-DD" />
          <a-button type="primary" icon="search" :loading="loading" @click="load">查询</a-button>
        </div>
      </div>

      <a-alert v-if="error" type="error" show-icon :message="error" />
      <a-alert v-if="scope === 'self'" type="info" show-icon message="当前账号仅能查看本人数据。团队排名和部门汇总需要部门管理数据范围。" />
      <a-alert v-if="!summary.salesCount" type="warning" show-icon message="当前没有纳入统计的在职拓展销售，请核对部门归属和成员状态。" />

      <div class="sales-metrics">
        <div v-for="metric in metrics" :key="metric.key" class="sales-metric">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.money ? money(summary[metric.key]) : number(summary[metric.key]) }}</strong>
        </div>
      </div>

      <section class="sales-section">
        <div class="section-heading"><h3>销售人员过程与结果</h3><span>按实际回款、签约金额、新增客户排序；可点击列标题排序</span></div>
        <a-table size="middle" :columns="columns" :data-source="staff" :pagination="{ pageSize: 20, showTotal: total => '共 ' + total + ' 人' }" row-key="userId" :scroll="staffTableScroll">
          <span slot="conversion" slot-scope="text, row">{{ rate(row.signedContracts, row.newCustomers) }}</span>
          <span slot="pipeline" slot-scope="text">{{ money(text) }}</span>
          <span slot="signed" slot-scope="text">{{ money(text) }}</span>
          <span slot="received" slot-scope="text">{{ money(text) }}</span>
          <span slot="attention" slot-scope="text, row">
            <a-tag v-if="row.overdueCustomers" color="red">逾期跟进 {{ row.overdueCustomers }}</a-tag>
            <a-tag v-if="row.stagnantOpportunities" color="orange">停滞商机 {{ row.stagnantOpportunities }}</a-tag>
            <span v-if="!row.overdueCustomers && !row.stagnantOpportunities" class="sales-muted">暂无</span>
          </span>
        </a-table>
      </section>

      <section class="sales-section">
        <div class="section-heading"><h3>需要跟进</h3><span>客户计划已逾期或尚无跟进记录；商机连续 14 天未更新</span></div>
        <a-table size="small" :columns="attentionColumns" :data-source="attention" :pagination="{ pageSize: 10, showTotal: total => '共 ' + total + ' 项' }" row-key="attentionKey" :scroll="attentionTableScroll">
          <span slot="type" slot-scope="text">{{ attentionType(text) }}</span>
          <span slot="age" slot-scope="text, row">{{ row.updatedDays != null ? row.updatedDays + ' 天未更新' : dateText(row.dueTime) }}</span>
          <span slot="amount" slot-scope="text">{{ money(text) }}</span>
          <span slot="links" slot-scope="text, row">
            <a @click="openCustomer(row)">客户档案</a>
            <template v-if="row.opportunityId"><a-divider type="vertical" /><a @click="openOpportunity(row)">商机</a></template>
          </span>
        </a-table>
        <a-empty v-if="!attention.length" description="当前没有逾期待跟进客户或停滞商机" />
      </section>

      <p class="sales-footnote">客户归属按当前负责人统计；商机统计当前开放阶段；合同金额按统计期内签署生效版本统计，回款按确认到账扣除退款及冲销。排名用于发现差异，不替代目标完成率或有效跟进质量复核。</p>
    </a-spin>
  </page-header-wrapper>
</template>

<script>
import moment from 'moment'
import { getSalesPerformance } from '/src/api/eos/crm/index.js'

export default {
  name: 'CrmSalesPerformance',
  data() {
    return {
      loading: false,
      error: '',
      period: [moment().startOf('month'), moment()],
      department: '',
      manager: '',
      windowWidth: typeof window === 'undefined' ? 1734 : window.innerWidth,
      scope: '',
      summary: {},
      staff: [],
      attention: [],
      metrics: [
        { key: 'salesCount', label: '在职拓展销售' }, { key: 'customers', label: '当前负责客户' },
        { key: 'newCustomers', label: '本期新增客户' }, { key: 'followups', label: '本期客户跟进' },
        { key: 'overdueCustomers', label: '逾期或未跟进客户' }, { key: 'activeOpportunities', label: '开放商机' },
        { key: 'stagnantOpportunities', label: '停滞商机' }, { key: 'signedAmount', label: '本期签约金额', money: true },
        { key: 'receivedAmount', label: '本期净回款', money: true }
      ],
      columns: [
        { title: '排名', key: 'rank', customRender: (text, row, index) => index + 1, width: 70 },
        { title: '拓展销售', dataIndex: 'name', width: 130 },
        { title: '当前客户', dataIndex: 'customerCount', sorter: (a, b) => a.customerCount - b.customerCount, width: 100 },
        { title: '本期新增', dataIndex: 'newCustomers', sorter: (a, b) => a.newCustomers - b.newCustomers, width: 100 },
        { title: '有效跟进', dataIndex: 'followups', sorter: (a, b) => a.followups - b.followups, width: 100 },
        { title: '有效商机', dataIndex: 'activeOpportunities', sorter: (a, b) => a.activeOpportunities - b.activeOpportunities, width: 100 },
        { title: '停滞商机', dataIndex: 'stagnantOpportunities', sorter: (a, b) => a.stagnantOpportunities - b.stagnantOpportunities, width: 100 },
        { title: '商机金额', dataIndex: 'pipelineAmount', scopedSlots: { customRender: 'pipeline' }, sorter: (a, b) => Number(a.pipelineAmount) - Number(b.pipelineAmount), width: 140 },
        { title: '签约合同', dataIndex: 'signedContracts', sorter: (a, b) => a.signedContracts - b.signedContracts, width: 100 },
        { title: '签约金额', dataIndex: 'signedAmount', scopedSlots: { customRender: 'signed' }, sorter: (a, b) => Number(a.signedAmount) - Number(b.signedAmount), width: 140 },
        { title: '净回款', dataIndex: 'receivedAmount', scopedSlots: { customRender: 'received' }, sorter: (a, b) => Number(a.receivedAmount) - Number(b.receivedAmount), width: 140 },
        { title: '需关注', key: 'attention', scopedSlots: { customRender: 'attention' }, width: 190 }
      ],
      attentionColumns: [
        { title: '类型', dataIndex: 'type', scopedSlots: { customRender: 'type' }, width: 140 }, { title: '客户', dataIndex: 'customerName', width: 190 },
        { title: '事项 / 商机', dataIndex: 'name', width: 240 }, { title: '阶段', dataIndex: 'stage', width: 130 },
        { title: '负责人', dataIndex: 'ownerName', width: 120 }, { title: '跟进 / 停滞时间', key: 'age', scopedSlots: { customRender: 'age' }, width: 160 },
        { title: '商机金额', dataIndex: 'amount', scopedSlots: { customRender: 'amount' }, width: 140 },
        { title: '入口', key: 'links', scopedSlots: { customRender: 'links' }, width: 160 }
      ]
    }
  },
  computed: {
    staffTableScroll() { return this.windowWidth - 256 < 1410 ? { x: 1410 } : undefined },
    attentionTableScroll() { return this.windowWidth - 256 < 1476 ? { x: 1476 } : undefined }
  },
  mounted() {
    window.addEventListener('resize', this.updateWindowWidth)
    this.load()
  },
  beforeDestroy() { window.removeEventListener('resize', this.updateWindowWidth) },
  methods: {
    updateWindowWidth() { this.windowWidth = window.innerWidth },
    async load() {
      this.loading = true
      this.error = ''
      try {
        const { data } = await getSalesPerformance({ from: this.period[0].format('YYYY-MM-DD'), to: this.period[1].format('YYYY-MM-DD') })
        this.department = data.department || ''
        this.manager = data.manager || ''
        this.scope = data.scope || ''
        this.summary = data.summary || {}
        this.staff = (data.staff || []).map((row, index) => ({ ...row, rank: index + 1 }))
      this.attention = (data.attention || []).map((row, index) => ({ ...row, attentionKey: row.type + ':' + (row.customerId || row.opportunityId || index) }))
      } catch (error) {
        this.error = error.message || '加载拓展经营数据失败'
      } finally { this.loading = false }
    },
    disabledDate(current) { return current && (current > moment().endOf('day') || current < moment().subtract(1, 'year').startOf('day')) },
    number(value) { return Number(value || 0).toLocaleString('zh-CN') },
    money(value) { return '￥' + Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) },
    rate(numerator, denominator) { return denominator ? Math.round((Number(numerator || 0) / Number(denominator)) * 100) + '%' : '—' },
    dateText(value) { return value ? moment(value).format('YYYY-MM-DD') : '未安排下次跟进' },
    attentionType(value) { return ({ 'No follow-up': '尚未跟进', 'Follow-up overdue': '计划已逾期', 'Stagnant opportunity': '商机停滞' })[value] || value },
    openCustomer(row) { this.$router.push({ path: '/crm/customers', query: { keyword: row.customerName || '' } }) },
    openOpportunity(row) { this.$router.push({ path: '/crm/opportunities', query: { keyword: row.name || '' } }) }
  }
}
</script>

<style lang="less" scoped>
.sales-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 16px; h2 { margin: 0 0 5px; font-size: 22px; } p { margin: 0; color: #667085; } }
.sales-controls { display: flex; flex-wrap: wrap; gap: 8px; }
.sales-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin: 16px 0; }
.sales-metric { min-width: 0; padding: 13px 16px; border: 1px solid #e5e9ee; border-radius: 6px; background: #fff; span { display: block; color: #718096; font-size: 12px; } strong { display: block; margin-top: 5px; color: #17212b; font-size: 21px; overflow-wrap: anywhere; } }
.sales-section { padding: 16px 0; border-top: 1px solid #e8edf3; }
.section-heading { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; margin-bottom: 12px; h3 { margin: 0; font-size: 16px; } span,.sales-muted { color: #718096; font-size: 12px; } }
.sales-footnote { margin: 8px 0 0; color: #718096; font-size: 12px; }
@media (max-width: 900px) { .sales-header { flex-direction: column; } .sales-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } .section-heading { align-items: flex-start; flex-direction: column; } }
</style>
