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
          <a-button v-if="canManageRules" icon="setting" @click="openRules">监管规则</a-button>
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
            <button v-if="row.overdueCustomers" type="button" class="attention-link" title="点击查看该销售的超期客户" @click="locateAttention(row, 'customer')"><a-tag color="red">逾期跟进 {{ row.overdueCustomers }}</a-tag></button>
            <button v-if="row.stagnantOpportunities" type="button" class="attention-link" title="点击查看该销售的停滞商机" @click="locateAttention(row, 'opportunity')"><a-tag color="orange">停滞商机 {{ row.stagnantOpportunities }}</a-tag></button>
            <span v-if="!row.overdueCustomers && !row.stagnantOpportunities" class="sales-muted">暂无</span>
          </span>
        </a-table>
      </section>

      <section ref="attentionSection" class="sales-section">
        <div class="section-heading"><h3>需要跟进</h3><span>{{ ruleSummary }}</span></div>
        <div v-if="attentionOwnerId" class="attention-filter">
          <span>当前查看：{{ attentionOwnerName }} · {{ attentionKind === 'customer' ? '超期客户' : '停滞商机' }}（{{ filteredAttention.length }} 项）</span>
          <a-button type="link" size="small" @click="clearAttentionFilter">查看全部</a-button>
        </div>
        <a-table size="small" :columns="attentionColumns" :data-source="filteredAttention" :pagination="{ current: attentionPage, pageSize: 10, showTotal: total => '共 ' + total + ' 项' }" row-key="attentionKey" :scroll="attentionTableScroll" @change="changeAttentionPage">
          <span slot="type" slot-scope="text">{{ attentionType(text) }}</span>
          <span slot="customer" slot-scope="text, row"><a v-if="row.customerId" @click="openCustomer(row)">{{ text || '查看客户' }}</a><span v-else>{{ text || '未关联客户' }}</span></span>
          <span slot="matter" slot-scope="text, row">{{ text }}<small v-if="row.reason" class="attention-reason">{{ row.reason }}</small></span>
          <span slot="age" slot-scope="text, row">{{ row.updatedDays != null ? row.updatedDays + ' 天未更新' : '截止 ' + dateText(row.dueTime) }}</span>
          <span slot="amount" slot-scope="text, row">{{ row.opportunityId ? money(text) : '—' }}</span>
          <span slot="links" slot-scope="text, row">
            <a v-if="row.customerId" @click="openCustomer(row)">查看并跟进</a>
            <template v-if="row.opportunityId"><a-divider type="vertical" /><a @click="openOpportunity(row)">商机</a></template>
          </span>
        </a-table>
        <a-empty v-if="!filteredAttention.length" description="当前没有符合条件的超期客户或停滞商机" />
      </section>

      <p class="sales-footnote">客户归属按当前负责人统计；商机统计当前开放阶段；合同金额按统计期内签署生效版本统计，回款按确认到账扣除退款及冲销。排名用于发现差异，不替代目标完成率或有效跟进质量复核。</p>
    </a-spin>
    <a-modal v-model="rulesVisible" title="拓展经营监管规则" :confirm-loading="savingRules" :mask-closable="false" @ok="saveRules">
      <a-form-model layout="vertical">
        <a-form-model-item label="分配后首次跟进时限（天）"><a-input-number v-model="ruleForm.firstFollowupDays" :min="1" :max="365" :precision="0" /></a-form-model-item>
        <a-form-model-item label="未安排下次计划时的跟进间隔（天）"><a-input-number v-model="ruleForm.followupIntervalDays" :min="1" :max="365" :precision="0" /></a-form-model-item>
        <a-form-model-item label="商机连续未更新时限（天）"><a-input-number v-model="ruleForm.stagnantOpportunityDays" :min="1" :max="365" :precision="0" /></a-form-model-item>
      </a-form-model>
      <a-alert type="info" show-icon message="首次跟进从本次分配时间起算；本次分配后的有效客户跟进均参与超期判断，包括管理员或协作人员代录。已有下次跟进计划时以计划时间为准，否则按跟进间隔计算。重新分配后重新起算；保存后立即按新规则重新统计。" />
    </a-modal>
  </page-header-wrapper>
</template>

