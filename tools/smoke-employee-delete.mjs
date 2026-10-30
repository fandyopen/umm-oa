// Uses a dedicated headless browser and clearly named test employees only.
const api = 'http://127.0.0.1:8080'
const web = 'http://127.0.0.1:3000'
const cdpPort = process.env.CDP_PORT || '9232'
const login = async username => {
  const result = await fetch(api + '/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: process.env.LOGIN_PASSWORD || 'admin123123' })
  }).then(response => response.json())
  if (!result.token) throw new Error('Login failed for test account: ' + result.code)
  return result.token
}
const token = await login('13610316814')
const headers = { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }
const version = await fetch('http://127.0.0.1:' + cdpPort + '/json/version').then(response => response.json())
const socket = new WebSocket(version.webSocketDebuggerUrl)
let nextId = 0
const pending = new Map()
const runtimeErrors = []
socket.addEventListener('message', event => {
  const message = JSON.parse(event.data)
  if (message.method === 'Runtime.exceptionThrown') runtimeErrors.push(message.params.exceptionDetails.text)
  if (pending.has(message.id)) {
    const { resolve, reject } = pending.get(message.id)
    pending.delete(message.id)
    message.error ? reject(new Error(message.error.message)) : resolve(message.result)
  }
})
await new Promise((resolve, reject) => {
  socket.addEventListener('open', resolve, { once: true })
  socket.addEventListener('error', reject, { once: true })
})
function send(method, params = {}, sessionId) {
  const id = ++nextId
  socket.send(JSON.stringify({ id, method, params, sessionId }))
  return new Promise((resolve, reject) => pending.set(id, { resolve, reject }))
}
const created = await send('Target.createTarget', { url: 'about:blank' })
const { sessionId } = await send('Target.attachToTarget', { targetId: created.targetId, flatten: true })
await send('Page.enable', {}, sessionId)
await send('Runtime.enable', {}, sessionId)
await send('Network.enable', {}, sessionId)
await send('Network.setCacheDisabled', { cacheDisabled: true }, sessionId)
await send('Page.addScriptToEvaluateOnNewDocument', {
  source: "localStorage.setItem('access_token', " + JSON.stringify(JSON.stringify(token)) + ')'
}, sessionId)
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true }, sessionId)
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text)
  return result.result.value
}
async function until(expression, label) {
  for (let i = 0; i < 90; i++) {
    if (await evaluate(expression)) return
    await pause(500)
  }
  throw new Error('Timed out: ' + label + ' ' + await evaluate('document.body.innerText.slice(0, 600)'))
}
const remaining = new Set()
const checks = []
try {
  for (const [index, path] of ['/hr/members', '/system/zzjg', '/hr/members'].entries()) {
    const suffix = String(Date.now()).slice(-7)
    const phone = '199' + String(index) + suffix
    const name = '自动删除测试' + suffix
    const employee = await fetch(api + '/system/user', {
      method: 'POST', headers, body: JSON.stringify({ phonenumber: phone, nickName: name, deptId: 329, remark: 'Automated employee deletion smoke test' })
    }).then(response => response.json())
    if (employee.code !== 200 || !employee.data) throw new Error('Test employee creation failed: ' + employee.code)
    const id = employee.data
    remaining.add(id)
    const departed = index === 2
    if (departed) {
      const result = await fetch(api + '/system/user/dismiss?userIds=' + id, { headers }).then(response => response.json())
      if (result.code !== 200) throw new Error('Test employee departure failed')
    }
    await send('Page.navigate', { url: web + '/qs' + path }, sessionId)
    if (departed) {
      await until("Boolean(document.querySelector('.hr-member-sidebar'))", 'HR sidebar')
      await evaluate("Array.from(document.querySelectorAll('.hr-member-sidebar .ant-menu-item')).find(e=>e.textContent.includes('离职成员')).click()")
    }
    await until("Array.from(document.querySelectorAll('tr')).some(r => r.innerText.includes(" + JSON.stringify(name) + '))', 'test employee rendered in ' + path)
    const bulk = index === 1
    const clicked = await evaluate(bulk
      ? "(() => {const row=Array.from(document.querySelectorAll('tr')).find(r=>r.dataset.rowKey===" + JSON.stringify(String(id)) + ");row.querySelector('input[type=checkbox]').click();return true})()"
      : "(() => { const rows=Array.from(document.querySelectorAll('tr')).filter(r=>r.dataset.rowKey===" + JSON.stringify(String(id)) + "); const button=rows.flatMap(row=>Array.from(row.querySelectorAll('button'))).find(b=>b.textContent.replace(/\\s/g,'')==='删除'); if(!button)return false;button.click();return true })()")
    if (!clicked) throw new Error('Missing row delete action in ' + path)
    if (bulk) {
      await until("Array.from(document.querySelectorAll('.member-btn-pane button')).some(b=>b.textContent.includes('删除员工')&&!b.disabled)", 'bulk delete enabled')
      await evaluate("Array.from(document.querySelectorAll('.member-btn-pane button')).find(b=>b.textContent.includes('删除员工')).click()")
    }
    await until("Boolean(document.querySelector('.ant-modal-confirm'))", 'delete confirmation')
    const drawerOpened = await evaluate("Boolean(document.querySelector('.ant-drawer-open'))")
    if (drawerOpened) throw new Error('Delete click incorrectly opened employee details')
    await evaluate("Array.from(document.querySelectorAll('.ant-modal-confirm button')).find(b=>b.innerText.replace(/\\s/g,'')==='确认删除').click()")
    await until("!Array.from(document.querySelectorAll('tr')).some(r=>r.innerText.includes(" + JSON.stringify(name) + ")) && !document.querySelector('.ant-modal-confirm')", 'deleted employee removed from list')
    for (const list of ['list', 'dismiss_list']) {
      const result = await fetch(api + '/system/user/' + list + '?nickName=' + encodeURIComponent(name), { headers }).then(response => response.json())
      if (result.code !== 200 || result.total !== 0) throw new Error('Deleted employee remains in ' + list)
    }
    remaining.delete(id)
    checks.push({ path, departed, deletion: bulk ? 'selection' : 'row', confirmation: true, listRefresh: true, absentFromBothLists: true })
  }
  const hrToken = await login('17600000001')
  await send('Page.addScriptToEvaluateOnNewDocument', {
    source: "localStorage.setItem('access_token', " + JSON.stringify(JSON.stringify(hrToken)) + ')'
  }, sessionId)
  await send('Page.navigate', { url: web + '/qs/hr/members' }, sessionId)
  await until("document.querySelectorAll('.member-table tr').length > 1", 'ordinary HR manager list')
  const ordinaryDeleteVisible = await evaluate("Array.from(document.querySelectorAll('.member-btn-pane button, .member-table button')).some(b => ['删除员工','删除'].includes(b.innerText.replace(/\\s/g,'')))")
  if (ordinaryDeleteVisible) throw new Error('Ordinary HR manager can see super-admin deletion controls')
  checks.push({ ordinaryHrDeleteHidden: true })
  console.log(JSON.stringify({ checks, runtimeErrors }, null, 2))
  if (runtimeErrors.length) throw new Error('Browser runtime exceptions occurred')
} finally {
  for (const id of remaining) {
    await fetch(api + '/system/user/' + id, { method: 'DELETE', headers }).catch(() => {})
  }
  await send('Target.closeTarget', { targetId: created.targetId }).catch(() => {})
  socket.close()
}
