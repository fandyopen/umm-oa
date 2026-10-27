import fs from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const docsDir = path.join(projectDir, 'docs')
const require = createRequire(import.meta.url)
const JSEncrypt = require('jsencrypt')
const origin = process.env.REFERENCE_ORIGIN || 'http://silianpan.cn'
const apiBase = `${origin}/api-qs`
const password = process.env.REFERENCE_PASSWORD || 'admin123123'
const publicKey = [
  'MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKoR8mX0rGKLqzcWmOzbfj64K8ZIgOdH',
  'nzkXSOVOZbFu/TJhZ7rFAN+eaGkl3C4buccQd/EjEsj9ir7ijT7h96MCAwEAAQ=='
].join('')

const allAccounts = [
  { username: '17630135660', labels: ['超级管理员'] },
  { username: '17673605591', labels: ['总经理'] },
  { username: '15685266238', labels: ['财务会计', '财务部经理'] },
  { username: '17689037697', labels: ['财务出纳', '合同归档'] },
  { username: '15646844893', labels: ['副总（销售）'] },
  { username: '13689213154', labels: ['副总（生产）'] },
  { username: '15650389435', labels: ['项目总管'] },
  { username: '15609825919', labels: ['采购负责人'] },
  { username: '17655872713', labels: ['综合部经理', '盖章确认'] }
]
const accountFilter = new Set(
  (process.env.REFERENCE_ACCOUNTS || '')
    .split(',')
    .map(value => value.trim())
    .filter(Boolean)
)
const accounts = accountFilter.size
  ? allAccounts.filter(account => accountFilter.has(account.username))
  : allAccounts

const ocrProgram = [
  'import base64',
  'import sys',
  'import ddddocr',
  'data = base64.b64decode(sys.stdin.read())',
  'print(ddddocr.DdddOcr(show_ad=False).classification(data))'
].join('\n')

