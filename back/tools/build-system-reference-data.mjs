import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const sourceFile = path.join(projectDir, 'docs/reference-system-settings.json')
const h2Output = path.join(projectDir, 'src/main/resources/system-reference-data.sql')
const mysqlOutput = path.join(projectDir, 'db/mysql/source/system-reference-data.sql')
const source = JSON.parse(fs.readFileSync(sourceFile, 'utf8'))

function sqlString(value) {
  if (value == null) {
    return 'NULL'
  }
  return `'${String(value).replaceAll('\\', '\\\\').replaceAll("'", "''")}'`
}

const dictTypes = source.responses.dictTypes.rows
const dictData = source.responses.dictData.rows
const configs = source.responses.configs.rows

const h2 = [
  '-- Generated from public reference system settings by tools/build-system-reference-data.mjs.',
  'DELETE FROM sys_dict_data;',
  'DELETE FROM sys_dict_type;',
  'DELETE FROM sys_config;'
]
const mysql = [
  '-- Generated from public reference system settings by tools/build-system-reference-data.mjs.',
  'DELETE FROM sys_dict_data;',
  'DELETE FROM sys_dict_type;',
  'DELETE FROM sys_config;'
]

for (const item of dictTypes) {
  const values = [
    item.dictId,
    sqlString(item.dictName),
    sqlString(item.dictType),
    sqlString(item.status || '0'),
    sqlString(item.createTime || '2026-10-26 18:00:00'),
    sqlString(item.remark || '')
  ].join(', ')
  h2.push(
    'MERGE INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) ' +
    `KEY (dict_id) VALUES (${values});`
  )
  mysql.push(
    'INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) ' +
    `VALUES (${values}) ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), ` +
    'dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);'
  )
}

for (const item of dictData) {
  const values = [
    item.dictCode,
    item.dictSort || 0,
    sqlString(item.dictLabel),
    sqlString(item.dictValue),
    sqlString(item.dictType),
    sqlString(item.cssClass || ''),
    sqlString(item.listClass || 'default'),
    sqlString(item.isDefault || 'N'),
    sqlString(item.status || '0')
  ].join(', ')
  h2.push(
    'MERGE INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, ' +
    'css_class, list_class, is_default, status) KEY (dict_code) VALUES (' + values + ');'
  )
  mysql.push(
    'INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, ' +
    'css_class, list_class, is_default, status) VALUES (' + values + ') ' +
    'ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), ' +
    'dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), ' +
    'list_class = VALUES(list_class), status = VALUES(status);'
  )
}

for (const item of configs) {
  const values = [
    item.configId,
    sqlString(item.configName),
    sqlString(item.configKey),
    sqlString(item.configValue),
    sqlString(item.configType || 'N'),
    sqlString(item.createTime || '2026-10-26 18:00:00'),
    sqlString(item.remark || '')
  ].join(', ')
  h2.push(
    'MERGE INTO sys_config (config_id, config_name, config_key, config_value, config_type, ' +
    'create_time, remark) KEY (config_id) VALUES (' + values + ');'
  )
  mysql.push(
    'INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, ' +
    'create_time, remark) VALUES (' + values + ') ON DUPLICATE KEY UPDATE ' +
    'config_name = VALUES(config_name), config_key = VALUES(config_key), ' +
    'config_value = VALUES(config_value), config_type = VALUES(config_type), remark = VALUES(remark);'
  )
}

fs.writeFileSync(h2Output, `${h2.join('\n')}\n`)
fs.writeFileSync(mysqlOutput, `${mysql.join('\n')}\n`)
console.log(
  `Generated ${dictTypes.length} dictionary types, ${dictData.length} dictionary values, ` +
  `${configs.length} configurations`
)
