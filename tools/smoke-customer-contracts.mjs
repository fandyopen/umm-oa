import fs from 'node:fs/promises'
import path from 'node:path'

if (process.env.CONTRACT_SMOKE_ISOLATED !== '1') throw new Error('Run this smoke test against an isolated in-memory backend with CONTRACT_SMOKE_ISOLATED=1.')
const apiBase = process.env.CONTRACT_SMOKE_API || 'http://127.0.0.1:8081'
const webBase = process.env.CONTRACT_SMOKE_WEB || 'http://127.0.0.1:3001'
const cdpPort = process.env.CONTRACT_SMOKE_CDP || '9232'
const outputDir = process.env.CONTRACT_SMOKE_OUTPUT
const errors = []
const apiErrors = []
const checks = []
const day = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const moveDay = offset => { const value = new Date(day + 'T12:00:00+08:00'); value.setUTCDate(value.getUTCDate() + offset); return value.toISOString().slice(0, 10) }

async function api(url, token, data, method = data ? 'POST' : 'GET') {
  const response = await fetch(apiBase + url, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(data ? { body: JSON.stringify(data) } : {}) })
  const result = await response.json()
  if (result.code !== 200) throw new Error(`${method} ${url}: ${result.code} ${result.msg}`)
  return result
}
const admin = (await api('/login', null, { username: '13610316814', password: 'admin123123' })).token
await api('/app/user/addModule', admin, { roleId: 3, tenantModuleIds: [340341, 341202, 'action:341202:approve'] })
const reviewer = (await api('/login', null, { username: '17673605591', password: 'admin123123' })).token
await api('/activiti/mongo/setProcess', admin, {
  tenantAppId: 1, menuId: 341201, menuName: '合同列表', actionCode: 'submit', approvalEnabled: true,
  processData: {
    nodes: [{ id: 'start', type: 'start', name: '发起人' }, { id: 'review', type: 'approval', name: '合同审核', approver: '17673605591' }, { id: 'end', type: 'end', name: '结束' }],
    edges: [{ source: 'start', target: 'review' }, { source: 'review', target: 'end' }]
  }
})
const customerId = (await api('/project/merchant', admin, { name: `合同页面联调客户-${Date.now()}`, industry: '软件与信息服务', ownerUserId: 1 })).data
const version = await fetch(`http://127.0.0.1:${cdpPort}/json/version`).then(response => response.json())
const socket = new WebSocket(version.webSocketDebuggerUrl)
const pending = new Map()
let nextId = 0
socket.addEventListener('message', event => {
  const message = JSON.parse(event.data)
  if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails.exception?.description || message.params.exceptionDetails.text)
  if (message.method === 'Network.responseReceived') {
    const response = message.params.response
    if (response.url.includes('/api-qs/project/crm/') && response.mimeType.includes('json')) {
      setTimeout(() => send('Network.getResponseBody', { requestId: message.params.requestId }, message.sessionId).then(body => {
        try { const result = JSON.parse(body.body); if (result.code !== 200) apiErrors.push({ url: new URL(response.url).pathname, code: result.code, message: result.msg }) } catch {}
      }).catch(() => {}), 30)
    }
  }
  if (message.id && pending.has(message.id)) { const item = pending.get(message.id); pending.delete(message.id); message.error ? item.reject(new Error(message.error.message)) : item.resolve(message.result) }
})
await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }) })
function send(method, params = {}, sessionId) { const id = ++nextId; socket.send(JSON.stringify({ id, method, params, sessionId })); return new Promise((resolve, reject) => pending.set(id, { resolve, reject })) }
const target = await send('Target.createTarget', { url: 'about:blank' })
const { sessionId } = await send('Target.attachToTarget', { targetId: target.targetId, flatten: true })
await send('Page.enable', {}, sessionId)
await send('Runtime.enable', {}, sessionId)
await send('Network.enable', {}, sessionId)
await send('Emulation.setDeviceMetricsOverride', { width: 1512, height: 1040, deviceScaleFactor: 1, mobile: false }, sessionId)
async function evaluate(expression) { const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true }, sessionId); if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text); return result.result.value }
async function waitFor(expression, label, timeout = 60000) {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) { if (await evaluate(expression)) return; await new Promise(resolve => setTimeout(resolve, 250)) }
  throw new Error(`Timed out: ${label}. URL: ${await evaluate('location.href')}. Errors: ${JSON.stringify({ errors, apiErrors })}. Page: ${await evaluate('document.body.innerText.slice(0, 1600)')}`)
}
const findVm = name => `(() => { const visited = new Set(); function find(vm) { if (!vm || visited.has(vm)) return null; visited.add(vm); if (vm.$options?.name === ${JSON.stringify(name)}) return vm; for (const child of vm.$children || []) { const result = find(child); if (result) return result } return null } for (const node of document.querySelectorAll('body *')) { if (!node.getClientRects().length) continue; const result = find(node.__vue__); if (result) return result } return null })()`
async function clickText(text) { const clicked = await evaluate(`(() => { const item = [...document.querySelectorAll('button, a')].find(el => el.getClientRects().length && el.textContent.trim() === ${JSON.stringify(text)}); if (!item) return false; item.click(); return true })()`); if (!clicked) throw new Error('Missing action: ' + text) }
async function confirmModal() { await evaluate(`(() => { const buttons = [...document.querySelectorAll('.ant-modal-footer .ant-btn-primary')].filter(el => el.getClientRects().length); if (!buttons.length) throw new Error('Modal confirm is not visible'); buttons.at(-1).click() })()`) }
async function screenshot(name) { if (!outputDir) return; await new Promise(resolve => setTimeout(resolve, 400)); await fs.mkdir(outputDir, { recursive: true }); const result = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }, sessionId); await fs.writeFile(path.join(outputDir, `${name}.png`), Buffer.from(result.data, 'base64')) }
async function navigate(route, title) { await send('Page.navigate', { url: `${webBase}/qs${route}` }, sessionId); await waitFor(`document.querySelector('.workspace-head h2')?.textContent === ${JSON.stringify(title)}`, title); await waitFor(`!document.querySelector('.ant-spin-spinning')`, 'page data loaded'); checks.push(title); await screenshot(`contract-${route.split('/').at(-1)}`) }
async function navigateLegacy(route, targetPath, tab) {
  await send('Page.navigate', { url: `${webBase}/qs/crm/contracts/${route}` }, sessionId)
  await waitFor(`location.pathname === ${JSON.stringify('/qs' + targetPath)}`, 'legacy route redirected')
  if (tab) await waitFor(`${findVm('CrmContractDetail')}?.activeTab === ${JSON.stringify(tab)} && ${findVm('CrmContractDetail')}?.loading === false`, 'legacy detail context')
  checks.push(`旧入口 ${route.split('?')[0]} → ${targetPath}${tab ? ' / ' + tab : ''}`)
}

