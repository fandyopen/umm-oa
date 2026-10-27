const cdpPort = process.env.CDP_PORT || '9231'
const loginUrl = process.env.LOGIN_URL || 'http://localhost:3000/qs/user/login'
const username = process.env.LOGIN_USERNAME || '17630135660'
const password = process.env.LOGIN_PASSWORD || 'admin123123'
const afterLoginPath = process.env.AFTER_LOGIN_PATH || ''

const version = await fetch(`http://127.0.0.1:${cdpPort}/json/version`).then(response => response.json())
const socket = new WebSocket(version.webSocketDebuggerUrl)
let nextId = 0
const pending = new Map()
const exceptions = []

function send(method, params = {}, sessionId) {
  const id = ++nextId
  socket.send(JSON.stringify({ id, method, params, sessionId }))
  return new Promise((resolve, reject) => pending.set(id, { resolve, reject }))
}

socket.addEventListener('message', event => {
  const message = JSON.parse(event.data)
  if (message.method === 'Runtime.exceptionThrown') {
    exceptions.push(message.params.exceptionDetails.exception?.description || message.params.exceptionDetails.text)
  }
  if (message.id && pending.has(message.id)) {
    const request = pending.get(message.id)
    pending.delete(message.id)
    if (message.error) request.reject(new Error(message.error.message))
    else request.resolve(message.result)
  }
})

await new Promise((resolve, reject) => {
  socket.addEventListener('open', resolve, { once: true })
  socket.addEventListener('error', reject, { once: true })
})

const created = await send('Target.createTarget', { url: 'about:blank' })
const attached = await send('Target.attachToTarget', {
  targetId: created.targetId,
  flatten: true
})
const sessionId = attached.sessionId

await send('Page.enable', {}, sessionId)
await send('Runtime.enable', {}, sessionId)
await send('Page.navigate', { url: loginUrl }, sessionId)

async function evaluate(expression) {
  const result = await send('Runtime.evaluate', {
    expression,
    returnByValue: true,
    awaitPromise: true
  }, sessionId)
  return result.result.value
}

for (let attempt = 0; attempt < 60; attempt += 1) {
  if (await evaluate(`Boolean(document.querySelector('.loginForm'))`)) break
  await new Promise(resolve => setTimeout(resolve, 500))
}

const submitted = await evaluate(`(() => {
  const inputs = document.querySelectorAll('.loginForm input')
  const values = [${JSON.stringify(username)}, ${JSON.stringify(password)}, '1234']
  inputs.forEach((input, index) => {
    input.value = values[index] || ''
    input.dispatchEvent(new Event('input', { bubbles: true }))
  })
  document.querySelector('.confirmBtn').click()
  return inputs.length
})()`)

let finalPath = ''
for (let attempt = 0; attempt < 80; attempt += 1) {
  finalPath = await evaluate('location.pathname')
  if (!finalPath.endsWith('/user/login')) break
  await new Promise(resolve => setTimeout(resolve, 500))
}

let targetVisible = null
if (afterLoginPath) {
  await send('Page.navigate', { url: `http://localhost:3000${afterLoginPath}` }, sessionId)
  for (let attempt = 0; attempt < 80; attempt += 1) {
    targetVisible = await evaluate(`Boolean(document.querySelector('.process-designer, .app-container, .empty-container'))`)
    if (targetVisible) break
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  finalPath = await evaluate('location.pathname')
}

const bodyText = await evaluate('document.body.innerText.slice(0, 500)')
console.log(JSON.stringify({
  submittedInputs: submitted,
  finalPath,
  loginSucceeded: finalPath !== '/qs/user/login',
  targetVisible,
  bodyText,
  exceptions
}, null, 2))

await send('Target.closeTarget', { targetId: created.targetId })
socket.close()
