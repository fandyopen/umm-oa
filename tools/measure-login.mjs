const cdpPort = process.env.CDP_PORT || '9222'
const targetUrl = process.env.LOGIN_URL || 'http://localhost:3000/qs/user/login'
const version = await fetch(`http://127.0.0.1:${cdpPort}/json/version`).then(response => response.json())
const socket = new WebSocket(version.webSocketDebuggerUrl)
let nextId = 0
const pending = new Map()
const exceptions = []
const loadingFailures = []
const initiators = new Map()

function send(method, params = {}, sessionId) {
  const id = ++nextId
  socket.send(JSON.stringify({ id, method, params, sessionId }))
  return new Promise((resolve, reject) => pending.set(id, { resolve, reject }))
}

socket.addEventListener('message', event => {
  const message = JSON.parse(event.data)
  if (message.method === 'Runtime.exceptionThrown') {
    exceptions.push(message.params.exceptionDetails)
  }
  if (message.method === 'Network.requestWillBeSent') {
    initiators.set(message.params.request.url, message.params.initiator)
  }
  if (message.method === 'Network.loadingFailed') {
    loadingFailures.push(message.params)
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
await send('Performance.enable', {}, sessionId)
await send('Network.enable', {}, sessionId)
await send('Page.navigate', { url: targetUrl }, sessionId)

for (let attempt = 0; attempt < 60; attempt += 1) {
  const probe = await send('Runtime.evaluate', {
    expression: `Boolean(document.querySelector('.loginForm'))`,
    returnByValue: true
  }, sessionId)
  if (probe.result.value) break
  await new Promise(resolve => setTimeout(resolve, 500))
}
await new Promise(resolve => setTimeout(resolve, 1000))

const evaluation = await send('Runtime.evaluate', {
  expression: `(() => {
    const navigation = performance.getEntriesByType('navigation')[0]
    const resources = performance.getEntriesByType('resource').map(entry => ({
      name: entry.name,
      duration: entry.duration,
      transferSize: entry.transferSize,
      encodedBodySize: entry.encodedBodySize,
      initiatorType: entry.initiatorType
    }))
    return {
      href: location.href,
      pathname: location.pathname,
      title: document.title,
      loginVisible: Boolean(document.querySelector('.loginForm')),
      bodyText: document.body ? document.body.innerText.slice(0, 1000) : '',
      localStorage: Object.fromEntries(Object.entries(localStorage)),
      sessionStorage: Object.fromEntries(Object.entries(sessionStorage)),
      navigation: navigation ? {
        domContentLoaded: navigation.domContentLoadedEventEnd,
        load: navigation.loadEventEnd,
        transferSize: navigation.transferSize
      } : null,
      resources
    }
  })()`,
  returnByValue: true,
  awaitPromise: true
}, sessionId)

const result = evaluation.result.value
const resources = result.resources || []
const totalBytes = resources.reduce((total, entry) => total + (entry.transferSize || 0), 0)
const slowest = resources
  .slice()
  .sort((left, right) => right.duration - left.duration)
  .slice(0, 15)
const largest = resources
  .slice()
  .sort((left, right) => right.transferSize - left.transferSize)
  .slice(0, 15)
  .map(entry => ({
    ...entry,
    initiator: initiators.get(entry.name)
  }))
const heavyInitiators = resources
  .filter(entry => /ProLayout|element-ui|plugins\/ui\/index|plugins\/global\/index|iconfont|generator-routers/.test(entry.name))
  .map(entry => ({
    name: entry.name,
    initiator: initiators.get(entry.name)
  }))

console.log(JSON.stringify({
  href: result.href,
  pathname: result.pathname,
  title: result.title,
  loginVisible: result.loginVisible,
  bodyText: result.bodyText,
  localStorage: result.localStorage,
  sessionStorage: result.sessionStorage,
  navigation: result.navigation,
  requests: resources.length,
  transferBytes: totalBytes,
  transferMiB: Math.round(totalBytes / 1024 / 1024 * 100) / 100,
  exceptions: exceptions.map(exception => ({
    text: exception.text,
    description: exception.exception?.description,
    url: exception.url,
    lineNumber: exception.lineNumber,
    columnNumber: exception.columnNumber
  })),
  loadingFailures,
  heavyInitiators,
  slowest,
  largest
}, null, 2))

await send('Target.closeTarget', { targetId: created.targetId })
socket.close()
