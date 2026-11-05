<template>
  <div>
    <div class="hr-heading">
      <div>
        <h2>{{ selfMode ? '我的考勤' : '员工考勤表' }}</h2>
        <p>钉钉负责打卡与请假审批，本系统保留导入版本，确认后用于工资核算。</p>
      </div>
      <a-space v-if="!selfMode"
        ><hr-template-downloads /><a-dropdown
          ><a-button icon="download">下载考勤模板<a-icon type="down" /></a-button
          ><a-menu slot="overlay" @click="downloadTemplate"
            ><a-menu-item key="monthly">月度汇总模板</a-menu-item><a-menu-item key="daily">每日考勤模板</a-menu-item><a-menu-item key="leave">请假记录模板</a-menu-item></a-menu
          ></a-dropdown
        ><a-button v-if="showModuleAction('attendance', 'import')" type="primary" icon="upload" @click="openImport">导入钉钉数据</a-button></a-space
      >
    </div>
    <div class="hr-toolbar">
      <a-month-picker v-model="period" value-format="YYYY-MM" placeholder="考勤月份" @change="load" /><a-select v-model="kind" style="width: 160px" @change="load"
        ><a-select-option value="monthly">月度汇总</a-select-option><a-select-option value="daily">每日考勤</a-select-option><a-select-option value="leave">请假记录</a-select-option></a-select
      ><a-select v-model="batchId" style="min-width: 280px" placeholder="选择导入批次" @change="loadDetail"
        ><a-select-option v-for="b in batches" :key="b.id" :value="b.id">{{ b.period }} · #{{ b.id }} · {{ statusNames[b.status] }}</a-select-option></a-select
      ><a-button icon="reload" @click="load">刷新</a-button>
    </div>
    <template v-if="detail"
      ><div class="hr-toolbar">
        <a-tag>{{ statusNames[detail.status] }}</a-tag
        ><span class="hr-note">批次 #{{ detail.id }} · 版本 {{ detail.revision }} · {{ detail.rows.length }} 条记录</span
        ><a-button v-if="!selfMode && detail.status === 'draft' && showModuleAction('attendance', 'confirm')" type="primary" @click="confirm">确认本期考勤</a-button
        ><a-button v-if="!selfMode && showModuleAction('attendance', 'export')" icon="export" @click="exportRows">导出考勤报表</a-button
        ><a-button v-if="!selfMode && showModuleAction('attendance', 'export')" @click="downloadSource">下载原始文件</a-button
        ><a-radio-group v-if="kind === 'daily'" v-model="dailyView" button-style="solid"><a-radio-button value="calendar">员工考勤日历</a-radio-button><a-radio-button value="table">明细表</a-radio-button></a-radio-group>
      </div>
      <a-alert v-if="detail.status === 'superseded'" type="warning" show-icon message="这是历史版本，已有新的考勤数据；历史工资保留原始引用，新工资应使用已确认版本。" style="margin-bottom: 16px" />
      <a-table
        v-if="kind !== 'daily' || dailyView === 'table'"
        :loading="loading"
        :columns="columns"
        :data-source="detail.rows"
        :row-key="(_, index) => index"
        :pagination="{ pageSize: 15 }"
        :scroll="{ x: Math.max(1000, columns.length * 125) }"
        size="middle"
      />
      <a-table v-else :columns="calendarColumns" :data-source="calendarRows" row-key="userId" :scroll="{ x: 180 + dayCount * 86 }" :pagination="{ pageSize: 15 }" size="small"
        ><template slot="calendarDay" slot-scope="value"
          ><span :class="['calendar-cell', !value ? 'empty' : hasIssue(value) ? 'warn' : '']" @click="value && showDay(value)"
            ><template v-if="value">{{ value.scheduledHours ? value.actualHours + 'h出勤' : '休息' }}<br />{{ value.result || (hasIssue(value) ? '有异常' : '已记录') }}</template
            ><template v-else>无记录</template></span
          ></template
        ></a-table
      > </template
    ><a-empty v-else description="暂无考勤数据，请选择批次或导入钉钉考勤" />
    <hr-excel-import v-if="importVisible" :visible="importVisible" :type="kind" :extra="{ period, kind }" :preview-api="preview" :commit-api="commit" @close="importVisible = false" @done="load" />
    <a-modal :visible="!!day" title="当日考勤详情" :footer="null" @cancel="day = null"
      ><a-descriptions v-if="day" bordered :column="1"
        ><a-descriptions-item v-for="field in dayFields" :key="field.key" :label="field.label">{{ day[field.key] === undefined ? '—' : day[field.key] }}</a-descriptions-item></a-descriptions
      ></a-modal
    >
  </div>
