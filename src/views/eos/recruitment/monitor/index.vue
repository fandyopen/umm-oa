<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-card :bordered="false" class="monitor">
      <div class="heading"><div><h2>招聘部门看板</h2><p>查看人员跟进、节点成果及停滞情况，点击数字可查看统计明细。</p></div><a-button icon="reload" :loading="loading" @click="refresh">刷新</a-button></div>
      <div class="filters">
        <a-select v-model="query.deptId" allow-clear placeholder="全部部门" @change="changeDepartment"><a-select-option v-for="item in options.departments || []" :key="item.id" :value="item.id">{{ item.name || '未分部门' }}</a-select-option></a-select>
        <a-select v-model="query.userId" allow-clear show-search option-filter-prop="children" placeholder="全部人员" @change="refresh"><a-select-option v-for="item in userOptions" :key="item.id" :value="item.id">{{ item.name }}</a-select-option></a-select>
        <a-select v-model="query.customerId" allow-clear show-search option-filter-prop="children" placeholder="全部客户" @change="changeCustomer"><a-select-option v-for="item in options.customers || []" :key="item.id" :value="item.id">{{ item.name }}</a-select-option></a-select>
        <a-select v-model="query.jobId" allow-clear show-search option-filter-prop="children" placeholder="全部职位" @change="refresh"><a-select-option v-for="item in jobOptions" :key="item.id" :value="item.id">{{ item.name }}</a-select-option></a-select>
      </div>
      <div class="period">
        <span>成果统计时间</span><a-radio-group v-model="period" button-style="solid" @change="setPeriod"><a-radio-button value="week">本周</a-radio-button><a-radio-button value="month">本月</a-radio-button><a-radio-button value="custom">自定义</a-radio-button></a-radio-group>
        <a-input v-model="query.startDate" type="date" aria-label="统计开始日期" @change="customPeriod"/><span>至</span><a-input v-model="query.endDate" type="date" aria-label="统计结束日期" @change="customPeriod"/>
        <a-button @click="reset">重置筛选</a-button>
      </div>
      <a-alert v-if="error" type="error" show-icon :message="error" class="notice" />
      <a-spin :spinning="loading">
        <div class="cards"><button v-for="key in cards" :key="key" type="button" @click="openDetails(key)"><span>{{ metrics[key] }}</span><strong :class="{ danger: key === 'overdue' && summary[key] > 0 }">{{ summary[key] || 0 }}</strong><small>查看明细 <a-icon type="right" /></small></button></div>
        <div class="scope-note">当前状态及风险不受成果日期限制；停滞按连续 {{ threshold }} 天（{{ threshold * 24 }} 小时）未有效跟进计算。数据更新于 {{ asOf || '—' }}。</div>
        <a-tabs v-model="tab">
          <a-tab-pane key="people" tab="人员跟进情况">
            <a-table row-key="userId" :data-source="people" :columns="peopleColumns" :scroll="{ x: 1390 }" :pagination="{ pageSize: 10, showSizeChanger: true }" size="middle">
              <template slot="person" slot-scope="_, row"><strong>{{ row.userName }}</strong><div class="muted">{{ row.deptName || '未分部门' }}</div></template>
              <template v-for="key in personMetrics" :slot="key" slot-scope="value, row"><a v-if="value" :key="key" @click="openDetails(key, row)">{{ value }}</a><span v-else :key="key">0</span></template>
              <template slot="lastFollowupTime" slot-scope="value">{{ value ? formatTime(value) : '暂无有效跟进' }}</template>
            </a-table>
            <p class="muted">跟进人数在所选时间内按“负责人＋候选人”去重。待处理事项包含推进中的招聘流程、待联系人才、未完成跟进任务及异常事项；同一人可能有多项工作。</p>
          </a-tab-pane>
          <a-tab-pane key="ranking" tab="招聘节点排行榜">
            <div class="ranking-filters"><a-radio-group v-model="rankingMetric" button-style="solid"><a-radio-button v-for="key in rankingMetrics" :key="key" :value="key">{{ metrics[key] }}榜</a-radio-button></a-radio-group><span class="muted">{{ appliedQuery.startDate }} 至 {{ appliedQuery.endDate }}</span></div>
            <a-table row-key="userId" :data-source="ranking" :pagination="{ pageSize: 10 }" size="middle">
              <a-table-column title="排名" data-index="rank" :width="80"><template slot-scope="value"><span :class="['rank', { medal: value <= 3 }]">{{ value }}</span></template></a-table-column>
              <a-table-column title="人员" data-index="userName" :width="150"/><a-table-column title="部门" data-index="deptName" :width="170"/>
              <a-table-column :title="metrics[rankingMetric] + '人次'" :width="170"><template slot-scope="_, row"><a @click="openDetails(rankingMetric, row)">{{ row[rankingMetric] }}</a></template></a-table-column>
              <a-table-column title="相对数量"><template slot-scope="_, row"><a-progress :percent="Math.round(row[rankingMetric] * 100 / ranking[0][rankingMetric])" :show-info="false" :stroke-color="'var(--umm-primary, #0eb3a2)'" /></template></a-table-column>
            </a-table>
            <p class="muted">按本次招聘的负责顾问归属，招聘节点以人岗流程为单位去重。多轮面试、重复发放和更正不重复计数；并列数量显示相同名次。入职按实际入职日期统计，其余按首次有效节点记录统计。历史缺少可靠发生时间的节点不推算。</p>
          </a-tab-pane>
          <a-tab-pane key="risks" tab="停滞与超期监控">
            <div class="ranking-filters"><a-radio-group v-model="riskMetric" @change="changeRiskMetric"><a-radio-button value="stagnant">{{ threshold }} 天未跟进（{{ summary.stagnant || 0 }}）</a-radio-button><a-radio-button value="overdue">超期未处理（{{ summary.overdue || 0 }}）</a-radio-button></a-radio-group><span class="muted">展开行可查看最近跟进内容</span></div>
            <monitor-records :rows="risks" :loading="riskLoading" :pagination="riskPagination" @change="changeRiskPage" />
          </a-tab-pane>
        </a-tabs>
      </a-spin>
      <a-drawer :visible="detailVisible" :title="detailTitle" width="min(1400px, 96vw)" @close="detailVisible = false" destroy-on-close>
        <p class="muted">{{ detailPeriod }}；展开行查看节点依据、跟进内容及实际操作人。</p>
        <a-alert v-if="detailError" type="error" :message="detailError" class="notice"/>
        <monitor-records :rows="detailRows" :loading="detailLoading" :pagination="detailPagination" @change="changeDetailPage" />
      </a-drawer>
    </a-card>
  </page-header-wrapper>
