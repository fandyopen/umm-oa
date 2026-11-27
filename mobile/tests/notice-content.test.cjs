const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const source = fs.readFileSync(path.join(__dirname, '../src/services/noticeContent.js'), 'utf8').replace('export function noticeNodes', 'function noticeNodes')
const noticeNodes = new Function(source + '; return noticeNodes')()

test('mobile notice rich text keeps formatting and text while removing executable attributes and elements', () => {
  const nodes = noticeNodes('<p onclick="bad()">通知 &amp; 正文<strong>加粗</strong></p><script>bad()</script><svg><script>bad()</script></svg><img src="javascript:bad()" onerror="bad()"><iframe src="https://evil.example"></iframe>')
  assert.deepEqual(nodes, [{ name: 'p', attrs: {}, children: [{ type: 'text', text: '通知 & 正文' }, { name: 'strong', attrs: {}, children: [{ type: 'text', text: '加粗' }] }] }])
})
test('notice images only use supported protocols and local public files receive the mobile API prefix', () => {
  const nodes = noticeNodes('<img src="/profile/2026/notice.png"><img src="https://example.com/notice.png"><img src="//evil.example/a"><img src="/profile/../a"><img src="javascript&#58;alert(1)">', 'https://api.example.com')
  assert.equal(nodes.length, 2)
  assert.equal(nodes[0].attrs.src, 'https://api.example.com/profile/2026/notice.png')
  assert.equal(nodes[1].attrs.src, 'https://example.com/notice.png')
})
test('nested lists and encoded angle brackets stay as text rather than introducing HTML', () => {
  const nodes = noticeNodes('<ul><li>一</li><li>&lt;script&gt;文字&lt;/script&gt; &#x1F44D;</li></ul>')
  assert.equal(nodes[0].children.length, 2)
  assert.equal(nodes[0].children[1].children[0].text, '<script>文字</script> 👍')
})
