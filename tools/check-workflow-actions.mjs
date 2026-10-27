const apiBase = process.env.API_BASE || 'http://127.0.0.1:8080'
const username = process.env.OA_USERNAME || '17630135660'
const password = process.env.OA_PASSWORD || 'admin123123'
const moduleId = Number(process.env.MODULE_ID || 341001)

async function json(url, options = {}) {
  const response = await fetch(url, options)
  const body = await response.json()
  if (body.code !== 200) {
    throw new Error(`${url}: ${body.msg || `HTTP ${response.status}`}`)
  }
  return body
}

const login = await json(`${apiBase}/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password })
})
const headers = { Authorization: `Bearer ${login.token}` }
const module = await json(`${apiBase}/system/module/${moduleId}`, { headers })
const actions = await json(`${apiBase}/system/module/${moduleId}/actions`, { headers })
const workflowActions = await json(
  `${apiBase}/system/module/${moduleId}/actions?workflowOnly=true`,
  { headers }
)
const handlers = await json(`${apiBase}/workflow/action/handlers`, { headers })

console.log(JSON.stringify({
  moduleId,
  moduleName: module.data?.moduleName,
  hasFlow: module.data?.hasFlow,
  actions: actions.data,
  workflowActions: workflowActions.data,
  handlers: handlers.data
}, null, 2))
