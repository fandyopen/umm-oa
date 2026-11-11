<template>
  <div>
    <div class="hr-heading">
      <div>
        <h2>{{ financeMode ? '工资发放与对账' : '工资核算与审批' }}</h2>
        <p>{{ financeMode ? '仅展示已批准工资，逐人登记实际付款、失败重试与差额扣回。' : '自动引用员工档案中的生效薪资与已确认考勤，核对调整后提交审批。' }}</p>
      </div>
      <a-space
        ><hr-template-downloads /><a-button v-if="!financeMode && allowed('generate')" type="primary" @click="openGenerate('generate')">生成工资草稿</a-button
        ><a-button v-if="!financeMode && allowed('generate')" @click="openGenerate('supplement')">补发 / 扣回</a-button
        ><a-button icon="download" @click="downloadBlank">下载{{ financeMode ? '发放回执' : '工资调整' }}模板</a-button></a-space
      >
    </div>
    <div class="hr-toolbar">
      <a-month-picker v-model="period" format="YYYY-MM" value-format="YYYY-MM" placeholder="工资月份（留空查全部）" @change="load" /><a-button icon="reload" @click="load">刷新</a-button
      ><a-button v-if="allowed('export')" icon="export" @click="exportSummary">导出月度工资汇总</a-button>
    </div>
    <a-table :loading="loading" :data-source="batches" row-key="id" :pagination="{ pageSize: 10 }"
      ><a-table-column title="批次" data-index="id" /><a-table-column title="工资月份" data-index="period" /><a-table-column title="类型" data-index="batchType"
        ><template slot-scope="v">{{ statusNames[v] }}</template></a-table-column
      ><a-table-column title="人数" data-index="employeeCount" /><a-table-column title="应付实发" data-index="netAmount"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      ><a-table-column title="实际已付" data-index="paidAmount"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      ><a-table-column title="审批状态" data-index="status"
        ><template slot-scope="v"
          ><a-tag>{{ statusNames[v] }}</a-tag></template
        ></a-table-column
      ><a-table-column title="操作"
        ><template slot-scope="_, r"
          ><a-button type="link" @click="open(r.id)">{{ financeMode ? '办理发放' : '查看工资表' }}</a-button></template
        ></a-table-column
      ></a-table
    >
    <template v-if="detail"
      ><a-divider />
      <div class="hr-heading">
        <div>
          <h3>{{ detail.period }} 工资表 · #{{ detail.id }} · 版本 {{ detail.revision }}</h3>
          <span class="hr-note">{{ statusNames[detail.status] }} · {{ detail.published ? '工资条已发布' : '工资条未发布' }}</span>
        </div>
        <a-space
          ><a-button v-if="allowed('export')" icon="export" @click="exportRows">{{ financeMode ? '导出发放对账' : '导出工资表（可回传）' }}</a-button
          ><a-button v-if="!financeMode && editable && allowed('edit')" @click="startImport">上传调整工资表</a-button
          ><a-button v-if="!financeMode && editable && allowed('generate') && detail.batchType === 'regular'" @click="openGenerate('recalculate')">重新核算</a-button></a-space
        >
      </div>
      <a-alert
        v-if="detail.attendanceChanged || detail.sourcesChanged"
        type="warning"
        show-icon
        message="引用的薪资标准、任职资料或考勤已有更新版本。草稿需重新核算，已批准或已发工资通过更正、差额批次处理，历史金额不会自动变化。"
        class="tw-mb-4"
      />
      <div class="hr-metrics">
        <div>
          <span>本期应发</span><strong>{{ money(gross) }}</strong>
        </div>
        <div>
          <span>本期实发</span><strong>{{ money(detail.netAmount) }}</strong>
        </div>
        <div>
          <span>实际已付</span><strong>{{ money(detail.paidAmount) }}</strong>
        </div>
        <div>
          <span>剩余应付 / 扣回</span><strong>{{ money(Number(detail.netAmount) - Number(detail.paidAmount)) }}</strong>
        </div>
      </div>
      <div v-if="!financeMode" class="hr-toolbar">
        <a-button v-if="editable && allowed('edit')" @click="refreshAccounts">更新发薪账户快照</a-button><a-button v-if="editable && allowed('edit')" @click="openReason('verify')">确认个税与本期项目已核对</a-button
        ><a-button v-if="editable && allowed('submit')" type="primary" @click="submit">提交工资审批</a-button><a-button v-if="detail.status === 'approved' && allowed('publish')" @click="action('publish')">发布工资条</a-button
        ><a-button v-if="detail.status === 'approved' && allowed('edit')" @click="openReason('reopen')">撤销批准并更正</a-button><span class="hr-note">个税及个人承担项目不自动推算，需核对后才可提交。</span>
      </div>
      <a-table :data-source="items" row-key="id" :pagination="{ pageSize: 15 }" :scroll="{ x: 2100 }" size="small"
        ><a-table-column title="员工" fixed="left" :width="150"
          ><template slot-scope="_, r"
            ><a @click="showItem(r)">{{ r.employeeName }}</a>
            <div class="hr-note">{{ r.employeeNo }}</div></template
          ></a-table-column
        ><a-table-column title="部门" data-index="deptName" :width="140" /><a-table-column v-for="f in amountFields" :key="f.key" :title="f.label" :data-index="f.key" :width="115"
          ><template slot-scope="v">{{ money(v) }}</template></a-table-column
        ><a-table-column title="核对" :width="90"
          ><template slot-scope="_, r">{{ r.verified ? '已核对' : '待核对' }}</template></a-table-column
        ><a-table-column title="操作" fixed="right" :width="150"
          ><template slot-scope="_, r"
            ><a-button v-if="detail.status === 'approved' && allowed('pay') && Number(r.netAmount) !== Number(r.paidAmount)" type="link" @click="openPay(r)">登记发放</a-button
            ><a-button type="link" @click="showItem(r)">明细</a-button></template
          ></a-table-column
        ></a-table
      >
      <h3 class="hr-section-title">操作与审批历史</h3>
      <a-timeline
        ><a-timeline-item v-for="(h, index) in detail.history" :key="index">{{ h.action }} · 操作人 {{ h.operatorId }} · {{ h.createTime }}</a-timeline-item></a-timeline
      >
    </template>
    <a-modal v-model="generateVisible" :title="generateMode === 'supplement' ? '创建补发或扣回批次' : '按考勤核算工资'" :confirm-loading="busy" width="700px" @ok="generate"
      ><a-alert type="info" show-icon message="计薪参数由公司确认填写，不预设法定除数或请假比例。月薪按本月应出勤折算，固定补贴同比例折算；月中调薪、入离职需要每日考勤。" class="tw-mb-4" /><a-form-model layout="vertical"
        ><a-form-model-item label="工资月份"><a-month-picker v-model="generateForm.period" :allow-clear="false" format="YYYY-MM" value-format="YYYY-MM" @change="loadAttendance" class="tw-w-full" /></a-form-model-item
        ><a-form-model-item v-if="generateMode !== 'supplement'" label="已确认月度考勤"
          ><a-select v-model="generateForm.attendanceId"
            ><a-select-option v-for="a in attendanceBatches" :key="a.id" :value="a.id">{{ a.period }} · 批次 #{{ a.id }}</a-select-option></a-select
          ></a-form-model-item
        >
        <div v-if="generateMode !== 'supplement'" class="hr-form-grid">
          <a-form-model-item v-for="f in ruleFields" :key="f.key" :label="f.label"><a-input-number v-model="generateForm.rules[f.key]" :min="0" :max="f.rate ? 1 : undefined" :precision="4" class="tw-w-full" /></a-form-model-item>
        </div>
        <a-form-model-item v-if="generateMode !== 'generate'" label="补发、扣回或重算原因"><a-textarea v-model="generateForm.reason" :max-length="1000" /></a-form-model-item
        ><a-alert v-if="generateMode === 'recalculate'" type="warning" message="重算将生成新的核算快照，当前人工调整会清除并留存历史。请先导出保存需要保留的调整。" /></a-form-model
    ></a-modal>
    <a-modal v-model="reasonVisible" title="核对或更正说明" :confirm-loading="busy" @ok="saveReason"><a-textarea v-model="reason" :rows="4" :max-length="1000" placeholder="请填写核对依据或更正原因" /></a-modal>
    <a-modal v-model="payVisible" title="登记实际工资付款 / 扣回" :confirm-loading="busy" @ok="savePay"
      ><a-alert type="info" show-icon message="此操作登记线下真实结果，不发起银行转账。成功需上传凭证，失败需填写原因；重试不会重复累计成功金额。" class="tw-mb-4" /><a-form-model layout="vertical"
        ><a-form-model-item label="员工与收款账户"
          ><span>{{ payItem.employeeName }} · {{ payItem.bankName }} · {{ payItem.bankAccount }}</span></a-form-model-item
        ><a-form-model-item label="本次金额（扣回填负数）"><a-input-number v-model="payForm.amount" :precision="2" class="tw-w-full" /></a-form-model-item
        ><a-form-model-item label="实际日期"><a-date-picker v-model="payForm.paymentDate" format="YYYY-MM-DD" value-format="YYYY-MM-DD" class="tw-w-full" /></a-form-model-item
        ><a-form-model-item label="结果"
          ><a-select v-model="payForm.status"><a-select-option value="paid">成功</a-select-option><a-select-option value="failed">失败</a-select-option></a-select></a-form-model-item
        ><a-form-model-item label="银行流水号 / 失败单号"><a-input v-model="payForm.referenceNo" :max-length="128" /></a-form-model-item
        ><a-form-model-item label="备注 / 失败原因"><a-textarea v-model="payForm.reason" :max-length="1000" /></a-form-model-item
        ><a-upload :before-upload="paymentFile" :show-upload-list="false"><a-button icon="upload">上传私有凭证</a-button></a-upload
        ><span class="hr-note">{{ payForm.fileName }}</span></a-form-model
      ></a-modal
    >
    <a-drawer :visible="!!selectedItem" title="工资核算与发放明细" width="min(1000px,94vw)" @close="selectedItem = null"
      ><template v-if="selectedItem"
        ><h3>{{ selectedItem.employeeName }} · {{ selectedItem.employeeNo }}</h3>
        <a-descriptions bordered :column="2"
          ><a-descriptions-item v-for="f in amountFields" :key="f.key" :label="f.label">{{ money(selectedItem[f.key]) }}</a-descriptions-item
          ><a-descriptions-item label="开户人">{{ selectedItem.accountName }}</a-descriptions-item
          ><a-descriptions-item label="银行">{{ selectedItem.bankName }}</a-descriptions-item
          ><a-descriptions-item label="银行卡号">{{ selectedItem.bankAccount }}</a-descriptions-item
          ><a-descriptions-item label="核对说明">{{ selectedItem.reason || '—' }}</a-descriptions-item></a-descriptions
        >
        <h3 class="hr-section-title">计薪分段快照</h3>
        <a-table :columns="segmentColumns" :data-source="selectedItem.snapshot.segments || []" :row-key="(_, index) => index" :pagination="false" size="small" />
        <h3 class="hr-section-title">付款与失败记录</h3>
        <a-table :data-source="selectedItem.payments" row-key="id" :pagination="false" size="small"
          ><a-table-column title="日期" data-index="paymentDate" /><a-table-column title="金额" data-index="amount" /><a-table-column title="结果" data-index="status"
            ><template slot-scope="v">{{ statusNames[v] }}</template></a-table-column
          ><a-table-column title="流水号" data-index="referenceNo" /><a-table-column title="凭证"
            ><template slot-scope="_, r"><a v-if="r.attachmentId" @click="downloadFile(r.attachmentId)">下载</a></template></a-table-column
          ></a-table
        ></template
      ></a-drawer
    >
    <hr-excel-import
      v-if="importType"
      :visible="!!importType"
      :type="importType"
      :extra="importExtra"
      :template-rows="exportRecords"
      :preview-api="importType === 'payroll' ? previewImport : undefined"
      :commit-api="commitImport"
      @close="importType = ''"
      @done="importDone"
    />
  </div>
