const sections = {
  'route-map': 'docs/pages/RouteMap',
  'api-docs': 'docs/pages/ApiDocs',
  'module-docs': 'docs/pages/ModuleDocs',
  'commission-rules': 'docs/pages/CommissionRules',
  'recruitment-rules': 'docs/pages/RecruitmentRules',
  'customer-contract-rules': 'docs/pages/CustomerContractRules'
}

// Resolve from the authorized menu so renamed paths and revoked pages stay effective.
export function docsLocation(routes, section) {
  const pages = []
  const visit = (nodes, parentPath = '') => (nodes || []).forEach(node => {
    const path = node.path?.startsWith('/') ? node.path : `${parentPath}/${node.path || ''}`
    const component = typeof node.component === 'string' ? node.component : node.meta?.component
    if (Object.values(sections).includes(component)) pages.push({ path, component, hidden: node.hidden })
    visit(node.children, path === '/' ? '' : path)
  })
  visit(routes)
  const component = sections[section]
  const page = component ? pages.find(page => page.component === component) : pages.find(page => !page.hidden)
  return page ? { path: page.path } : null
}
