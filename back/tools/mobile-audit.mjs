import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const command = process.argv[2]
const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const docsDir = path.join(projectDir, 'docs')

async function extractInventory() {
  const origin = process.env.REFERENCE_ORIGIN || 'http://silianpan.cn'
  const html = await (await fetch(`${origin}/qs-h5/`)).text()
  const scriptPath = [...html.matchAll(/src=([^\s>]+\.js)/g)]
    .map(match => match[1])
    .find(value => value.includes('/static/js/index.'))
  if (!scriptPath) {
    throw new Error('Unable to locate the H5 entry script')
  }
  const scriptUrl = new URL(scriptPath, `${origin}/qs-h5/`).toString()
  const source = await (await fetch(scriptUrl)).text()

  const pageMap = new Map()
  const routePattern = /path:"([^"]+)"[\s\S]{0,2000}?navigationBarTitleText:"([^"]+)"/g
  for (const match of source.matchAll(routePattern)) {
    pageMap.set(match[1], match[2])
  }

  const apiPaths = new Set()
  for (const match of source.matchAll(/url\("(\/[^"]+)"\)/g)) {
    apiPaths.add(match[1])
  }

  const pages = [...pageMap.entries()]
    .map(([route, title]) => ({ route, title }))
    .sort((left, right) => left.route.localeCompare(right.route))
  const apis = [...apiPaths].sort()

  fs.mkdirSync(docsDir, { recursive: true })
  fs.writeFileSync(
    path.join(docsDir, 'reference-mobile-inventory.json'),
    `${JSON.stringify({ scriptUrl, pages, apis }, null, 2)}\n`
  )

  console.log(`Extracted ${pages.length} H5 pages and ${apis.length} API paths`)
}

function compareApis() {
  const sourceDir = path.join(projectDir, 'src/main/java')
  const mobile = JSON.parse(
    fs.readFileSync(path.join(docsDir, 'reference-mobile-inventory.json'), 'utf8')
  )

  function walkFiles(directory) {
    return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
      const file = path.join(directory, entry.name)
      return entry.isDirectory() ? walkFiles(file) : [file]
    })
  }

  const source = walkFiles(sourceDir)
    .filter(file => file.endsWith('.java'))
    .map(file => fs.readFileSync(file, 'utf8'))
    .join('\n')

  const endpoints = mobile.apis.map(api => ({
    api,
    mapped: source.includes(`"${api}"`) || source.includes(`"${api.replace(/\/$/, '')}"`)
  }))
  const missing = endpoints.filter(endpoint => !endpoint.mapped)

  fs.writeFileSync(
    path.join(docsDir, 'mobile-api-parity.json'),
    `${JSON.stringify({ total: endpoints.length, missing: missing.length, endpoints }, null, 2)}\n`
  )

  const lines = [
    '# Mobile API Parity',
    '',
    `Mobile API paths: **${endpoints.length}**`,
    '',
    `Missing exact mappings: **${missing.length}**`,
    '',
    '| API | Status |',
    '| --- | --- |',
    ...endpoints.map(endpoint => `| \`${endpoint.api}\` | ${endpoint.mapped ? 'mapped' : 'fallback'} |`)
  ]
  fs.writeFileSync(path.join(docsDir, 'mobile-api-parity.md'), `${lines.join('\n')}\n`)
  console.log(`Compared ${endpoints.length} mobile APIs: ${missing.length} without exact mappings`)
}

if (command === 'extract') {
  await extractInventory()
} else if (command === 'compare') {
  compareApis()
} else {
  console.error('Usage: node tools/mobile-audit.mjs <extract|compare>')
  process.exitCode = 2
}
