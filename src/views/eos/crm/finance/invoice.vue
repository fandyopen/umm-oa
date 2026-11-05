<script>
import FinanceRecords from './modules/FinanceRecords.vue'

export default {
  name: 'FinanceInvoices',
  extends: FinanceRecords,
  financeWorkspace: 'invoice',
  props: { recordType: { type: String, default: 'invoice' } }
}
</script>
