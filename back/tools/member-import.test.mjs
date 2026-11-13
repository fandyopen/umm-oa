import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import * as XLSX from 'xlsx'
import compiler from 'vue-template-compiler'
import { parseMemberImport } from '../../src/utils/memberImport.mjs'

const schema = JSON.parse(fs.readFileSync(new URL('../src/main/resources/templates/members/catalog.json', import.meta.url), 'utf8'))
const file = (headers, rows, formula = false) => {
  const book = XLSX.utils.book_new()
  const sheet = XLSX.utils.aoa_to_sheet([headers, ...rows])
  if (formula) sheet.D2 = { t: 's', f: '"部门"', v: '部门' }
  XLSX.utils.book_append_sheet(book, sheet, '数据')
  return XLSX.write(book, { type: 'buffer', bookType: 'xlsx' })
}

test('new and legacy department headers parse IDs and names without losing physical rows or phone zeros', () => {
  for (const label of ['部门ID/部门', '部门ID', '部门', '部门名称']) {
    const bytes = file(['姓名', '手机号码', '邮箱', label], [[], ['甲', '00123456789', '', ' 综合部 '], ['乙', '13900001234', '', 324]])
    const rows = parseMemberImport(bytes, schema)
    assert.equal(rows.length, 2)
    assert.equal(rows[0].rowNumber, 3)
    assert.equal(rows[0].department, '综合部')
    assert.equal(rows[0].phonenumber, '00123456789')
    assert.equal(rows[1].department, '324')
  }
})
test('rejects missing or ambiguous department columns, empty sheets, formulas and oversized batches', () => {
  assert.throws(() => parseMemberImport(file(['姓名', '手机号码'], [['甲', '13900000001']]), schema), /缺少表头：部门ID\/部门/)
  assert.throws(() => parseMemberImport(file(['姓名', '手机号码', '部门ID', '部门'], [['甲', '13900000001', 324, '综合部']]), schema), /只保留一列/)
  const headers = ['姓名', '手机号码', '邮箱', '部门ID/部门']
  assert.throws(() => parseMemberImport(file(headers, []), schema), /1至200/)
  assert.throws(() => parseMemberImport(file(headers, [['甲', '13900000001', '', '部门']], true), schema), /第2行.*不能使用公式/)
  assert.throws(() => parseMemberImport(file(headers, Array.from({ length: 201 }, () => ['甲', '13900000001', '', 324])), schema), /1至200/)
})
test('downloaded backend member template uses the new required department column', () => {
  const book = XLSX.read(fs.readFileSync(new URL('../src/main/resources/templates/members/address-book.xlsx', import.meta.url)), { type: 'buffer', cellNF: true, sheetStubs: true })
  assert.equal(book.Sheets['数据'].D1.v, '部门ID/部门')
  assert.equal(book.Sheets['数据'].D501.z, '@')
  assert.equal(schema.fields.find(field => field.key === 'department').required, true)
  const guide = JSON.stringify(XLSX.utils.sheet_to_json(book.Sheets['填写说明'], { header: 1 }))
  assert.match(guide, /重名/)
})
test('member import preview and approval views compile', () => {
  for (const path of ['src/views/enterprise/components/memberElement/index.vue', 'src/views/workflow/todoDetail.vue']) {
    const source = fs.readFileSync(new URL('../../' + path, import.meta.url), 'utf8')
    assert.deepEqual(compiler.compile(compiler.parseComponent(source).template.content).errors, [], path)
  }
})
