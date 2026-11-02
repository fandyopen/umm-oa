<template>
  <div>
    <contract-detail :module-meta="moduleContexts.contract" v-if="row.contractId" :contract-id="Number(row.contractId)" :initial-tab="todoItem.businessType === 'crmContractApproval' ? 'approvals' : 'reminders'" @loaded="contract = $event" @changed="$emit('changed')" @close="$emit('close')" />
    <customer-detail :module-meta="moduleContexts.customer" v-else-if="row.customerId" :form-id="Number(row.customerId)" initial-tab="followups" />
    <a-empty v-else description="未找到关联的客户或合同" />
    <a-card v-if="canReviewLegacy" title="历史在途合同审批" style="margin-top: 20px">
      <p>此任务创建于统一流程接入前，仅在待办中保留办结入口。新提交的合同使用统一流程审批。</p>
      <a-textarea v-model="comment" :rows="3" :max-length="2000" placeholder="填写审批意见，退回时必填" :disabled="saving || !!moduleActionBusy.reviewLegacy" />
      <div class="legacy-review-actions">
        <a-button type="primary" :loading="saving || !!moduleActionBusy.reviewLegacy" @click="reviewLegacy('approved')">通过</a-button>
        <a-button type="danger" ghost :disabled="saving" @click="reviewLegacy('rejected')">退回</a-button>
      </div>
    </a-card>
  </div>
</template>
<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { crmModules } from '/src/views/eos/crm/moduleContexts.js'
import ContractDetail from './ContractDetail.vue'
import CustomerDetail from '../../customer/modules/CustomerDetail.vue'
import { approveLegacyContract } from '/src/api/eos/crm/contracts.js'

export default {
  mixins: [
    withModuleContexts(
      { approval: crmModules.approval, contract: crmModules.contract, customer: crmModules.customer },
      {
        reviewLegacy: { module: 'approval', action: 'approve', label: '办理历史合同审批' }
      }
    )
  ],
  name: 'CrmTodoDetail',
  components: { ContractDetail, CustomerDetail },
  props: { row: { type: Object, default: () => ({}) }, todoItem: { type: Object, default: () => ({}) } },
  data() {
    return { contract: {}, comment: '', saving: false }
  },
  computed: {
    canReviewLegacy() {
      return (
        this.todoItem.businessType === 'crmContractApproval' &&
        this.todoItem.status === 'todo' &&
        this.contract.status === 'pending' &&
        !this.contract.workflow?.instanceId &&
        Number(this.contract.approvalUserId) === Number(this.$store.getters.userInfo?.userId) &&
        this.showModuleAction('approval', 'approve')
      )
    }
  },
  methods: {
    async reviewLegacy(decision) {
      if (this.saving || !this.canReviewLegacy) return
      if (decision === 'rejected' && !this.comment.trim()) return this.$message.warning('请填写退回原因')
      this.saving = true
      try {
        this.contract = (await approveLegacyContract(this.row.contractId, { revision: this.contract.revision, decision, comment: this.comment })).data
        this.$message.success('历史审批已办理')
        this.$emit('changed')
        this.$emit('close')
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.legacy-review-actions { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 16px; }
</style>