</template>

<script>
import moment from 'moment'
import { recruitmentRequest } from '/src/api/eos/recruitment/index.js'
import MonitorRecords from './MonitorRecords.vue'
import { monitorMetrics, rankingMetrics, rankedPeople, formatMonitorTime } from './metrics.js'

const dates = period => {
  const now = moment()
  return { startDate: now.clone().startOf(period === 'week' ? 'isoWeek' : 'month').format('YYYY-MM-DD'), endDate: now.format('YYYY-MM-DD') }
}
const personMetrics = ['active', 'followups', 'pending', 'overdue', 'stagnant', 'recommended', 'interviewed', 'offered', 'entered', 'completed']
export default {
  name: 'RecruitmentMonitor',
  components: { MonitorRecords },
  data: () => ({
    metrics: monitorMetrics, rankingMetrics, personMetrics, cards: ['active', 'feedback', 'interview', 'entry', 'guarantee', 'overdue'],
    query: { ...dates('month'), deptId: undefined, userId: undefined, customerId: undefined, jobId: undefined }, appliedQuery: {}, period: 'month',
    loading: false, error: '', people: [], options: {}, summary: {}, threshold: 3, asOf: '', tab: 'people', rankingMetric: 'recommended',
    riskMetric: 'stagnant', risks: [], riskLoading: false, riskPage: 1, riskSize: 10, riskTotal: 0,
    detailVisible: false, detailLoading: false, detailError: '', detailRows: [], detailTitle: '', detailQuery: {}, detailPeriod: '', detailPage: 1, detailSize: 10, detailTotal: 0
  }),
  computed: {
    userOptions() { return (this.options.users || []).filter(u => !this.query.deptId || u.deptId === this.query.deptId) },
    jobOptions() { return (this.options.jobs || []).filter(j => !this.query.customerId || j.customerId === this.query.customerId) },
    ranking() { return rankedPeople(this.people, this.rankingMetric) },
    peopleColumns() {
      return [
        { title: '人员 / 部门', key: 'person', scopedSlots: { customRender: 'person' }, width: 150, fixed: 'left' },
        ...personMetrics.map(key => ({ title: this.metrics[key], dataIndex: key, width: 105, scopedSlots: { customRender: key }, sorter: (a, b) => a[key] - b[key] })),
        { title: '最近有效跟进', dataIndex: 'lastFollowupTime', width: 190, scopedSlots: { customRender: 'lastFollowupTime' } }
      ]
    },
    riskPagination() { return { current: this.riskPage, pageSize: this.riskSize, total: this.riskTotal, showSizeChanger: true, showTotal: n => '共 ' + n + ' 项' } },
    detailPagination() { return { current: this.detailPage, pageSize: this.detailSize, total: this.detailTotal, showSizeChanger: true, showTotal: n => '共 ' + n + ' 条' } }
  },
  watch: { tab(value) { if (value === 'risks') this.loadRisks() } },
  mounted() { this.refresh() },
  methods: {
    formatTime: formatMonitorTime,
    async refresh() {
      if (!this.query.startDate || !this.query.endDate || this.query.endDate < this.query.startDate) { this.error = '请选择完整的统计日期，结束日期不能早于开始日期'; return }
      const requestId = this._overviewRequest = (this._overviewRequest || 0) + 1
      this.loading = true; this.error = ''
      const query = { ...this.query }
      try {
        const { data } = await recruitmentRequest('/monitor', 'get', query)
        if (requestId !== this._overviewRequest) return
        this.people = data.people || []; this.summary = data.summary || {}; this.options = data.options || {}; this.threshold = data.stagnantDays; this.asOf = formatMonitorTime(data.asOf)
        this.appliedQuery = query; this.riskPage = 1
        if (this.tab === 'risks') this.loadRisks()
      } catch (error) { if (requestId === this._overviewRequest) this.error = error.message || '统计加载失败，请重试' }
      finally { if (requestId === this._overviewRequest) this.loading = false }
    },
    changeDepartment() { this.query.userId = undefined; this.refresh() },
    changeCustomer() { this.query.jobId = undefined; this.refresh() },
    setPeriod() { if (this.period !== 'custom') { Object.assign(this.query, dates(this.period)); this.refresh() } },
    customPeriod() { this.period = 'custom'; this.refresh() },
    reset() { this.period = 'month'; this.query = { ...dates('month'), deptId: undefined, userId: undefined, customerId: undefined, jobId: undefined }; this.refresh() },
    async loadRisks() {
      const requestId = this._riskRequest = (this._riskRequest || 0) + 1
      this.riskLoading = true
      try {
        const data = await recruitmentRequest('/monitor/details', 'get', { ...this.appliedQuery, metric: this.riskMetric, pageNum: this.riskPage, pageSize: this.riskSize })
        if (requestId === this._riskRequest) { this.risks = data.rows || []; this.riskTotal = data.total || 0 }
      } catch (error) { if (requestId === this._riskRequest) { this.risks = []; this.riskTotal = 0; this.error = error.message || '风险明细加载失败' } }
      finally { if (requestId === this._riskRequest) this.riskLoading = false }
    },
    changeRiskPage(page) { this.riskPage = page.current; this.riskSize = page.pageSize; this.loadRisks() },
    changeRiskMetric() { this.riskPage = 1; this.loadRisks() },
    openDetails(metric, person) {
      this.detailQuery = { ...this.appliedQuery, metric, ...(person ? { userId: person.userId } : {}) }
      this.detailTitle = (person ? person.userName + ' · ' : '') + this.metrics[metric]
      this.detailPeriod = ['followups', ...rankingMetrics].includes(metric) ? this.appliedQuery.startDate + ' 至 ' + this.appliedQuery.endDate : '当前状态，统计时点：' + this.asOf
      this.detailPage = 1; this.detailRows = []; this.detailTotal = 0; this.detailVisible = true; this.loadDetails()
    },
    async loadDetails() {
      const requestId = this._detailRequest = (this._detailRequest || 0) + 1
      this.detailLoading = true; this.detailError = ''
      try {
        const data = await recruitmentRequest('/monitor/details', 'get', { ...this.detailQuery, pageNum: this.detailPage, pageSize: this.detailSize })
        if (requestId === this._detailRequest) { this.detailRows = data.rows || []; this.detailTotal = data.total || 0 }
      } catch (error) { if (requestId === this._detailRequest) this.detailError = error.message || '明细加载失败，请重试' }
      finally { if (requestId === this._detailRequest) this.detailLoading = false }
    },
    changeDetailPage(page) { this.detailPage = page.current; this.detailSize = page.pageSize; this.loadDetails() }
  }
}
</script>

