import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import compiler from 'vue-template-compiler'
import { parse, parseExpression } from '@babel/parser'
import traverseModule from '@babel/traverse'
import postcss from 'postcss'

const traverse = traverseModule.default || traverseModule
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const filesIn = directory => fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => (entry.isDirectory() ? filesIn(path.join(directory, entry.name)) : [path.join(directory, entry.name)]))
const kebab = value => value.replace(/[A-Z]/g, char => '-' + char.toLowerCase())
const arbitrary = value => '[' + value.trim().replace(/\s+/g, '_') + ']'
const enums = {
  display: { block: 'block', inline: 'inline', 'inline-block': 'inline-block', flex: 'flex', 'inline-flex': 'inline-flex', grid: 'grid', none: 'hidden' },
  position: { relative: 'relative', absolute: 'absolute', fixed: 'fixed', sticky: 'sticky', static: 'static' },
  float: { left: 'float-left', right: 'float-right', none: 'float-none' },
  overflow: { auto: 'overflow-auto', hidden: 'overflow-hidden', scroll: 'overflow-scroll', visible: 'overflow-visible' },
  'overflow-x': { auto: 'overflow-x-auto', hidden: 'overflow-x-hidden', scroll: 'overflow-x-scroll' },
  'overflow-y': { auto: 'overflow-y-auto', hidden: 'overflow-y-hidden', scroll: 'overflow-y-scroll' },
  'text-align': { left: 'text-left', center: 'text-center', right: 'text-right', justify: 'text-justify' },
  'vertical-align': { top: 'align-top', middle: 'align-middle', bottom: 'align-bottom', baseline: 'align-baseline' },
  'align-items': { center: 'items-center', 'flex-start': 'items-start', 'flex-end': 'items-end', stretch: 'items-stretch' },
  'justify-content': { center: 'justify-center', 'flex-start': 'justify-start', 'flex-end': 'justify-end', 'space-between': 'justify-between', 'space-around': 'justify-around' },
  'flex-direction': { row: 'flex-row', column: 'flex-col', 'row-reverse': 'flex-row-reverse', 'column-reverse': 'flex-col-reverse' },
  'flex-wrap': { wrap: 'flex-wrap', nowrap: 'flex-nowrap' },
  cursor: { pointer: 'cursor-pointer', default: 'cursor-default', 'not-allowed': 'cursor-not-allowed', move: 'cursor-move' },
  'user-select': { none: 'select-none', text: 'select-text', auto: 'select-auto' },
  'white-space': { nowrap: 'whitespace-nowrap', normal: 'whitespace-normal', pre: 'whitespace-pre' },
  'box-sizing': { 'border-box': 'box-border', 'content-box': 'box-content' },
  'font-weight': { normal: 'font-normal', bold: 'font-bold', 400: 'font-normal', 500: 'font-medium', 600: 'font-semibold', 700: 'font-bold' },
  'text-decoration': { none: 'no-underline', underline: 'underline' }
}
const prefixes = {
  width: 'w',
  height: 'h',
  'min-width': 'min-w',
  'max-width': 'max-w',
  'min-height': 'min-h',
  'max-height': 'max-h',
  margin: 'm',
  'margin-top': 'mt',
  'margin-right': 'mr',
  'margin-bottom': 'mb',
  'margin-left': 'ml',
  padding: 'p',
  'padding-top': 'pt',
  'padding-right': 'pr',
  'padding-bottom': 'pb',
  'padding-left': 'pl',
  top: 'top',
  right: 'right',
  bottom: 'bottom',
  left: 'left',
  gap: 'gap',
  'column-gap': 'gap-x',
  'row-gap': 'gap-y',
  color: 'text',
  'font-size': 'text',
  'line-height': 'leading',
  'z-index': 'z',
  opacity: 'opacity',
  'border-radius': 'rounded',
  'border-color': 'border',
  'background-color': 'bg',
  'flex-basis': 'basis'
}

