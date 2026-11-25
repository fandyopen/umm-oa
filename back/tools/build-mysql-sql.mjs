import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const mysqlSourceDir = path.join(projectDir, 'db/mysql/source')
const schema = fs.readFileSync(path.join(projectDir, 'src/main/resources/schema.sql'), 'utf8')
const customerContractSchema = fs.readFileSync(path.join(projectDir, 'src/main/resources/customer-contract-schema.sql'), 'utf8')
const recruitmentSchema = fs.readFileSync(path.join(projectDir, 'src/main/resources/recruitment-schema.sql'), 'utf8')
const recruitmentMenu = fs.readFileSync(path.join(projectDir, 'src/main/resources/recruitment-menu.sql'), 'utf8')
const customerFinanceSchema = fs.readFileSync(path.join(projectDir, 'src/main/resources/customer-finance-schema.sql'), 'utf8')
const customerFinanceMenu = fs.readFileSync(path.join(projectDir, 'src/main/resources/customer-finance-menu.sql'), 'utf8')
const salesPerformanceMenu = fs.readFileSync(path.join(projectDir, 'src/main/resources/sales-performance-menu.sql'), 'utf8')
const hrSchema = fs.readFileSync(path.join(projectDir, 'src/main/resources/hr-schema.sql'), 'utf8')
const hrMenu = fs.readFileSync(path.join(projectDir, 'src/main/resources/hr-menu.sql'), 'utf8')
const customerCleanup = fs.readFileSync(path.join(projectDir, 'src/main/resources/customer-cleanup.sql'), 'utf8')
const junkCleanup = fs.readFileSync(path.join(projectDir, 'src/main/resources/junk-cleanup.sql'), 'utf8')
const commissionSchema = fs.readFileSync(path.join(projectDir, 'src/main/resources/commission.sql'), 'utf8')
  .replace(/CREATE INDEX IF NOT EXISTS (\w+) ON (\w+)([^;]+);/g, (_, index, table, columns) => `SET @commission_sql = (SELECT IF(COUNT(*)=0,'CREATE INDEX ${index} ON ${table}${columns}','SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='${table}' AND index_name='${index}');
PREPARE commission_stmt FROM @commission_sql;
EXECUTE commission_stmt;
DEALLOCATE PREPARE commission_stmt;`)
const commissionContract = fs.readFileSync(path.join(projectDir, 'src/main/resources/commission-contract.sql'), 'utf8')
  .replace(/ALTER TABLE (\w+) ALTER COLUMN (\w+) DROP NOT NULL;/g, 'ALTER TABLE $1 MODIFY COLUMN $2 BIGINT NULL;')
  .replace(/ALTER TABLE (\w+) ADD COLUMN IF NOT EXISTS (\w+) ([^;]+);/g, (_, table, column, definition) => `SET @commission_sql = (SELECT IF(COUNT(*)=0,'ALTER TABLE ${table} ADD COLUMN ${column} ${definition.replaceAll("'", "''")}','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='${table}' AND column_name='${column}');
PREPARE commission_stmt FROM @commission_sql;
EXECUTE commission_stmt;
DEALLOCATE PREPARE commission_stmt;`)
const approvalInbox = fs.readFileSync(path.join(projectDir, 'src/main/resources/approval-inbox.sql'), 'utf8')
  .replace(',\n    complete_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP\n);', ',\n    complete_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n    INDEX idx_approval_decision_instance (instance_id)\n);')
  .replace(/CREATE INDEX IF NOT EXISTS idx_approval_decision_instance[^;]+;\s*/g, '')
const contractLifecycle = fs.readFileSync(path.join(projectDir, 'src/main/resources/contract-lifecycle.sql'), 'utf8')
const mysqlContractLifecycle = contractLifecycle.replace(/ALTER TABLE (\w+) ADD COLUMN IF NOT EXISTS (\w+) ([^;]+);/g, (_, table, column, definition) => `SET @contract_sql = (SELECT IF(COUNT(*)=0,'ALTER TABLE ${table} ADD COLUMN ${column} ${definition.replaceAll("'", "''")}','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='${table}' AND column_name='${column}');
PREPARE contract_stmt FROM @contract_sql;
EXECUTE contract_stmt;
DEALLOCATE PREPARE contract_stmt;`)
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

${customerContractSchema}

${recruitmentSchema}

${recruitmentMenu}

${customerFinanceSchema}

${customerFinanceMenu}

${salesPerformanceMenu}

