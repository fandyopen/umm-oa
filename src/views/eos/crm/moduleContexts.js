// Stable business selectors; authorization IDs and permission codes remain server-owned.
export const crmModules = {
  commission: { component: 'eos/crm/finance/commission', label: '提成核算' },
  customer: { component: 'eos/crm/customer/index', label: '客户档案' },
  opportunity: { component: 'eos/business/index', label: '商机管理' },
  contract: { component: 'eos/crm/contract/index', label: '合同列表' },
  approval: { component: 'eos/crm/contract/approvals', label: '合同审批' },
  fee: { component: 'eos/crm/contract/fees', label: '合同回款' },
  version: { component: 'eos/crm/contract/versions', label: '合同版本' },
  reminder: { component: 'eos/crm/contract/reminders', label: '合同提醒' },
  risk: { component: 'eos/crm/risk/index', label: '客户风险' }
}
