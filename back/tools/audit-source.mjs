import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from '@babel/parser'
import traverseModule from '@babel/traverse'
import compiler from 'vue-template-compiler'

const traverse = traverseModule.default || traverseModule

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const sourceRoot = path.join(root, 'src')
const normalize = value => value.replaceAll('\\', '/')
const relative = value => normalize(path.relative(root, value))
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const flags = new Set(process.argv.slice(2))

function filesIn(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name)
    return entry.isDirectory() ? filesIn(file) : [file]
  })
}

function visit(node, callback) {
  if (!node || typeof node.type !== 'string') return
  callback(node)
  for (const [key, value] of Object.entries(node)) {
    if (['loc', 'start', 'end', 'comments', 'tokens'].includes(key)) continue
    if (Array.isArray(value)) value.forEach(child => visit(child, callback))
    else if (value && typeof value === 'object') visit(value, callback)
  }
}

const files = filesIn(sourceRoot).map(relative)
const codeFiles = files.filter(file => /\.(vue|js|jsx)$/.test(file))
const fileSet = new Set(files)
const records = new Map()
const unresolved = []
const uncertainImports = []

function resolveImport(owner, request) {
  if (!request || !/^(\/src\/|@\/|\.)/.test(request)) return null
  const clean = request.split('?')[0]
  const base = path.posix.normalize(clean.startsWith('/src/') ? clean.slice(1) : clean.startsWith('@/') ? 'src/' + clean.slice(2) : normalize(path.posix.join(path.posix.dirname(owner), clean)))
  return (
    [base, ...['.js', '.jsx', '.vue', '.json', '.less', '.css'].map(extension => base + extension), ...['index.js', 'index.jsx', 'index.vue'].map(index => base + '/' + index)].find(
      candidate => fileSet.has(candidate) || (!candidate.startsWith('src/') && fs.existsSync(path.join(root, candidate)) && fs.statSync(path.join(root, candidate)).isFile())
    ) || null
  )
}

for (const file of codeFiles) {
  const full = read(file)
  const sfc = file.endsWith('.vue') ? compiler.parseComponent(full) : null
  const source = sfc ? sfc.script?.content || '' : full
  const ast = parse(source, { sourceType: 'module', plugins: ['jsx', 'decorators-legacy'] })
  const localReferences = new Set()
  traverse(ast, {
    Program(program) {
      for (const binding of Object.values(program.scope.bindings)) {
        if (binding.referencePaths.some(reference => reference.type !== 'ExportNamedDeclaration')) {
          localReferences.add(binding.identifier.name)
        }
      }
    }
  })
  const record = { file, source, full, ast, localReferences, exportNames: new Set(), exportAll: false, dependencies: new Set(), imports: [], exports: [], reexports: [], namespaceTargets: new Set() }
  records.set(file, record)
  const addDependency = (request, names = [], node) => {
    if (request && /[?&](url|raw)(?:&|$)/.test(request)) names = []
    const target = resolveImport(file, request)
    if (target) {
      record.dependencies.add(target)
      record.imports.push({ target, names, start: node?.start, end: node?.end })
      if (names.includes('*')) record.namespaceTargets.add(target)
    } else if (request && /^(\/src\/|@\/|\.)/.test(request)) {
      unresolved.push({ file, request })
    }
  }
  visit(ast.program, node => {
    if (node.type === 'ExportDefaultDeclaration') record.exportNames.add('default')
    if (node.type === 'ExportAllDeclaration') record.exportAll = true
    if (node.type === 'ExportNamedDeclaration') {
      for (const specifier of node.specifiers || []) record.exportNames.add(specifier.exported.name || specifier.exported.value)
    }
    if (node.type === 'ImportDeclaration') {
      addDependency(
        node.source.value,
        node.specifiers.map(specifier => (specifier.type === 'ImportNamespaceSpecifier' ? '*' : specifier.type === 'ImportDefaultSpecifier' ? 'default' : specifier.imported.name || specifier.imported.value)),
        node
      )
    }
    if (['ExportNamedDeclaration', 'ExportAllDeclaration'].includes(node.type) && node.source) {
      addDependency(node.source.value, node.type === 'ExportAllDeclaration' ? ['*'] : node.specifiers.map(specifier => specifier.local.name), node)
      if (node.type === 'ExportNamedDeclaration') {
        record.reexports.push({ source: node.source.value, names: node.specifiers.map(item => item.exported.name || item.exported.value) })
      }
    }
    if (node.type === 'ExportNamedDeclaration' && node.declaration) {
      const declaration = node.declaration
      const names = declaration.type === 'VariableDeclaration' ? declaration.declarations.map(item => item.id.name) : [declaration.id?.name]
      names.filter(Boolean).forEach(name => {
        record.exports.push({ name, start: node.start, end: node.end })
        record.exportNames.add(name)
      })
    }
    if (node.type === 'CallExpression' && node.callee.type === 'Import') {
      const argument = node.arguments[0]
      if (argument?.type === 'StringLiteral') addDependency(argument.value)
      else uncertainImports.push({ file, expression: source.slice(node.start, node.end) })
    }
    if (node.type === 'CallExpression' && node.callee.type === 'MemberExpression' && ['glob', 'globEager'].includes(node.callee.property.name)) {
      const pattern = node.arguments[0]?.value
      // Router glob is a lookup table, not an instruction to activate every page.
      if (pattern === '../views/**/*.vue') return
      if (pattern === './lang/*.js') {
        codeFiles.filter(candidate => candidate.startsWith('src/locales/lang/')).forEach(candidate => record.dependencies.add(candidate))
      }
    }
  })
  for (const style of sfc?.styles || []) if (style.attrs.src) addDependency(style.attrs.src)
}

