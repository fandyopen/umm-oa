import fs from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const JSEncrypt = require('jsencrypt')
const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const remoteOrigin = process.env.REMOTE_ORIGIN || 'http://silianpan.cn'
const remoteApi = `${remoteOrigin}/api-qs`
const username = process.env.REFERENCE_USERNAME || '13686621113'
const password = process.env.REFERENCE_PASSWORD || 'admin123123'
const outputFile = path.join(projectDir, 'docs/reference-workflow-2575064.json')

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
  let body
  try {
    body = JSON.parse(text)
  } catch {
    throw new Error(`Expected JSON from ${url}, received ${response.status}: ${text.slice(0, 200)}`)
  }
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} from ${url}: ${text.slice(0, 200)}`)
  }
  return body
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

const token = await authenticate()
const headers = { Authorization: `Bearer ${token}` }
const todoList = await jsonRequest(
  `${remoteApi}/project/activity/taskWaitList?pageNum=1&pageSize=10`,
  { headers }
)
const firstTodo = todoList.rows?.[0]
if (!firstTodo?.instanceId) {
  throw new Error('Reference account has no waiting task to inspect')
}

const instanceId = String(firstTodo.instanceId)
const historyBody = {
  pageNum: 1,
  pageSize: 999,
  processInstanceId: instanceId,
  activityName: null,
  assignee: null
}
const [history, validElements, allElements, flowIds] = await Promise.all([
  jsonRequest(`${remoteApi}/activiti/process/listHistory`, {
    method: 'POST',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify(historyBody)
  }),
  jsonRequest(
    `${remoteApi}/activiti/process/listFlowElementsValid?pProcessInstanceId=${instanceId}`,
    { headers }
  ),
  jsonRequest(
    `${remoteApi}/activiti/process/listFlowElements?pProcessInstanceId=${instanceId}`,
    { headers }
  ),
  jsonRequest(
    `${remoteApi}/activiti/process/listFlowIds?pProcessInstanceId=${instanceId}`,
    { headers }
  )
])

const output = {
  generatedAt: new Date().toISOString(),
  username,
  firstTodo,
  requests: {
    history: { body: historyBody, response: history },
    validElements,
    allElements,
    flowIds
  }
}
fs.writeFileSync(outputFile, `${JSON.stringify(output, null, 2)}\n`)
console.log(`Wrote ${outputFile}`)
