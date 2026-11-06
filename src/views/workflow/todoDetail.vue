<template>
  <div>
    <hr-approval-detail v-if="row.payrollId || row.salaryId || row.eventId" :row="row" />
    <s-descriptions :title="row.financeRecord ? '财务审批申请' : row.entityType ? '招聘业务审批申请' : row.contractId ? '合同审批申请' : '业务变更申请'" :formItems="formItems" :formData="normalizedRow" />
    <template v-if="row.financeRecord">
      <a-alert type="info" show-icon message="此处展示提交时冻结的财务单据。审批通过后，开票和退款仍需登记实际办理结果；回款、核销、冲销和成本按单据确认入账。" class="tw-my-4 tw-mx-0" />
      <div v-for="(file, index) in financeFiles" :key="index">
        <a :href="file.fileName" target="_blank" rel="noopener noreferrer">{{ file.name || '查看财务凭证' }}</a>
      </div>
      <a-button v-if="row.contractId" type="link" icon="file-text" @click="openContract">查看关联合同</a-button>
    </template>
    <template v-if="row.entityType && row.snapshot">
      <a-alert type="info" show-icon message="此处展示提交时冻结的招聘资料和变更内容。全部审批节点通过后执行，退回不改变正式数据。" class="tw-my-4 tw-mx-0" />
      <a-table :columns="recruitColumns" :data-source="recruitSnapshot" row-key="key" :pagination="false" size="small" />
    </template>
    <template v-if="row.contractId && !row.financeRecord">
      <a-alert type="info" show-icon message="审批内容为本次提交时冻结的合同版本；完成当前节点后，按已发布流程流转。" class="tw-my-4 tw-mx-0" />
      <a-table :columns="feeColumns" :data-source="row.feeTerms || []" row-key="key" :pagination="false" size="small" />
      <a-button type="link" icon="file-text" @click="openContract">查看关联合同与流程记录</a-button>
    </template>
  </div>
</template>

<script>
import SDescriptions from '/src/components/Descriptions/index.jsx'
import HrApprovalDetail from '/src/components/HrApprovalDetail/index.vue'
import ContractDetail from '/src/views/eos/crm/contract/modules/ContractDetail.vue'
import { normalizeContractId } from '/src/api/eos/crm/contracts.js'

const labels = {
  title: '申请事项',
  actionName: '审批动作',
  entityId: '招聘业务编号',
  entityName: '职位 / 候选人',
  reason: '申请原因',
  targetId: '目标档案编号',
  targetName: '保留的候选人档案',
  ownerUserName: '转交后的负责人',
  contractId: '合同编号',
  code: '合同业务编号',
  name: '合同名称',
  customerName: '客户名称',
  versionNo: '提交版本',
  signedDate: '实际签署日期',
  serviceType: '服务类型',
  amount: '合同金额（元）',
  startDate: '开始日期',
  endDate: '结束日期',
  nickName: '成员姓名',
  phonenumber: '手机号',
  email: '邮箱',
  deptId: '目标部门',
  userIds: '成员ID',
  remark: '备注'
}
const financeLabels = {
  title: '申请事项',
  recordId: '财务单据编号',
  recordType: '单据类型',
  customerName: '客户名称',
  contractName: '关联合同',
  receivableTitle: '应收项目',
  termKey: '收费节点',
  amount: '本次金额（元）',
  businessDate: '业务日期',
  invoiceTitle: '发票抬头',
  taxNo: '税号',
  channel: '收付款渠道',
  referenceNo: '业务流水号',
  originalReceiptId: '原合同到账编号',
  originalRecordId: '原财务到账编号',
  category: '成本类别',
  payee: '收款对象',
  reason: '办理原因',
  remark: '备注'
}
const financeTypes = { invoice: '开票申请', receipt: '回款确认', refund: '退款申请', writeoff: '应收核销', reversal: '到账冲销', cost: '直接成本' }

export default {
  name: 'WorkflowActionTodoDetail',
  components: { SDescriptions, HrApprovalDetail },
  data() {
    return {
      feeColumns: [
        { title: '收费名称', dataIndex: 'title' },
        { title: '触发条件', dataIndex: 'trigger' },
        { title: '收费金额（元）', dataIndex: 'amount' },
        { title: '收费到期', dataIndex: 'dueDate' },
        { title: '保证期（天）', dataIndex: 'guaranteeDays' }
      ]
    }
  },
  props: {
    row: {
      type: Object,
      default: () => ({})
    }
  },
  methods: {
    openContract() {
      const contractId = normalizeContractId(this.row.contractId)
      if (!contractId) return this.$message.warning('合同编号无效，请刷新待办后重试')
      this.$createAntdDrawer({
        drawerProps: { title: '合同详情', width: '86%' },
        content: { template: ContractDetail, props: { contractId, initialTab: this.row.financeRecord ? 'fees' : 'approvals' }, on: { changed: () => this.$emit('changed') } }
      })
    }
  },
  computed: {
    financeFiles() {
      try {
        return JSON.parse(this.row.attachment || '[]').filter(file => typeof file.fileName === 'string' && file.fileName.startsWith('/profile/') && !file.fileName.includes('..'))
      } catch (error) {
        return []
      }
    },
    recruitColumns() {
      return [
        { title: '项目', dataIndex: 'name' },
        { title: '申请时原资料', dataIndex: 'before' },
        { title: '申请后内容', dataIndex: 'after' }
      ]
    },
    recruitSnapshot() {
      const fields = {
        title: '职位名称',
        name: '候选人',
        customerName: '所属客户',
        ownerName: '负责人',
        city: '地点',
        industry: '行业',
        experience: '经验要求',
        skills: '技能',
        salaryMin: '月薪下限',
        salaryMax: '月薪上限',
        headcount: '招聘人数',
        requirements: '岗位要求',
        feeTerms: '收费条件',
        targetDate: '目标日期',
        contractId: '关联合同编号',
        experienceYears: '经验年限',
        status: '当前状态'
      }
      const before = this.row.snapshot || {}
      const changes = this.row.fields || {}
      return Object.entries(fields)
        .filter(([key]) => before[key] !== undefined || changes[key] !== undefined)
        .map(([key, name]) => ({ key, name, before: before[key] === null ? '—' : before[key], after: changes[key] === undefined ? '保持原值' : changes[key] === null ? '—' : changes[key] }))
    },
    normalizedRow() {
      const result = {}
      Object.keys(this.row || {}).forEach(key => {
        const value = this.row[key]
        result[key] = Array.isArray(value) ? value.join('、') : value
      })
      if (this.row.financeRecord) result.recordType = financeTypes[this.row.recordType] || this.row.recordType
      return result
    },
    formItems() {
      const fields = this.row.financeRecord ? financeLabels : labels
      return Object.keys(this.row || {})
        .filter(key => fields[key])
        .map(key => ({ label: fields[key], prop: key }))
    }
  }
}
</script>
