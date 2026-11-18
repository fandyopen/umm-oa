<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb"
    ><a-card :bordered="false"
      ><a-tabs v-model="tab"
        ><a-tab-pane key="attendance" tab="我的考勤"><attendance-workspace self-mode /></a-tab-pane
        ><a-tab-pane key="salary" tab="我的工资条"
          ><a-alert type="info" show-icon message="工资条发布与实际付款分别记录；已查看工资条不代表已到账。" class="tw-mb-4" /><a-button icon="download" @click="download">下载我的工资条</a-button
          ><a-button icon="reload" :loading="loading" @click="load">刷新</a-button
          ><a-table :data-source="rows" :loading="loading" :scroll="{ x: 1745 }" row-key="id" class="tw-mt-4"
            ><a-table-column title="所属月份" data-index="period" :width="110" /><a-table-column v-for="field in fields" :key="field.key" :title="field.label" :data-index="field.key" :width="110"
              ><template slot-scope="v">{{ money(v) }}</template></a-table-column
            ><a-table-column title="查看状态" :width="185">
              <template slot-scope="_, r"><a-tag :color="Number(r.viewed) === 1 ? 'green' : 'orange'">{{ Number(r.viewed) === 1 ? '已查看' : '未查看' }}</a-tag><div v-if="Number(r.viewed) === 1" class="payslip-viewed-time">{{ r.viewedAt ? formatTime(r.viewedAt) : '历史记录未保留查看时间' }}</div></template>
            </a-table-column><a-table-column title="操作" :width="130" fixed="right"
              ><template slot-scope="_, r"><span v-if="Number(r.viewed) === 1" class="payslip-viewed-label"><a-icon type="check-circle" /> 已查看</span><a-button v-else type="link" :loading="readingIds.includes(r.id)" @click="read(r)">标记已查看</a-button></template></a-table-column
            ></a-table
          ></a-tab-pane
        ><a-tab-pane key="commission" tab="我的提成">
          <a-alert type="info" show-icon message="销售签约后查看预计业绩，实际回款后可结算；交付按入职或验收条件释放。预计、可核算与实际发放分别记录。" class="tw-mb-4" />
          <a-button icon="reload" @click="loadCommissions">刷新提成</a-button>
          <h3 class="tw-mt-4">我的业绩进度</h3>
          <a-alert v-if="commissionError" type="error" :message="commissionError" show-icon />
          <a-table :data-source="commissionProgress" row-key="id" size="small" class="tw-mt-3">
            <a-table-column title="客户" data-index="customerName" /><a-table-column title="合同" data-index="contractName" />
            <a-table-column title="贡献"><template slot-scope="_, r">{{ r.side === 'development' ? '客户拓展' : '招聘交付' }}</template></a-table-column>
            <a-table-column title="预计提成"><template slot-scope="_, r">{{ money(r.expectedAmount) }}</template></a-table-column>
            <a-table-column title="累计满足条件"><template slot-scope="_, r">{{ money(r.earnedAmount) }}</template></a-table-column>
            <a-table-column title="待核算 / 扣回"><template slot-scope="_, r">{{ money(r.pendingAmount) }}</template></a-table-column>
            <a-table-column title="进度" data-index="state" /><a-table-column title="说明" data-index="reason" />
          </a-table>
          <h3 class="tw-mt-5">已批准提成与实际发放</h3>
          <a-table :data-source="commissions" row-key="id" class="tw-mt-4">
            <a-table-column title="月份" data-index="period" /><a-table-column title="核算批次" data-index="batchId" />
            <a-table-column title="客户" data-index="snapshot.customerName" /><a-table-column title="合同" data-index="snapshot.contractName" /><a-table-column title="职位" data-index="snapshot.jobTitle" /><a-table-column title="候选人" data-index="snapshot.talentName" />
            <a-table-column title="贡献"><template slot-scope="_, r">{{ r.side === 'development' ? '客户拓展' : '招聘交付' }}</template></a-table-column>
            <a-table-column title="本次提成 / 扣回"><template slot-scope="_, r">{{ money(r.amount) }}</template></a-table-column>
            <a-table-column title="工资批次" data-index="payrollId" /><a-table-column title="结清状态" data-index="paymentStatus" />
          </a-table>
        </a-tab-pane></a-tabs
      ></a-card
    ></page-header-wrapper
  >
</template>
<script>
import AttendanceWorkspace from './AttendanceWorkspace.vue'
import { commission } from '/src/api/eos/crm/commission.js'
import { hr } from '/src/api/enterprise/hr.js'
import { exportTemplate } from './templates.js'
import { money } from './hrShared.js'
export default {
  name: 'HrSelf',
  components: { AttendanceWorkspace },
  data: () => ({
    tab: 'attendance',
    rows: [],
    loading: false,
    readingIds: [],
    commissions: [],
    commissionProgress: [],
    commissionError: '',
    fields: [
      { key: 'baseAmount', label: '基本工资' },
      { key: 'allowance', label: '补贴' },
      { key: 'overtime', label: '加班工资' },
      { key: 'bonus', label: '奖金' },
      { key: 'commission', label: '提成' },
      { key: 'adjustment', label: '本期调整' },
      { key: 'grossAmount', label: '应发' },
      { key: 'deduction', label: '扣款' },
      { key: 'tax', label: '个税' },
      { key: 'contribution', label: '个人承担' },
      { key: 'netAmount', label: '实发' },
      { key: 'paidAmount', label: '实际已付' }
    ]
  }),
  mounted() {
    this.tab = ['salary', 'commission'].includes(this.$route.query.tab) ? this.$route.query.tab : 'attendance'
    this.load()
    this.loadCommissions()
  },
  methods: {
    money,
    formatTime(value) { return String(value).replace('T', ' ').slice(0, 19) },
    async loadCommissions() {
      const results = await Promise.allSettled([commission('/self'), commission('/self/overview')])
      if (results[0].status === 'fulfilled') this.commissions = results[0].value.data || []
      this.commissionProgress = results[1].status === 'fulfilled' ? results[1].value.data || [] : []
      this.commissionError = results[1].status === 'rejected' ? results[1].reason?.message || '业绩进度加载失败' : ''
    },
    async download() {
      const rows = (await hr('/self/payslips')).data || []
      exportTemplate(
        'payslip',
        rows.map(r => ({ ...r, remainingAmount: Number(r.netAmount) - Number(r.paidAmount) })),
        '我的工资条'
      )
    },
    async load() {
      const requestId = this._payslipRequest = (this._payslipRequest || 0) + 1
      this.loading = true
      try {
        const { data } = await hr('/self/payslips')
        if (requestId === this._payslipRequest) this.rows = data || []
      } finally { if (requestId === this._payslipRequest) this.loading = false }
    },
    async read(row) {
      if (Number(row.viewed) === 1 || this.readingIds.includes(row.id)) return
      this.readingIds.push(row.id)
      try {
        const { data } = await hr('/self/payslips/' + row.id + '/read', 'post', {})
        this._payslipRequest = (this._payslipRequest || 0) + 1
        this.loading = false
        const current = this.rows.find(item => item.id === row.id)
        if (current) { this.$set(current, 'viewed', data.viewed); this.$set(current, 'viewedAt', data.viewedAt) }
        this.$message.success('已标记查看')
      } finally { this.readingIds = this.readingIds.filter(id => id !== row.id) }
    }
  }
}
</script>
<style scoped>
.payslip-viewed-time { color: #84909e; font-size: 12px; margin-top: 5px; }
.payslip-viewed-label { color: var(--umm-primary, #0eb3a2); white-space: nowrap; }
</style>
