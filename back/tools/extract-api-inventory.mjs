import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from '@babel/parser'

const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(process.argv[2] || path.resolve(toolDir, '../..'))
const apiRoot = path.join(projectRoot, 'src/api')
const outputRoot = path.resolve(process.argv[3] || path.resolve(toolDir, '../docs'))

function walkFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const absolutePath = path.join(directory, entry.name)
    return entry.isDirectory() ? walkFiles(absolutePath) : [absolutePath]
  })
}

function walk(node, visitor) {
  if (!node || typeof node.type !== 'string') return
  visitor(node)
  for (const [key, value] of Object.entries(node)) {
    if (key === 'loc' || key === 'start' || key === 'end') continue
    if (Array.isArray(value)) value.forEach(child => walk(child, visitor))
    else if (value && typeof value === 'object') walk(value, visitor)
  }
}

function literalValue(node) {
  if (!node) return null
  if (node.type === 'StringLiteral') return node.value
  if (node.type === 'TemplateLiteral' && node.expressions.length === 0) {
    return node.quasis.map(part => part.value.cooked).join('')
  }
  return null
}

function resolveUrl(node) {
  if (!node) return null
  const literal = literalValue(node)
  if (literal) return literal
  if (node.type === 'BinaryExpression' && node.operator === '+') {
    const left = resolveUrl(node.left)
    const right = resolveUrl(node.right)
    if (left !== null) return left + (right === null ? '{dynamic}' : right)
  }
  if (node.type === 'TemplateLiteral') {
    return node.quasis.reduce((result, part, index) => {
      const expression = node.expressions[index] ? '{dynamic}' : ''
      return result + part.value.cooked + expression
    }, '')
  }
  return null
}

function propertyName(property) {
  if (!property.computed && property.key.type === 'Identifier') {
    return property.key.name
  }
  return literalValue(property.key)
}

function objectProperties(node) {
  if (!node || node.type !== 'ObjectExpression') return {}
  return Object.fromEntries(node.properties
    .filter(property => property.type === 'ObjectProperty')
    .map(property => [propertyName(property), property.value]))
}

function functionName(pathNode) {
  if (pathNode.type === 'FunctionDeclaration') return pathNode.id?.name
  if (pathNode.type === 'VariableDeclarator' && pathNode.id.type === 'Identifier') {
    return pathNode.id.name
  }
  return null
}

const endpoints = []

for (const file of walkFiles(apiRoot).filter(file => file.endsWith('.js'))) {
  const source = fs.readFileSync(file, 'utf8')
  const ast = parse(source, {
    sourceType: 'module',
    plugins: ['jsx']
  })
  const relativeFile = path.relative(projectRoot, file).replaceAll('\\', '/')

  walk(ast.program, node => {
    if (!['FunctionDeclaration', 'VariableDeclarator'].includes(node.type)) return
    const name = functionName(node)
    if (!name) return

    walk(node, child => {
      if (child.type !== 'CallExpression') return
      if (child.callee.type !== 'Identifier' || child.callee.name !== 'request') return
      const properties = objectProperties(child.arguments[0])
      const method = (literalValue(properties.method) || 'get').toUpperCase()
      const url = resolveUrl(properties.url)
      if (!url) return

      endpoints.push({
        module: relativeFile.replace(/^src\/api\//, '').replace(/\.js$/, ''),
        function: name,
        method,
        url,
        hasParams: Boolean(properties.params),
        hasData: Boolean(properties.data),
        file: relativeFile
      })
    })
  })
}

endpoints.sort((left, right) =>
  left.module.localeCompare(right.module) ||
  left.url.localeCompare(right.url) ||
  left.function.localeCompare(right.function)
)

fs.mkdirSync(outputRoot, { recursive: true })
fs.writeFileSync(
  path.join(outputRoot, 'api-inventory.json'),
  `${JSON.stringify(endpoints, null, 2)}\n`
)

const lines = [
  '# Frontend API Inventory',
  '',
  `Generated from: \`src/api\``,
  '',
  `Endpoints: **${endpoints.length}**`,
  '',
  '| Module | Function | Method | URL | Body | Params |',
  '| --- | --- | --- | --- | --- | --- |'
]

for (const endpoint of endpoints) {
  lines.push(
    `| ${endpoint.module} | ${endpoint.function} | ${endpoint.method} | ` +
    `\`${endpoint.url}\` | ${endpoint.hasData ? 'yes' : ''} | ${endpoint.hasParams ? 'yes' : ''} |`
  )
}

fs.writeFileSync(path.join(outputRoot, 'api-inventory.md'), `${lines.join('\n')}\n`)
console.log(`Extracted ${endpoints.length} endpoints to ${outputRoot}`)