<style lang="less" scoped>
.heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; h2 { font-size: 24px; margin-bottom: 5px; } p { color: #778491; } }
.filters, .period, .ranking-filters { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin: 15px 0; }
.filters .ant-select { width: 195px; }
.period .ant-input { width: 155px; }
.cards { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 14px; margin: 22px 0 12px; }
.cards button { text-align: left; border: 1px solid #e7edf0; background: #f8fafb; border-radius: 8px; padding: 17px 18px; cursor: pointer; display: grid; gap: 8px; transition: border-color .2s; }
.cards button:hover { border-color: var(--umm-primary, #0eb3a2); }
.cards span { font-size: 13px; color: #687787; }
.cards strong { font-size: 28px; line-height: 1.2; color: #263a4d; }
.cards small { font-size: 11px; color: #84909e; }
.cards .danger { color: #d25b4e; }
.scope-note, .muted { color: #84909e; font-size: 12px; line-height: 1.8; }
.notice { margin: 14px 0; }
.rank { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; border-radius: 6px; background: #f0f3f5; }
.medal { color: #9d6b1d; background: #fff3d8; font-weight: 600; }
@media (max-width: 1150px) { .cards { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 600px) { .cards { grid-template-columns: repeat(2, minmax(0, 1fr)); } .filters .ant-select { width: 100%; } }
</style>
