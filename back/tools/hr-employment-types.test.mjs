import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import { createRequire } from 'node:module'
import { parse } from '@babel/parser'
import compiler from 'vue-template-compiler'

const require = createRequire(import.meta.url)
const XLSX = require('xlsx')
const read = file => fs.readFileSync(new URL('../../' + file, import.meta.url), 'utf8')
const load = (file, dependencies, names) =>
  new Function(
    ...Object.keys(dependencies),
    read(file)
      .replace(/^import .*$/gm, '')
      .replace(/export /g, '') +
      ';return {' +
      names.join(',') +
      '}'
  )(...Object.values(dependencies))
const options = [
  { value: 'direct', label: '直接聘用' },
  { value: 'dispatch', label: '劳务派遣' }
]
const types = load('src/views/enterprise/hr/employmentTypes.js', { hr: async () => ({ data: { employmentTypes: options } }) }, ['loadEmploymentTypes', 'normalizeEmploymentType'])
const templates = load('src/views/enterprise/hr/templates.js', { XLSX, saveAs() {}, loadEmploymentTypes: types.loadEmploymentTypes }, ['templates', 'buildWorkbook', 'convertCell', 'suggestColumn'])

test('employment options come from the API and import accepts only its labels and codes', async () => {
  assert.deepEqual(await types.loadEmploymentTypes(), options)
  assert.equal(types.normalizeEmploymentType(' 直接聘用 ', options), 'direct')
  assert.equal(types.normalizeEmploymentType('dispatch', options), 'dispatch')
  assert.equal(types.normalizeEmploymentType(null, options), '')
  assert.equal(types.normalizeEmploymentType('更名后的类型', [{ value: 'stable-code', label: '更名后的类型' }]), 'stable-code')
  assert.throws(() => types.normalizeEmploymentType('试用', options), /不在系统选项中/)
  const unavailable = load('src/views/enterprise/hr/employmentTypes.js', { hr: async () => ({ data: {} }) }, ['loadEmploymentTypes'])
  await assert.rejects(unavailable.loadEmploymentTypes(), /加载失败/)
})

test('employee import previews canonical codes and reports invalid employment types with row numbers', () => {
  const source = compiler.parseComponent(read('src/components/HrExcelImport/index.vue')).script.content
  const ast = parse(source, { sourceType: 'module' })
  const definition = ast.program.body.find(node => node.type === 'ExportDefaultDeclaration').declaration
  const methods = definition.properties.find(node => node.key.name === 'methods').value
  const method = methods.properties.find(node => node.key.name === 'payload')
  const payload = new Function('convertCell', 'normalizeEmploymentType', 'return ({' + source.slice(method.start, method.end) + '}).payload')(value => String(value ?? '').trim(), types.normalizeEmploymentType)
  const vm = { schema: { fields: [{ key: 'employmentType', label: '用工类型' }] }, mapping: { employmentType: '0' }, raw: [['直接聘用'], ['试用']], headerRow: 1, extra: {} }
  assert.equal(payload.call(vm, options).rows[0].employmentType, 'direct')
  assert.match(vm.errors[0], /第3行.*试用/)
  const computed = definition.properties.find(node => node.key.name === 'computed').value
  const columnsMethod = computed.properties.find(node => node.key.name === 'previewColumns')
  const columns = new Function('return ({' + source.slice(columnsMethod.start, columnsMethod.end) + '}).previewColumns')()
  vm.employmentTypes = options
  assert.equal(columns.call(vm)[0].customRender('direct'), '直接聘用', 'preview displays labels rather than stored codes')
  vm.raw = [['']]
  assert.deepEqual(payload.call(vm, options).rows, [{}], 'blank optional type must not overwrite existing values')
})

