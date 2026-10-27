import fs from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const JSEncrypt = require('jsencrypt')
const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const parityFile = path.join(projectDir, 'docs/page-parity.json')
const outputFile = path.join(projectDir, 'docs/reference-page-api.json')
const remoteOrigin = process.env.REMOTE_ORIGIN || 'http://silianpan.cn'
const remoteApi = `${remoteOrigin}/api-qs`
const extraRequests = (process.env.REFERENCE_EXTRA_REQUESTS || '')
  .split(',')
  .map(value => value.trim())
  .filter(Boolean)
const username = process.env.REFERENCE_USERNAME || '17630135660'
const password = process.env.REFERENCE_PASSWORD || 'admin123123'
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

async function authenticate() {
  let lastError = ''
  for (let attempt = 1; attempt <= 20; attempt += 1) {
    const captcha = await jsonRequest(`${remoteApi}/captchaImage`)
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
    const login = await jsonRequest(`${remoteApi}/login`, {
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
    if (login.token) {
      return login.token
    }
    lastError = `${login.code || ''} ${login.msg || ''}`.trim()
  }
  throw new Error(`Reference login failed: ${lastError}`)
}

const parity = JSON.parse(fs.readFileSync(parityFile, 'utf8'))
const requests = new Map()
for (const [pagePath, page] of Object.entries(parity.sides.local || {})) {
  for (const api of page.api || []) {
    if (!api.fallback) {
      continue
    }
    const url = new URL(api.url)
    requests.set(url.pathname + url.search, {
      pagePath,
      pageTitle: parity.comparisons.find(item => item.path === pagePath)?.title || pagePath,
      localUrl: api.url
    })
  }
}
for (const requestPath of extraRequests) {
  requests.set(requestPath, {
    pagePath: '',
    pageTitle: 'Targeted parity request',
    localUrl: `http://localhost:3000${requestPath}`
  })
}

const token = await authenticate()
const previousResults = fs.existsSync(outputFile)
  ? JSON.parse(fs.readFileSync(outputFile, 'utf8')).results || []
  : []
const resultsByUrl = new Map(previousResults.map(result => [result.localUrl, result]))
for (const [requestPath, metadata] of requests) {
  const remoteUrl = `${remoteApi}${requestPath.replace(/^\/api-qs/, '')}`
  try {
    const response = await jsonRequest(remoteUrl, {
      headers: { Authorization: `Bearer ${token}` }
    })
    resultsByUrl.set(metadata.localUrl, { ...metadata, remoteUrl, response })
    console.log(`captured ${metadata.pageTitle} ${requestPath}`)
  } catch (error) {
    resultsByUrl.set(metadata.localUrl, { ...metadata, remoteUrl, error: error.message })
    console.error(`failed ${metadata.pageTitle} ${requestPath}: ${error.message}`)
  }
}
const results = [...resultsByUrl.values()]

fs.writeFileSync(outputFile, `${JSON.stringify({
  generatedAt: new Date().toISOString(),
  username,
  requestCount: requests.size,
  results
}, null, 2)}\n`)
console.log(`Wrote ${outputFile}`)
