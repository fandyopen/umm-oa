export const DEFAULT_CUSTOM = Object.freeze({ primary: '#0eb3a2', header: 'light', background: 'gradient', backgroundColor: '#f3f9fa' })
export const DEFAULT_APPEARANCE = Object.freeze({ mode: 'light', custom: DEFAULT_CUSTOM })
export const PRESET_COLORS = ['#0eb3a2', '#348ee4', '#6366f1', '#8b5cf6', '#db2777', '#c2410c']

export function isHexColor(value) {
  return typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value)
}

export function normalizeAppearance(value = {}) {
  const custom = value?.custom || {}
  return {
    mode: ['blue', 'light', 'custom'].includes(value?.mode) ? value.mode : 'light',
    custom: {
      primary: isHexColor(custom.primary) ? custom.primary.toLowerCase() : DEFAULT_CUSTOM.primary,
      header: custom.header === 'primary' ? 'primary' : 'light',
      background: custom.background === 'solid' ? 'solid' : 'gradient',
      backgroundColor: isHexColor(custom.backgroundColor) ? custom.backgroundColor.toLowerCase() : DEFAULT_CUSTOM.backgroundColor
    }
  }
}

export const appearanceStorageKey = username => `umm:appearance:v1:${encodeURIComponent(username)}`

function rgb(hex) {
  return [1, 3, 5].map(start => parseInt(hex.slice(start, start + 2), 16))
}

function mix(hex, target, weight) {
  return '#' + rgb(hex).map((channel, i) => Math.round(channel * (1 - weight) + rgb(target)[i] * weight).toString(16).padStart(2, '0')).join('')
}

function luminance(hex) {
  return rgb(hex).map(channel => {
    const c = channel / 255
    return c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4
  }).reduce((sum, c, i) => sum + c * [.2126, .7152, .0722][i], 0)
}

export function contrastText(hex) {
  const background = luminance(hex)
  if ((background + .05) / (luminance('#152a3c') + .05) >= 4.5) return '#152a3c'
  if (1.05 / (background + .05) >= 4.5) return '#ffffff'
  return '#000000'
}

export function appearanceTokens(value) {
  const { mode, custom } = normalizeAppearance(value)
  const primary = mode === 'blue' ? '#348ee4' : mode === 'light' ? '#0eb3a2' : custom.primary
  let link = primary
  while (luminance(link) > .183) link = mix(link, '#000000', .12)
  const header = mode !== 'custom' || custom.header === 'primary'
  const headerText = header ? '#ffffff' : '#405769'
  const [r, g, b] = rgb(primary)
  return {
    '--umm-primary': primary,
    '--umm-primary-hover': mix(primary, '#000000', .12),
    '--umm-primary-active': mix(primary, '#000000', .22),
    '--umm-primary-text': '#ffffff',
    '--umm-primary-soft': mix(primary, '#ffffff', .91),
    '--umm-primary-border': mix(primary, '#ffffff', .7),
    '--umm-focus-ring': `rgba(${r}, ${g}, ${b}, .16)`,
    '--umm-link': link,
    '--umm-header-bg': header ? primary : 'rgba(255, 255, 255, .94)',
    '--umm-header-text': headerText,
    '--umm-header-selected': header ? mix(primary, '#000000', .13) : mix(primary, '#ffffff', .91),
    '--umm-header-active-text': header ? headerText : link,
    '--umm-border': '#e0eaed',
    '--umm-text': '#21374a',
    '--umm-muted': '#718596',
    '--umm-radius': '14px',
    '--umm-page-bg': mode === 'custom' && custom.background === 'solid' ? custom.backgroundColor :
      `radial-gradient(ellipse at 85% 0%, rgba(${r}, ${g}, ${b}, .12), transparent 33%), radial-gradient(ellipse at 5% 100%, rgba(163, 215, 242, .25), transparent 30%), linear-gradient(135deg, #f6fafb, #f0f8f8 55%, #f4f8fc)`
  }
}

export function applyAppearance(value, root = document.documentElement) {
  const normalized = normalizeAppearance(value)
  root.dataset.appearance = normalized.mode
  Object.entries(appearanceTokens(normalized)).forEach(([key, token]) => root.style.setProperty(key, token))
  const browser = root.ownerDocument?.defaultView
  if (browser) browser.dispatchEvent(new browser.Event('umm:appearance-change'))
}
