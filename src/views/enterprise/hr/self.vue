<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb"
    ><a-card :bordered="false"
      ><a-tabs v-model="tab"
        ><a-tab-pane key="attendance" tab="我的考勤"><attendance-workspace self-mode /></a-tab-pane
        ><a-tab-pane key="salary" tab="我的工资条"
          ><a-alert type="info" show-icon message="工资条发布与实际付款分别记录；已查看工资条不代表已到账。" class="tw-mb-4" /><a-button icon="download" @click="download">下载我的工资条</a-button
          ><a-button icon="reload" @click="load">刷新</a-button
          ><a-table :data-source="rows" row-key="id" :scroll="{ x: 1300 }" class="tw-mt-4"
            ><a-table-column title="所属月份" data-index="period" /><a-table-column v-for="field in fields" :key="field.key" :title="field.label" :data-index="field.key"
              ><template slot-scope="v">{{ money(v) }}</template></a-table-column
            ><a-table-column title="操作"
              ><template slot-scope="_, r"><a-button type="link" @click="read(r)">标记已查看</a-button></template></a-table-column
            ></a-table
          ></a-tab-pane
        ><a-tab-pane key="commission" tab="我的提成">
          <a-alert type="info" show-icon message="仅展示本人已批准的提成及扣回。纳入工资后还需核税和实际发放，工资批次未结清时会显示待结清。" class="tw-mb-4" />
          <a-button icon="reload" @click="loadCommissions">刷新提成</a-button>
          <a-table :data-source="commissions" row-key="id" class="tw-mt-4" :scroll="{ x: 1000 }">
            <a-table-column title="月份" data-index="period" /><a-table-column title="核算批次" data-index="batchId" />
            <a-table-column title="客户" data-index="snapshot.customerName" /><a-table-column title="职位" data-index="snapshot.jobTitle" /><a-table-column title="候选人" data-index="snapshot.talentName" />
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
    commissions: [],
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
    async loadCommissions() { this.commissions = (await commission('/self')).data || [] },
    async download() {
      const rows = (await hr('/self/payslips')).data || []
      exportTemplate(
        'payslip',
        rows.map(r => ({ ...r, remainingAmount: Number(r.netAmount) - Number(r.paidAmount) })),
        '我的工资条'
      )
    },
    async load() {
      this.rows = (await hr('/self/payslips')).data || []
    },
    async read(row) {
      await hr('/self/payslips/' + row.id + '/read', 'post', {})
      this.$message.success('已标记查看')
    }
  }
}
</script>
