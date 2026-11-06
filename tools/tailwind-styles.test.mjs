import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
import test from 'node:test'
import compiler from 'vue-template-compiler'
import postcss from 'postcss'
import tailwindcss from 'tailwindcss'
import selectorParser from 'postcss-selector-parser'
import { migrateSource } from './migrate-static-styles.mjs'

const require = createRequire(import.meta.url)
const config = require('../tailwind.config.js')
const filesIn = directory => fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => (entry.isDirectory() ? filesIn(path.join(directory, entry.name)) : [path.join(directory, entry.name)]))

test('Vue migration preserves CRLF, multiline attributes, scoped slots, bindings and scripts', () => {
  const source = `<template>\r\n  <a-table :data-source="rows">\r\n    <span slot="value" slot-scope="row" class="existing" :class="row.type"\r\n      style="font-size: 25px; margin: 0 auto; text-align: center">{{ row.name }}</span>\r\n    <template v-if="open"><a-input :style="{ width: '100%' }" /></template>\r\n  </a-table>\r\n</template>\r\n<script>export default { data: () => ({ rows: [], open: true }) }</script>`
  const result = migrateSource(source, 'Example.vue')
  assert.equal(result.changes.length, 2)
  assert.match(result.output, /class="existing tw-text-\[25px\].*tw-text-center" :class="row.type"/)
  assert.match(result.output, /<a-input class="tw-w-full"/)
  assert.equal(compiler.parseComponent(result.output).script.content, compiler.parseComponent(source).script.content)
  assert.deepEqual(compiler.compile(compiler.parseComponent(result.output).template.content).errors, [])
  assert.equal(migrateSource(result.output, 'Example.vue').changes.length, 0)
})

test('JSX migration retains dynamic classes and leaves data-driven styles intact', () => {
  const source = `const render = () => <div><span class={active ? 'a' : 'b'} style="margin-left: 8px" /><span style={{ width: size + 'px' }}/><span style={{ width: '100%' }} /></div>`
  const result = migrateSource(source, 'Example.jsx')
  assert.equal(result.changes.length, 2)
  assert.match(result.output, /class=\{\[active \? 'a' : 'b', "tw-ml-2"\]\}/)
  assert.ok(result.output.includes("style={{ width: size + 'px' }}"))
  assert.match(result.output, /class="tw-w-full"/)
  assert.equal(migrateSource(result.output, 'Example.jsx').output, result.output)
})

test('runtime style overrides remain inline and complex business-specific styles are preserved', () => {
  const source = `<template><div><span style="width: 100%" :style="dynamicStyle" /><span :style="{ width: width + '%' }" /><span style="border-right: 1px solid #ccc" /></div></template>`
  assert.equal(migrateSource(source, 'Example.vue').output, source)
})

test('every migrated utility is emitted, retains pixel values and avoids component resets', async () => {
  const files = filesIn('src').filter(file => /\.(vue|jsx|js|css)$/.test(file))
  const content = files.map(file => ({ raw: fs.readFileSync(file, 'utf8'), extension: path.extname(file).slice(1) }))
  const result = await postcss([tailwindcss({ ...config, content })]).process(fs.readFileSync('src/assets/style/tailwind.css', 'utf8'), { from: undefined })
  const rules = new Map()
  result.root.walkRules(rule => {
    selectorParser(selectors => selectors.walkClasses(node => rules.set(node.value, rule))).processSync(rule.selector)
  })
  const used = new Set(content.flatMap(file => file.raw.match(/\btw-[^\s"'<>`{};]+/g) || []))
  for (const token of used) assert.ok(rules.has(token), 'Missing Tailwind utility: ' + token)
  assert.ok(used.size > 50)
  const declarations = token =>
    Object.fromEntries(
      rules
        .get(token)
        .nodes.filter(node => node.type === 'decl')
        .map(node => [node.prop, node.value])
    )
  assert.deepEqual(declarations('tw-text-sm'), { 'font-size': '14px' }, 'font utility must not change existing line height')
  assert.equal(declarations('tw-mb-4')['margin-bottom'], '16px')
  assert.equal(declarations('tw-max-h-[70vh]')['max-height'], '70vh')
  assert.ok(rules.get('tw-w-full').nodes.every(node => node.important))
  assert.equal(declarations('app-control-full').width, '100%')
  assert.ok(
    rules.get('app-control-full').nodes.every(node => !node.important),
    'component defaults must remain overridable by parent classes and styles'
  )
  assert.equal(config.corePlugins.preflight, false)
  assert.equal(config.prefix, 'tw-')
})

test('remaining Vue/JSX inline styles cannot be automatically replaced by the supported utilities', () => {
  for (const file of filesIn('src').filter(file => /\.(vue|jsx)$/.test(file))) {
    const result = migrateSource(fs.readFileSync(file, 'utf8'), file)
    assert.equal(result.changes.length, 0, 'Reusable inline style introduced in ' + file)
  }
})
