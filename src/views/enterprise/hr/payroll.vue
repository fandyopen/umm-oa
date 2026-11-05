<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb"
    ><a-card :bordered="false"><payroll-workspace /></a-card
  ></page-header-wrapper>
</template>
<script>
import PayrollWorkspace from './PayrollWorkspace.vue'
export default { name: 'HrPayroll', components: { PayrollWorkspace } }
</script>