test('downloaded template instructions use API labels and codes without sample employees', () => {
  const book = templates.buildWorkbook([{ type: 'employee' }], options)
  const restored = XLSX.read(XLSX.write(book, { type: 'buffer', bookType: 'xlsx' }), { type: 'buffer' })
  const guide = XLSX.utils.sheet_to_json(restored.Sheets['填写说明'], { header: 1 })
  const field = guide.find(row => row[2] === '用工类型')
  assert.match(field[3], /直接聘用（direct）/)
  assert.match(field[3], /劳务派遣（dispatch）/)
  const filled = XLSX.utils
    .sheet_to_json(restored.Sheets['数据'], { header: 1 })
    .slice(1)
    .filter(row => row.some(value => value !== '' && value != null))
  assert.equal(filled.length, 0)
})

test('partial archive import requires a version and an actual employee identifier while preserving blanks', () => {
  const source = compiler.parseComponent(read('src/components/HrExcelImport/index.vue')).script.content
  const definition = parse(source, { sourceType: 'module' }).program.body.find(node => node.type === 'ExportDefaultDeclaration').declaration
  const method = definition.properties.find(node => node.key.name === 'methods').value.properties.find(node => node.key.name === 'payload')
  const payload = new Function('convertCell', 'normalizeEmploymentType', 'return ({' + source.slice(method.start, method.end) + '}).payload')(templates.convertCell, types.normalizeEmploymentType)
  const schema = templates.templates.employee
  const vm = { schema, mapping: { employeeNo: '0', dingtalkUserId: '1', revision: '2', emergencyContact: '3', entryDate: '4' }, raw: [['EMP2', '', 3, '新联系人', '']], headerRow: 1, extra: {} }
  assert.deepEqual(payload.call(vm, options).rows, [{ employeeNo: 'EMP2', revision: 3, emergencyContact: '新联系人' }])
  assert.deepEqual(vm.errors, [], 'partial update does not require an entry date')
  vm.raw = [['', 'ding-2', 3, '新联系人', '']]
  payload.call(vm, options)
  assert.deepEqual(vm.errors, [], 'a bound DingTalk ID can identify the employee')
  vm.raw = [['', '', '', '新联系人', '']]
  payload.call(vm, options)
  assert.match(vm.errors.join('；'), /第2行缺少档案版本/)
  assert.match(vm.errors.join('；'), /第2行缺少员工编号或钉钉人员ID/)
  const book = templates.buildWorkbook([{ type: 'employeeReport', records: [{ employeeNo: 'EMP2', revision: 3 }] }], options)
  assert.equal(XLSX.utils.sheet_to_json(book.Sheets['数据'])[0]['档案版本'], 3, 'export supplies the version required for import')
})

test('employee reports display labels while preserving unclassified historical text', () => {
  const reports = load('src/views/enterprise/hr/reports.js', { statusNames: { active: '在职' } }, ['employeeRecords'])
  const rows = reports.employeeRecords([{ employmentType: 'direct', employmentTypeName: '直接聘用', employmentStatus: 'active' }, { employmentType: '历史原值' }])
  assert.equal(rows[0].employmentType, '直接聘用')
  assert.equal(rows[0].employmentStatus, '在职')
  assert.equal(rows[1].employmentType, '历史原值')
})

test('salary report can be mapped back to the salary import schema', () => {
  const reports = load('src/views/enterprise/hr/reports.js', { statusNames: { effective: '已生效' } }, ['salaryStandardRecords'])
  const records = reports.salaryStandardRecords([{ employeeNo: 'EMP2', employeeName: '测试员工', effectiveDate: '2026-11-01', payType: 'monthly', baseAmount: 10000, allowance: 500, reason: '年度定薪', status: 'effective' }])
  const book = templates.buildWorkbook([{ type: 'salaryReport', records }], options)
  const rows = XLSX.utils.sheet_to_json(book.Sheets['数据'], { header: 1 })
  const headers = rows[0]
  for (const field of templates.templates.salary.fields) assert.equal(templates.suggestColumn(headers, field), field.label)
  assert.equal(rows[1][headers.indexOf('计薪方式（monthly或daily）')], 'monthly')
})
