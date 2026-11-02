<template>
  <div>
    <s-descriptions :title="row.entityType ? '招聘业务审批申请' : row.contractId ? '合同审批申请' : '业务变更申请'" :formItems="formItems" :formData="normalizedRow" />
    <template v-if="row.entityType && row.snapshot">
      <a-alert type="info" show-icon message="此处展示提交时冻结的招聘资料和变更内容。全部审批节点通过后执行，退回不改变正式数据。" style="margin: 16px 0" />
      <a-table :columns="recruitColumns" :data-source="recruitSnapshot" row-key="key" :pagination="false" size="small" />
    </template>
    <template v-if="row.contractId">
      <a-alert type="info" show-icon message="审批内容为本次提交时冻结的合同版本；完成当前节点后，按已发布流程流转。" style="margin: 16px 0" />
      <a-table :columns="feeColumns" :data-source="row.feeTerms || []" row-key="key" :pagination="false" size="small" />
      <a-button type="link" icon="file-text" @click="openContract">查看关联合同与流程记录</a-button>
    </template>
  </div>
</template>

<script>
import SDescriptions from '/src/components/Descriptions/index.jsx'
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

export default {
  name: 'WorkflowActionTodoDetail',
  components: { SDescriptions },
  data() {
    return { feeColumns: [{ title: '收费名称', dataIndex: 'title' }, { title: '触发条件', dataIndex: 'trigger' }, { title: '收费金额（元）', dataIndex: 'amount' }, { title: '收费到期', dataIndex: 'dueDate' }, { title: '保证期（天）', dataIndex: 'guaranteeDays' }] }
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
      this.$createAntdDrawer({ drawerProps: { title: '合同详情', width: '86%' }, content: { template: ContractDetail, props: { contractId, initialTab: 'approvals' }, on: { changed: () => this.$emit('changed') } } })
    }
  },
  computed: {
    recruitColumns() {
      return [{ title: '项目', dataIndex: 'name' }, { title: '申请时原资料', dataIndex: 'before' }, { title: '申请后内容', dataIndex: 'after' }]
    },
    recruitSnapshot() {
      const fields = { title: '职位名称', name: '候选人', customerName: '所属客户', ownerName: '负责人', city: '地点', industry: '行业', experience: '经验要求', skills: '技能', salaryMin: '月薪下限', salaryMax: '月薪上限', headcount: '招聘人数', requirements: '岗位要求', feeTerms: '收费条件', targetDate: '目标日期', contractId: '关联合同编号', experienceYears: '经验年限', status: '当前状态' }
      const before = this.row.snapshot || {}
      const changes = this.row.fields || {}
      return Object.entries(fields).filter(([key]) => before[key] !== undefined || changes[key] !== undefined).map(([key, name]) => ({ key, name, before: before[key] === null ? '—' : before[key], after: changes[key] === undefined ? '保持原值' : changes[key] === null ? '—' : changes[key] }))
    },
    normalizedRow() {
      const result = {}
      Object.keys(this.row || {}).forEach(key => {
        const value = this.row[key]
        result[key] = Array.isArray(value) ? value.join('、') : value
      })
      return result
    },
    formItems() {
      return Object.keys(this.row || {})
        .filter(key => labels[key])
        .map(key => ({ label: labels[key], prop: key }))
    }
  }
}
</script>
