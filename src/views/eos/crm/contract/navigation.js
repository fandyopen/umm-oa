export const contractDetailTabs = ['overview', 'terms', 'approvals', 'versions', 'reminders']

// Keep bookmarked links usable while moving each task to its business entry.
export function contractEntryTarget(mode, query = {}) {
  if (mode === 'approvals') return { path: '/todoItem/todo', query: { ...query, businessCategory: 'contract' } }
  if (mode === 'reminders') return { path: '/crm/risks', query: { ...query } }
  return { path: '/crm/contracts/list', query: { ...query, tab: mode === 'versions' ? 'versions' : 'terms' } }
}
