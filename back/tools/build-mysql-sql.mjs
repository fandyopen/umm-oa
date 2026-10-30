import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const mysqlSourceDir = path.join(projectDir, 'db/mysql/source')
const schema = fs.readFileSync(path.join(projectDir, 'src/main/resources/schema.sql'), 'utf8')
const data = fs.readFileSync(path.join(projectDir, 'src/main/resources/data.sql'), 'utf8')
const moduleDataPath = path.join(mysqlSourceDir, 'module-data.sql')
const moduleData = fs.existsSync(moduleDataPath)
  ? fs.readFileSync(moduleDataPath, 'utf8')
  : ''
const capabilityDataPath = path.join(mysqlSourceDir, 'capability-data.sql')
const capabilityData = fs.existsSync(capabilityDataPath)
  ? fs.readFileSync(capabilityDataPath, 'utf8')
  : ''
const merchantDataPath = path.join(mysqlSourceDir, 'merchant-data.sql')
const merchantData = fs.existsSync(merchantDataPath)
  ? fs.readFileSync(merchantDataPath, 'utf8')
  : ''
const organizationDataPath = path.join(mysqlSourceDir, 'organization-data.sql')
const organizationData = fs.existsSync(organizationDataPath)
  ? fs.readFileSync(organizationDataPath, 'utf8')
  : ''
const organizationPermissionPath = path.join(mysqlSourceDir, 'organization-permission.sql')
const organizationPermissionData = fs.existsSync(organizationPermissionPath)
  ? fs.readFileSync(organizationPermissionPath, 'utf8')
  : ''
const demoFormDataPath = path.join(mysqlSourceDir, 'demo-form-data.sql')
const demoFormData = fs.existsSync(demoFormDataPath)
  ? fs.readFileSync(demoFormDataPath, 'utf8')
  : ''
const parityDataPath = path.join(mysqlSourceDir, 'parity-data.sql')
const parityData = fs.existsSync(parityDataPath)
  ? fs.readFileSync(parityDataPath, 'utf8')
  : ''
const systemReferenceDataPath = path.join(mysqlSourceDir, 'system-reference-data.sql')
const systemReferenceData = fs.existsSync(systemReferenceDataPath)
  ? fs.readFileSync(systemReferenceDataPath, 'utf8')
  : ''
const todoParityDataPath = path.join(mysqlSourceDir, 'todo-parity-data.sql')
const todoParityData = fs.existsSync(todoParityDataPath)
  ? fs.readFileSync(todoParityDataPath, 'utf8')
  : ''
const retiredModuleDataPath = path.join(mysqlSourceDir, 'retire-business-modules.sql')
const retiredModuleData = fs.existsSync(retiredModuleDataPath)
  ? fs.readFileSync(retiredModuleDataPath, 'utf8')
  : ''

const mysqlData = data.replaceAll(
  "DATEADD('DAY', 365, CURRENT_DATE)",
  'DATE_ADD(CURRENT_DATE, INTERVAL 365 DAY)'
).replaceAll(
  "DATEADD('DAY', 60, CURRENT_DATE)",
  'DATE_ADD(CURRENT_DATE, INTERVAL 60 DAY)'
)

const output = `-- UMM OA MySQL 8 initialization script
-- Generated from the H2 development schema by tools/build-mysql-sql.mjs.
-- Fixed super administrator: 13610316814 / admin123123

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS umm_oa
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE umm_oa;

${schema}

${mysqlData}

${systemReferenceData}

${capabilityData}

${moduleData}

${organizationData}

${organizationPermissionData}

${merchantData}

${demoFormData}

${parityData}

${todoParityData}

${retiredModuleData}

SET FOREIGN_KEY_CHECKS = 1;
`

const outputDir = path.join(projectDir, 'db/mysql')
fs.mkdirSync(outputDir, { recursive: true })
fs.writeFileSync(path.join(outputDir, 'umm_oa.sql'), output)
console.log(`Generated ${path.join(outputDir, 'umm_oa.sql')}`)
