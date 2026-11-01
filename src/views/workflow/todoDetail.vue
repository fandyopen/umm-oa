<template>
  <div>
    <s-descriptions :title="row.contractId ? '合同审批申请' : '业务变更申请'" :formItems="formItems" :formData="normalizedRow" />
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
