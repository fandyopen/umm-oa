<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-card :bordered="false" class="monitor">
      <div class="heading">
        <div>
          <h2>部门看板</h2>
          <p>监控销售人员从客户接触、商机推进到合同签署的情况，点击数字可核对业务明细。</p>
        </div>
        <div class="heading-actions"><a-button v-if="canManageRules" icon="setting" @click="openRules">监管规则</a-button><a-button icon="reload" :loading="loading" @click="refresh">刷新</a-button></div>
      </div>
      <div class="filters">
        <a-select v-model="query.deptId" allow-clear placeholder="全部部门" @change="changeDepartment"
          ><a-select-option v-for="item in options.departments || []" :key="item.id" :value="item.id">{{ item.name || '未分部门' }}</a-select-option></a-select
        >
        <a-select v-model="query.userId" allow-clear show-search option-filter-prop="children" placeholder="全部人员" @change="refresh"
          ><a-select-option v-for="item in userOptions" :key="item.id" :value="item.id">{{ item.name }}</a-select-option></a-select
        >
        <a-select v-model="query.customerId" allow-clear show-search option-filter-prop="children" placeholder="全部客户" @change="refresh"
          ><a-select-option v-for="item in options.customers || []" :key="item.id" :value="item.id">{{ item.name }}</a-select-option></a-select
        >
      </div>
      <div class="period">
        <span>成果统计时间</span
        ><a-radio-group v-model="period" button-style="solid" @change="setPeriod"
          ><a-radio-button value="week">本周</a-radio-button><a-radio-button value="month">本月</a-radio-button><a-radio-button value="custom">自定义</a-radio-button></a-radio-group
        ><a-input v-model="query.startDate" type="date" aria-label="统计开始日期" @change="customPeriod" /><span>至</span><a-input v-model="query.endDate" type="date" aria-label="统计结束日期" @change="customPeriod" /><a-button
          @click="reset"
          >重置筛选</a-button
        >
      </div>
      <a-alert v-if="error" type="error" show-icon :message="error" class="notice" />
      <a-alert v-if="scope === 'self'" type="info" show-icon message="当前仅可查看本人数据；部门汇总和排行榜按账号的数据范围展示。" />
      <a-spin :spinning="loading">
        <div class="cards">
          <button v-for="key in cards" :key="key" type="button" @click="openDetails(key)">
            <span>{{ metrics[key] }}</span
            ><strong :class="{ danger: key === 'overdue' && summary[key] > 0 }">{{ display(key, summary[key]) }}</strong
            ><small>查看明细 <a-icon type="right" /></small>
          </button>
        </div>
        <div class="scope-note">统计范围：拓展客户部及其下属部门，当前 {{ people.length }} 位在职人员（含零业绩）；客户与风险不受成果日期限制。客户连续 3 天（72 小时）未跟进提示停滞，商机按 {{ rules.stagnantOpportunityDays }} 天未推进提示。更新于 {{ asOf || '—' }}。</div>
        <a-tabs v-model="tab">
          <a-tab-pane key="people" tab="人员跟进情况">
            <a-table row-key="userId" :data-source="people" :columns="peopleColumns" :scroll="{ x: 1915 }" :pagination="{ pageSize: 10, showSizeChanger: true }" size="middle">
              <template slot="person" slot-scope="_, row"
                ><strong>{{ row.userName }}</strong>
                <div class="muted">{{ row.deptName || '未分部门' }}</div></template
              >
              <template v-for="key in personMetrics" :slot="key" slot-scope="value, row"
                ><a v-if="Number(value) !== 0" :key="key" @click="openDetails(key, row)">{{ display(key, value) }}</a
                ><span v-else :key="key">{{ display(key, 0) }}</span></template
              >
              <template slot="lastFollowupTime" slot-scope="value">{{ value ? formatTime(value) : '暂无有效跟进' }}</template>
            </a-table>
            <p class="muted">按当前负责人及所在部门归属，管理员或协作人员代录保留实际操作人。接触客户按期内有真实跟进的客户去重，跟进次数按记录计数；仅统计本次分配后的客户跟进。</p>
          </a-tab-pane>
          <a-tab-pane key="ranking" tab="销售节点排行榜">
            <p class="muted ranking-period">{{ appliedQuery.startDate }} 至 {{ appliedQuery.endDate }} · 按数量从高到低排列，保留零业绩人员；并列数量显示相同名次。</p>
            <div class="sales-rankings">
              <section v-for="board in rankings" :key="board.metric" class="ranking-board" :data-metric="board.metric">
                <h3>{{ board.title }}</h3>
                <p class="muted">{{ board.description }}</p>
                <a-table row-key="userId" :data-source="board.people" :pagination="{ pageSize: 10, hideOnSinglePage: true }" size="small" :scroll="{ x: 440 }">
                  <a-table-column title="排名" data-index="rank" :width="60">
                    <template slot-scope="value, row"><span :class="['rank', { medal: value <= 3 && row[board.metric] > 0 }]">{{ value }}</span></template>
                  </a-table-column>
                  <a-table-column title="人员 / 部门" :width="150">
                    <template slot-scope="_, row"><strong>{{ row.userName }}</strong><div class="muted">{{ row.deptName || '未分部门' }}</div></template>
                  </a-table-column>
                  <a-table-column :title="board.countLabel" :width="100">
                    <template slot-scope="_, row"><a @click="openDetails(board.metric, row)">{{ row[board.metric] || 0 }}</a></template>
                  </a-table-column>
                  <a-table-column title="相对数量" :width="130">
                    <template slot-scope="_, row"><a-progress :percent="Math.round(Number(row[board.metric] || 0) * 100 / board.maximum)" :show-info="false" stroke-color="var(--umm-primary, #0eb3a2)" /></template>
                  </a-table-column>
                </a-table>
              </section>
            </div>
            <p class="muted ranking-period">签约按合同首次实际签署日期统计，草稿不计入，后续改版不重复计数；独立续约合同单独计数。点击数量可查看对应档案或合同明细。</p>
          </a-tab-pane>
          <a-tab-pane key="risks" tab="停滞与超期监控">
            <div class="ranking-filters">
              <a-radio-group v-model="riskMetric" @change="changeRiskMetric"
                ><a-radio-button value="stagnant">3 天未跟进客户（{{ summary.stagnant || 0 }}）</a-radio-button><a-radio-button value="overdue">跟进超期（{{ summary.overdue || 0 }}）</a-radio-button
                ><a-radio-button value="stagnantOpportunities">停滞商机（{{ summary.stagnantOpportunities || 0 }}）</a-radio-button></a-radio-group
              >
            </div>
            <p class="muted">首次跟进从本次分配起 {{ rules.firstFollowupDays }} 天；后续优先按下次计划，未安排计划按 {{ rules.followupIntervalDays }} 天。商机仅有效阶段记录重置停滞计时，普通编辑不算推进。展开行可核对内容。</p>
            <monitor-records :rows="risks" :loading="riskLoading" :pagination="riskPagination" @change="changeRiskPage" />
          </a-tab-pane>
        </a-tabs>
      </a-spin>
      <a-drawer :visible="detailVisible" :title="detailTitle" width="min(1400px, 96vw)" @close="detailVisible = false" destroy-on-close
        ><p class="muted">{{ detailPeriod }}；展开行查看统计依据、跟进内容与实际操作人。</p>
        <a-alert v-if="detailError" type="error" :message="detailError" class="notice" /><monitor-records :rows="detailRows" :loading="detailLoading" :pagination="detailPagination" @change="changeDetailPage"
      /></a-drawer>
      <a-modal v-model="rulesVisible" title="拓展经营监管规则" :confirm-loading="savingRules" :mask-closable="false" @ok="saveRules">
        <a-form-model layout="vertical"
          ><a-form-model-item label="分配后首次跟进时限（天）"><a-input-number v-model="ruleForm.firstFollowupDays" :min="1" :max="365" :precision="0" /></a-form-model-item
          ><a-form-model-item label="未安排下次计划时的跟进间隔（天）"><a-input-number v-model="ruleForm.followupIntervalDays" :min="1" :max="365" :precision="0" /></a-form-model-item
          ><a-form-model-item label="商机连续未推进时限（天）"><a-input-number v-model="ruleForm.stagnantOpportunityDays" :min="1" :max="365" :precision="0" /></a-form-model-item
        ></a-form-model>
        <a-alert type="info" show-icon message="保存后立即重算超期与商机停滞。客户3天未跟进是独立的固定观察指标，不受本表时限修改影响；重新分配后重新起算。" />
      </a-modal>
    </a-card>
  </page-header-wrapper>