<script>
import moment from 'moment'
import { getSalesPerformance, saveSalesPerformanceRules } from '/src/api/eos/crm/index.js'

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
      attentionOwnerId: null,
      attentionOwnerName: '',
      attentionKind: '',
      attentionPage: 1,
      rules: { firstFollowupDays: 3, followupIntervalDays: 7, stagnantOpportunityDays: 14 },
      canManageRules: false,
      rulesVisible: false,
      savingRules: false,
      ruleForm: {},
      metrics: [
        { key: 'salesCount', label: '在职拓展销售' }, { key: 'customers', label: '当前负责客户' },
        { key: 'newCustomers', label: '本期新增客户' }, { key: 'followups', label: '本期客户跟进' },
        { key: 'overdueCustomers', label: '跟进超期客户' }, { key: 'activeOpportunities', label: '开放商机' },
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
        { title: '类型', dataIndex: 'type', scopedSlots: { customRender: 'type' }, width: 140 }, { title: '客户', dataIndex: 'customerName', scopedSlots: { customRender: 'customer' }, width: 190 },
        { title: '事项 / 商机', dataIndex: 'name', scopedSlots: { customRender: 'matter' }, width: 240 }, { title: '阶段', dataIndex: 'stage', width: 130, customRender: text => text || '—' },
        { title: '负责人', dataIndex: 'ownerName', width: 120 }, { title: '跟进 / 停滞时间', key: 'age', scopedSlots: { customRender: 'age' }, width: 160 },
        { title: '商机金额', dataIndex: 'amount', scopedSlots: { customRender: 'amount' }, width: 140 },
        { title: '入口', key: 'links', scopedSlots: { customRender: 'links' }, width: 160 }
      ]
    }
  },
  computed: {
    filteredAttention() {
      return this.attention.filter(row => !this.attentionOwnerId || (Number(row.ownerUserId) === this.attentionOwnerId && (this.attentionKind === 'opportunity' ? row.type === 'Stagnant opportunity' : row.type !== 'Stagnant opportunity')))
    },
    ruleSummary() { return `首次跟进：分配后 ${this.rules.firstFollowupDays} 天；后续优先按计划，无计划间隔 ${this.rules.followupIntervalDays} 天；商机 ${this.rules.stagnantOpportunityDays} 天未更新` },
    staffTableScroll() { return this.windowWidth - 256 < 1410 ? { x: 1410 } : undefined },
    attentionTableScroll() { return this.windowWidth - 256 < 1476 ? { x: 1476 } : undefined }
  },
  mounted() {
    window.addEventListener('resize', this.updateWindowWidth)
    this.load()
  },
  beforeDestroy() { window.removeEventListener('resize', this.updateWindowWidth) },
  methods: {
    locateAttention(row, kind) {
      this.attentionOwnerId = Number(row.userId)
      this.attentionOwnerName = row.name
      this.attentionKind = kind
      this.attentionPage = 1
      this.$nextTick(() => this.$refs.attentionSection?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
    },
    clearAttentionFilter() { this.attentionOwnerId = null; this.attentionOwnerName = ''; this.attentionKind = ''; this.attentionPage = 1 },
    changeAttentionPage(pagination) { this.attentionPage = pagination.current },
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
        this.rules = data.rules || this.rules
        this.canManageRules = !!data.canManageRules
        this.staff = (data.staff || []).map((row, index) => ({ ...row, rank: index + 1 }))
        this.attention = (data.attention || []).map((row, index) => ({ ...row, attentionKey: row.type + ':' + (row.opportunityId || row.customerId || index) }))
        this.attentionPage = 1
      } catch (error) {
        this.error = error.message || '加载拓展经营数据失败'
      } finally { this.loading = false }
    },
    openRules() { this.ruleForm = { ...this.rules }; this.rulesVisible = true },
    async saveRules() {
      if (this.savingRules) return
      if (Object.values(this.ruleForm).some(value => !Number.isInteger(value) || value < 1 || value > 365)) return this.$message.warning('请填写1至365的整数天数')
      this.savingRules = true
      try {
        await saveSalesPerformanceRules(this.ruleForm)
        this.rulesVisible = false
        this.$message.success('监管规则已保存')
        await this.load()
      } finally { this.savingRules = false }
    },
    disabledDate(current) { return current && (current > moment().endOf('day') || current < moment().subtract(1, 'year').startOf('day')) },
    number(value) { return Number(value || 0).toLocaleString('zh-CN') },
    money(value) { return '￥' + Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) },
    rate(numerator, denominator) { return denominator ? Math.round((Number(numerator || 0) / Number(denominator)) * 100) + '%' : '—' },
    dateText(value) { return value ? moment(value).format('YYYY-MM-DD') : '未安排下次跟进' },
    attentionType(value) { return ({ 'No follow-up': '首次跟进超期', 'Follow-up overdue': '跟进已超期', 'Stagnant opportunity': '商机停滞' })[value] || value },
    openCustomer(row) { this.$router.push({ path: '/crm/customers', query: { customerId: String(row.customerId), tab: 'followups' } }) },
    openOpportunity(row) { this.$router.push({ path: '/crm/opportunities', query: { businessId: String(row.opportunityId) } }) }
  }
}
</script>

<style lang="less" scoped>
.sales-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 16px; h2 { margin: 0 0 5px; font-size: 22px; } p { margin: 0; color: #667085; } }
.sales-controls { display: flex; flex-wrap: wrap; gap: 8px; }
.sales-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin: 16px 0; }
.sales-metric { min-width: 0; padding: 13px 16px; border: 1px solid #e5e9ee; border-radius: 6px; background: #fff; span { display: block; color: #718096; font-size: 12px; } strong { display: block; margin-top: 5px; color: #17212b; font-size: 21px; overflow-wrap: anywhere; } }
.sales-section { padding: 16px 0; border-top: 1px solid #e8edf3; }
.sales-section { scroll-margin-top: 90px; }
.attention-link { padding: 0; border: 0; background: transparent; cursor: pointer; .ant-tag { cursor: pointer; } }
.attention-link:focus-visible { outline: 2px solid #13a89e; outline-offset: 2px; }
.attention-filter { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; padding: 8px 12px; background: #e6f7f5; border-radius: 4px; }
.attention-reason { display: block; margin-top: 4px; color: #718096; white-space: normal; }
.section-heading { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; margin-bottom: 12px; h3 { margin: 0; font-size: 16px; } span,.sales-muted { color: #718096; font-size: 12px; } }
.sales-footnote { margin: 8px 0 0; color: #718096; font-size: 12px; }
@media (max-width: 900px) { .sales-header { flex-direction: column; } .sales-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } .section-heading { align-items: flex-start; flex-direction: column; } }
</style>
