<script>
import FinanceRecords from './modules/FinanceRecords.vue'

export default {
  name: 'FinanceReceipts',
  extends: FinanceRecords,
  financeWorkspace: 'receipt',
  props: { recordType: { type: String, default: 'receipt' } }
}
</script>
