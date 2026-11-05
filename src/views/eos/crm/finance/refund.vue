<script>
import FinanceRecords from './modules/FinanceRecords.vue'

export default {
  name: 'FinanceRefunds',
  extends: FinanceRecords,
  financeWorkspace: 'refund',
  props: { recordType: { type: String, default: 'refund' } }
}
</script>
