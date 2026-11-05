<script>
import FinanceRecords from './modules/FinanceRecords.vue'

export default {
  name: 'FinanceAdjustments',
  extends: FinanceRecords,
  financeWorkspace: 'adjustment',
  props: { recordType: { type: String, default: 'adjustment' } }
}
</script>
