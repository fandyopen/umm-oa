import fs from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const JSEncrypt = require('jsencrypt')
const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const outputJson = path.join(projectDir, 'back/docs/page-parity.json')
const outputMarkdown = path.join(projectDir, 'back/docs/page-parity.md')
const routesFile = path.join(projectDir, 'back/db/source/remote-routers.json')
const cdpPort = process.env.CDP_PORT || '9231'
const username = process.env.AUDIT_USERNAME || '17630135660'
const password = process.env.AUDIT_PASSWORD || 'admin123123'
const localOrigin = process.env.LOCAL_ORIGIN || 'http://localhost:3000'
const remoteOrigin = process.env.REMOTE_ORIGIN || 'http://silianpan.cn'
const routeFilter = new Set(
  (process.env.AUDIT_ROUTES || '')
    .split(',')
    .map(value => value.trim())
    .filter(Boolean)
)
const sideFilter = new Set(
  (process.env.AUDIT_SIDES || 'local,remote')
    .split(',')
    .map(value => value.trim())
    .filter(Boolean)
)
const remoteWaitMs = Number(process.env.REMOTE_PAGE_WAIT_MS || 18000)
const localWaitMs = Number(process.env.LOCAL_PAGE_WAIT_MS || 10000)

const publicKey = [
  'MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKoR8mX0rGKLqzcWmOzbfj64K8ZIgOdH',
  'nzkXSOVOZbFu/TJhZ7rFAN+eaGkl3C4buccQd/EjEsj9ir7ijT7h96MCAwEAAQ=='
].join('')

const captchaOcr = [
  'import base64,sys,ddddocr',
  'data=base64.b64decode(sys.stdin.read())',
  'print(ddddocr.DdddOcr(show_ad=False).classification(data))'
].join('\n')

function encryptPassword(value) {
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(publicKey)
  return encryptor.encrypt(value)
}

async function jsonRequest(url, options = {}) {
  const response = await fetch(url, options)
  const text = await response.text()
  try {
    return JSON.parse(text)
  } catch {
    throw new Error(`Expected JSON from ${url}, received ${response.status}: ${text.slice(0, 200)}`)
  }
}

async function localToken() {
  const result = await jsonRequest('http://127.0.0.1:8080/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, rememberMe: true })
  })
  if (!result.token) {
    throw new Error(`Local login failed: ${result.msg || result.code}`)
  }
  return result.token
}