function sqlValues(input) {
  const result = []
  let text = '',
    quoted = false
  for (let i = 0; i < input.length; i++) {
    const char = input[i]
    if (char === "'" && input[i + 1] === "'" && quoted) {
      text += "'"
      i++
      continue
    }
    if (char === "'") {
      quoted = !quoted
      continue
    }
    if (char === ',' && !quoted) {
      result.push(text.trim())
      text = ''
      continue
    }
    text += char
  }
  result.push(text.trim())
  return result
}

const retired = new Set(JSON.parse(read('back/db/retired-modules.json')).moduleIds.map(String))
const catalog = new Map()
for (const sql of ['back/src/main/resources/module-data.sql', 'back/src/main/resources/customer-contract-menu.sql', 'back/src/main/resources/recruitment-menu.sql']) {
  const source = read(sql)
  const matches = [
    ...source.matchAll(/MERGE INTO sys_module\s*\(([^)]+)\)[^;]*?VALUES\s*\(([^;]*)\);/gi),
    ...source.matchAll(/INSERT INTO sys_module\s*\(([^)]+)\)\s*SELECT\s+([^;]*?)\s+WHERE NOT EXISTS\s*\([^;]*\);/gi)
  ]
  for (const match of matches) {
    const values = sqlValues(match[2])
    const row = Object.fromEntries(match[1].split(',').map((column, index) => [column.trim(), values[index]]))
    if (!retired.has(row.module_id)) catalog.set(row.module_id, row)
  }
}

// Optionally include read-only runtime catalog exports before deleting custom pages.
const runtimeCatalog = path.join(root, 'back/target/source-audit-runtime.json')
if (fs.existsSync(runtimeCatalog)) {
  for (const row of JSON.parse(fs.readFileSync(runtimeCatalog, 'utf8'))) catalog.set(String(row.module_id), row)
}
const roots = new Set(['src/main.js'])
const missingPages = []
for (const row of catalog.values()) {
  if (!row.component || ['Layout', 'ParentView', 'RouteView'].includes(row.component)) continue
  const file = resolveImport('src/router/generator-routers.js', '/src/views/' + row.component)
  if (file) roots.add(file)
  else missingPages.push({ moduleId: row.module_id, name: row.module_name, component: row.component })
}
const reachable = new Set()
const queue = [...roots]
while (queue.length) {
  const file = queue.pop()
  if (reachable.has(file)) continue
  reachable.add(file)
  for (const dependency of records.get(file)?.dependencies || []) queue.push(dependency)
}