try {
  await send('Storage.clearDataForOrigin', { origin: webBase, storageTypes: 'all' }, sessionId)
  await send('Page.navigate', { url: `${webBase}/qs/user/login` }, sessionId)
  await waitFor(`Boolean(document.querySelector('.loginForm'))`, 'login form')
  await evaluate(`(() => { const values = ['13610316814', 'admin123123', '1234']; document.querySelectorAll('.loginForm input').forEach((input, index) => { input.value = values[index] || ''; input.dispatchEvent(new Event('input', { bubbles: true })) }); document.querySelector('.confirmBtn').click() })()`)
  await waitFor(`!location.pathname.endsWith('/user/login')`, 'authenticated navigation')
  await navigate('/crm/contracts/list', '合同列表')
  await waitFor(`document.querySelector('.first-level-menu')?.innerText.includes('客户与合同管理')`, 'unified top-level module')
  await waitFor(`document.querySelector('.ant-layout-sider')?.innerText.includes('客户档案') && document.querySelector('.ant-layout-sider')?.innerText.includes('风险提醒')`, 'customer and risk sibling navigation')
  await clickText('新建合同')
  await waitFor(`Boolean(${findVm('CrmContractForm')})`, 'contract editor')
  await waitFor(`${findVm('CrmContractForm')}?.loading === false`, 'contract editor loaded')
  await evaluate(`(() => { const vm = ${findVm('CrmContractForm')}; vm.form = { ...vm.form, customerId: ${customerId}, name: '招聘服务联调合同', serviceType: '猎头招聘', amount: 1000, startDate: ${JSON.stringify(moveDay(-30))}, endDate: ${JSON.stringify(moveDay(15))}, feeTerms: [{ key: 'smoke-fee', title: '招聘服务费', chargeMode: 'fixed', amount: 1000, trigger: '候选人入职', dueDate: ${JSON.stringify(moveDay(-1))}, guaranteeDays: 90, remark: '保证期内免费补荐一次' }] }; return true })()`)
  await screenshot('contract-draft-form')
  await clickText('保存草稿')
  await waitFor(`Boolean(${findVm('CrmContractDetail')})`, 'saved contract detail')
  let record = (await api('/project/crm/contracts?keyword=' + encodeURIComponent('招聘服务联调合同'), admin)).rows[0]
  if (!record) throw new Error('Saved draft missing from contract list')
  const contractId = record.id
  await clickText('提交审批')
  await waitFor(`document.querySelector('.ant-modal-title')?.textContent === '提交合同审批'`, 'submit modal')
  await confirmModal()
  await waitFor(`${findVm('CrmContractDetail')}?.contract.status === 'pending'`, 'submitted contract')
  record = (await api(`/project/crm/contracts/${contractId}`, admin)).data
  await api(`/project/crm/contracts/${contractId}/approve`, reviewer, { revision: record.revision, decision: 'approved', comment: '核对收费条款与保证期' })
  await send('Page.navigate', { url: `${webBase}/qs/crm/contracts/list?contractId=${contractId}` }, sessionId)
  await waitFor(`${findVm('CrmContractDetail')}?.contract.status === 'approved'`, 'approved detail')
  await clickText('登记签署并生效')
  await confirmModal()
  await waitFor(`${findVm('CrmContractDetail')}?.contract.status === 'active'`, 'signed contract')
  await screenshot('contract-active-detail')
  await navigate('/crm/contracts/list', '合同列表')
  await waitFor(`!['合同审批', '收费条款', '合同版本', '合同提醒'].some(title => document.querySelector('.ant-layout-sider')?.innerText.includes(title))`, 'duplicate menus removed')
  for (const [route, tab] of [['fees', 'terms'], ['versions', 'versions']]) {
    await navigateLegacy(`${route}?customerId=${customerId}&contractId=${contractId}`, '/crm/contracts/list', tab)
    await screenshot(`contract-detail-${tab}`)
  }
  await navigateLegacy(`reminders?customerId=${customerId}&contractId=${contractId}`, '/crm/risks')
  await waitFor(`${findVm('CrmRisks')}?.filteredRisks.length === 2`, 'customer and contract risk filters retained')
  await clickText('记录处理')
  await evaluate(`(() => { const vm = ${findVm('CrmRisks')}; vm.handleContent = '已联系客户确认付款与续约计划' })()`)
  await confirmModal()
  await waitFor(`${findVm('CrmRisks')}?.handleTarget === null && ${findVm('CrmRisks')}?.saving === false`, 'risk handling saved')
  const handled = (await api(`/project/crm/contracts/${contractId}`, admin)).data
  if (handled.reminderLogs.length !== 1 || handled.reminders.length !== 2) throw new Error('Handling must preserve history without dismissing active risk')
  await screenshot('contract-risk-handling')
  await navigateLegacy('approvals', '/todoItem/todo')
  await waitFor(`${findVm('Todo')}?.businessCategory === 'contract' && ${findVm('Todo')}?.loading === false`, 'unified contract approval filter')
  await screenshot('contract-unified-todo')
  await navigateLegacy(`fees?customerId=${customerId}&contractId=${contractId}`, '/crm/contracts/list', 'terms')
  await clickText('登记回款')
  await waitFor(`${findVm('CrmContractDetail')}?.operation === 'receipts'`, 'receipt editor')
  await confirmModal()
  await waitFor(`${findVm('CrmContractDetail')}?.contract.receivedAmount === 1000`, 'receipt reflected in contract')
  const customer = (await api(`/project/merchant/${customerId}`, admin)).data
  if (customer.contractAmount !== 1000 || customer.receivedAmount !== 1000) throw new Error('Contract collection did not update customer totals')
  const risks = await api(`/project/crm/risk/list?customerId=${customerId}`, admin)
  if (risks.rows.some(item => item.contractId === contractId && item.riskType === 'payment_overdue')) throw new Error('Paid fee still appears as overdue')
  await send('Page.navigate', { url: `${webBase}/qs/crm/customers?customerId=${customerId}` }, sessionId)
  await waitFor(`Boolean(${findVm('CustomerDetail')})`, 'linked customer detail')
  await evaluate(`(() => { const vm = ${findVm('CustomerDetail')}; vm.activeTab = 'contracts' })()`)
  await waitFor(`${findVm('CustomerDetail')}?.loading === false && [...document.querySelectorAll('.ant-drawer .ant-tabs-tabpane-active')].some(el => el.innerText.includes('招聘服务联调合同'))`, 'contract shown under customer')
  await screenshot('customer-contract-closure')
  await navigate('/crm/contracts/list', '合同列表')
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true }, sessionId)
  await screenshot('contract-list-mobile')
  await new Promise(resolve => setTimeout(resolve, 300))
  if (errors.length || apiErrors.length) throw new Error(JSON.stringify({ errors, apiErrors }))
  console.log(JSON.stringify({ passed: true, pages: [...new Set(checks)], contractId, customerTotals: { contractAmount: customer.contractAmount, receivedAmount: customer.receivedAmount }, pageErrors: errors, apiErrors, outputDir }, null, 2))
} finally {
  await send('Target.closeTarget', { targetId: target.targetId })
  socket.close()
}