</template>
<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { hr } from '/src/api/enterprise/hr.js'
import HrTemplateDownloads from '/src/components/HrTemplateDownloads/index.vue'
import HrExcelImport from '/src/components/HrExcelImport/index.vue'
import { templates, exportTemplate } from './templates.js'
import { statusNames } from './hrShared.js'
import { saveAs } from 'file-saver'
export default {
  name: 'AttendanceWorkspace',
  components: { HrExcelImport, HrTemplateDownloads },
  props: { selfMode: Boolean },
  mixins: [withModuleContexts({ attendance: { component: 'enterprise/hr/attendance', label: '考勤管理' } })],
  data: () => ({ period: new Date().toLocaleDateString('sv-SE').slice(0, 7), kind: 'monthly', batches: [], batchId: undefined, detail: null, loading: false, importVisible: false, dailyView: 'calendar', day: null, statusNames }),
  computed: {
    columns() {
      return [
        { title: '员工编号', dataIndex: 'employeeNo', width: 130, fixed: 'left' },
        { title: '姓名', dataIndex: 'employeeName', width: 100, fixed: 'left' },
        { title: '部门', dataIndex: 'deptName', width: 140 },
        ...templates[this.kind].fields.filter(f => !['employeeNo', 'employeeName', 'dingtalkUserId', 'period'].includes(f.key)).map(f => ({ title: f.label, dataIndex: f.key, width: 130 }))
      ]
    },
    dayFields() {
      return templates.daily.fields
    },
    dayCount() {
      const [year, month] = this.period.split('-').map(Number)
      return new Date(year, month, 0).getDate()
    },
    calendarColumns() {
      return [
        { title: '员工', dataIndex: 'employeeName', width: 180, fixed: 'left' },
        ...Array.from({ length: this.dayCount }, (_, i) => ({ title: String(i + 1) + '日', dataIndex: 'day' + (i + 1), width: 86, scopedSlots: { customRender: 'calendarDay' } }))
      ]
    },
    calendarRows() {
      const rows = new Map()
      for (const r of this.detail?.rows || []) {
        if (!rows.has(r.userId)) rows.set(r.userId, { userId: r.userId, employeeName: r.employeeName + ' · ' + r.employeeNo })
        rows.get(r.userId)['day' + Number(r.workDate.slice(8, 10))] = r
      }
      return [...rows.values()]
    }
  },
  mounted() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const response = await hr(this.selfMode ? '/self/attendance' : '/attendance', 'get', { period: this.period, kind: this.kind })
        this.batches = (response.data || []).filter(b => b.period === this.period && b.kind === this.kind)
        this.batchId = this.batches.find(b => b.status === 'confirmed')?.id || this.batches[0]?.id
        if (this.batchId) await this.loadDetail()
        else this.detail = null
      } finally {
        this.loading = false
      }
    },
    async loadDetail() {
      if (!this.batchId) return
      this.detail = (await hr((this.selfMode ? '/self' : '') + '/attendance/' + this.batchId)).data
    },
    async openImport() {
      if (!this.period) return this.$message.warning('请选择月份')
      if (!(await this.checkModuleAction({ module: 'attendance', action: 'import', label: '导入钉钉考勤' }))) return
      this.importVisible = true
    },
    preview(payload) {
      return hr('/attendance/preview', 'post', payload)
    },
    commit(payload) {
      return hr('/attendance', 'post', payload)
    },
    confirm() {
      this.$confirm({
        title: '确认此批考勤用于制薪？',
        content: '同范围旧版本将保留为历史版本，已生成工资会提示考勤变化。',
        onOk: async () => {
          await hr('/attendance/' + this.batchId + '/confirm', 'post', { revision: this.detail.revision })
          this.$message.success('考勤已确认')
          await this.load()
        }
      })
    },
    downloadTemplate({ key }) {
      exportTemplate(key)
    },
    async exportRows() {
      if (!(await this.checkModuleAction({ module: 'attendance', action: 'export', label: '导出考勤' }))) return
      const report = (await hr('/reports/attendance/' + this.batchId)).data
      exportTemplate(this.kind, report.rows, '员工考勤表-' + this.period + '-' + statusNames[this.kind])
    },
    async downloadSource() {
      const file = (await hr('/files/' + this.detail.fileId)).data
      saveAs(new Blob([Uint8Array.from(atob(file.fileContent), c => c.charCodeAt(0))]), file.fileName)
    },
    hasIssue(r) {
      return ['personalLeaveHours', 'sickLeaveHours', 'absentHours', 'lateCount', 'earlyCount', 'missingCount'].some(k => Number(r[k]) > 0)
    },
    showDay(r) {
      this.day = r
    }
  }
}
</script>
<style scoped src="./hr.css"></style>
