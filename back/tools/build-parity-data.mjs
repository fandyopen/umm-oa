import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const sourceFile = path.join(projectDir, 'docs/reference-page-api.json')
const h2Output = path.join(projectDir, 'src/main/resources/parity-data.sql')
const mysqlOutput = path.join(projectDir, 'db/mysql/source/parity-data.sql')
const retired = JSON.parse(fs.readFileSync(path.join(projectDir, 'db/retired-modules.json'), 'utf8'))
const source = JSON.parse(fs.readFileSync(sourceFile, 'utf8'))

const records = []
const seenPaths = new Set()
let nextId = 5000

for (const result of source.results) {
  const url = new URL(result.localUrl)
  if (seenPaths.has(url.pathname)) {
    continue
  }
  const response = result.response || {}
  let rows = Array.isArray(response.rows) ? response.rows : []
  if (!rows.length && Array.isArray(response.data)) {
    rows = response.data
  }
  if (!rows.length) {
    continue
  }
  seenPaths.add(url.pathname)
  const module = url.pathname.replace(/^\/api-qs/, '')
  if (retired.bizRecordPrefixes.some(prefix => module.startsWith(prefix))) {
    continue
  }
  for (const [index, payload] of rows.slice(0, 20).entries()) {
    records.push({
      id: nextId++,
      module,
      businessKey: `${module}:${index + 1}`,
      status: payload.state || payload.status || payload.processStatus || 'approved',
      createBy: payload.createBy || '17630135660',
      createTime: payload.createTime || payload.planTime || '2026-10-26 18:00:00',
      payload
    })
  }
}

function sqlString(value) {
  return `'${String(value).replaceAll('\\', '\\\\').replaceAll("'", "''")}'`
}

const h2 = [
  '-- Generated from public reference list API responses by tools/build-parity-data.mjs.',
  'DELETE FROM biz_record WHERE id BETWEEN 5000 AND 5999;'
]
const mysql = [
  '-- Generated from public reference list API responses by tools/build-parity-data.mjs.',
  'DELETE FROM biz_record WHERE id BETWEEN 5000 AND 5999;'
]

for (const record of records) {
  const payload = sqlString(JSON.stringify(record.payload))
  h2.push(
    'MERGE INTO biz_record ' +
    '(id, tenant_id, module, business_key, payload_json, status, create_by, create_time) ' +
    `KEY (id) VALUES (${record.id}, 1, ${sqlString(record.module)}, ` +
    `${sqlString(record.businessKey)}, ${payload}, ${sqlString(record.status)}, ` +
    `${sqlString(record.createBy)}, ${sqlString(record.createTime)});`
  )
  mysql.push(
    'INSERT INTO biz_record ' +
    '(id, tenant_id, module, business_key, payload_json, status, create_by, create_time) ' +
    `VALUES (${record.id}, 1, ${sqlString(record.module)}, ${sqlString(record.businessKey)}, ` +
    `${payload}, ${sqlString(record.status)}, ${sqlString(record.createBy)}, ` +
    `${sqlString(record.createTime)}) ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json);`
  )
}

fs.writeFileSync(h2Output, `${h2.join('\n')}\n`)
fs.writeFileSync(mysqlOutput, `${mysql.join('\n')}\n`)
console.log(`Generated ${records.length} parity records for ${seenPaths.size} list endpoints`)
