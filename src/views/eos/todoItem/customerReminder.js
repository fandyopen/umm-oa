import { withModuleContexts } from '/src/utils/moduleContext.js'
import { crmModules } from '/src/views/eos/crm/moduleContexts.js'
import { hrModules } from './hrReminder.js'

export const CUSTOMER_FOLLOWUP_TODO = 'crmCustomerFollowup'
export const OPPORTUNITY_FOLLOWUP_TODO = 'crmOpportunityFollowup'

export default {
  mixins: [withModuleContexts({ customer: crmModules.customer, opportunity: crmModules.opportunity, ...hrModules })],
  methods: {
    async openOpportunityReminder(row) {
      const businessId = Number(row.form?.businessId || row.businessId)
      if (!Number.isSafeInteger(businessId) || businessId <= 0) {
        this.$message.warning('待办关联商机无效，请刷新列表')
        return
      }
      if (!(await this.checkModuleAction({ module: 'opportunity', label: '查看待跟进商机' }))) return
      this.$emit('read')
      const query = { businessId: String(businessId), source: 'followupReminder' }
      if (this.$route.path === '/crm/opportunities' && this.$route.query.businessId === query.businessId) return
      await this.$router.push({ path: '/crm/opportunities', query })
    },
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