async function remoteToken() {
  let lastError = ''
  for (let attempt = 1; attempt <= 20; attempt += 1) {
    const captcha = await jsonRequest(`${remoteOrigin}/api-qs/captchaImage`)
    const classified = spawnSync('python', ['-c', captchaOcr], {
      input: captcha.img,
      encoding: 'utf8',
      timeout: 30000
    })
    const code = classified.stdout.trim().split(/\r?\n/).filter(Boolean).at(-1)
    if (!code) {
      lastError = classified.stderr || 'Captcha OCR returned no text'
      continue
    }
    const result = await jsonRequest(`${remoteOrigin}/api-qs/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username,
        password: encryptPassword(password),
        code,
        uuid: captcha.uuid,
        rememberMe: true
      })
    })
    if (result.token) {
      return result.token
    }
    lastError = `${result.code || ''} ${result.msg || ''}`.trim()
  }
  throw new Error(`Remote login failed: ${lastError}`)
}

function flattenRoutes(modules, parentPath = '') {
  const routes = []
  for (const module of modules || []) {
    const currentPath = module.path?.startsWith('/')
      ? module.path
      : `${parentPath}/${module.path || ''}`.replace(/\/+/g, '/')
    if (module.children?.length) {
      routes.push(...flattenRoutes(module.children, currentPath))
    } else {
      routes.push({
        moduleId: module.moduleId,
        name: module.name || '',
        title: module.meta?.title || module.moduleName,
        path: currentPath,
        component: module.component || '',
        hidden: Boolean(module.hidden)
      })
    }
  }
  return routes
}

function unique(values) {
  return [...new Set(values.map(value => value.replace(/\s+/g, ' ').trim()).filter(Boolean))]
}

class CdpSession {
  constructor(browserSocket, sessionId, targetId) {
    this.socket = browserSocket
    this.sessionId = sessionId
    this.targetId = targetId
    this.nextId = 0
    this.pending = new Map()
    this.network = []
    this.exceptions = []
  }

  static async connect() {
    const version = await jsonRequest(`http://127.0.0.1:${cdpPort}/json/version`)
    const socket = new WebSocket(version.webSocketDebuggerUrl)
    const pending = new Map()
    let nextId = 0
    const eventListeners = new Set()
    socket.addEventListener('message', event => {
      const message = JSON.parse(event.data)
      if (message.id && pending.has(message.id)) {
        const request = pending.get(message.id)
        pending.delete(message.id)
        if (message.error) {
          request.reject(new Error(message.error.message))
        } else {
          request.resolve(message.result)
        }
      }
      for (const listener of eventListeners) {
        listener(message)
      }
    })
    await new Promise((resolve, reject) => {
      socket.addEventListener('open', resolve, { once: true })
      socket.addEventListener('error', reject, { once: true })
    })
    const browser = {
      socket,
      pending,
      eventListeners,
      send(method, params = {}, sessionId) {
        const id = ++nextId
        socket.send(JSON.stringify({ id, method, params, sessionId }))
        return new Promise((resolve, reject) => pending.set(id, { resolve, reject }))
      }
    }
    const created = await browser.send('Target.createTarget', { url: 'about:blank' })
    const attached = await browser.send('Target.attachToTarget', {
      targetId: created.targetId,
      flatten: true
    })
    const session = new CdpSession(browser, attached.sessionId, created.targetId)
    session.browser = browser
    browser.eventListeners.add(message => {
      if (message.sessionId !== session.sessionId) {
        return
      }
      if (message.method === 'Network.responseReceived') {
        const response = message.params.response
        if (response.url.includes('/api-qs/')) {
          session.network.push({
            url: response.url,
            status: response.status,
            mimeType: response.mimeType,
            fromDiskCache: response.fromDiskCache,
            headers: response.headers
          })
        }
      }
      if (message.method === 'Runtime.exceptionThrown') {
        session.exceptions.push(
          message.params.exceptionDetails.exception?.description ||
          message.params.exceptionDetails.text
        )
      }
    })
    await session.send('Page.enable')
    await session.send('Runtime.enable')
    await session.send('Network.enable')
    return session
  }

  send(method, params = {}) {
    return this.browser.send(method, params, this.sessionId)
  }

  async evaluate(expression) {
    const result = await this.send('Runtime.evaluate', {
      expression,
      returnByValue: true,
      awaitPromise: true
    })
    if (result.exceptionDetails) {
      throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text)
    }
    return result.result.value
  }

  async prepare(side, token) {
    const origin = side === 'local' ? localOrigin : remoteOrigin
    await this.send('Page.navigate', { url: `${origin}/qs/user/login` })
    for (let attempt = 0; attempt < 40; attempt += 1) {
      if (await this.evaluate('document.readyState === "complete"')) {
        break
      }
      await new Promise(resolve => setTimeout(resolve, 250))
    }
    await this.evaluate(`localStorage.setItem('access_token', ${JSON.stringify(token)}); true`)
    await this.send('Page.navigate', { url: `${origin}/qs/index` })
    for (let attempt = 0; attempt < 120; attempt += 1) {
      await new Promise(resolve => setTimeout(resolve, 300))
      const ready = await this.evaluate(`(() => {
        const app = document.querySelector('#app')
        const vue = app && (app.__vue__ || app.__vue)
        const router = vue && (vue.$router || vue.$root?.$router)
        return Boolean(router?.matcher?.getRoutes) &&
          router.matcher.getRoutes().length > 80
      })()`)
      if (ready) {
        break
      }
    }
    return this.evaluate(`(() => {
      const app = document.querySelector('#app')
      const vue = app && (app.__vue__ || app.__vue)
      const router = vue && (vue.$router || vue.$root?.$router)
      return router.matcher.getRoutes().map(route => ({
        path: route.path,
        name: route.name || '',
        title: route.meta?.title || ''
      }))
    })()`)
  }

  async visit(pathname, waitMs) {
    this.network = []
    this.exceptions = []
    await this.evaluate(`(async () => {
      const app = document.querySelector('#app')
      const vue = app && (app.__vue__ || app.__vue)
      const router = vue && (vue.$router || vue.$root?.$router)
      await router.push(${JSON.stringify(pathname)})
      return router.currentRoute.fullPath
    })()`)
    const deadline = Date.now() + waitMs
    let stableChecks = 0
    let previousApiCount = -1
    while (Date.now() < deadline) {
      await new Promise(resolve => setTimeout(resolve, 500))
      const state = await this.evaluate(`(() => ({
        readyState: document.readyState,
        title: document.title,
        textLength: document.body?.innerText?.length || 0,
        spinning: Boolean(document.querySelector('.ant-spin-spinning, .loading-container')),
        hasContent: Boolean(document.querySelector(
          'th, .ant-form-item, .ant-empty-description, .dashboard, .process-designer, .app-container'
        ))
      }))()`)
      if (
        state.readyState === 'complete' &&
        state.title &&
        state.textLength > 80 &&
        !state.spinning
      ) {
        if (this.network.length === previousApiCount) {
          stableChecks += 1
        } else {
          stableChecks = 0
        }
        previousApiCount = this.network.length
        if (state.hasContent || stableChecks >= 4) {
          break
        }
      } else {
        stableChecks = 0
        previousApiCount = this.network.length
      }
    }
    if (!this.network.length) {
      await new Promise(resolve => setTimeout(resolve, 1000))
    }
    if (!await this.evaluate('Boolean(document.body)')) {
      for (let attempt = 0; attempt < 20; attempt += 1) {
        await new Promise(resolve => setTimeout(resolve, 250))
        if (await this.evaluate('Boolean(document.body)')) {
          break
        }
      }
    }
    if (!await this.evaluate('document.readyState === "complete"')) {
      for (let attempt = 0; attempt < 20; attempt += 1) {
        await new Promise(resolve => setTimeout(resolve, 250))
        if (await this.evaluate('document.readyState === "complete"')) {
          break
        }
      }
    }
    if (!await this.evaluate('document.body.innerText.length > 20')) {
      for (let attempt = 0; attempt < 20; attempt += 1) {
        await new Promise(resolve => setTimeout(resolve, 250))
        if (await this.evaluate('document.body.innerText.length > 20')) {
          break
        }
      }
    }
    const dom = await this.evaluate(`(() => {
      const clean = value => (value || '').replace(/\\s+/g, ' ').trim()
      const values = selector => Array.from(document.querySelectorAll(selector))
        .map(node => clean(node.innerText || node.textContent))
        .filter(Boolean)
      const attrs = (selector, attribute) => Array.from(document.querySelectorAll(selector))
        .map(node => clean(node.getAttribute(attribute)))
        .filter(Boolean)
      const main = document.querySelector('.ant-layout-content') ||
        document.querySelector('main') ||
        document.querySelector('#app')
      return {
        url: location.href,
        title: document.title,
        breadcrumbs: values('.ant-breadcrumb-link, .ant-breadcrumb-separator'),
        headings: values('h1, h2, .page-title, .ant-page-header-heading-title'),
        tableHeaders: values('th'),
        buttons: values('button, .ant-btn'),
        formLabels: values('.ant-form-item-label, label'),
        placeholders: attrs('input[placeholder], textarea[placeholder]', 'placeholder'),
        emptyText: values('.ant-empty-description'),
        errors: values('.ant-message-error, .ant-notification-notice-error, .error-message'),
        mainText: clean(main?.innerText || document.body?.innerText || '').slice(0, 5000)
      }
    })()`)
    return {
      ...dom,
      tableHeaders: unique(dom.tableHeaders),
      buttons: unique(dom.buttons),
      formLabels: unique(dom.formLabels),
      placeholders: unique(dom.placeholders),
      emptyText: unique(dom.emptyText),
      errors: unique(dom.errors),
      api: this.network.map(response => ({
        url: response.url,
        status: response.status,
        fallback: Boolean(
          response.headers['x-umm-oa-compatible'] ||
          response.headers['X-UMM-OA-Compatible']
        )
      })),
      exceptions: unique(this.exceptions)
    }
  }

  async close() {
    await this.browser.send('Target.closeTarget', { targetId: this.targetId })
    this.browser.socket.close()
  }
}

