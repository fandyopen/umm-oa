<script>
import FinanceRecords from './modules/FinanceRecords.vue'

export default {
  name: 'FinanceCosts',
  extends: FinanceRecords,
  financeWorkspace: 'cost',
  props: { recordType: { type: String, default: 'cost' } }
}
</script>
