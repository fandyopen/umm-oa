import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import { parse } from '@babel/parser'
import compiler from 'vue-template-compiler'

const require = createRequire(import.meta.url)
const Vue = require('vue')
const read = file => fs.readFileSync(new URL('../../src/views/enterprise/hr/' + file, import.meta.url), 'utf8')
function member(file, section, name, dependencies = {}) {
  const source = compiler.parseComponent(read(file)).script.content
  const ast = parse(source, { sourceType: 'module' })
  const definition = ast.program.body.find(node => node.type === 'ExportDefaultDeclaration').declaration
  const property = definition.properties.find(node => node.key.name === section)
  const target = name ? property.value.properties.find(node => node.key.name === name) : property
  return new Function(...Object.keys(dependencies), 'return ({' + source.slice(target.start, target.end) + '}).' + (name || section))(...Object.values(dependencies))
}

test('omitted archive dates remain reactive when picked, cleared and loaded again', async () => {
  const vm = new Vue({
    data: member('employees.vue', 'data', null, { statusNames: {}, eventNames: {} }),
    methods: { setEmployee: member('employees.vue', 'methods', 'setEmployee') }
  })
  const response = { userId: 4, employeeNo: 'EMP4' }
  vm.setEmployee(response)
  assert.equal('entryDate' in response, false, 'normalization must not mutate the API response')
  const changes = []
  for (const field of ['entryDate', 'regularDate', 'contractStart', 'contractEnd']) {
    assert.equal(vm.employee[field], null)
    vm.$watch(() => vm.employee[field], value => changes.push([field, value]))
    Vue.set(vm.employee, field, '2026-11-05')
    await Vue.nextTick()
    Vue.set(vm.employee, field, null)
    await Vue.nextTick()
  }
  assert.equal(changes.length, 8, 'both choosing and clearing each optional date must notify the view')
  vm.setEmployee({ ...response, entryDate: '2026-11-05T00:00:00+08:00' })
  await Vue.nextTick()
  assert.equal(vm.employee.entryDate, '2026-11-05', 'dates must keep their original calendar day')
  assert.equal(vm.employee.regularDate, null)
  vm.$destroy()
})

test('loading an archive establishes empty date fields before displaying the form', async () => {
  const response = { userId: 4, employeeNo: 'EMP4' }
  const vm = {
    profileFields: [{ key: 'entryDate', date: true }, { key: 'regularDate', date: true }],
    setEmployee: member('employees.vue', 'methods', 'setEmployee')
  }
  await member('employees.vue', 'methods', 'open', { hr: async () => ({ data: response }) }).call(vm, { userId: 4 })
  assert.equal(vm.employee.entryDate, null)
  assert.equal(vm.employee.regularDate, null)
  assert.equal(vm.tab, 'profile')
})

test('empty attendance month neither crashes the calendar nor sends an unbounded request', async () => {
  const vm = { period: null, batches: [{}], batchId: 1, detail: {} }
  assert.equal(member('AttendanceWorkspace.vue', 'computed', 'dayCount').call(vm), 0)
  let requests = 0
  await member('AttendanceWorkspace.vue', 'methods', 'load', { hr: () => { requests++; throw new Error('unexpected request') } }).call(vm)
  assert.equal(requests, 0)
  assert.deepEqual(vm.batches, [])
  assert.equal(vm.detail, null)
  assert.equal(member('AttendanceWorkspace.vue', 'computed', 'dayCount').call({ period: '2024-02' }), 29)
})

test('generating payroll after clearing the list filter still uses a valid month and resets attendance', async () => {
  const calls = []
  const vm = {
    period: null,
    guard: async () => true,
    loadAttendance: async () => { calls.push('load') }
  }
  await member('PayrollWorkspace.vue', 'methods', 'openGenerate').call(vm, 'generate')
  assert.match(vm.generateForm.period, /^[0-9]{4}-[0-9]{2}$/)
  assert.equal(vm.generateVisible, true)
  assert.deepEqual(calls, ['load'])
  vm.generateForm.period = null
  vm.generateForm.attendanceId = 17
  vm.attendanceBatches = [{ id: 17 }]
  await member('PayrollWorkspace.vue', 'methods', 'loadAttendance', { hr: () => assert.fail('empty month sent to API') }).call(vm)
  assert.equal(vm.generateForm.attendanceId, undefined)
  assert.deepEqual(vm.attendanceBatches, [])
})
