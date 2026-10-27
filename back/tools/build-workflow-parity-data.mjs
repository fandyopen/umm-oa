import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const sourceFile = path.join(projectDir, 'docs/reference-workflow-2575064.json')
const h2Resource = path.join(projectDir, 'src/main/resources/workflow-parity-data.sql')
const h2Source = path.join(projectDir, 'db/h2/workflow.sql')
const mysqlSource = path.join(projectDir, 'db/mysql/source/workflow-parity-data.sql')
const retired = JSON.parse(fs.readFileSync(path.join(projectDir, 'db/retired-modules.json'), 'utf8'))

const capture = JSON.parse(fs.readFileSync(sourceFile, 'utf8'))
const todo = capture.firstTodo
const instanceId = String(todo.instanceId)
const historyRows = capture.requests.history.response.rows || []
const validElements = capture.requests.validElements.data || []
const flowIds = capture.requests.flowIds.data || {}

const records = []
let id = 6100
const retiredTask = retired.moduleIds.some(moduleId =>
  String(todo.formKey || '').includes(String(moduleId))
)
if (retiredTask) {
  writeEmpty()
  process.exit(0)
}
for (const row of historyRows) {
  records.push({
    id: id++,
    module: `workflow:history:${instanceId}`,
    businessKey: String(row.taskId || instanceId),
    payload: row,
    status: row.activityType || 'history',
    createBy: row.assignee || todo.applyUserId,
    createTime: normalizeTime(row.startTime)
  })
}
for (const row of validElements) {
  records.push({
    id: id++,
    module: `workflow:valid:${instanceId}`,
    businessKey: String(row.id),
    payload: row,
    status: row.current ? 'current' : 'pending',
    createBy: todo.applyUserId,
    createTime: normalizeTime(todo.applyTime)
  })
}
records.push({
  id: id++,
  module: `workflow:flowids:${instanceId}`,
  businessKey: instanceId,
  payload: flowIds,
  status: 'active',
  createBy: todo.applyUserId,
  createTime: normalizeTime(todo.applyTime)
})
records.push({
  id: id++,
  module: `workflow:meta:${instanceId}`,
  businessKey: instanceId,
  payload: {
    instanceId,
    processKey: todo.formKey,
    processName: '业务支付',
    businessType: todo.businessType,
    formId: todo.formId,
    initiatorUserId: todo.applyUserId,
    initiatorUserName: todo.applyUserName,
    initiatorDepartment: '软件开发部',
    initiatorRoles: ['副总-销售', '项目总管', '副总-生产', '财务部经理'],
    entryModuleId: 340483,
    entryName: '业务支付',
    entryPath: '/sale-payment1/payment',
    currentElementId: 'fsxHHE41'
  },
  status: 'active',
  createBy: todo.applyUserId,
  createTime: normalizeTime(todo.applyTime)
})

function normalizeTime(value) {
  if (!value) {
    return '2024-01-29 15:49:50'
  }
  return String(value)
    .replace('T', ' ')
    .replace(/\.\d+/, '')
    .replace(/\+0800$/, '')
}

function sqlLiteral(value) {
  return `'${String(value ?? '').replace(/'/g, "''")}'`
}

function h2Sql() {
  const lines = [
    '-- Generated from docs/reference-workflow-2575064.json.',
    '-- Idempotent reference workflow data for the local H2 database.'
  ]
  for (const record of records) {
    lines.push(
      'MERGE INTO biz_record (id, tenant_id, module, business_key, payload_json, status, create_by, create_time) ' +
      `KEY (id) VALUES (${record.id}, 1, ${sqlLiteral(record.module)}, ${sqlLiteral(record.businessKey)}, ` +
      `${sqlLiteral(JSON.stringify(record.payload))}, ${sqlLiteral(record.status)}, ` +
      `${sqlLiteral(record.createBy)}, ${sqlLiteral(record.createTime)});`
    )
  }
  return `${lines.join('\n')}\n`
}

function mysqlSql() {
  const lines = [
    '-- Generated from docs/reference-workflow-2575064.json.',
    '-- Idempotent reference workflow data for MySQL.'
  ]
  for (const record of records) {
    lines.push(
      'INSERT INTO biz_record (id, tenant_id, module, business_key, payload_json, status, create_by, create_time) ' +
      `VALUES (${record.id}, 1, ${sqlLiteral(record.module)}, ${sqlLiteral(record.businessKey)}, ` +
      `${sqlLiteral(JSON.stringify(record.payload))}, ${sqlLiteral(record.status)}, ` +
      `${sqlLiteral(record.createBy)}, ${sqlLiteral(record.createTime)}) ` +
      'ON DUPLICATE KEY UPDATE module = VALUES(module), business_key = VALUES(business_key), ' +
      'payload_json = VALUES(payload_json), status = VALUES(status), create_by = VALUES(create_by), ' +
      'create_time = VALUES(create_time);'
    )
  }
  return `${lines.join('\n')}\n`
}

const sql = h2Sql()
fs.writeFileSync(h2Resource, sql)
fs.writeFileSync(h2Source, sql)
fs.writeFileSync(mysqlSource, mysqlSql())
console.log(`Wrote ${records.length} workflow records`)

function writeEmpty() {
  const h2 = '-- Source workflow belonged to a retired module.\\n'
  const mysql = '-- Source workflow belonged to a retired module.\\n'
  fs.writeFileSync(h2Resource, h2)
  fs.writeFileSync(h2Source, h2)
  fs.writeFileSync(mysqlSource, mysql)
  console.log('Skipped retired reference workflow')
}
