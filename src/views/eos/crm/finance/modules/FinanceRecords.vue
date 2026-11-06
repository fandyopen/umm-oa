<template>
  <div>
    <div class="toolbar">
      <a-input-search v-model="query.keyword" allow-clear placeholder="搜索客户、合同、票号或摘要" class="tw-w-[250px]" @search="search" />
      <a-select v-model="query.status" allow-clear placeholder="全部状态" class="tw-w-[135px]" @change="search"
        ><a-select-option v-for="s in visibleStatuses" :key="s.value" :value="s.value">{{ s.label }}</a-select-option></a-select
      >
      <a-button icon="reload" @click="load">刷新</a-button>
      <a-button v-if="recordType !== 'adjustment' && showModuleAction('finance', 'add')" type="primary" icon="plus" @click="openForm()" v-hasModuleAction="moduleActionBinding('finance', 'add')">新建{{ typeName }}</a-button>
      <a-dropdown v-if="recordType === 'adjustment' && showModuleAction('finance', 'add')" :trigger="['click']"
        ><a-button type="primary" icon="plus" v-hasModuleAction="moduleActionBinding('finance', 'add')">登记核销或冲销<a-icon type="down" /></a-button
        ><a-menu slot="overlay"><a-menu-item key="writeoff" @click="openForm(null, 'writeoff')">应收核销</a-menu-item><a-menu-item key="reversal" @click="openForm(null, 'reversal')">到账冲销</a-menu-item></a-menu></a-dropdown
      >
    </div>
    <a-table :loading="loading" :data-source="rows" :pagination="pagination" row-key="id" :scroll="{ x: 1350 }" size="small" @change="changePage">
      <a-table-column title="编号" data-index="id" width="75" /><a-table-column title="客户" data-index="customerName" width="160" /><a-table-column title="业务关联" width="230"
        ><template slot-scope="r"
          >{{ r.contractName || r.jobTitle || r.projectName || '—' }}<small class="subline">{{ r.contractCode || r.category || r.referenceNo }}</small></template
        ></a-table-column
      >
      <a-table-column title="类别 / 内容" width="170"
        ><template slot-scope="r"
          >{{ r.invoiceTitle || r.reason || r.category || typeName }}<small class="subline">{{ r.termKey ? '收费节点 ' + r.termKey : r.sourceEvent }}</small></template
        ></a-table-column
      >
      <a-table-column title="金额" data-index="amount" width="120"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      ><a-table-column title="业务日期" data-index="businessDate" width="110" />
      <a-table-column title="凭证" width="80"
        ><template slot-scope="r"
          ><a v-if="getFiles(r).length" @click="preview(r)">查看 {{ getFiles(r).length }}</a
          ><span v-else>—</span></template
        ></a-table-column
      >
      <a-table-column title="状态" data-index="status" width="110"
        ><template slot-scope="v"
          ><a-tag :color="statusColor[v]">{{ statusLabel[v] || v }}</a-tag></template
        ></a-table-column
      >
      <a-table-column title="审批进度" width="130"
        ><template slot-scope="r"
          ><span v-if="r.status === 'approving'">{{ r.taskName || '审批处理中' }}</span
          ><a v-else-if="r.instanceId" @click="processHistory(r.instanceId)">查看审批记录</a><span v-else>—</span></template
        ></a-table-column
      >
      <a-table-column title="操作" fixed="right" width="250"
        ><template slot-scope="r">
          <a-button
            v-if="['draft', 'rejected'].includes(r.status) && type !== 'adjustment' && type !== 'receivable' && showModuleAction('finance', 'edit')"
            type="link"
            size="small"
            @click="openForm(r)"
            v-hasModuleAction="moduleActionBinding('finance', 'edit')"
            >修改</a-button
          >
          <a-button
            v-if="['draft', 'rejected'].includes(r.status) && type !== 'adjustment' && type !== 'receivable' && showModuleAction('finance', submitAction(r))"
            type="link"
            size="small"
            @click="submitWorkflow(r)"
            v-hasModuleAction="moduleActionBinding('finance', submitAction(r))"
            >提交{{ needsApproval(r) ? '审批' : '确认' }}</a-button
          >
          <a-button
            v-if="r.status === 'approved' && r.recordType === 'invoice' && showModuleAction('finance', 'invoice_issue')"
            type="link"
            size="small"
            @click="openSettle(r, 'issue')"
            v-hasModuleAction="moduleActionBinding('finance', 'invoice_issue')"
            >登记开票结果</a-button
          >
          <a-button
            v-if="r.status === 'approved' && r.recordType === 'refund' && showModuleAction('finance', 'refund_pay')"
            type="link"
            size="small"
            @click="openSettle(r, 'pay')"
            v-hasModuleAction="moduleActionBinding('finance', 'refund_pay')"
            >登记实际退款</a-button
          >
          <a-button v-if="['draft', 'rejected', 'approved'].includes(r.status) && showModuleAction('finance', 'void')" type="link" size="small" @click="voidRow(r)" v-hasModuleAction="moduleActionBinding('finance', 'void')"
            >作废</a-button
          >
          <a-button type="link" size="small" @click="showDetail(r)">历史</a-button>
        </template></a-table-column
      >
    </a-table>
    <a-modal :visible="visible" :title="(form.id ? '修改' : '新建') + typeName" :confirm-loading="saving" width="650px" @cancel="visible = false" @ok="save">
      <a-form-model :model="form" ref="form" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-model-item v-if="type !== 'cost'" label="应收节点" prop="sourceKey"
          ><a-select v-model="form.sourceKey" show-search option-filter-prop="children" placeholder="选择已确认应收节点" @change="sourceChanged"
            ><a-select-option v-for="s in sources" :key="s.sourceKey" :value="s.sourceKey">{{ sourceLabel(s) }}</a-select-option></a-select
          ></a-form-model-item
        >
        <template v-if="type === 'refund' || type === 'reversal'"
          ><a-form-model-item label="原到账" prop="originalKey"
            ><a-select v-model="form.originalKey" show-search option-filter-prop="children" placeholder="选择原到账记录" @change="originalChanged"
              ><a-select-option v-for="r in originalsForSource" :key="originalKey(r)" :value="originalKey(r)">{{ originalLabel(r) }}</a-select-option></a-select
            ></a-form-model-item
          ><a-form-model-item label="原因" prop="reason"><a-textarea v-model="form.reason" :max-length="1000" /></a-form-model-item
        ></template>
        <a-form-model-item v-if="type === 'writeoff'" label="核销原因" prop="reason"><a-textarea v-model="form.reason" :max-length="1000" /></a-form-model-item>
        <a-form-model-item v-if="type === 'cost'" label="客户" prop="customerId"
          ><a-select v-model="form.customerId" show-search option-filter-prop="children" placeholder="选择客户" @change="customerChanged"
            ><a-select-option v-for="c in customers" :key="c.id" :value="c.id">{{ c.name }}</a-select-option></a-select
          ></a-form-model-item
        >
        <a-form-model-item v-if="type === 'cost'" label="关联合同">
          <a-select v-model="form.contractId" allow-clear placeholder="可选，关联生效合同" @change="contractChanged"
            ><a-select-option v-for="c in contractsForCustomer" :key="c.id" :value="c.id">{{ c.code }} · {{ c.name }}</a-select-option></a-select
          >
        </a-form-model-item>
        <a-form-model-item v-if="type === 'cost' && jobsForCustomer.length" label="职位"
          ><a-select v-model="form.jobId" allow-clear
            ><a-select-option v-for="j in jobsForCustomer" :key="j.id" :value="j.id">{{ j.title }}</a-select-option></a-select
          ></a-form-model-item
        >
        <a-form-model-item v-if="type === 'cost'" label="项目"><a-input v-model="form.projectName" maxlength="200" /></a-form-model-item>
        <a-form-model-item v-if="type === 'invoice'" label="发票抬头" prop="invoiceTitle"><a-input v-model="form.invoiceTitle" maxlength="200" /></a-form-model-item>
        <a-form-model-item v-if="type === 'invoice'" label="税号"><a-input v-model="form.taxNo" maxlength="100" /></a-form-model-item>
        <a-form-model-item v-if="type === 'cost'" label="成本类别" prop="category"
          ><a-select v-model="form.category"
            ><a-select-option v-for="c in costCategories" :key="c" :value="c">{{ c }}</a-select-option></a-select
          ></a-form-model-item
        >
        <a-form-model-item v-if="type === 'cost'" label="收款对象"><a-input v-model="form.payee" maxlength="200" /></a-form-model-item>
        <a-form-model-item label="金额" prop="amount"
          ><a-input-number v-model="form.amount" :min="0.01" :precision="2" :max="selectedSource && ['receipt', 'invoice', 'writeoff'].includes(type) ? available : undefined" class="tw-w-full"
        /></a-form-model-item>
        <a-form-model-item label="日期" prop="businessDate"><a-date-picker v-model="form.businessDate" value-format="YYYY-MM-DD" class="tw-w-full" /></a-form-model-item>
        <a-form-model-item v-if="type === 'receipt'" label="付款渠道"><a-input v-model="form.channel" maxlength="100" /></a-form-model-item>
        <a-form-model-item v-if="type === 'invoice' || type === 'receipt'" label="业务流水号"><a-input v-model="form.referenceNo" maxlength="100" /></a-form-model-item>
        <a-form-model-item label="凭证"
          ><a-upload :action="uploadUrl" :headers="uploadHeaders" :file-list="files" @change="onUpload"><a-button icon="upload">上传凭证</a-button></a-upload></a-form-model-item
        >
        <a-form-model-item v-if="type !== 'refund' && type !== 'reversal'" label="备注"><a-textarea v-model="form.remark" :max-length="1000" /></a-form-model-item>
      </a-form-model>
      <template slot="footer"><a-button @click="visible = false">取消</a-button><a-button type="primary" :loading="saving" @click="save">保存草稿</a-button></template>
    </a-modal>
    <a-modal :visible="!!settle" :title="settle && settle.action === 'issue' ? '登记开票结果' : '登记实际退款'" :confirm-loading="saving" @cancel="settle = null" @ok="settleNow">
      <a-form-model :model="settleForm" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-model-item v-if="settle && settle.action === 'issue'" label="发票号码" required><a-input v-model="settleForm.invoiceNo" maxlength="100" /></a-form-model-item>
        <a-form-model-item label="实际日期" required><a-date-picker v-model="settleForm.businessDate" value-format="YYYY-MM-DD" /></a-form-model-item>
        <a-form-model-item label="业务流水号"><a-input v-model="settleForm.referenceNo" maxlength="100" /></a-form-model-item>
        <a-form-model-item label="凭证" required
          ><a-upload :action="uploadUrl" :headers="uploadHeaders" :file-list="settleFiles" @change="onSettleUpload"><a-button icon="upload">上传凭证</a-button></a-upload></a-form-model-item
        >
      </a-form-model>
    </a-modal>
    <a-modal :visible="!!detail" title="财务记录历史" :footer="null" width="860px" :body-style="{ maxHeight: '72vh', overflowY: 'auto' }" @cancel="detail = null">
      <finance-record-history v-if="detail" :record="detail" />
    </a-modal>
    <a-modal :visible="!!previewFile" title="凭证" :footer="null" @cancel="previewFile = null"
      ><div v-for="(file, index) in previewFile || []" :key="file.fileName || index" class="tw-mb-2.5">
        <a :href="file.fileName" target="_blank" rel="noopener noreferrer">{{ file.name || '打开凭证 ' + (index + 1) }}</a>
      </div></a-modal
    >
  </div>
