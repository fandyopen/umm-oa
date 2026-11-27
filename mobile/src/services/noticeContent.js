// Build allowlisted rich-text nodes for both H5 and mini programs, without a DOM dependency.
const tags = new Set('p div span br strong em b i u ul ol li h1 h2 h3 h4 blockquote table tr td th tbody thead img hr pre code'.split(' '))
const decode = value => value.replace(/&(#x[\da-f]+|#\d+|amp|lt|gt|quot|apos|nbsp);/gi, (match, entity) => {
  const named = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ' }
  if (entity[0] !== '#') return named[entity.toLowerCase()] || match
  const code = entity[1].toLowerCase() === 'x' ? parseInt(entity.slice(2), 16) : parseInt(entity.slice(1), 10)
  return code > 0 && code <= 0x10ffff ? String.fromCodePoint(code) : ''
})
export function noticeNodes(html, apiBase = '') {
  const root = { children: [] }, stack = [root]
  const source = String(html || '').replace(/<!--[\s\S]*?-->/g, '').replace(/<(script|style|iframe|object|svg|math)\b[^>]*>[\s\S]*?<\/\1\s*>/gi, '')
  for (const token of source.match(/<[^>]*>|[^<]+/g) || []) {
    if (token[0] !== '<') { stack[stack.length - 1].children.push({ type: 'text', text: decode(token) }); continue }
    const match = token.match(/^<\s*(\/?)\s*([a-z0-9]+)/i)
    if (!match) continue
    const name = match[2].toLowerCase()
    if (!tags.has(name)) continue
    if (match[1]) {
      const at = stack.map(node => node.name).lastIndexOf(name)
      if (at > 0) stack.length = at
      continue
    }
    const node = { name, attrs: {}, children: [] }
    if (name === 'img') {
      const attribute = token.match(/\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/i)
      let src = decode(attribute ? attribute[1] || attribute[2] || attribute[3] || '' : '').trim()
      if (/^\/profile\/[\w/.-]+$/.test(src) && !src.includes('..')) src = apiBase + src
      else if (!/^https:\/\/[^\s<>"']+$/i.test(src) && !/^data:image\/(png|jpeg|gif|webp);base64,[a-z0-9+/=]+$/i.test(src)) continue
      node.attrs = { src, style: 'max-width:100%;height:auto' }
    }
    stack[stack.length - 1].children.push(node)
    if (!['br', 'hr', 'img'].includes(name) && !/\/\s*>$/.test(token)) stack.push(node)
  }
  return root.children
}
