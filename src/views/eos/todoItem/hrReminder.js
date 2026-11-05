export const HR_REMINDERS = ['hrPayroll', 'hrPayslip', 'hrHandover']
export const hrModules = {
  hrFinance: { component: 'eos/crm/finance/payroll', label: '工资发放' },
  hrPayroll: { component: 'enterprise/hr/payroll', label: '工资管理' },
  hrSelf: { component: 'enterprise/hr/self', label: '我的考勤与工资' },
  hrChanges: { component: 'enterprise/hr/changes', label: '人事异动' }
}
export default {
  methods: {
    isHrReminder(row) {
      return HR_REMINDERS.includes(row.businessType)
    },
    async openHrReminder(row) {
      await this.ensureModuleContexts(['hrFinance', 'hrPayroll', 'hrSelf', 'hrChanges'])
      let key, path, query
      if (row.businessType === 'hrPayroll') {
        key = this.canViewModule('hrFinance') ? 'hrFinance' : 'hrPayroll'
        path = key === 'hrFinance' ? '/crm/finance/payroll' : '/hr/payroll'
        query = { payrollId: String(row.businessId) }
      } else if (row.businessType === 'hrPayslip') {
        key = 'hrSelf'
        path = '/hr/self'
        query = { tab: 'salary' }
      } else {
        key = 'hrChanges'
        path = '/hr/changes'
        query = { handoverUserId: String(row.businessId) }
      }
      if (!(await this.checkModuleAction({ module: key, label: '进入人事待办' }))) return
      this.$emit('read')
      await this.$router.push({ path, query })
    }
  }
}
