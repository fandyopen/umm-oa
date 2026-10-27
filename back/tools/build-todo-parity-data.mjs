import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const sourceFile = path.join(projectDir, 'docs/reference-todo-13686621113.json')
const h2Output = path.join(projectDir, 'src/main/resources/todo-parity-data.sql')
const mysqlOutput = path.join(projectDir, 'db/mysql/source/todo-parity-data.sql')
const retired = JSON.parse(fs.readFileSync(path.join(projectDir, 'db/retired-modules.json'), 'utf8'))
const source = JSON.parse(fs.readFileSync(sourceFile, 'utf8'))
const waiting = source.responses['/project/activity/taskWaitList?pageNum=1&pageSize=10']
const retiredIds = new Set(retired.moduleIds.map(String))

function sqlString(value) {
  return `'${String(value).replaceAll('\\', '\\\\').replaceAll("'", "''")}'`
}

const h2 = [
  '-- Generated from public reference todo data by tools/build-todo-parity-data.mjs.',
  'DELETE FROM biz_record WHERE id BETWEEN 6000 AND 6999;'
]
const mysql = [
  '-- Generated from public reference todo data by tools/build-todo-parity-data.mjs.',
  'DELETE FROM biz_record WHERE id BETWEEN 6000 AND 6999;'
]

for (const [index, row] of (waiting.rows || []).entries()) {
  const rowText = JSON.stringify(row)
  if ([...retiredIds].some(id => rowText.includes(id))) {
    continue
  }
  const id = 6000 + index
  const module = `todo:wait:${source.username}`
  const payload = sqlString(JSON.stringify(row))
  h2.push(
    'MERGE INTO biz_record ' +
    '(id, tenant_id, module, business_key, payload_json, status, create_by, create_time) ' +
    `KEY (id) VALUES (${id}, 1, ${sqlString(module)}, ${sqlString(row.taskId)}, ` +
    `${payload}, 'todo', ${sqlString(source.username)}, ${sqlString(row.createTime)});`
  )
  mysql.push(
    'INSERT INTO biz_record ' +
    '(id, tenant_id, module, business_key, payload_json, status, create_by, create_time) ' +
    `VALUES (${id}, 1, ${sqlString(module)}, ${sqlString(row.taskId)}, ${payload}, ` +
    `'todo', ${sqlString(source.username)}, ${sqlString(row.createTime)}) ` +
    'ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json);'
  )
}

fs.writeFileSync(h2Output, `${h2.join('\n')}\n`)
fs.writeFileSync(mysqlOutput, `${mysql.join('\n')}\n`)
console.log(`Generated ${waiting.rows?.length || 0} waiting tasks for ${source.username}`)