function diffValues(referenceValues, localValues) {
  const reference = new Set(referenceValues)
  const local = new Set(localValues)
  return {
    missing: [...reference].filter(value => !local.has(value)),
    extra: [...local].filter(value => !reference.has(value))
  }
}

function summarizeRoute(route, local, remote) {
  const tableHeaders = diffValues(remote?.tableHeaders || [], local?.tableHeaders || [])
  const buttons = diffValues(remote?.buttons || [], local?.buttons || [])
  const formLabels = diffValues(remote?.formLabels || [], local?.formLabels || [])
  const placeholders = diffValues(remote?.placeholders || [], local?.placeholders || [])
  const localErrors = [
    ...(local?.errors || []),
    ...(local?.api || [])
      .filter(response => response.status >= 400 || response.fallback)
      .map(response => `${response.status}${response.fallback ? ' fallback' : ''} ${response.url}`),
    ...(local?.exceptions || [])
  ]
  const remoteErrors = [
    ...(remote?.errors || []),
    ...(remote?.api || [])
      .filter(response => response.status >= 400)
      .map(response => `${response.status} ${response.url}`),
    ...(remote?.exceptions || [])
  ]
  return {
    moduleId: route.moduleId,
    title: route.title,
    path: route.path,
    localPath: local?.url ? new URL(local.url).pathname : '',
    remotePath: remote?.url ? new URL(remote.url).pathname : '',
    tableHeaders,
    buttons,
    formLabels,
    placeholders,
    localErrors,
    remoteErrors
  }
}

