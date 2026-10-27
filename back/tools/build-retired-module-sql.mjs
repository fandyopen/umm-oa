import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const config = JSON.parse(fs.readFileSync(path.join(projectDir, 'db/retired-modules.json'), 'utf8'))
const moduleIds = [...new Set(config.moduleIds)].sort((a, b) => a - b)
const moduleList = moduleIds.join(',')
const processConditions = moduleIds
  .map(id => `process_key LIKE '%${id}%'`)
  .join('\n   OR ')
const workflowPayloadConditions = moduleIds
  .map(id => `payload_json LIKE '%${id}%'`)
  .join('\n   OR ')
const bizConditions = config.bizRecordPrefixes
  .map(prefix => `module LIKE '${prefix}%'`)
  .join('\n   OR ')
const appPathList = config.appModulePaths.map(value => `'${value}'`).join(',')
const todoTaskList = config.todoTaskIds.map(value => `'${value}'`).join(',')
const sysMenuPermissionConditions = config.sysMenuPermissionPrefixes
  .map(prefix => `perms LIKE '${prefix}%'`)
  .join('\n   OR ')
const dictTypeConditions = config.dictTypePrefixes
  .map(prefix => `dict_type LIKE '${prefix}%'`)
  .join('\n   OR ')

const lines = [
  '-- Retired top-level modules: 项目, 合同与采购, 钱票收支.',
  '-- Idempotent cleanup for existing databases and generated initialization scripts.',
  '',
  'DELETE FROM sys_role_menu WHERE menu_id IN (' + moduleList + ');',
  'DELETE FROM sys_role_menu WHERE menu_id IN (',
  '    SELECT menu_id FROM sys_menu WHERE ' + sysMenuPermissionConditions,
  ');',
  'DELETE FROM sys_menu WHERE menu_id IN (' + moduleList + ');',
  'DELETE FROM sys_menu WHERE ' + sysMenuPermissionConditions + ';',
  'DELETE FROM sys_role_module_action WHERE module_id IN (' + moduleList + ');',
  'DELETE FROM sys_module_action WHERE module_id IN (' + moduleList + ');',
  'DELETE FROM sys_role_module WHERE module_id IN (' + moduleList + ');',
  'DELETE FROM sys_dept_module WHERE module_id IN (' + moduleList + ');',
  'DELETE FROM oa_module_action_flow WHERE module_id IN (' + moduleList + ');',
  'DELETE FROM oa_workflow_request WHERE module_id IN (' + moduleList + ');',
  'DELETE FROM oa_process_design WHERE menu_id IN (' + moduleList + ');',
  'DELETE FROM sys_app_module WHERE module_path IN (' + appPathList + ');',
  '',
  'DELETE FROM oa_todo WHERE process_instance_id IN (',
  '    SELECT id FROM oa_process_instance WHERE ' + processConditions,
  ');',
  'DELETE FROM oa_todo WHERE task_id IN (' + todoTaskList + ');',
  'DELETE FROM oa_process_task WHERE instance_id IN (',
  '    SELECT id FROM oa_process_instance WHERE ' + processConditions,
  ');',
  'DELETE FROM oa_process_instance WHERE ' + processConditions + ';',
  'DELETE FROM oa_process_definition WHERE ' + processConditions + ';',
  '',
  'DELETE FROM sys_dict_data WHERE ' + dictTypeConditions + ';',
  'DELETE FROM sys_dict_type WHERE ' + dictTypeConditions + ';',
  '',
  'DELETE FROM biz_record WHERE ' + bizConditions + ';',
  'DELETE FROM biz_record WHERE business_key IN (' + todoTaskList + ');',
  'DELETE FROM biz_record',
  "WHERE module LIKE 'workflow:%'",
  '  AND (' + workflowPayloadConditions + ');',
  '',
  ...config.domainTables.map(table => `DELETE FROM ${table};`),
  '',
  'DELETE FROM sys_module WHERE module_id IN (' + moduleList + ');',
  ''
]

const output = lines.join('\n')
const targets = [
  path.join(projectDir, 'src/main/resources/retire-business-modules.sql'),
  path.join(projectDir, 'db/mysql/source/retire-business-modules.sql')
]
for (const target of targets) {
  fs.writeFileSync(target, output)
}
console.log(`Generated retired-module cleanup for ${moduleIds.length} modules`)
for (const target of targets) {
  console.log(target)
}
