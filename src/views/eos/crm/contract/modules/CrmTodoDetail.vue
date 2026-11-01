<template>
  <contract-detail v-if="row.contractId" :contract-id="Number(row.contractId)" :initial-tab="todoItem.businessType === 'crmContractApproval' ? 'approvals' : 'reminders'" @changed="$emit('changed')" @close="$emit('close')" />
  <customer-detail v-else-if="row.customerId" :form-id="Number(row.customerId)" initial-tab="followups" />
  <a-empty v-else description="未找到关联的客户或合同" />
</template>
<script>
import ContractDetail from './ContractDetail.vue'
import CustomerDetail from '../../customer/modules/CustomerDetail.vue'
export default { name: 'CrmTodoDetail', components: { ContractDetail, CustomerDetail }, props: { row: { type: Object, default: () => ({}) }, todoItem: { type: Object, default: () => ({}) } } }
</script>