</template>
<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { hr } from '/src/api/enterprise/hr.js'
import HrTemplateDownloads from '/src/components/HrTemplateDownloads/index.vue'
import { payrollReportEntries, payrollSummary } from './reports.js'
import HrExcelImport from '/src/components/HrExcelImport/index.vue'
import { exportTemplate, exportWorkbook } from './templates.js'
import { money, statusNames, newToken, fileBody } from './hrShared.js'
import { saveAs } from 'file-saver'
export default {
  name: 'PayrollWorkspace',
  components: { HrExcelImport, HrTemplateDownloads },
  props: { financeMode: Boolean },
  mixins: [withModuleContexts({ payroll: { component: 'enterprise/hr/payroll', label: '工资管理' }, finance: { component: 'eos/crm/finance/payroll', label: '工资发放' } })],
  data: () => ({
    period: new Date().toLocaleDateString('sv-SE').slice(0, 7),
    batches: [],
    detail: null,
    loading: false,
    busy: false,
    generateVisible: false,
    generateMode: 'generate',
    generateForm: { rules: {} },
    attendanceBatches: [],
    reasonVisible: false,
    reason: '',
    reasonAction: '',
    payVisible: false,
    payForm: {},
    payItem: {},
    selectedItem: null,
    importType: '',
    importExtra: {},
    statusNames,
    ruleFields: [
      { key: 'hoursPerDay', label: '每日计薪工时' },
      { key: 'personalPaidRate', label: '事假计薪比例（0至1）', rate: true },
      { key: 'sickPaidRate', label: '病假计薪比例（0至1）', rate: true },
      { key: 'absencePaidRate', label: '缺勤计薪比例（0至1）', rate: true },
      { key: 'overtimeMultiplier', label: '加班计薪倍率' },
      { key: 'latePenalty', label: '每次迟到扣款（元）' },
      { key: 'earlyPenalty', label: '每次早退扣款（元）' }
    ],
    amountFields: [
      { key: 'baseAmount', label: '基本工资' },
      { key: 'allowance', label: '固定补贴' },
      { key: 'overtime', label: '加班工资' },
      { key: 'bonus', label: '奖金' },
      { key: 'commission', label: '提成' },
      { key: 'adjustment', label: '本期调整' },
      { key: 'grossAmount', label: '应发' },
      { key: 'deduction', label: '其他扣款' },
      { key: 'tax', label: '个税' },
      { key: 'contribution', label: '个人承担' },
      { key: 'netAmount', label: '实发' },
      { key: 'paidAmount', label: '实际已付' }
    ],
    segmentColumns: [
      { title: '日期', dataIndex: 'date' },
      { title: '开始', dataIndex: 'from' },
      { title: '结束', dataIndex: 'to' },
      { title: '薪资版本', dataIndex: 'salaryId' },
      { title: '计薪小时', dataIndex: 'paidHours' },
      { title: '基本工资', dataIndex: 'baseAmount' }
    ]
  }),
  computed: {
    contextKey() {
      return this.financeMode ? 'finance' : 'payroll'
    },
    editable() {
      return ['draft', 'rejected'].includes(this.detail?.status)
    },
    items() {
      return (this.detail?.rows || []).map(row => ({ ...row, ...row.snapshot, employeeName: row.snapshot.nickName, remainingAmount: Number(row.netAmount) - Number(row.paidAmount) }))
    },
    gross() {
      return this.items.reduce((sum, row) => sum + Number(row.grossAmount), 0)
    },
    exportRecords() {
      return this.items.map(row => ({ ...row, batchId: this.detail.id, period: this.detail.period, revision: this.detail.revision }))
    }
  },
  mounted() {
    this.load()
    if (this.$route.query.payrollId) this.open(Number(this.$route.query.payrollId))
  },
  methods: {
    money,
    allowed(action) {
      return this.showModuleAction(this.contextKey, action)
    },
    async guard(action, label) {
      return this.checkModuleAction({ module: this.contextKey, action, label })
    },
    async load() {
      this.loading = true
      try {
        this.batches = (await hr(this.financeMode ? '/finance/payroll' : '/payroll', 'get', { period: this.period })).data || []
      } finally {
        this.loading = false
      }
    },
    async open(id) {
      this.detail = (await hr((this.financeMode ? '/finance' : '') + '/payroll/' + id)).data
    },
    async openGenerate(mode) {
      if (!(await this.guard('generate', '核算工资'))) return
      this.generateMode = mode
      this.generateForm = {
        period: mode === 'recalculate' ? this.detail.period : this.period || new Date().toLocaleDateString('sv-SE').slice(0, 7),
        rules: { ...(mode === 'recalculate' ? this.detail.rules : {}) },
        attendanceId: undefined,
        reason: ''
      }
      this.generateVisible = true
      await this.loadAttendance()
    },
    async loadAttendance() {
      this.attendanceBatches = []
      this.generateForm.attendanceId = undefined
      if (!this.generateForm.period) return
      this.attendanceBatches = (await hr('/payroll/attendance-options', 'get', { period: this.generateForm.period })).data.filter(a => a.status === 'confirmed')
      this.generateForm.attendanceId = this.attendanceBatches[0]?.id
    },
    async generate() {
      if (!this.generateForm.period) return this.$message.warning('请选择月份')
      if (this.generateMode === 'supplement') {
        if (!this.generateForm.reason) return this.$message.warning('请填写补发扣回原因')
        this.importExtra = { period: this.generateForm.period, reason: this.generateForm.reason }
        this.generateVisible = false
        this.importType = 'supplement'
        return
      }
      if (this.ruleFields.some(f => this.generateForm.rules[f.key] === undefined || this.generateForm.rules[f.key] === null)) return this.$message.warning('请明确填写全部公司计薪参数')
      this.busy = true
      try {
        const response = await hr(this.generateMode === 'recalculate' ? '/payroll/' + this.detail.id + '/recalculate' : '/payroll', 'post', {
          ...this.generateForm,
          revision: this.detail?.revision,
          discardAdjustments: this.generateMode === 'recalculate'
        })
        this.detail = response.data
        this.period = this.detail.period
        this.generateVisible = false
        this.load()
        this.$message.success('工资草稿已核算，请核对个税与本期项目')
      } finally {
        this.busy = false
      }
    },
    async startImport() {
      if (!(await this.guard('edit', '回传工资调整'))) return
      this.importExtra = { revision: this.detail.revision }
      this.importType = 'payroll'
    },
    previewImport(payload) {
      return hr('/payroll/' + this.detail.id + '/preview', 'post', payload)
    },
    async commitImport(payload) {
      const response = await hr(this.importType === 'supplement' ? '/payroll/supplement' : '/payroll/' + this.detail.id + '/import', 'post', payload)
      if (this.importType === 'supplement') this.detail = response.data
      return response
    },
    async importDone() {
      await this.load()
      if (this.detail) await this.open(this.detail.id)
    },
    downloadBlank() {
      if (this.financeMode) exportTemplate('payment')
      else if (this.detail) exportTemplate('payroll', this.exportRecords)
      else {
        exportTemplate('payroll')
        this.$message.info('回传时请使用具体工资草稿导出的批次数据')
      }
    },
    async exportRows() {
      if (!(await this.guard('export', '导出工资报表'))) return
      if (!this.detail) return this.exportSummary()
      const report = (await hr('/reports/payroll/' + this.detail.id, 'get', { finance: this.financeMode })).data
      exportWorkbook(payrollReportEntries(report, this.financeMode), '工资报表-' + report.period + '-' + report.id)
    },
    async exportSummary() {
      if (!(await this.guard('export', '导出月度工资汇总'))) return
      const response = await hr('/reports/payroll', 'get', { period: this.period, finance: this.financeMode })
      exportTemplate('payrollSummary', payrollSummary(response.data || []), '月度工资汇总-' + (this.period || '全部'))
    },
    openReason(action) {
      this.reasonAction = action
      this.reason = ''
      this.reasonVisible = true
    },
    async saveReason() {
      if (!this.reason) return this.$message.warning('请填写说明')
      this.busy = true
      try {
        await this.action(this.reasonAction, { reason: this.reason })
        this.reasonVisible = false
      } finally {
        this.busy = false
      }
    },
    async action(action, extra = {}) {
      await hr('/payroll/' + this.detail.id + '/' + action, 'post', { revision: this.detail.revision, ...extra })
      await this.open(this.detail.id)
      this.load()
      this.$message.success('操作完成')
    },
    async refreshAccounts() {
      await this.action('accounts')
    },
    async submit() {
      const response = await this.submitConfiguredAction('submit', { payrollId: this.detail.id, revision: this.detail.revision, title: this.detail.period + '工资审批' }, '提交工资审批')
      if (!response) return
      this.$message.success('已按配置提交审批或批准')
      await this.open(this.detail.id)
      this.load()
    },
    showItem(row) {
      this.selectedItem = row
    },
    async openPay(row) {
      if (!(await this.guard('pay', '登记工资发放'))) return
      this.payItem = row
      this.payForm = { requestToken: newToken(), amount: Number(row.netAmount) - Number(row.paidAmount), status: 'paid', paymentDate: new Date().toLocaleDateString('sv-SE') }
      this.payVisible = true
    },
    async paymentFile(file) {
      if (file.size > 10 * 1024 * 1024) {
        this.$message.warning('文件不能超过10MB')
        return false
      }
      this.payForm = { ...this.payForm, ...(await fileBody(file)) }
      return false
    },
    async savePay() {
      if (this.payForm.status === 'paid' && !this.payForm.fileContent) return this.$message.warning('请上传实际付款凭证')
      this.busy = true
      try {
        await hr('/payroll/items/' + this.payItem.id + '/pay', 'post', this.payForm)
        this.payVisible = false
        await this.open(this.detail.id)
        await this.load()
        this.$message.success('实际发放结果已登记')
      } finally {
        this.busy = false
      }
    },
    async downloadFile(id) {
      const file = (await hr('/files/' + id)).data
      saveAs(new Blob([Uint8Array.from(atob(file.fileContent), c => c.charCodeAt(0))]), file.fileName)
    }
  }
}
</script>
<style scoped src="./hr.css"></style>