</template>
<script>
import storage from 'store'
import { ACCESS_TOKEN } from '/src/store/mutation-types.js'
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { executeWorkflowAction } from '/src/api/workflow/action.js'
import { handleProcessHistory } from '/src/views/eos/config/busUtil.js'
import { today } from '../../contract/shared.js'
import FinanceRecordHistory from './FinanceRecordHistory.vue'
import { listFinanceRecords, getFinanceRecord, listFinanceOptions, listFinanceReceipts, createFinanceRecord, updateFinanceRecord, financeRecordAction } from '/src/api/eos/crm/finance.js'
const ctx = { component: 'eos/crm/finance/index', label: '财务与回款管理' }
const labels = { invoice: '开票申请', receipt: '回款申请', refund: '退款申请', reversal: '到账冲销', writeoff: '应收核销', cost: '直接成本' }
const actions = { invoice: 'invoice_submit', receipt: 'receipt_confirm', refund: 'refund_submit', reversal: 'adjust_confirm', writeoff: 'adjust_confirm', cost: 'cost_confirm' }
export default {
  name: 'FinanceRecords',
  components: { FinanceRecordHistory },
  mixins: [
    withModuleContexts(
      { finance: ctx },
      {
        openForm: function (row) {
          return { module: 'finance', action: row?.id ? 'edit' : 'add', label: '编辑财务单据' }
        },
        save: function () {
          return { module: 'finance', action: this.form.id ? 'edit' : 'add', label: '保存财务单据' }
        },
        submitWorkflow: function (row) {
          return { module: 'finance', action: this.submitAction(row), label: '提交财务单据' }
        },
        openSettle: function (row, action) {
          return { module: 'finance', action: action === 'issue' ? 'invoice_issue' : 'refund_pay', label: '登记实际办理结果' }
        },
        settleNow: function () {
          return { module: 'finance', action: this.settle?.action === 'issue' ? 'invoice_issue' : 'refund_pay', label: '登记实际办理结果' }
        },
        voidRow: { module: 'finance', action: 'void', label: '作废财务单据' }
      }
    )
  ],
  props: { recordType: { type: String, required: true } },
  data: () => ({
    loading: false,
    saving: false,
    rows: [],
    total: 0,
    pageNum: 1,
    pageSize: 20,
    query: { keyword: '', status: undefined },
    visible: false,
    form: {},
    editingType: '',
    files: [],
    settle: null,
    settleForm: {},
    settleFiles: [],
    detail: null,
    previewFile: null,
    sources: [],
    originals: [],
    customers: [],
    jobs: [],
    contracts: [],
    costCategories: ['招聘渠道', '寻访成本', '项目采购', '服务外包', '其他直接成本'],
    statusLabel: { draft: '草稿', approving: '审批中', approved: '待实际办理', confirmed: '已确认', rejected: '已驳回', void: '已作废' },
    statusColor: { draft: undefined, approving: 'blue', approved: 'gold', confirmed: 'green', rejected: 'red', void: undefined }
  }),
  computed: {
    type() {
      return this.recordType === 'adjustment' ? this.editingType || 'writeoff' : this.recordType
    },
    typeName() {
      return labels[this.type] || '财务记录'
    },
    pagination() {
      return { current: this.pageNum, pageSize: this.pageSize, total: this.total, showSizeChanger: true, showTotal: n => '共 ' + n + ' 条' }
    },
    visibleStatuses() {
      return Object.keys(this.statusLabel).map(value => ({ value, label: this.statusLabel[value] }))
    },
    uploadUrl() {
      return import.meta.env.VITE_APP_BASE_API + '/common/upload'
    },
    uploadHeaders() {
      return { Authorization: 'Bearer ' + storage.get(ACCESS_TOKEN) }
    },
    selectedSource() {
      return this.sources.find(s => s.sourceKey === this.form.sourceKey)
    },
    available() {
      return this.type === 'invoice' ? Number(this.selectedSource?.invoiceAvailable || 0) : Number(this.selectedSource?.receiptAvailable || 0)
    },
    originalsForSource() {
      return this.originals.filter(r => this.form.sourceKey && this.sourceKey(r) === this.form.sourceKey)
    },
    jobsForCustomer() {
      return this.jobs.filter(j => String(j.customerId) === String(this.form.customerId) && (!this.form.contractId || !j.contractId || String(j.contractId) === String(this.form.contractId)))
    },
    contractsForCustomer() {
      return this.contracts.filter(c => String(c.customerId) === String(this.form.customerId))
    }
  },
  mounted() {
    this.load()
    this.loadOptions()
  },
  methods: {
    processHistory: handleProcessHistory,
    money(v) {
      return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
    },
    async load() {
      this.loading = true
      try {
        const r = await listFinanceRecords(this.recordType, { ...this.query, pageNum: this.pageNum, pageSize: this.pageSize })
        this.rows = r.data?.rows || []
        this.total = r.data?.total || 0
      } finally {
        this.loading = false
      }
    },
    async loadOptions() {
      const [o, r] = await Promise.all([listFinanceOptions(), listFinanceReceipts({ pageNum: 1, pageSize: 1000 })])
      this.sources = o.data?.sources || []
      this.customers = o.data?.customers || []
      this.jobs = o.data?.jobs || []
      this.contracts = o.data?.contracts || []
      this.originals = r.data || []
    },
    changePage(p) {
      this.pageNum = p.current
      this.pageSize = p.pageSize
      this.load()
    },
    search() {
      this.pageNum = 1
      this.load()
    },
    async changed() {
      await Promise.all([this.load(), this.loadOptions()])
      this.$emit('changed')
    },
    validFiles(files) {
      return files.every(f => f.status === 'done' && !!(f.response?.fileName || f.fileName))
    },
    sourceKey(s) {
      return s.receivableId ? 'manual:' + s.receivableId : 'contract:' + s.contractId + ':' + s.termKey
    },
    sourceLabel(s) {
      return s.customerName + ' · ' + (s.contractCode || s.title) + ' · ' + (s.title || s.termTitle) + ' · 待收 ' + this.money(s.outstandingAmount)
    },
    originalKey(r) {
      return r.originalReceiptId ? 'receipt:' + r.originalReceiptId : 'record:' + r.originalRecordId
    },
    originalLabel(r) {
      return r.customerName + ' · ' + (r.contractName || r.projectName || '手工应收') + ' · ' + r.businessDate + ' · ' + this.money(r.amount)
    },
    async openForm(row, kind) {
      await this.loadOptions()
      this.editingType = row?.recordType || kind || this.type
      this.form = row ? { ...row } : { createToken: window.crypto?.randomUUID ? window.crypto.randomUUID() : Date.now() + '-' + Math.random(), amount: undefined, businessDate: today() }
      if (row) {
        this.form.sourceKey = this.sourceKey(row)
        if (row.originalReceiptId || row.originalRecordId) this.form.originalKey = this.originalKey(row)
      }
      this.files = this.parseFiles(row?.attachment)
      this.visible = true
    },
    parseFiles(v) {
      try {
        return JSON.parse(v || '[]').map((f, i) => ({ ...f, uid: 'saved-' + i, status: 'done' }))
      } catch (e) {
        return []
      }
    },
    sourceChanged(key) {
      const s = this.sources.find(x => x.sourceKey === key)
      this.form.originalKey = undefined
      this.form.originalReceiptId = undefined
      this.form.originalRecordId = undefined
      if (s) {
        this.form.customerId = s.customerId
        this.form.contractId = s.contractId
        this.form.termKey = s.termKey
        this.form.receivableId = s.receivableId
        if (['receipt', 'invoice', 'writeoff'].includes(this.type)) this.form.amount = Math.min(Number(this.form.amount || 0) || this.available, this.available)
      }
    },
    originalChanged(key) {
      const r = this.originals.find(x => this.originalKey(x) === key)
      if (!r) return
      this.form.originalReceiptId = r.originalReceiptId
      this.form.originalRecordId = r.originalRecordId
      this.form.customerId = r.customerId
      this.form.contractId = r.contractId
      this.form.termKey = r.termKey
      this.form.receivableId = r.receivableId
      this.form.sourceKey = this.sourceKey(r)
      this.form.amount = Math.min(Number(this.form.amount || 0) || Number(r.amount), Number(r.amount))
    },
    customerChanged() {
      this.form.contractId = undefined
      this.form.jobId = undefined
    },
    contractChanged() {
      if (!this.jobsForCustomer.some(j => j.id === this.form.jobId)) this.form.jobId = undefined
    },
    onUpload(info) {
      this.files = info.fileList
      this.uploadResult(info, this.files)
    },
    onSettleUpload(info) {
      this.settleFiles = info.fileList
      this.uploadResult(info, this.settleFiles)
    },
    uploadResult(info, files) {
      if (info.file.status === 'done' && info.file.response?.code !== 200) {
        const i = files.findIndex(f => f.uid === info.file.uid)
        if (i >= 0) files.splice(i, 1)
        this.$message.error(info.file.response?.msg || '上传失败')
      } else if (info.file.status === 'error') this.$message.error('凭证上传失败')
    },
    async save() {
      if (this.saving) return
      if (!this.form.sourceKey && this.type !== 'cost') return this.$message.warning('请选择应收节点')
      if (['refund', 'reversal'].includes(this.type) && !this.form.originalKey) return this.$message.warning('请选择原到账')
      if (['refund', 'reversal', 'writeoff'].includes(this.type) && !this.form.reason?.trim()) return this.$message.warning('请填写办理原因')
      if (this.type === 'invoice' && !this.form.invoiceTitle?.trim()) return this.$message.warning('请填写发票抬头')
      if (this.type === 'cost' && (!this.form.customerId || !this.form.category)) return this.$message.warning('请选择客户和成本类别')
      if (!this.form.amount || !this.form.businessDate) return this.$message.warning('请填写金额和日期')
      if (!this.validFiles(this.files)) return this.$message.warning('请等待凭证上传完成，或移除上传失败的文件')
      this.saving = true
      try {
        const data = { ...this.form, attachment: JSON.stringify(this.files.map(f => ({ name: f.name, url: f.response?.url || f.url, fileName: f.response?.fileName || f.fileName }))) }
        delete data.sourceKey
        delete data.originalKey
        if (data.id) await updateFinanceRecord(this.type, data.id, data)
        else await createFinanceRecord(this.type, data)
        this.visible = false
        this.$message.success('草稿已保存')
        await this.changed()
      } finally {
        this.saving = false
      }
    },
    submitAction(r) {
      return actions[r.recordType] || actions[this.type]
    },
    needsApproval(r) {
      return ['invoice', 'refund'].includes(r.recordType)
    },
    async submitWorkflow(r) {
      const action = this.submitAction(r)
      const response = await executeWorkflowAction({ moduleId: this.moduleContexts.finance.moduleId, actionCode: action, payload: { recordId: r.id, revision: r.revision } })
      this.$message.success(response.data?.status === 'pendingApproval' ? '审批申请已提交' : this.needsApproval(r) ? '已确认，请登记实际办理结果' : '财务记录已确认')
      await this.changed()
    },
    openSettle(r, action) {
      this.settle = { ...r, action }
      this.settleForm = { revision: r.revision, businessDate: today() }
      this.settleFiles = []
    },
    async settleNow() {
      if (this.saving || !this.settle) return
      if (!this.settleForm.businessDate) return this.$message.warning('请选择实际办理日期')
      if (this.settle.action === 'issue' && !this.settleForm.invoiceNo?.trim()) return this.$message.warning('请填写发票号码')
      if (!this.settleFiles.length || !this.validFiles(this.settleFiles)) return this.$message.warning('请上传实际办理凭证，并等待全部上传完成')
      this.saving = true
      try {
        const data = { ...this.settleForm, attachment: JSON.stringify(this.settleFiles.map(f => ({ name: f.name, url: f.response?.url || f.url, fileName: f.response?.fileName || f.fileName }))) }
        await financeRecordAction(this.settle.recordType, this.settle.id, this.settle.action, data)
        this.settle = null
        this.$message.success('实际办理结果已登记')
        await this.changed()
      } finally {
        this.saving = false
      }
    },
    async voidRow(r) {
      let answer
      try {
        answer = await this.$prompt('作废原因', '作废财务记录', { inputValidator: v => !!v?.trim() && v.trim().length <= 1000, message: '原因必填，最多1000字' })
      } catch (e) {
        return
      }
      await financeRecordAction(r.recordType, r.id, 'void', { reason: answer.value, revision: r.revision })
      await this.changed()
    },
    getFiles(r) {
      return this.parseFiles(r.attachment)
    },
    preview(r) {
      this.previewFile = this.getFiles(r).filter(f => typeof f.fileName === 'string' && f.fileName.startsWith('/profile/') && !f.fileName.includes('..'))
    },
    async showDetail(r) {
      const x = await getFinanceRecord(r.id)
      this.detail = x.data
    }
  }
}
</script>
<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.toolbar .ant-btn-primary {
  margin-left: auto;
}
.subline {
  display: block;
  color: #989ea8;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
