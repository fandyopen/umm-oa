import assert from 'node:assert/strict'
import fs from 'node:fs'

const origin = process.env.HR_SMOKE_API || 'http://localhost:8080'
const response = await fetch(origin + '/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: process.env.HR_SMOKE_USERNAME, password: process.env.HR_SMOKE_PASSWORD })
}).then(r => r.json())
assert.equal(response.code, 200, 'local login failed')
const headers = { Authorization: 'Bearer ' + response.token }
const paths = [
  '/getRouters', '/project/hr/employees', '/project/hr/options', '/project/hr/changes',
  '/project/hr/attendance', '/project/hr/payroll', '/project/hr/finance/payroll',
  '/project/hr/payroll/attendance-options?period=2026-10',
  '/project/hr/reports/employees', '/project/hr/reports/changes',
  '/project/hr/reports/payroll?period=2026-10',
  '/project/hr/reports/payroll?period=2026-10&finance=true',
  '/project/hr/self/attendance', '/project/hr/self/payslips',
  '/project/crm/finance/receipts?pageNum=1&pageSize=1000'
]
const results = await Promise.all(paths.map(async route => {
  const result = await fetch(origin + route, { headers }).then(r => r.json())
  assert.equal(result.code, 200, route + ': ' + result.msg)
  if (route === '/getRouters') {
    const components = []
    const visit = rows => rows.forEach(row => { components.push(row.component); visit(row.children || []) })
    visit(result.data)
    for (const component of ['enterprise/hr/employees', 'enterprise/hr/changes', 'enterprise/hr/attendance',
      'enterprise/hr/payroll', 'enterprise/hr/self', 'eos/crm/finance/payroll']) {
      assert.ok(components.includes(component), 'menu missing: ' + component)
    }
  }
  if (route === '/project/hr/reports/employees') {
    for (const row of result.data) {
      for (const key of ['bankAccount', 'accountName', 'salaries', 'attachments', 'events']) {
        assert.ok(!(key in row), 'private employee information in roster export: ' + key)
      }
    }
  }
  return { route, code: result.code }
}))
fs.mkdirSync('back/logs', { recursive: true })
fs.writeFileSync('back/logs/hr-api-smoke.json', JSON.stringify(results, null, 2))
process.stdout.write(JSON.stringify({ endpoints: results.length, passed: results.length }))