export function utility(property, input) {
  const value = String(input).trim()
  if (enums[property]?.[value]) return ['tw-' + enums[property][value]]
  if (['margin', 'padding'].includes(property)) {
    const values = value.split(/\s+/)
    if (values.length > 1 && values.length <= 4 && !value.includes('(')) {
      const [t, r = t, b = t, l = r] = values
      if (t === b && r === l) {
        const vertical = utility(property + '-top', t)
        const horizontal = utility(property + '-right', r)
        if (vertical && horizontal)
          return [
            ...vertical.map(token => token.replace('-' + (property === 'margin' ? 'mt' : 'pt') + '-', '-' + (property === 'margin' ? 'my' : 'py') + '-')),
            ...horizontal.map(token => token.replace('-' + (property === 'margin' ? 'mr' : 'pr') + '-', '-' + (property === 'margin' ? 'mx' : 'px') + '-'))
          ]
      }
      return [
        ['top', t],
        ['right', r],
        ['bottom', b],
        ['left', l]
      ].flatMap(([side, size]) => utility(property + '-' + side, size))
    }
  }
  const prefix = prefixes[property]
  if (!prefix || /[;{}"'\\]/.test(value)) return null
  if (['color', 'background-color', 'border-color'].includes(property) && ['white', 'black', 'transparent', 'currentColor'].includes(value)) return ['tw-' + prefix + '-' + (value === 'currentColor' ? 'current' : value)]
  if (property === 'font-size') {
    const size = { '12px': 'xs', '14px': 'sm', '16px': 'base', '18px': 'lg', '20px': 'xl', '24px': '2xl', '30px': '3xl', '36px': '4xl' }[value]
    return ['tw-text-' + (size || arbitrary(value))]
  }
  const dimension = /^(w|h|min-w|max-w|min-h|max-h|basis)$/.test(prefix)
  if (dimension && value === '100%') return ['tw-' + prefix + '-full']
  if (value === 'auto' && (/^m/.test(prefix) || dimension || ['top', 'right', 'bottom', 'left'].includes(prefix))) return ['tw-' + prefix + '-auto']
  if (/^(m|p|mt|mr|mb|ml|pt|pr|pb|pl|gap|gap-x|gap-y|w|h|min-w|max-w|min-h|max-h|top|right|bottom|left|basis)$/.test(prefix) && /^(0|\d+(?:\.\d+)?px)$/.test(value)) {
    const number = parseFloat(value)
    const scale = [0, 2, 4, 6, 8, 10, 12, 14, 16, 20, 24, 28, 32, 36, 40, 44, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 208, 224, 240, 256, 288, 320, 384]
    if (number === 1) return ['tw-' + prefix + '-px']
    if (scale.includes(number)) return ['tw-' + prefix + '-' + number / 4]
  }
  return ['tw-' + prefix + '-' + arbitrary(value)]
}

function declarations(style) {
  try {
    const ast = postcss.parse('a{' + style + '}')
    const result = []
    for (const node of ast.first.nodes) {
      if (node.type !== 'decl') return null
      const classes = utility(node.prop, node.value)
      if (!classes) return null
      result.push(...classes)
    }
    return result.length ? [...new Set(result)] : null
  } catch {
    return null
  }
}

function staticStyle(expression) {
  if (expression?.type === 'StringLiteral') return declarations(expression.value)
  if (expression?.type !== 'ObjectExpression') return null
  const result = []
  for (const property of expression.properties) {
    if (property.type !== 'ObjectProperty' || property.computed || !['StringLiteral', 'NumericLiteral'].includes(property.value.type)) return null
    const name = kebab(property.key.name || property.key.value)
    let value = property.value.value
    // Vue's style object uses raw numbers; only these unitless properties are safe.
    if (typeof value === 'number' && value !== 0 && !['opacity', 'z-index', 'font-weight', 'line-height', 'flex-grow', 'flex-shrink'].includes(name)) return null
    const classes = utility(name, value)
    if (!classes) return null
    result.push(...classes)
  }
  return result.length ? [...new Set(result)] : null
}

export function migrateSource(source, filename) {
  const sfc = filename.endsWith('.vue') ? compiler.parseComponent(source, { deindent: false }) : null
  const edits = []
  const changes = []
  const skipped = []
  function replaceStyle(attribute, existingClass, classes, offset, original) {
    if (!classes) {
      skipped.push(original)
      return
    }
    const trimRange = attribute => {
      const text = source.slice(offset + attribute.start, offset + attribute.end)
      return { ...attribute, start: attribute.start + text.length - text.trimStart().length }
    }
    attribute = trimRange(attribute)
    if (existingClass) existingClass = trimRange(existingClass)
    if (!/^(?:style|:style|v-bind:style)=/.test(source.slice(offset + attribute.start, offset + attribute.end))) throw new Error('Style source range mismatch: ' + filename)
    if (existingClass) {
      const value = (existingClass.value + ' ' + classes.join(' ')).trim()
      edits.push({ start: offset + existingClass.start, end: offset + existingClass.end, value: 'class="' + value + '"' })
      edits.push({ start: offset + attribute.start, end: offset + attribute.end, value: '' })
    } else edits.push({ start: offset + attribute.start, end: offset + attribute.end, value: 'class="' + classes.join(' ') + '"' })
    changes.push(original)
  }
  if (sfc?.template) {
    const template = sfc.template
    const leadingSpace = template.content.length - template.content.trimStart().length
    const offset = template.start + leadingSpace
    const compiled = compiler.compile(template.content, { outputSourceRange: true })
    const seen = new Set()
    const visit = node => {
      if (!node || seen.has(node)) return
      seen.add(node)
      const attributes = node.rawAttrsMap || {}
      const inline = attributes.style
      const bound = attributes[':style'] || attributes['v-bind:style']
      if (inline && !bound) replaceStyle(inline, attributes.class, declarations(inline.value), offset, inline.value)
      if (bound && !inline) {
        let expression
        try {
          expression = parseExpression(bound.value)
        } catch {
          skipped.push(bound.value)
        }
        if (expression) replaceStyle(bound, attributes.class, staticStyle(expression), offset, bound.value)
      }
      for (const child of node.children || []) visit(child)
      for (const child of Object.values(node.scopedSlots || {})) visit(child)
      for (const condition of node.ifConditions || []) visit(condition.block)
    }
    visit(compiled.ast)
  }
  const script = sfc?.script
  const scriptSource = sfc ? script?.content : source
  if (scriptSource) {
    const offset = script?.start || 0
    const ast = parse(scriptSource, { sourceType: 'module', plugins: ['jsx', 'decorators-legacy'] })
    traverse(ast, {
      JSXOpeningElement({ node }) {
        const style = node.attributes.find(a => a.type === 'JSXAttribute' && a.name.name === 'style')
        if (!style) return
        const value = style.value?.type === 'JSXExpressionContainer' ? style.value.expression : style.value
        const classes = staticStyle(value)
        if (!classes) {
          skipped.push(scriptSource.slice(style.start, style.end))
          return
        }
        const classAttribute = node.attributes.find(a => a.type === 'JSXAttribute' && ['class', 'className'].includes(a.name.name))
        if (classAttribute?.value?.type === 'JSXExpressionContainer') {
          const expression = classAttribute.value.expression
          edits.push({
            start: offset + classAttribute.start,
            end: offset + classAttribute.end,
            value: classAttribute.name.name + '={[' + scriptSource.slice(expression.start, expression.end) + ', ' + JSON.stringify(classes.join(' ')) + ']}'
          })
          edits.push({ start: offset + style.start, end: offset + style.end, value: '' })
          changes.push(scriptSource.slice(style.start, style.end))
        } else replaceStyle(style, classAttribute ? { ...classAttribute, value: classAttribute.value?.value || '' } : null, classes, offset, scriptSource.slice(style.start, style.end))
      }
    })
  }
  let output = source
  for (const edit of edits.sort((a, b) => b.start - a.start)) output = output.slice(0, edit.start) + edit.value + output.slice(edit.end)
  return { output, changes, skipped }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const write = process.argv.includes('--write')
  const report = []
  for (const file of filesIn(path.join(root, 'src')).filter(file => /\.(vue|jsx)$/.test(file))) {
    const source = fs.readFileSync(file, 'utf8')
    const result = migrateSource(source, file)
    if (result.changes.length || result.skipped.length) report.push({ file: path.relative(root, file).replaceAll('\\', '/'), migrated: result.changes.length, remaining: result.skipped })
    if (write && result.output !== source) {
      const backup = path.join(root, 'back/logs/tailwind/before-src', path.relative(root, file))
      fs.mkdirSync(path.dirname(backup), { recursive: true })
      if (!fs.existsSync(backup)) fs.writeFileSync(backup, source)
      if (file.endsWith('.vue')) {
        const before = compiler.parseComponent(source)
        const after = compiler.parseComponent(result.output)
        const errors = component => compiler.compile(component.template?.content || '').errors
        if (JSON.stringify(errors(before)) !== JSON.stringify(errors(after))) throw new Error('Template compilation changed: ' + file)
      }
      fs.writeFileSync(file, result.output)
    }
  }
  fs.mkdirSync(path.join(root, 'back/logs/tailwind'), { recursive: true })
  fs.writeFileSync(path.join(root, 'back/logs/tailwind/migration.json'), JSON.stringify(report, null, 2))
  console.log(
    JSON.stringify({
      mode: write ? 'write' : 'check',
      files: report.filter(file => file.migrated).length,
      convertibleStyles: report.reduce((sum, file) => sum + file.migrated, 0),
      remainingStyles: report.reduce((sum, file) => sum + file.remaining.length, 0)
    })
  )
}