function encrypt(value) {
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

function classifyCaptcha(base64Image) {
  const result = spawnSync('python', ['-c', ocrProgram], {
    input: base64Image,
    encoding: 'utf8',
    timeout: 30000
  })
  if (result.status !== 0) {
    throw new Error(result.stderr || 'Captcha OCR failed')
  }
  return result.stdout.trim().split(/\r?\n/).filter(Boolean).at(-1) || ''
}

async function authenticate(username) {
  let lastError = ''
  for (let attempt = 1; attempt <= 20; attempt += 1) {
    const captcha = await jsonRequest(`${apiBase}/captchaImage`)
    const code = classifyCaptcha(captcha.img)
    if (code.length < 4) {
      lastError = `OCR returned ${JSON.stringify(code)}`
      continue
    }
    const login = await jsonRequest(`${apiBase}/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json;charset=UTF-8' },
      body: JSON.stringify({
        username,
        password: encrypt(password),
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
  throw new Error(`Login failed after retries: ${lastError}`)
}

function flattenModules(modules, parentPath = '') {
  return modules.flatMap(module => {
    const routePath = module.path?.startsWith('/')
      ? module.path
      : `${parentPath}/${module.path || ''}`.replace(/\/+/g, '/')
    const current = {
      moduleId: module.moduleId,
      title: module.meta?.title || module.moduleName,
      path: routePath,
      component: module.component || '',
      perms: module.perms || '',
      hasChildren: Boolean(module.children?.length)
    }
    return [current, ...flattenModules(module.children || [], routePath)]
  })
}

async function probe(account) {
  const token = await authenticate(account.username)
  const headers = { Authorization: `Bearer ${token}` }
  const [infoResult, routersResult, appsResult] = await Promise.all([
    jsonRequest(`${apiBase}/getInfo`, { headers }),
    jsonRequest(`${apiBase}/getRouters`, { headers }),
    jsonRequest(`${apiBase}/app/user/list`, { headers }).catch(error => ({
      error: error.message
    }))
  ])
  const modules = flattenModules(routersResult.data || [])
  return {
    username: account.username,
    labels: account.labels,
    user: infoResult.user || {},
    roles: infoResult.roles || [],
    permissions: infoResult.permissions || [],
    posts: infoResult.posts || [],
    topLevelMenus: (routersResult.data || []).map(module => module.meta?.title || module.moduleName),
    visibleModules: modules.filter(module => !module.hasChildren),
    moduleCount: modules.length,
    apps: appsResult.data || appsResult,
    routers: routersResult.data || []
  }
}

fs.mkdirSync(docsDir, { recursive: true })
const resultFile = path.join(docsDir, 'reference-account-capabilities.json')
const previousResults = fs.existsSync(resultFile)
  ? JSON.parse(fs.readFileSync(resultFile, 'utf8'))
  : []
const results = process.env.REFERENCE_METADATA_ONLY === 'true'
  ? previousResults
  : []
if (process.env.REFERENCE_METADATA_ONLY !== 'true') {
  for (const account of accounts) {
    try {
      const result = await probe(account)
      const previousIndex = results.findIndex(item => item.username === account.username)
      if (previousIndex >= 0) {
        results[previousIndex] = result
      } else {
        results.push(result)
      }
      console.log(`${account.username}: ${result.moduleCount} modules, ${result.visibleModules.length} pages`)
    } catch (error) {
      const failed = { ...account, error: error.message }
      const previousIndex = results.findIndex(item => item.username === account.username)
      if (previousIndex >= 0) {
        results[previousIndex] = failed
      } else {
        results.push(failed)
      }
      console.error(`${account.username}: ${error.message}`)
    }
  }
}

fs.writeFileSync(
  resultFile,
  `${JSON.stringify(results, null, 2)}\n`
)

const lines = [
  '# Reference Account Capability Matrix',
  '',
  `Source: ${origin}`,
  '',
  '| Account | Roles | Posts | Top-level menus | Visible pages |',
  '| --- | --- | --- | --- | --- |'
]
for (const result of results) {
  if (result.error) {
    lines.push(`| ${result.username} | ERROR |  |  | ${result.error} |`)
    continue
  }
  const roles = result.roles.join(', ')
  const posts = result.posts.join(', ')
  const menus = result.topLevelMenus.join(', ')
  const pages = result.visibleModules.map(module => module.title).join(', ')
  lines.push(`| ${result.username} | ${roles} | ${posts} | ${menus} | ${pages} |`)
}

fs.writeFileSync(
  path.join(docsDir, 'reference-account-capabilities.md'),
  `${lines.join('\n')}\n`
)

try {
  const adminToken = await authenticate('17630135660')
  const users = await jsonRequest(`${apiBase}/system/user/list?pageNum=1&pageSize=200`, {
    headers: { Authorization: `Bearer ${adminToken}` }
  })
  fs.writeFileSync(
    path.join(docsDir, 'reference-users.json'),
    `${JSON.stringify(users, null, 2)}\n`
  )
  console.log(`Captured ${users.rows?.length || 0} reference users`)

  const roleSummaries = new Map()
  for (const user of users.rows || []) {
    for (const role of user.roles || []) {
      if (role.roleId != null) {
        roleSummaries.set(role.roleId, role)
      }
    }
  }
  const roleDetails = await Promise.all([...roleSummaries.values()].map(role =>
    jsonRequest(`${apiBase}/system/role/${role.roleId}`, {
      headers: { Authorization: `Bearer ${adminToken}` }
    }).then(detail => ({
      ...role,
      ...(detail.data || {}),
      menuIds: detail.data?.menuIds || []
    })).catch(error => ({ ...role, error: error.message, menuIds: [] }))
  ))
  fs.writeFileSync(
    path.join(docsDir, 'reference-roles.json'),
    `${JSON.stringify(roleDetails, null, 2)}\n`
  )
  console.log(`Captured ${roleDetails.length} reference roles`)

  const groups = await jsonRequest(`${apiBase}/system/admin/query/adminGroup`, {
    headers: { Authorization: `Bearer ${adminToken}` }
  })
  const groupDetails = await Promise.all((groups.data || []).map(group =>
    jsonRequest(`${apiBase}/system/admin/adminGroup/${group.groupId}`, {
      headers: { Authorization: `Bearer ${adminToken}` }
    }).then(detail => ({
      ...group,
      detail: detail.data || null
    })).catch(error => ({ ...group, error: error.message }))
  ))
  fs.writeFileSync(
    path.join(docsDir, 'reference-admin-groups.json'),
    `${JSON.stringify(groupDetails, null, 2)}\n`
  )
  console.log(`Captured ${groupDetails.length} reference admin groups`)

  const departments = await jsonRequest(`${apiBase}/system/dept/admin/tree`, {
    headers: { Authorization: `Bearer ${adminToken}` }
  })
  fs.writeFileSync(
    path.join(docsDir, 'reference-departments.json'),
    `${JSON.stringify(departments, null, 2)}\n`
  )
  console.log(`Captured ${departments.data?.length || 0} reference department roots`)

  const roleAdminList = await jsonRequest(`${apiBase}/system/role/admin/list`, {
    headers: { Authorization: `Bearer ${adminToken}` }
  })
  fs.writeFileSync(
    path.join(docsDir, 'reference-role-admin-list.json'),
    `${JSON.stringify(roleAdminList, null, 2)}\n`
  )
  console.log('Captured reference role admin list')
} catch (error) {
  console.error(`Unable to capture reference user directory: ${error.message}`)
}

console.log(`Wrote account capability reports to ${docsDir}`)
