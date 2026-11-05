<template><payroll-workspace ref="payroll" finance-mode /></template>
<script>
import PayrollWorkspace from '/src/views/enterprise/hr/PayrollWorkspace.vue'
export default {
  name: 'FinancePayroll',
  components: { PayrollWorkspace },
  financeWorkspace: 'payroll',
  methods: {
    exportRows() {
      return this.$refs.payroll.exportRows()
    }
  }
}
</script>