const usedExports = new Map()
const namespaceTargets = new Set()
for (const file of reachable) {
  const record = records.get(file)
  if (!record) continue
  record.namespaceTargets.forEach(target => namespaceTargets.add(target))
  for (const item of record.imports) {
    if (!usedExports.has(item.target)) usedExports.set(item.target, new Set())
    item.names.forEach(name => usedExports.get(item.target).add(name))
  }
}
const unusedApiExports = []
const unusedImports = []
const unusedReexports = []
const missingExports = []
const missingDocFiles = []
for (const file of reachable) {
  const record = records.get(file)
  if (!record) continue
  for (const item of record.imports) {
    const target = records.get(item.target)
    if (!target || target.exportAll) continue
    for (const name of item.names) {
      if (name !== '*' && !target.exportNames.has(name)) missingExports.push({ file, target: item.target, name })
    }
  }
  if (file.startsWith('src/views/docs/')) {
    visit(record.ast.program, node => {
      if (node.type === 'StringLiteral' && node.value.startsWith('src/') && !fs.existsSync(path.join(root, node.value))) {
        missingDocFiles.push({ file, path: node.value })
      }
    })
  }
  if (!namespaceTargets.has(file)) {
    for (const item of record.reexports) {
      const names = item.names.filter(name => !usedExports.get(file)?.has(name))
      if (names.length) unusedReexports.push({ file, source: item.source, names })
    }
  }
  if (file.startsWith('src/api/') && !namespaceTargets.has(file)) {
    for (const item of record.exports) {
      if (!usedExports.get(file)?.has(item.name) && !record.localReferences.has(item.name)) {
        unusedApiExports.push({ file, ...item })
      }
    }
  }
  const body = record.ast.program.body.filter(node => node.type !== 'ImportDeclaration')
  const identifiers = new Set()
  body.forEach(node =>
    visit(node, item => {
      if (['Identifier', 'JSXIdentifier'].includes(item.type)) identifiers.add(item.name)
    })
  )
  const template = compiler.parseComponent(record.full).template?.content || ''
  for (const node of record.ast.program.body.filter(item => item.type === 'ImportDeclaration')) {
    const unused = node.specifiers.filter(specifier => !identifiers.has(specifier.local.name) && !new RegExp('\\b' + specifier.local.name + '\\b').test(template))
    if (unused.length) unusedImports.push({ file, source: node.source.value, names: unused.map(item => item.local.name) })
  }
}
// Business pages must resolve module metadata instead of embedding authorization IDs.
const hardcodedModuleIds = []
for (const [file, record] of records) {
  if (!/^src\/views\/(eos\/(crm|business)\/|enterprise\/(hr|components\/memberElement)\/)/.test(file)) continue
  const pattern = /\bmoduleId\s*:\s*['"]?([0-9]+)|\b(?:can|canView|canModule|canViewModule)\s*\(\s*['"]?([0-9]+)|\bmodule:([0-9]+):/g
  for (const match of record.full.matchAll(pattern)) {
    hardcodedModuleIds.push({ file, line: record.full.slice(0, match.index).split('\n').length, moduleId: match[1] || match[2] || match[3] })
  }
}
const report = {
  summary: {
    sourceFiles: codeFiles.length,
    reachableFiles: [...reachable].filter(file => records.has(file)).length,
    modulePages: roots.size - 1,
    unreachableFiles: codeFiles.filter(file => !reachable.has(file)).length,
    unusedApiExports: unusedApiExports.length,
    unusedImports: unusedImports.length,
    unusedReexports: unusedReexports.length,
    missingExports: missingExports.length,
    missingDocFiles: missingDocFiles.length,
    hardcodedModuleIds: hardcodedModuleIds.length
  },
  unreachableFiles: codeFiles.filter(file => !reachable.has(file)),
  unusedApiExports,
  unusedImports,
  unusedReexports,
  unresolved: unresolved.filter(item => reachable.has(item.file)),
  uncertainImports,
  missingPages,
  missingExports,
  missingDocFiles,
  hardcodedModuleIds,
  modules: [...catalog.values()]
}
const destination = path.join(root, 'back/target/source-audit.json')
fs.mkdirSync(path.dirname(destination), { recursive: true })
fs.writeFileSync(destination, JSON.stringify(report, null, 2) + '\n')
console.log(JSON.stringify(flags.has('--details') ? report : { ...report.summary, report: relative(destination), unresolved: report.unresolved, missingPages, missingExports, missingDocFiles, uncertainImports }, null, 2))
if (
  flags.has('--check') &&
  (report.unreachableFiles.length || unusedApiExports.length || unusedImports.length || unusedReexports.length || report.unresolved.length || missingPages.length || missingExports.length || missingDocFiles.length || hardcodedModuleIds.length)
)
  process.exitCode = 1
