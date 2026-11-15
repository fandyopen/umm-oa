<template>
  <div>
    <div class="hr-heading">
      <div>
        <h2>{{ selfMode ? '我的考勤' : '员工考勤表' }}</h2>
        <p>可生成正常考勤、手工调整或导入覆盖，核对并确认后用于工资核算。</p>
      </div>
      <a-space v-if="!selfMode"
        ><a-button v-if="kind !== 'leave' && showModuleAction('attendance', 'generate')" type="primary" icon="calendar" @click="openGenerate">生成正常考勤</a-button><hr-template-downloads /><a-dropdown
          ><a-button icon="download">下载考勤模板<a-icon type="down" /></a-button
          ><a-menu slot="overlay" @click="downloadTemplate"
            ><a-menu-item key="monthly">月度汇总模板</a-menu-item><a-menu-item key="daily">每日考勤模板</a-menu-item><a-menu-item key="leave">请假记录模板</a-menu-item></a-menu
          ></a-dropdown
        ><a-button v-if="showModuleAction('attendance', 'import')" icon="upload" @click="openImport">导入 / 覆盖考勤</a-button></a-space
      >
    </div>
    <div class="hr-toolbar">
      <a-month-picker v-model="period" format="YYYY-MM" value-format="YYYY-MM" :allow-clear="false" placeholder="考勤月份" @change="load" /><a-select v-model="kind" class="tw-w-40" @change="load"
        ><a-select-option value="monthly">月度汇总</a-select-option><a-select-option value="daily">每日考勤</a-select-option><a-select-option value="leave">请假记录</a-select-option></a-select
      ><a-select v-model="batchId" class="tw-min-w-[280px]" placeholder="选择考勤批次" @change="loadDetail"
        ><a-select-option v-for="b in batches" :key="b.id" :value="b.id">{{ b.period }} · #{{ b.id }} · {{ statusNames[b.status] }}</a-select-option></a-select
      ><a-button icon="reload" @click="load">刷新</a-button>
    </div>
    <template v-if="detail"
      ><div class="hr-toolbar">
        <a-tag>{{ statusNames[detail.status] }}</a-tag
        ><span class="hr-note">批次 #{{ detail.id }} · 版本 {{ detail.revision }} · {{ detail.rows.length }} 条记录</span
        ><a-button v-if="!selfMode && detail.status === 'draft' && showModuleAction('attendance', 'confirm')" type="primary" @click="confirm">确认本期考勤</a-button
        ><a-button v-if="!selfMode && showModuleAction('attendance', 'export')" icon="export" @click="exportRows">导出考勤报表</a-button
        ><a-button v-if="!selfMode && showModuleAction('attendance', 'export')" @click="downloadSource">下载来源文件</a-button
        ><a-radio-group v-if="kind === 'daily'" v-model="dailyView" button-style="solid"><a-radio-button value="calendar">员工考勤日历</a-radio-button><a-radio-button value="table">明细表</a-radio-button></a-radio-group>
      </div>
      <a-alert v-if="!selfMode && detail.status === 'draft'" type="info" show-icon message="当前考勤尚未确认，暂不能用于工资核算。核对数据后点击“确认本期考勤”。" class="tw-mb-4" />
      <a-alert v-if="!selfMode && detail.status === 'confirmed'" type="success" show-icon class="tw-mb-4">
        <template slot="message">本期考勤已确认，可以进入工资管理生成工资草稿。</template>
        <template slot="description"><a-button type="link" class="tw-p-0" @click="goPayroll">前往工资管理<a-icon type="right" /></a-button></template>
      </a-alert>
      <a-alert v-if="detail.status === 'superseded'" type="warning" show-icon message="这是历史版本，已有新的考勤数据；历史工资保留原始引用，新工资应使用已确认版本。" class="tw-mb-4" />
      <a-table
        v-if="kind !== 'daily' || dailyView === 'table'"
        :loading="loading"
        :columns="columns"
        :data-source="detail.rows"
        :row-key="(_, index) => index"
        :pagination="{ pageSize: 15 }"
        size="middle"
        ><template slot="editAttendance" slot-scope="text, record"><a-button type="link" @click="openEdit(record)">修改</a-button></template></a-table
      >
      <a-table v-else :columns="calendarColumns" :data-source="calendarRows" row-key="userId" :scroll="{ x: 180 + dayCount * 86 + (dayCount + 1) * 32 }" :pagination="{ pageSize: 15 }" size="small"
        ><template slot="calendarDay" slot-scope="value"
          ><span :class="['calendar-cell', !value ? 'empty' : hasIssue(value) ? 'warn' : '']" @click="value && showDay(value)"
            ><template v-if="value">{{ value.scheduledHours ? value.actualHours + 'h出勤' : '休息' }}<br />{{ value.result || (hasIssue(value) ? '有异常' : '已记录') }}</template
            ><template v-else>无记录</template></span
          ></template
        ></a-table
      > </template
    ><a-empty v-else :description="selfMode ? '暂无已确认的考勤数据' : '暂无考勤数据，可生成正常考勤或导入考勤文件'" />
    <hr-excel-import
      v-if="importVisible"
      :visible="importVisible"
      :type="importContext.kind"
      :extra="importContext"
      :notice="importNotice"
      :preview-api="preview"
      :commit-api="commit"
      @close="importVisible = false"
      @done="reloadImported"
    />
    <a-modal :visible="generateVisible" title="生成正常考勤" :confirm-loading="saving" @ok="generate" @cancel="generateVisible = false">
      <p>{{ generateForm.period }} · {{ statusNames[generateForm.kind] }}，根据当前有权限查看的在职成员生成。</p>
      <a-form-model layout="vertical">
        <a-form-model-item label="每周工作日"><a-checkbox-group v-model="generateForm.workdays" :options="weekdayOptions" /></a-form-model-item>
        <a-form-model-item label="每日标准工时"><a-input-number v-model="generateForm.hoursPerDay" :min="0.5" :max="24" :step="0.5" /></a-form-model-item>
      </a-form-model>
      <a-alert type="info" show-icon message="默认周一至周五正常出勤，异常和加班为0；保留全月排班，实际出勤按任职期间计算。法定节假日、调休和实际异常请生成后调整。已有考勤会保留，生成结果需核对并确认。" />
    </a-modal>
    <a-modal :visible="editVisible" title="修改考勤" :confirm-loading="saving" @ok="saveEdit" @cancel="editVisible = false">
      <p>{{ editForm.employeeNo }} · {{ editForm.employeeName }} · {{ editForm.workDate || editForm.period }}</p>
      <a-alert v-if="editConfirmed" type="info" show-icon message="已确认考勤修改后生成新草稿，重新确认后生效；已有工资保留原始记录并提示重新核算。" class="tw-mb-4" />
      <a-form-model layout="vertical" class="hr-form-grid">
        <a-form-model-item v-for="field in editFields" :key="field.key" :label="field.label">
          <a-input-number v-if="field.type === 'number'" v-model="editForm[field.key]" :min="0" :precision="['lateCount', 'earlyCount', 'missingCount'].includes(field.key) ? 0 : 2" class="tw-w-full" />
          <a-date-picker v-else-if="field.type === 'date'" v-model="editForm[field.key]" value-format="YYYY-MM-DD" class="tw-w-full" />
          <a-input v-else v-model="editForm[field.key]" :max-length="200" />
        </a-form-model-item>
      </a-form-model>
    </a-modal>
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
import { getTemplateSchema, loadTemplateCatalog, exportTemplate } from './templates.js'
import { statusNames } from './hrShared.js'
import { saveAs } from 'file-saver'
export default {
  name: 'AttendanceWorkspace',
  components: { HrExcelImport, HrTemplateDownloads },
  props: { selfMode: Boolean },
  mixins: [withModuleContexts({ attendance: { component: 'enterprise/hr/attendance', label: '考勤管理' } })],
  data: () => ({
    period: new Date().toLocaleDateString('sv-SE').slice(0, 7),
    kind: 'monthly',
    batches: [],
    batchId: undefined,
    detail: null,
    loading: false,
    importVisible: false,
    importContext: {},
    importedBatchId: null,
    dailyView: 'calendar',
    day: null,
    statusNames,
    generateVisible: false,
    generateForm: { period: '', kind: 'monthly', workdays: [1, 2, 3, 4, 5], hoursPerDay: 8 },
    weekdayOptions: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'].map((label, i) => ({ label, value: i + 1 })),
    editVisible: false,
    editForm: {},
    editKind: 'monthly',
    editBatchId: null,
    editRevision: null,
    editConfirmed: false,
    saving: false
  }),
  computed: {
    canEdit() {
      return !this.selfMode && this.detail && ['draft', 'confirmed'].includes(this.detail.status) && this.showModuleAction('attendance', 'edit')
    },
    importNotice() {
      return this.importContext.baseBatchId ? '将按员工编号及日期覆盖所选批次的匹配记录，保留其他记录。已确认批次生成新草稿，重新确认后用于工资核算。' : '导入后生成考勤草稿；核对并确认后用于工资核算。'
    },
    editFields() {
      return getTemplateSchema(this.editKind).fields.filter(f => !['employeeNo', 'employeeName', 'dingtalkUserId', 'period', 'workDate', 'leaveId'].includes(f.key))
    },
    columns() {
      return [
        { title: '员工编号', dataIndex: 'employeeNo', width: 130, fixed: 'left' },
        { title: '姓名', dataIndex: 'employeeName', width: 100, fixed: 'left' },
        { title: '部门', dataIndex: 'deptName', width: 140 },
        ...getTemplateSchema(this.kind)
          .fields.filter(f => !['employeeNo', 'employeeName', 'dingtalkUserId', 'period'].includes(f.key))
          .map(f => ({ title: f.label, dataIndex: f.key, width: 130 })),
        ...(this.canEdit ? [{ title: '操作', key: 'editAttendance', width: 90, fixed: 'right', scopedSlots: { customRender: 'editAttendance' } }] : [])
      ]
    },
    dayFields() {
      return getTemplateSchema('daily').fields
    },
    dayCount() {
      if (!this.period) return 0
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
    loadTemplateCatalog().catch(error => this.$message.error(error.message || '模板字段加载失败'))
    this.load()
  },
  methods: {
    async load(preferredId) {
      if (!this.period) {
        this.batches = []
        this.batchId = undefined
        this.detail = null
        return
      }
      this.loading = true
      try {
        const response = await hr(this.selfMode ? '/self/attendance' : '/attendance', 'get', { period: this.period, kind: this.kind })
        this.batches = (response.data || []).filter(b => b.period === this.period && b.kind === this.kind)
        this.batchId = this.batches.find(b => b.id === preferredId)?.id || this.batches.find(b => b.status === 'draft')?.id || this.batches.find(b => b.status === 'confirmed')?.id || this.batches[0]?.id
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
      if (!(await this.checkModuleAction({ module: 'attendance', action: 'import', label: '导入考勤' }))) return
      this.importContext = { period: this.period, kind: this.kind }
      if (this.detail && ['draft', 'confirmed'].includes(this.detail.status)) {
        this.importContext.baseBatchId = this.detail.id
        this.importContext.revision = this.detail.revision
      }
      this.importVisible = true
    },
    preview(payload) {
      return hr('/attendance/preview', 'post', payload)
    },
    async commit(payload) {
      const response = await hr('/attendance', 'post', payload)
      this.importedBatchId = response.data.id
      return response
    },
    async reloadImported() {
      this.period = this.importContext.period
      this.kind = this.importContext.kind
      await this.load(this.importedBatchId)
    },
    async openGenerate() {
      if (!this.period) return this.$message.warning('请选择月份')
      if (!(await this.checkModuleAction({ module: 'attendance', action: 'generate', label: '生成正常考勤' }))) return
      this.generateForm = { period: this.period, kind: this.kind, workdays: [1, 2, 3, 4, 5], hoursPerDay: 8 }
      this.generateVisible = true
    },
    async generate() {
      if (this.saving) return
      if (!this.generateForm.workdays.length || !this.generateForm.hoursPerDay) return this.$message.warning('请选择工作日并填写每日工时')
      this.saving = true
      try {
        const response = await hr('/attendance/generate', 'post', this.generateForm)
        this.period = response.data.period
        this.kind = response.data.kind
        this.generateVisible = false
        await this.load(response.data.id)
        this.$message.success('正常考勤已生成，请核对后确认')
      } finally {
        this.saving = false
      }
    },
    async openEdit(row) {
      if (!(await this.checkModuleAction({ module: 'attendance', action: 'edit', label: '修改考勤' }))) return
      this.editForm = { ...row }
      this.editKind = this.detail.kind
      this.editBatchId = this.detail.id
      this.editRevision = this.detail.revision
      this.editConfirmed = this.detail.status === 'confirmed'
      this.editVisible = true
    },
    async saveEdit() {
      if (this.saving) return
      this.saving = true
      try {
        const response = await hr('/attendance/' + this.editBatchId, 'put', { revision: this.editRevision, rows: [this.editForm] })
        this.period = response.data.period
        this.kind = response.data.kind
        this.editVisible = false
        await this.load(response.data.id)
        this.$message.success('考勤修改已保存，请核对后确认')
      } finally {
        this.saving = false
      }
    },
    confirm() {
      const batchId = this.batchId
      const revision = this.detail.revision
      this.$antdconfirm({
        title: '确认此批考勤用于制薪？',
        content: '同范围旧版本将保留为历史版本，已生成工资会提示考勤变化。',
        onOk: async () => {
          await hr('/attendance/' + batchId + '/confirm', 'post', { revision })
          await this.load(batchId)
          this.$message.success('考勤已确认')
        }
      })
    },
    goPayroll() {
      this.$router.push({ path: '/hr/payroll', query: { period: this.detail.period } })
    },
    async downloadTemplate({ key }) {
      try {
        await exportTemplate(key)
      } catch (error) {
        this.$message.error(error.message || '模板下载失败')
      }
    },
    async exportRows() {
      if (!(await this.checkModuleAction({ module: 'attendance', action: 'export', label: '导出考勤' }))) return
      const report = (await hr('/reports/attendance/' + this.batchId)).data
      await exportTemplate(this.kind, report.rows, '员工考勤表-' + this.period + '-' + statusNames[this.kind])
    },
    async downloadSource() {
      const file = (await hr('/files/' + this.detail.fileId)).data
      saveAs(new Blob([Uint8Array.from(atob(file.fileContent), c => c.charCodeAt(0))]), file.fileName)
    },
    hasIssue(r) {
      return ['personalLeaveHours', 'sickLeaveHours', 'absentHours', 'lateCount', 'earlyCount', 'missingCount'].some(k => Number(r[k]) > 0)
    },
    showDay(r) {
      if (this.canEdit) return this.openEdit(r)
      this.day = r
    }
  }
}
</script>
<style src="./hr.css"></style>
