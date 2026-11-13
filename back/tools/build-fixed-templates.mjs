// Development tool: regenerate committed, empty workbooks from the backend catalogs.
// Requires @oai/artifact-tool; the production backend only serves the generated resources.
import fs from 'node:fs/promises'
import path from 'node:path'
const { Workbook, SpreadsheetFile } = await import(process.env.ARTIFACT_TOOL_MODULE || '@oai/artifact-tool')
const root = path.resolve(process.argv[2] || 'back/src/main/resources/templates')
const previewDir = process.argv[3]
const catalog = JSON.parse(await fs.readFile(path.join(root, 'hr/catalog.json'), 'utf8'))
const member = JSON.parse(await fs.readFile(path.join(root, 'members/catalog.json'), 'utf8'))
const types = catalog.groups.flatMap(group => group.types)
const employmentTypes = '直接聘用（direct）、劳务派遣（dispatch）、实习（internship）、劳务合作（labor）'
const general = '空模板不含示例人员；前500行的编号、银行卡、日期等字段已设为文本。超过500行请复制已有空白行格式。工号或已绑定钉钉人员ID用于匹配，不能仅凭姓名。缺失数据不能默认当零；工资调整回传必须使用实际批次导出文件。'
const col = index => {
  let result = ''
  for (let n = index + 1; n > 0; n = Math.floor((n - 1) / 26)) result = String.fromCharCode(65 + ((n - 1) % 26)) + result
  return result
}
function dataSheet(book, name, schema) {
  const sheet = book.worksheets.add(name)
  sheet.showGridLines = false
  const last = col(schema.fields.length - 1)
  sheet.getRange(`A1:${last}501`).format.font = { name: 'Arial', size: 10 }
  sheet.getRange(`A1:${last}1`).values = [schema.fields.map(field => field.label)]
  sheet.getRange(`A1:${last}1`).format = {
    fill: '#164E63',
    font: { name: 'Arial', size: 10, color: '#FFFFFF', bold: true },
    horizontalAlignment: 'center',
    verticalAlignment: 'center',
    rowHeight: 42,
    wrapText: true
  }
  schema.fields.forEach((field, index) => {
    sheet.getRange(`${col(index)}1:${col(index)}501`).format.columnWidth = Math.min(34, Math.max(16, field.label.length * 2 + 2))
    sheet.getRange(`${col(index)}2:${col(index)}501`).setNumberFormat(['text', 'date'].includes(field.type) ? '@' : field.type === 'money' ? '#,##0.00' : '#,##0.######')
  })
  const payType = schema.fields.findIndex(field => field.key === 'payType')
  if (payType >= 0) sheet.getRange(`${col(payType)}2:${col(payType)}501`).dataValidation = { rule: { type: 'list', values: ['monthly', 'daily'] } }
  sheet.freezePanes.freezeRows(1)
}
function instructions(book, schemas, memberMode = false) {
  const sheet = book.worksheets.add('填写说明')
  sheet.showGridLines = false
  const rows = [
    ['模板 / 报表', '说明', '字段', '要求'],
    ['通用说明', memberMode ? '本模板用于邀请成员，手机号为文本；前500行已设置文本格式。请勿填写真实人员到模板资源文件中。' : general, '', '']
  ]
  for (const schema of schemas) {
    rows.push([schema.name, schema.note, '', ''])
    for (const field of schema.fields) {
      const requirement = field.key === 'employmentType' ? '用工关系；' + employmentTypes : { number: '数字', money: '金额（元），两位小数', date: 'YYYY-MM-DD', text: '文本，编号请保持文本格式' }[field.type]
      rows.push(['', '', field.label, (field.required ? '必填' : '选填') + '；' + requirement])
    }
  }
  sheet.getRange(`A1:D${rows.length}`).values = rows
  sheet.getRange(`A1:D${rows.length}`).format = { font: { name: 'Arial', size: 10 }, verticalAlignment: 'top', wrapText: true, rowHeight: 34 }
  sheet.getRange('A1:D1').format = { fill: '#164E63', font: { color: '#FFFFFF', bold: true }, rowHeight: 26 }
  ;[28, 100, 30, 65].forEach((width, index) => {
    sheet.getRange(`${col(index)}1:${col(index)}${rows.length}`).format.columnWidth = width
  })
  rows.forEach((row, index) => {
    if (row[1]) sheet.getRange(`A${index + 1}:D${index + 1}`).format.rowHeight = 64
  })
  sheet.freezePanes.freezeRows(1)
}
async function save(book, relative, previewName) {
  book.recalculate()
  await (await SpreadsheetFile.exportXlsx(book)).save(path.join(root, relative))
  // Artifact inspection sidecars belong in QA output, not the packaged template directory.
  await fs.unlink(path.join(root, relative + '.inspect.ndjson')).catch(error => {
    if (error.code !== 'ENOENT') throw error
  })
  if (previewDir && previewName) {
    await fs.mkdir(previewDir, { recursive: true })
    const preview = await book.render({ sheetName: '数据', range: 'A1:H7', scale: 1.5, format: 'png' })
    await fs.writeFile(path.join(previewDir, previewName + '.png'), new Uint8Array(await preview.arrayBuffer()))
    console.log((await book.inspect({ kind: 'region', sheetId: '数据', range: 'A1:H2', maxChars: 1500 })).ndjson)
  }
  console.log('Created ' + relative)
}
if (!process.argv.includes('--business-only') && !process.argv.includes('--members-only')) {
  for (const type of types) {
    const book = Workbook.create()
    dataSheet(book, '数据', catalog.templates[type])
    instructions(book, [catalog.templates[type]])
    await save(book, `hr/${type}.xlsx`, ['salary', 'employee', 'payment'].includes(type) ? type : null)
  }
  const pack = Workbook.create()
  for (const type of types) dataSheet(pack, catalog.templates[type].name.slice(0, 31), catalog.templates[type])
  instructions(
    pack,
    types.map(type => catalog.templates[type])
  )
  await save(pack, 'hr/all.xlsx')
}
if (!process.argv.includes('--business-only')) {
  const members = Workbook.create()
  dataSheet(members, '数据', member)
  instructions(members, [member], true)
  await save(members, 'members/address-book.xlsx', 'address-book')
}
const business = JSON.parse(await fs.readFile(path.join(root, 'business/catalog.json'), 'utf8'))
for (const [type, schema] of Object.entries(process.argv.includes('--members-only') ? {} : business.templates)) {
  const book = Workbook.create()
  dataSheet(book, '数据', schema)
  instructions(book, [schema])
  await save(book, `business/${type}.xlsx`, type)
}