</template>

<script>
import moment from 'moment'
import { getSalesMonitor, getSalesMonitorDetails, saveSalesPerformanceRules } from '/src/api/eos/crm/index.js'
import MonitorRecords from './MonitorRecords.vue'
import { metrics, salesRankings, moneyMetrics, currentMetrics, rankedPeople, formatTime, money } from './metrics.js'
const dates = period => ({
  startDate: moment()
    .startOf(period === 'week' ? 'isoWeek' : 'month')
    .format('YYYY-MM-DD'),
  endDate: moment().format('YYYY-MM-DD')
})
const personMetrics = ['customers', 'newCustomers', 'contacted', 'followups', 'activeOpportunities', 'pendingContracts', 'signedContracts', 'signedAmount', 'receivedAmount', 'stagnant', 'overdue', 'stagnantOpportunities']
export default {
  name: 'CrmSalesPerformance',
  components: { MonitorRecords },
  data: () => ({
    metrics,
    personMetrics,
    cards: ['customers', 'contacted', 'activeOpportunities', 'signedContracts', 'signedAmount', 'overdue'],
    query: { ...dates('month'), deptId: undefined, userId: undefined, customerId: undefined },
    appliedQuery: {},
    period: 'month',
    loading: false,
    error: '',
    people: [],
    options: {},
    summary: {},
    scope: '',
    asOf: '',
    tab: 'people',
    rules: { firstFollowupDays: 3, followupIntervalDays: 7, stagnantOpportunityDays: 14 },
    canManageRules: false,
    rulesVisible: false,
    savingRules: false,
    ruleForm: {},
    riskMetric: 'stagnant',
    risks: [],
    riskLoading: false,
    riskPage: 1,
    riskSize: 10,
    riskTotal: 0,
    detailVisible: false,
    detailLoading: false,
    detailError: '',
    detailRows: [],
    detailTitle: '',
    detailQuery: {},
    detailPeriod: '',
    detailPage: 1,
    detailSize: 10,
    detailTotal: 0
  }),
  computed: {
    userOptions() {
      return (this.options.users || []).filter(u => !this.query.deptId || u.deptId === this.query.deptId)
    },
    rankings() {
      return salesRankings.map(board => ({ ...board, people: rankedPeople(this.people, board.metric), maximum: Math.max(1, ...this.people.map(p => Number(p[board.metric] || 0))) }))
    },
    peopleColumns() {
      return [
        { title: '人员 / 部门', key: 'person', scopedSlots: { customRender: 'person' }, width: 150, fixed: 'left' },
        ...personMetrics.map(key => ({ title: this.metrics[key], dataIndex: key, width: moneyMetrics.includes(key) ? 150 : 115, scopedSlots: { customRender: key }, sorter: (a, b) => Number(a[key]) - Number(b[key]) })),
        { title: '最近客户跟进', dataIndex: 'lastFollowupTime', width: 185, scopedSlots: { customRender: 'lastFollowupTime' } }
      ]
    },
    riskPagination() {
      return { current: this.riskPage, pageSize: this.riskSize, total: this.riskTotal, showSizeChanger: true, showTotal: n => '共 ' + n + ' 项' }
    },
    detailPagination() {
      return { current: this.detailPage, pageSize: this.detailSize, total: this.detailTotal, showSizeChanger: true, showTotal: n => '共 ' + n + ' 条' }
    }
  },
  watch: {
    tab(value) {
      if (value === 'risks') this.loadRisks()
    }
  },
  mounted() {
    this.refresh()
  },
  methods: {
    formatTime,
    display(key, value) {
      return moneyMetrics.includes(key) ? money(value) : Number(value || 0).toLocaleString('zh-CN')
    },
    async refresh() {
      if (!this.query.startDate || !this.query.endDate || this.query.endDate < this.query.startDate) {
        this.error = '请选择完整的统计日期，结束日期不能早于开始日期'
        return
      }
      const requestId = (this._overviewRequest = (this._overviewRequest || 0) + 1)
      this.loading = true
      this.error = ''
      const query = { ...this.query }
      try {
        const { data } = await getSalesMonitor(query)
        if (requestId !== this._overviewRequest) return
        this.people = data.people || []
        this.summary = data.summary || {}
        this.options = data.options || {}
        this.rules = data.rules
        this.canManageRules = !!data.canManageRules
        this.scope = data.scope
        this.asOf = formatTime(data.asOf)
        this.appliedQuery = query
        this.riskPage = 1
        if (this.tab === 'risks') this.loadRisks()
      } catch (error) {
        if (requestId === this._overviewRequest) this.error = error.message || '统计加载失败，请重试'
      } finally {
        if (requestId === this._overviewRequest) this.loading = false
      }
    },
    changeDepartment() {
      this.query.userId = undefined
      this.refresh()
    },
    setPeriod() {
      if (this.period !== 'custom') {
        Object.assign(this.query, dates(this.period))
        this.refresh()
      }
    },
    customPeriod() {
      this.period = 'custom'
      this.refresh()
    },
    reset() {
      this.period = 'month'
      this.query = { ...dates('month'), deptId: undefined, userId: undefined, customerId: undefined }
      this.refresh()
    },
    async loadRisks() {
      const requestId = (this._riskRequest = (this._riskRequest || 0) + 1)
      this.riskLoading = true
      try {
        const data = await getSalesMonitorDetails({ ...this.appliedQuery, metric: this.riskMetric, pageNum: this.riskPage, pageSize: this.riskSize })
        if (requestId === this._riskRequest) {
          this.risks = data.rows || []
          this.riskTotal = data.total || 0
        }
      } catch (error) {
        if (requestId === this._riskRequest) {
          this.risks = []
          this.riskTotal = 0
          this.error = error.message || '风险明细加载失败'
        }
      } finally {
        if (requestId === this._riskRequest) this.riskLoading = false
      }
    },
    changeRiskPage(page) {
      this.riskPage = page.current
      this.riskSize = page.pageSize
      this.loadRisks()
    },
    changeRiskMetric() {
      this.riskPage = 1
      this.loadRisks()
    },
    openDetails(metric, person) {
      this.detailQuery = { ...this.appliedQuery, metric, ...(person ? { userId: person.userId } : {}) }
      this.detailTitle = (person ? person.userName + ' · ' : '') + this.metrics[metric]
      this.detailPeriod = currentMetrics.includes(metric) ? '当前状态，统计时点：' + this.asOf : this.appliedQuery.startDate + ' 至 ' + this.appliedQuery.endDate
      this.detailPage = 1
      this.detailRows = []
      this.detailTotal = 0
      this.detailVisible = true
      this.loadDetails()
    },
    async loadDetails() {
      const requestId = (this._detailRequest = (this._detailRequest || 0) + 1)
      this.detailLoading = true
      this.detailError = ''
      try {
        const data = await getSalesMonitorDetails({ ...this.detailQuery, pageNum: this.detailPage, pageSize: this.detailSize })
        if (requestId === this._detailRequest) {
          this.detailRows = data.rows || []
          this.detailTotal = data.total || 0
        }
      } catch (error) {
        if (requestId === this._detailRequest) this.detailError = error.message || '明细加载失败，请重试'
      } finally {
        if (requestId === this._detailRequest) this.detailLoading = false
      }
    },
    changeDetailPage(page) {
      this.detailPage = page.current
      this.detailSize = page.pageSize
      this.loadDetails()
    },
    openRules() {
      this.ruleForm = { ...this.rules }
      this.rulesVisible = true
    },
    async saveRules() {
      if (this.savingRules) return
      if (Object.values(this.ruleForm).some(value => !Number.isInteger(value) || value < 1 || value > 365)) return this.$message.warning('请填写1至365的整数天数')
      this.savingRules = true
      try {
        await saveSalesPerformanceRules(this.ruleForm)
        this.rulesVisible = false
        this.$message.success('监管规则已保存')
        await this.refresh()
      } finally {
        this.savingRules = false
      }
    }
  }
}
</script>

