import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const schema = fs.readFileSync(path.join(projectDir, 'src/main/resources/schema.sql'), 'utf8')
const data = fs.readFileSync(path.join(projectDir, 'src/main/resources/data.sql'), 'utf8')

const mysqlData = data.replaceAll(
  "DATEADD('DAY', 365, CURRENT_DATE)",
  'DATE_ADD(CURRENT_DATE, INTERVAL 365 DAY)'
).replaceAll(
  "DATEADD('DAY', 60, CURRENT_DATE)",
  'DATE_ADD(CURRENT_DATE, INTERVAL 60 DAY)'
)

const output = `-- UMM OA MySQL 8 initialization script
-- Generated from the H2 development schema by tools/build-mysql-sql.mjs.
-- Local demo administrator: 17630135660 / admin123123

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS umm_oa
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE umm_oa;

${schema}

${mysqlData}

SET FOREIGN_KEY_CHECKS = 1;
`

const outputDir = path.join(projectDir, 'db/mysql')
fs.mkdirSync(outputDir, { recursive: true })
fs.writeFileSync(path.join(outputDir, 'umm_oa.sql'), output)
console.log(`Generated ${path.join(outputDir, 'umm_oa.sql')}`)