function markdownValue(values) {
  if (!values?.length) {
    return ''
  }
  return values.slice(0, 8).join(', ').replaceAll('|', '\\|')
}

const routes = flattenRoutes(JSON.parse(fs.readFileSync(routesFile, 'utf8')).data)
  .filter(route => route.path && route.path !== '/#')
  .filter(route => routeFilter.size === 0 || routeFilter.has(route.path) || routeFilter.has(route.title))

const previousResults = fs.existsSync(outputJson)
  ? JSON.parse(fs.readFileSync(outputJson, 'utf8'))
  : null
const results = {
  generatedAt: new Date().toISOString(),
  account: username,
  routeCount: routes.length,
  sides: previousResults?.sides || {},
  comparisons: []
}

for (const side of sideFilter) {
  const token = side === 'local' ? await localToken() : await remoteToken()
  const session = await CdpSession.connect()
  const origin = side === 'local' ? localOrigin : remoteOrigin
  const waitMs = side === 'local' ? localWaitMs : remoteWaitMs
  const pages = {}
  try {
    const browserRoutes = await session.prepare(side, token)
    const pathByName = new Map(
      browserRoutes
        .filter(route => route.name && route.path)
        .map(route => [route.name, route.path])
    )
    console.log(`${side} router paths ${pathByName.size}`)
    for (const [index, route] of routes.entries()) {
      const browserPath = pathByName.get(route.name) || route.path
      pages[route.path] = await session.visit(browserPath, waitMs)
      pages[route.path].browserPath = browserPath
      console.log(
        `${side} ${index + 1}/${routes.length} ${route.title} ${route.path} ` +
        `${pages[route.path].api.length} api`
      )
    }
  } finally {
    await session.close()
  }
  results.sides[side] = pages
}

for (const route of routes) {
  results.comparisons.push(summarizeRoute(
    route,
    results.sides.local?.[route.path],
    results.sides.remote?.[route.path]
  ))
}

fs.writeFileSync(outputJson, `${JSON.stringify(results, null, 2)}\n`)

const lines = [
  '# Page Parity Audit',
  '',
  `Account: ${username}`,
  '',
  `Routes audited: ${routes.length}`,
  '',
  '| Page | Path | Local API issues | Missing table headers | Missing form labels | Missing buttons |',
  '| --- | --- | ---: | --- | --- | --- |'
]
for (const comparison of results.comparisons) {
  lines.push(
    `| ${comparison.title} | ${comparison.path} | ${comparison.localErrors.length} | ` +
    `${markdownValue(comparison.tableHeaders.missing)} | ` +
    `${markdownValue(comparison.formLabels.missing)} | ` +
    `${markdownValue(comparison.buttons.missing)} |`
  )
}
fs.writeFileSync(outputMarkdown, `${lines.join('\n')}\n`)

console.log(`Wrote ${outputJson}`)
console.log(`Wrote ${outputMarkdown}`)