${hrSchema}

${hrMenu}

${commissionSchema}

${commissionContract}

${customerCleanup}

${junkCleanup}

${mysqlContractLifecycle}

${approvalInbox}

SET FOREIGN_KEY_CHECKS = 1;
`

const outputDir = path.join(projectDir, 'db/mysql')
fs.mkdirSync(outputDir, { recursive: true })
fs.writeFileSync(path.join(outputDir, 'commission-contract-upgrade.sql'), '-- Contract sales settle after actual cash; preserves existing attribution and batch history.\n' + commissionSchema + '\n' + commissionContract)
fs.writeFileSync(path.join(outputDir, 'upgrade-approval-inbox.sql'), '-- Approval inbox and actual decision actors; preserves business data and existing grants.\n' + approvalInbox)
fs.writeFileSync(path.join(outputDir, 'contract-lifecycle-upgrade.sql'), '-- Contract retirement upgrade; preserves existing business data.\n' + mysqlContractLifecycle)
fs.writeFileSync(path.join(outputDir, 'customer-cleanup-upgrade.sql'), '-- Customer cleanup menu and audit table; does not delete business data.\n' + customerCleanup)
fs.writeFileSync(path.join(outputDir, 'junk-cleanup-upgrade.sql'), '-- Special functions split and junk cleanup; bounded file candidates only, never removes database data.\n' + junkCleanup)
fs.writeFileSync(path.join(outputDir, 'umm_oa.sql'), output)
fs.writeFileSync(path.join(outputDir, 'recruitment-upgrade.sql'), '-- Existing MySQL 8 databases: recruitment upgrade; preserves business data.\n' + recruitmentSchema + '\n' + recruitmentMenu)
fs.writeFileSync(path.join(outputDir, 'customer-finance-upgrade.sql'), '-- Existing MySQL 8 databases: customer finance upgrade; preserves business data.\n' + customerFinanceSchema + '\n' + customerFinanceMenu)
fs.writeFileSync(path.join(outputDir, 'sales-performance-upgrade.sql'), '-- Existing MySQL 8 databases: customer sales performance menu upgrade.\n' + salesPerformanceMenu)
const hrColumns = [
  ['oa_hr_employee', 'work_province', "VARCHAR(64) NOT NULL DEFAULT '广东省'"],
  ['oa_hr_employee', 'work_city', "VARCHAR(64) NOT NULL DEFAULT '广州市'"],
  ['oa_hr_payroll_item', 'tax_entered', 'INT NOT NULL DEFAULT 0'],
  ['oa_hr_payroll_item', 'tax_reason', 'VARCHAR(1000)'],
  ['oa_hr_payroll_item', 'tax_operator_id', 'BIGINT'],
  ['oa_hr_payroll_item', 'tax_update_time', 'DATETIME']
]
const hrColumnUpgrade = hrColumns.map(([table, column, definition]) => `SET @hr_sql = (SELECT IF(COUNT(*)=0,'ALTER TABLE ${table} ADD COLUMN ${column} ${definition.replaceAll("'", "''")}','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='${table}' AND column_name='${column}');
PREPARE hr_stmt FROM @hr_sql;
EXECUTE hr_stmt;
DEALLOCATE PREPARE hr_stmt;`).join('\n') + `
CREATE TABLE IF NOT EXISTS oa_hr_upgrade (upgrade_key VARCHAR(100) PRIMARY KEY);
UPDATE oa_hr_payroll_item SET tax_entered=1,tax_reason='升级前已核对或已冻结的历史个税'
WHERE tax_entered=0 AND (tax<>0 OR verified=1 OR payroll_id IN (SELECT id FROM oa_hr_payroll WHERE status IN ('approving','approved')))
AND NOT EXISTS (SELECT 1 FROM oa_hr_upgrade WHERE upgrade_key='manual_tax_v1');
INSERT INTO oa_hr_upgrade(upgrade_key) SELECT 'manual_tax_v1' WHERE NOT EXISTS (SELECT 1 FROM oa_hr_upgrade WHERE upgrade_key='manual_tax_v1');
`
fs.writeFileSync(path.join(outputDir, 'hr-upgrade.sql'), '-- Existing MySQL 8 databases: personnel, attendance and payroll upgrade; preserves business data.\n' + hrSchema + '\n' + hrColumnUpgrade + '\n' + hrMenu)
console.log(`Generated ${path.join(outputDir, 'umm_oa.sql')}`)
