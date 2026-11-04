<template>
  <a-spin :spinning="loading">
    <div class="detail-head">
      <div><h2>{{ contract.name || '合同详情' }}</h2><div class="detail-meta"><a-tag :color="statusColors[contract.status]">{{ statuses[contract.status] }}</a-tag><span>{{ contract.code }}</span><a v-if="showModuleAction('customer')" @click="openCustomer">{{ contract.customerName }}</a><span v-else>{{ contract.customerName }}</span><span>负责人：{{ contract.ownerUserName || '未分配' }}</span></div></div>
      <div class="contract-total"><span>合同金额</span><strong>{{ money(contract.amount) }}</strong></div>
    </div>
    <a-alert v-if="contract.effectiveVersion && !['active', 'completed'].includes(contract.status)" type="info" show-icon :message="`V${contract.effectiveVersion} 继续生效，应收与风险按原签署版本计算。当前 V${contract.versionNo}：${statuses[contract.status]}。`" />
    <a-alert v-if="contract.approvalComment" :type="contract.status === 'rejected' ? 'warning' : 'info'" :message="`审批意见：${contract.approvalComment}`" style="margin-top: 12px" />
    <a-alert v-if="contract.signingPending" type="info" show-icon :message="'签署审批中：' + (contract.signingWorkflow.taskName || '等待审批')" description="审批通过后合同才生效；审批期间不能重复签署或创建新版本。" style="margin-top: 12px" />
    <a-alert v-else-if="contract.signingWorkflow && contract.signingWorkflow.status === 'rejected'" type="warning" show-icon message="签署申请已退回，合同尚未生效。请在审批记录查看意见，处理后重新提交签署申请。" style="margin-top: 12px" />
    <div class="detail-actions">
      <a-button v-if="editable && showModuleAction('contract', 'edit')" icon="edit" @click="openForm({ contractId })" :loading="!!moduleActionBusy.openForm">编辑草稿</a-button>
      <a-tooltip v-if="editable && showModuleAction('contract', 'submit')" title="流程配置：合同列表 / 提交审批，动作编码 submit"><a-button type="primary" @click="startAction('submit')" v-hasModuleAction="moduleActionBinding('contract', 'submit')" :loading="!!moduleActionBusy.startAction">提交审批</a-button></a-tooltip>
      <a-button v-if="contract.canEditCustomer && contract.status === 'approved' && !contract.signingPending && showModuleAction('contract', 'sign')" type="primary" @click="startAction('sign')" v-hasModuleAction="moduleActionBinding('contract', 'sign')" :loading="!!moduleActionBusy.startAction">{{ contract.signApprovalEnabled ? '提交签署审批' : '登记签署并生效' }}</a-button>
      <a-button v-if="contract.canEditCustomer && !contract.signingPending && ['active', 'approved'].includes(contract.status) && showModuleAction('version', 'edit')" @click="confirmAction('revision', '创建新版本', '新版本需要重新审批和签署，原生效版本继续履约。')" :loading="!!moduleActionBusy.confirmAction">创建新版本</a-button>
      <a-button v-if="contract.canEditCustomer && contract.effectiveVersion && showModuleAction('contract', 'add')" icon="reload" @click="renew" :loading="!!moduleActionBusy.openForm">发起续约</a-button>
      <a-button v-if="contract.canEditCustomer && contract.status === 'active' && showModuleAction('contract', 'edit')" @click="confirmAction('finish', '合同结案', '收费全部结清后可结案，结案后停止履约提醒。')" :loading="!!moduleActionBusy.confirmAction">合同结案</a-button>
    </div>
    <a-tabs v-model="activeTab" style="margin-top: 16px">
      <a-tab-pane key="overview" tab="基本信息">
    <a-descriptions bordered size="small" :column="2">
      <a-descriptions-item label="服务类型">{{ contract.serviceType }}</a-descriptions-item><a-descriptions-item label="审批人">{{ contract.approvalUserName || '未提交' }}</a-descriptions-item>
      <a-descriptions-item v-if="contract.workflow && contract.workflow.instanceId" label="审批节点">{{ contract.workflow.status === 'approved' ? '审批完成' : contract.workflow.status === 'rejected' ? '已退回' : contract.workflow.taskName || '审批中' }}</a-descriptions-item>
      <a-descriptions-item label="服务期限">{{ contract.startDate }} 至 {{ contract.endDate }}</a-descriptions-item><a-descriptions-item label="当前 / 生效版本">V{{ contract.versionNo }} / {{ contract.effectiveVersion ? `V${contract.effectiveVersion}` : '尚未生效' }}</a-descriptions-item>
      <a-descriptions-item label="生效合同额 / 已收">{{ money(contract.effectiveAmount) }} / {{ money(contract.receivedAmount) }}</a-descriptions-item><a-descriptions-item label="待回款">{{ money(contract.outstandingAmount) }}</a-descriptions-item>
      <a-descriptions-item v-if="contract.parentContractId" label="续约来源">原合同 #{{ contract.parentContractId }}</a-descriptions-item><a-descriptions-item v-if="contract.opportunityId" label="关联商机">#{{ contract.opportunityId }}</a-descriptions-item>
      <a-descriptions-item label="备注" :span="2">{{ contract.remark || '无' }}</a-descriptions-item>
      <a-descriptions-item label="合同附件" :span="2"><a v-for="(file, index) in attachments" :key="index" :href="file.url" target="_blank" rel="noopener noreferrer" style="margin-right: 16px">{{ file.name || '合同附件' }}</a><span v-if="!attachments.length">暂无附件</span></a-descriptions-item>
    </a-descriptions>
      </a-tab-pane>
      <a-tab-pane key="terms" tab="收费与回款">
        <h3>{{ contract.effectiveVersion ? `生效收费条款 · V${contract.effectiveVersion}` : '草稿收费条款' }}</h3>
        <a-table :columns="feeColumns" :data-source="displayTerms" :pagination="false" row-key="key" size="small" :scroll="{ x: 900 }">
          <span slot="money" slot-scope="value">{{ money(value) }}</span>
          <span slot="feeAction" slot-scope="_, record"><a-button v-if="contract.canEditCustomer && contract.effectiveVersion && contract.status !== 'completed' && record.outstandingAmount > 0 && showModuleAction('fee', 'receive')" type="link" @click="startReceipt(record)">登记回款</a-button></span>
        </a-table>
        <template v-if="contract.effectiveVersion && ['draft', 'pending', 'rejected', 'approved'].includes(contract.status)"><h3 style="margin-top: 22px">待生效版本收费条款 · V{{ contract.versionNo }}</h3><a-table :columns="feeColumns.slice(0, 5)" :data-source="contract.feeTerms" :pagination="false" row-key="key" size="small"><span slot="money" slot-scope="value">{{ money(value) }}</span></a-table></template>
        <h3 style="margin-top: 22px">回款流水</h3><a-table :columns="receiptColumns" :data-source="contract.receipts || []" :pagination="{ pageSize: 8 }" row-key="id" size="small"><span slot="money" slot-scope="value">{{ money(value) }}</span></a-table>
      </a-tab-pane>
      <a-tab-pane key="approvals" tab="审批记录">
        <a-alert type="info" show-icon message="审批任务统一在待办办理，此处按提交版本追溯审批进度与意见。" style="margin-bottom: 16px" />
        <a-table :columns="versionColumns" :data-source="contract.versions || []" :pagination="false" row-key="id" size="small"><span slot="version" slot-scope="value">V{{ value }} <a-tag v-if="value === contract.effectiveVersion" color="green">当前生效</a-tag></span><span slot="status" slot-scope="value"><a-tag :color="statusColors[value]">{{ statuses[value] }}</a-tag></span><span slot="workflow" slot-scope="value"><a v-if="value && value.instanceId" @click="workflowPreview = value">查看流程记录</a><span v-else>—</span></span></a-table>
        <h3 style="margin-top: 22px">签署审批记录</h3>
        <a-table :columns="signingColumns" :data-source="signingHistory" :pagination="false" row-key="requestId" size="small">
          <span slot="signingStatus" slot-scope="value">{{ { running: '审批中', approved: '已通过', rejected: '已退回' }[value] || value }}</span>
          <span slot="workflow" slot-scope="_, record"><a @click="workflowPreview = record">查看流程记录</a></span>
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="versions" tab="版本记录">
        <a-table :columns="snapshotColumns" :data-source="contract.versions || []" :pagination="false" row-key="id" size="small"><span slot="version" slot-scope="value">V{{ value }} <a-tag v-if="value === contract.effectiveVersion" color="green">当前生效</a-tag></span><span slot="status" slot-scope="value"><a-tag :color="statusColors[value]">{{ statuses[value] }}</a-tag></span></a-table>
        <div class="compare-controls"><strong>版本对比</strong><a-select v-model="compareBefore" placeholder="选择原版本" style="width: 160px"><a-select-option v-for="item in contract.versions || []" :key="item.versionNo" :value="item.versionNo">V{{ item.versionNo }}</a-select-option></a-select><span>与</span><a-select v-model="compareAfter" placeholder="选择新版本" style="width: 160px"><a-select-option v-for="item in contract.versions || []" :key="item.versionNo" :value="item.versionNo">V{{ item.versionNo }}</a-select-option></a-select></div>
        <a-alert v-if="(contract.versions || []).length < 2" type="info" message="每次提交审批保存完整快照，累计两个提交版本后可对比。" />
        <a-table v-else :columns="compareColumns" :data-source="differences" :pagination="false" row-key="field" size="small" :locale="{ emptyText: '所选版本无内容差异' }"><span slot="compare" slot-scope="value" style="white-space: pre-wrap; word-break: break-word">{{ value }}</span></a-table>
      </a-tab-pane>
      <a-tab-pane key="reminders" tab="提醒记录">
        <a-alert type="info" message="风险统一在客户风险提醒跟进；完成回款、签署续约或结案后，依据业务条件解除提醒。" style="margin-bottom: 16px" />
        <a-button v-if="showModuleAction('risk')" type="link" @click="openRisks">前往风险提醒处理</a-button>
        <h3>当前合同风险</h3><a-table :columns="reminderColumns" :data-source="contract.reminders || []" :pagination="false" row-key="taskId" size="small" />
        <h3 style="margin-top: 22px">处理记录</h3><a-table :columns="logColumns" :data-source="contract.reminderLogs || []" :pagination="{ pageSize: 8 }" row-key="id" size="small" />
      </a-tab-pane>
    </a-tabs>
    <a-modal :visible="!!operation" :title="operationTitle" :confirm-loading="saving || !!moduleActionBusy.runAction" @cancel="operation = ''" @ok="runAction">
      <template v-if="operation === 'submit'"><a-alert type="info" show-icon message="按已发布的审批流程发起" description="审批人及节点由流程设计决定。提交后冻结合同、收费条款和本次流程配置，全部节点通过后才能登记签署。" /><p style="margin-top: 12px">配置位置：模块管理 → 合同列表 → 提交审批（动作编码：<code>submit</code>）。请先在按钮修改页开启流程设计，再启用审批并发布流程。</p></template>
      <template v-else-if="operation === 'sign'"><p>{{ contract.signApprovalEnabled ? '登记实际签署日期并提交审批；全部节点通过后合同才生效，并更新应收与客户合作数据。' : '确认合同已完成线下签署，签署后建立应收并更新客户合作数据。' }}</p><a-date-picker v-model="actionForm.signedDate" value-format="YYYY-MM-DD" /></template>
      <template v-else-if="operation === 'receipts'"><p>{{ actionForm.termTitle }} · 待收 {{ money(actionForm.maxAmount) }}</p><a-form layout="vertical"><a-form-item label="本次回款（元）"><a-input-number v-model="actionForm.amount" :min="0.01" :max="actionForm.maxAmount" :precision="2" style="width: 100%" /></a-form-item><a-form-item label="实际回款日期"><a-date-picker v-model="actionForm.receiptDate" value-format="YYYY-MM-DD" style="width: 100%" /></a-form-item><a-form-item label="回款说明"><a-textarea v-model="actionForm.remark" :max-length="1000" /></a-form-item></a-form></template>
    </a-modal>
    <a-modal :visible="!!workflowPreview" title="审批流程记录" :footer="null" width="850px" @cancel="workflowPreview = null">
      <p>流程实例：{{ workflowPreview && workflowPreview.instanceId }}</p>
      <a-table :columns="workflowColumns" :data-source="workflowPreview ? workflowPreview.history || [] : []" :pagination="false" row-key="taskId" size="small"><span slot="taskStatus" slot-scope="value"><a-tag :color="value === 'done' ? 'green' : 'blue'">{{ value === 'done' ? '已办理' : '待审批' }}</a-tag></span></a-table>
    </a-modal>
  </a-spin>
