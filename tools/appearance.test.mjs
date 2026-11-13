import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeAppearance, appearanceTokens, appearanceStorageKey, applyAppearance, DEFAULT_CUSTOM, contrastText } from '../src/utils/appearance.mjs'

test('a first visit and damaged saved settings both fall back to the light theme', () => {
  for (const value of [undefined, null, '', { mode: 'unknown' }, { custom: null }]) {
    assert.equal(normalizeAppearance(value).mode, 'light')
    assert.equal(appearanceTokens(value)['--umm-primary'], '#0eb3a2')
    assert.equal(appearanceTokens(value)['--umm-header-bg'], '#0eb3a2')
    assert.equal(appearanceTokens(value)['--umm-primary-text'], '#ffffff')
  }
})

test('stored custom settings reject CSS injection and incomplete colors', () => {
  const preferences = normalizeAppearance({ mode: 'custom', custom: { primary: '#fff; background:url(x)', header: 'invalid', background: 'image', backgroundColor: '#123' } })
  assert.deepEqual(preferences.custom, DEFAULT_CUSTOM)
  const valid = normalizeAppearance({ mode: 'custom', custom: { primary: '#ABCDEF', background: 'solid', backgroundColor: '#012345', header: 'primary' } })
  assert.equal(valid.custom.primary, '#abcdef')
  assert.equal(appearanceTokens(valid)['--umm-page-bg'], '#012345')
})

test('preset switching preserves custom preferences and does not mutate the saved value', () => {
  const saved = { mode: 'custom', custom: { ...DEFAULT_CUSTOM, primary: '#8b5cf6' } }
  const blue = normalizeAppearance({ ...saved, mode: 'blue' })
  assert.equal(appearanceTokens(blue)['--umm-primary'], '#348ee4')
  assert.equal(blue.custom.primary, saved.custom.primary)
  blue.custom.primary = '#ffffff'
  assert.equal(saved.custom.primary, '#8b5cf6')
})

test('each account has a separate storage key without ambiguous separators', () => {
  assert.notEqual(appearanceStorageKey('17600000001'), appearanceStorageKey('13610316814'))
  assert.notEqual(appearanceStorageKey('a:b'), appearanceStorageKey('a%3Ab'))
})

test('very pale and very dark custom colors receive legible text and link colors', () => {
  assert.equal(contrastText('#ffffff'), '#152a3c')
  assert.equal(contrastText('#000000'), '#ffffff')
  for (const primary of ['#ffffff', '#ffff00', '#000000', '#0eb3a2', '#8b5cf6']) {
    const tokens = appearanceTokens({ mode: 'custom', custom: { primary } })
    const channels = tokens['--umm-link'].match(/[0-9a-f]{2}/g).map(c => parseInt(c, 16) / 255).map(c => c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4)
    const luminance = channels.reduce((sum, c, i) => sum + c * [.2126, .7152, .0722][i], 0)
    assert.ok(1.05 / (luminance + .05) >= 4.5, `Unreadable link for ${primary}`)
  }
})

test('applying another theme replaces existing tokens instead of retaining the previous background', () => {
  const properties = new Map()
  const root = { dataset: {}, style: { setProperty: (name, value) => properties.set(name, value) } }
  applyAppearance({ mode: 'custom', custom: { background: 'solid', backgroundColor: '#000000' } }, root)
  assert.equal(properties.get('--umm-page-bg'), '#000000')
  applyAppearance({ mode: 'light' }, root)
  assert.equal(root.dataset.appearance, 'light')
  assert.match(properties.get('--umm-page-bg'), /radial-gradient/)
})