<style lang="less" scoped>
.heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  h2 {
    font-size: 24px;
    margin-bottom: 5px;
  }
  p {
    color: #778491;
  }
}
.filters,
.period,
.ranking-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin: 15px 0;
}
.filters .ant-select {
  width: 195px;
}
.period .ant-input {
  width: 155px;
}
.cards {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
  margin: 22px 0 12px;
}
.cards button {
  text-align: left;
  border: 1px solid #e7edf0;
  background: #f8fafb;
  border-radius: 8px;
  padding: 17px 18px;
  cursor: pointer;
  display: grid;
  gap: 8px;
  transition: border-color 0.2s;
}
.cards button:hover {
  border-color: var(--umm-primary, #0eb3a2);
}
.cards span {
  font-size: 13px;
  color: #687787;
}
.cards strong {
  font-size: clamp(20px, 1.65vw, 28px);
  line-height: 1.2;
  color: #263a4d;
  overflow-wrap: anywhere;
}
.cards small {
  font-size: 11px;
  color: #84909e;
}
.cards .danger {
  color: #d25b4e;
}
.scope-note,
.muted {
  color: #84909e;
  font-size: 12px;
  line-height: 1.8;
}
.notice {
  margin: 14px 0;
}
.sales-rankings {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}
.ranking-board {
  min-width: 0;
  border: 1px solid #e7edf0;
  border-radius: 8px;
  padding: 18px 16px;
  h3 { font-size: 16px; margin: 0 0 6px; }
}
.ranking-period { margin: 16px 0; }
.rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: #f0f3f5;
}
.medal {
  color: #9d6b1d;
  background: #fff3d8;
  font-weight: 600;
}
@media (max-width: 1150px) {
  .sales-rankings { grid-template-columns: 1fr; }
  .cards {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .filters .ant-select {
    width: 100%;
  }
}
.heading-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