</template>

<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { crmModules } from '/src/views/eos/crm/moduleContexts.js'
import moment from 'moment'
import { getContract, contractAction, normalizeContractId } from '/src/api/eos/crm/contracts.js'
import { statuses, statusColors, money, today } from '../shared.js'
import ContractForm from './ContractForm.vue'

export default {
  mixins: [
    withModuleContexts(
      { contract: crmModules.contract, customer: crmModules.customer, fee: crmModules.fee, version: crmModules.version, risk: crmModules.risk },
      {
        openForm: function (props) {
          return { module: 'contract', action: props.contractId ? 'edit' : 'add', label: props.contractId ? '编辑草稿' : '发起续约' }
        },
        startAction: function (action) {
          return { module: 'contract', action, label: action === 'submit' ? '提交审批' : '登记签署' }
        },
        startReceipt: { module: 'fee', action: 'receive', label: '登记回款' },
        confirmAction: function (action, label) {
          return { module: action === 'revision' ? 'version' : 'contract', action: 'edit', label }
        },
        runAction: function () {
          return { module: this.operation === 'receipts' ? 'fee' : 'contract', action: this.operation === 'receipts' ? 'receive' : this.operation, label: this.operationTitle || '提交合同操作' }
        },
        openCustomer: { module: 'customer', action: 'view', label: '查看客户详情' },
        openRisks: { module: 'risk', action: 'view', label: '查看风险提醒' }
      }
    )
  ],
  name: 'CrmContractDetail',
  props: { contractId: { type: Number, required: true }, initialTab: { type: String, default: 'overview' }, initialTermKey: { type: String, default: '' } },
  data() {
    return {
      loading: false,
      saving: false,
      contract: {},
      activeTab: this.initialTab,
      operation: '',
      actionForm: {},
      workflowPreview: null,
      compareBefore: undefined,
      compareAfter: undefined,
      statuses,
      statusColors,
      feeColumns: [
        { title: '收费名称', dataIndex: 'title' },
        { title: '触发条件', dataIndex: 'trigger' },
        { title: '收费金额', dataIndex: 'amount', scopedSlots: { customRender: 'money' } },
        { title: '收费到期', dataIndex: 'dueDate' },
        { title: '保证期（天）', dataIndex: 'guaranteeDays' },
        { title: '已收', dataIndex: 'receivedAmount', scopedSlots: { customRender: 'money' } },
        { title: '待收', dataIndex: 'outstandingAmount', scopedSlots: { customRender: 'money' } },
        { title: '操作', scopedSlots: { customRender: 'feeAction' } }
      ],
      receiptColumns: [
        { title: '回款日期', dataIndex: 'receiptDate' },
        { title: '金额', dataIndex: 'amount', scopedSlots: { customRender: 'money' } },
        { title: '说明', dataIndex: 'remark' },
        { title: '登记人', dataIndex: 'createBy' }
      ],
      snapshotColumns: [
        { title: '版本', dataIndex: 'versionNo', scopedSlots: { customRender: 'version' } },
        { title: '状态', dataIndex: 'status', scopedSlots: { customRender: 'status' } },
        { title: '提交人', dataIndex: 'submitUserName' },
        { title: '提交时间', dataIndex: 'submitTime' },
        { title: '签署日期', dataIndex: 'signedDate' }
      ],
      versionColumns: [
        { title: '版本', dataIndex: 'versionNo', scopedSlots: { customRender: 'version' } },
        { title: '审批结果', dataIndex: 'status', scopedSlots: { customRender: 'status' } },
        { title: '提交人', dataIndex: 'submitUserName' },
        { title: '提交时间', dataIndex: 'submitTime' },
        { title: '当前 / 最终审批人', dataIndex: 'approvalUserName' },
        { title: '审批时间', dataIndex: 'approvalTime' },
        { title: '意见', dataIndex: 'approvalComment' },
        { title: '审批流程', dataIndex: 'workflow', scopedSlots: { customRender: 'workflow' } },
        { title: '签署日期', dataIndex: 'signedDate' }
      ],
      workflowColumns: [
        { title: '审批节点', dataIndex: 'taskName' },
        { title: '审批人', dataIndex: 'assigneeName' },
        { title: '任务状态', dataIndex: 'status', scopedSlots: { customRender: 'taskStatus' } },
        { title: '审批意见', dataIndex: 'comment' },
        { title: '办理时间', dataIndex: 'completeTime' }
      ],
      compareColumns: [
        { title: '变更项目', dataIndex: 'field', width: 120 },
        { title: '原版本', dataIndex: 'before', scopedSlots: { customRender: 'compare' } },
        { title: '新版本', dataIndex: 'after', scopedSlots: { customRender: 'compare' } }
      ],
      signingColumns: [
        { title: '版本', dataIndex: 'versionNo' },
        { title: '审批状态', dataIndex: 'status', scopedSlots: { customRender: 'signingStatus' } },
        { title: '当前节点', dataIndex: 'taskName' },
        { title: '审批流程', scopedSlots: { customRender: 'workflow' } }
      ],
      logColumns: [
        { title: '处理时间', dataIndex: 'createTime' },
        { title: '处理内容', dataIndex: 'content' },
        { title: '处理人', dataIndex: 'createBy' }
      ],
      reminderColumns: [
        { title: '提醒类型', dataIndex: 'title' },
        { title: '提醒原因', dataIndex: 'reason' },
        { title: '负责人', dataIndex: 'ownerUserName' }
      ]
    }
  },
  computed: {
    signingHistory() {
      return (this.contract.versions || []).flatMap(version => (version.signingHistory || []).map(workflow => ({ ...workflow, versionNo: version.versionNo })))
    },
    editable() {
      return this.contract.canEditCustomer && ['draft', 'rejected'].includes(this.contract.status)
    },
    displayTerms() {
      return this.contract.effectiveVersion ? this.contract.effectiveFeeTerms || [] : this.contract.feeTerms || []
    },
    operationTitle() {
      return { submit: '提交合同审批', sign: '签署生效', receipts: '登记回款' }[this.operation] || ''
    },
    attachments() {
      try {
        return JSON.parse(this.contract.attachment || '[]').filter(file => ['https://', 'http://', '/profile/'].some(prefix => String(file.url || '').startsWith(prefix)))
      } catch (_) {
        return []
      }
    },
    differences() {
      const versions = this.contract.versions || []
      const before = versions.find(item => item.versionNo === this.compareBefore)?.snapshot || {}
      const after = versions.find(item => item.versionNo === this.compareAfter)?.snapshot || {}
      const fields = { name: '合同名称', serviceType: '服务类型', amount: '合同金额', startDate: '开始日期', endDate: '结束日期', remark: '备注', attachment: '附件', feeTerms: '收费条款' }
      const format = (key, value) =>
        key === 'feeTerms'
          ? (value || [])
              .map(
                term =>
                  `${term.title}：${money(term.amount)}；${term.chargeMode === 'rate' ? `基数 ${money(term.baseAmount)} × ${term.rate}%` : '固定金额'}；${term.trigger}；到期 ${term.dueDate}；保证 ${term.guaranteeDays} 天；${
                    term.remark || ''
                  }`
              )
              .join('； ')
          : key === 'amount'
          ? money(value)
          : String(value || '—')
      return Object.keys(fields)
        .filter(key => JSON.stringify(before[key]) !== JSON.stringify(after[key]))
        .map(key => ({ field: fields[key], before: format(key, before[key]), after: format(key, after[key]) }))
    }
  },
  async mounted() {
    await this.load()
    if (this.initialTermKey) {
      const term = this.displayTerms.find(item => item.key === this.initialTermKey)
      if (term) this.startReceipt(term)
    }
  },
  methods: {
    money,
    async load() {
      if (!normalizeContractId(this.contractId)) return this.$message.error('合同编号无效，请关闭详情并刷新列表后重试')
      this.loading = true
      try {
        const res = await getContract(this.contractId)
        this.contract = res.data
        const versions = this.contract.versions || []
        this.compareAfter = versions[0]?.versionNo
        this.compareBefore = versions[1]?.versionNo
        this.$emit('loaded', this.contract)
      } finally {
        this.loading = false
      }
    },
    openCustomer() {
      this.$router.push({ path: '/crm/customers', query: { customerId: this.contract.customerId } })
      this.$emit('close')
    },
    openRisks() {
      this.$router.push({ path: '/crm/risks', query: { customerId: this.contract.customerId, contractId: this.contractId } })
      this.$emit('close')
    },
    renew() {
      const last = this.contract.versions?.find(item => item.versionNo === this.contract.effectiveVersion)?.snapshot || this.contract
      this.openForm({
        initial: {
          customerId: this.contract.customerId,
          customerName: this.contract.customerName,
          parentContractId: this.contractId,
          name: `${this.contract.name}（续约）`,
          serviceType: last.serviceType,
          amount: last.amount,
          startDate: moment(last.endDate).add(1, 'day').format('YYYY-MM-DD'),
          endDate: moment(last.endDate).add(1, 'year').format('YYYY-MM-DD'),
          feeTerms: (last.feeTerms || []).map(term => ({ ...term, dueDate: moment(term.dueDate).add(1, 'year').format('YYYY-MM-DD') }))
        }
      })
    },
    openForm(props) {
      this.$createAntdModal({
        modalProps: { title: props.contractId ? '编辑合同草稿' : '续约合同', width: 920, maskClosable: false, footer: null },
        onOk: () => {
          this.load()
          this.$emit('changed')
        },
        content: { template: ContractForm, props }
      })
    },
    async startAction(action) {
      this.actionForm = { revision: this.contract.revision, signedDate: today() }
      this.operation = action
    },
    startReceipt(term) {
      this.actionForm = { termKey: term.key, termTitle: term.title, maxAmount: Number(term.outstandingAmount), amount: Number(term.outstandingAmount), receiptDate: today(), remark: '' }
      this.operation = 'receipts'
    },
    confirmAction(action, title, content) {
      this.$antdconfirm({
        title,
        content,
        onOk: async () => {
          await contractAction(this.contractId, action, { revision: this.contract.revision })
          this.$message.success('操作成功')
          await this.load()
          this.$emit('changed')
        }
      })
    },
    async runAction() {
      if (this.saving) return
      this.saving = true
      try {
        const operation = this.operation
        const result = await contractAction(this.contractId, this.operation, this.actionForm)
        this.operation = ''
        const successMessage = result.data?.signingPending
          ? '签署申请已提交，审批通过后生效'
          : operation === 'receipts'
            ? '回款申请草稿已创建，请到财务与回款管理-回款登记提交审批'
            : '操作成功'
        this.$message.success(successMessage)
        await this.load()
        this.$emit('changed')
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style lang="less" scoped>
.detail-head { display: flex; justify-content: space-between; gap: 20px; padding-bottom: 20px; h2 { margin: 0 0 10px; font-size: 22px; font-weight: 600; color: #17212b; } }
.detail-meta, .detail-actions, .compare-controls { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.detail-meta { color: #667085; font-size: 13px; }
.detail-actions { padding: 18px 0; }
.contract-total { text-align: right; span { display: block; color: #667085; } strong { display: block; font-size: 24px; color: #1769aa; margin-top: 4px; } }
.compare-controls { margin: 22px 0 14px; }
@media (max-width: 700px) { .detail-head { flex-direction: column; } .contract-total { text-align: left; } }
</style>
