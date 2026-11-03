import { withModuleContexts } from '/src/utils/moduleContext.js'
import { crmModules } from '/src/views/eos/crm/moduleContexts.js'

export const CUSTOMER_FOLLOWUP_TODO = 'crmCustomerFollowup'

export default {
  mixins: [withModuleContexts({ customer: crmModules.customer })],
  methods: {
    async openCustomerReminder(row) {
      const customerId = Number(row.form?.customerId || row.businessId)
      if (!Number.isSafeInteger(customerId) || customerId <= 0) {
        this.$message.warning('待办关联客户无效，请刷新列表')
        return
      }
      if (!(await this.checkModuleAction({ module: 'customer', label: '查看待跟进客户' }))) return
      this.$emit('read')
      const query = { customerId: String(customerId), source: 'followupReminder' }
      if (this.$route.path === '/crm/customers' && this.$route.query.customerId === query.customerId) return
      await this.$router.push({ path: '/crm/customers', query })
    }
  }
}
