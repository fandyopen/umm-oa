import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const sourceFile = path.join(projectDir, 'db/source/remote-routers.json')
const h2Output = path.join(projectDir, 'src/main/resources/module-data.sql')
const mysqlOutput = path.join(projectDir, 'db/mysql/source/module-data.sql')
const migrationOutput = path.join(projectDir, 'db/h2/module.sql')
const retiredModuleIds = new Set(
  JSON.parse(fs.readFileSync(path.join(projectDir, 'db/retired-modules.json'), 'utf8')).moduleIds
)

const source = JSON.parse(fs.readFileSync(sourceFile, 'utf8').replace(/^\uFEFF/, ''))
const modules = []

function flatten(items, parentId = 0, parentName = '') {
  for (const [index, item] of (items || []).entries()) {
    const meta = item.meta || {}
    if (retiredModuleIds.has(Number(item.moduleId || 0))) {
      continue
    }
    modules.push({
      moduleId: Number(item.moduleId || 0),
      parentId: Number(item.parentId ?? parentId),
      parentName,
      moduleName: item.moduleName || meta.title || item.name || '',
      routeName: item.name || '',
      path: item.path || '',
      component: item.component || '',
      icon: meta.icon || '',
      moduleSort: Number(item.moduleSort ?? index),
      hidden: item.hidden ? 1 : 0,
      perms: item.perms || '',
      hasFlow: Number(item.hasFlow ?? 1),
      flowConditions: meta.flowConditions ? JSON.stringify(meta.flowConditions) : '',
      noCache: meta.noCache ? 1 : 0,
      isApp: item.isApp ?? '',
      isFrame: item.isFrame ?? '',
      query: item.query || '',
      redirect: item.redirect || '',
      status: item.status || '0'
    })
    if (item.children?.length) {
      flatten(item.children, modules.at(-1).moduleId, modules.at(-1).moduleName)
    }
  }
}

flatten(source.data)

modules.push(
  {
    moduleId: 341000,
    parentId: 0,
    parentName: '',
    moduleName: '人事管理',
    routeName: 'HR',
    path: '/hr',
    component: 'Layout',
    icon: 'iconfont-tuandui4',
    moduleSort: 3,
    hidden: 0,
    perms: '',
    hasFlow: 1,
    flowConditions: '',
    noCache: 0,
    isApp: '',
    isFrame: '',
    query: '',
    redirect: 'noRedirect',
    status: '0'
  },
  {
    moduleId: 341001,
    parentId: 341000,
    parentName: '人事管理',
    moduleName: '成员管理',
    routeName: 'HrMembers',
    path: 'members',
    component: 'enterprise/hr/index',
    icon: 'iconfont-tuandui4',
    moduleSort: 1,
    hidden: 0,
    perms: 'hr:member:list',
    hasFlow: 1,
    flowConditions: '',
    noCache: 0,
    isApp: '',
    isFrame: '',
    query: '',
    redirect: '',
    status: '0'
  },
  {
    moduleId: 341100,
    parentId: 340309,
    parentName: '系统',
    moduleName: '使用文档',
    routeName: 'Docs',
    path: 'docs',
    component: 'docs/index',
    icon: 'iconfont-bangzhu',
    moduleSort: 99,
    hidden: 1,
    perms: 'system:docs:list',
    hasFlow: 0,
    flowConditions: '',
    noCache: 0,
    isApp: '',
    isFrame: '',
    query: '',
    redirect: '',
    status: '0'
  }
)

const columns = [
  'module_id',
  'tenant_id',
  'parent_id',
  'module_name',
  'route_name',
  'path',
  'component',
  'icon',
  'module_sort',
  'hidden',
  'perms',
  'has_flow',
  'flow_conditions',
  'no_cache',
  'is_app',
  'is_frame',
  'query',
  'redirect',
  'status'
]

function sqlValue(value) {
  if (typeof value === 'number') return String(value)
  return `'${String(value ?? '').replaceAll('\\', '\\\\').replaceAll("'", "''")}'`
}

function values(module) {
  return [
    module.moduleId,
    1,
    module.parentId,
    module.moduleName,
    module.routeName,
    module.path,
    module.component,
    module.icon,
    module.moduleSort,
    module.hidden,
    module.perms,
    module.hasFlow,
    module.flowConditions,
    module.noCache,
    module.isApp,
    module.isFrame,
    module.query,
    module.redirect,
    module.status
  ].map(sqlValue).join(', ')
}

const h2Sql = modules.map(module =>
  `MERGE INTO sys_module (${columns.join(', ')}) KEY (module_id) VALUES (${values(module)});`
).join('\n')
const h2Migration = `CREATE TABLE IF NOT EXISTS sys_module (
    module_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    parent_id BIGINT NOT NULL DEFAULT 0,
    module_name VARCHAR(200) NOT NULL,
    route_name VARCHAR(128),
    path VARCHAR(255),
    component VARCHAR(255),
    icon VARCHAR(100),
    module_sort INT NOT NULL DEFAULT 0,
    hidden INT NOT NULL DEFAULT 0,
    perms VARCHAR(128),
    has_flow INT NOT NULL DEFAULT 1,
    flow_conditions TEXT,
    no_cache INT NOT NULL DEFAULT 0,
    is_app VARCHAR(1),
    is_frame VARCHAR(1),
    query VARCHAR(255),
    redirect VARCHAR(255),
    status VARCHAR(1) NOT NULL DEFAULT '0',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

${h2Sql}
`

const mysqlSql = modules.map(module =>
  `INSERT INTO sys_module (${columns.join(', ')}) VALUES (${values(module)})\n` +
  `ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), ` +
  `route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), ` +
  `icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), ` +
  `perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), ` +
  `no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), ` +
  `query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);`
).join('\n')

fs.writeFileSync(h2Output, `${h2Sql}\n`)
fs.writeFileSync(mysqlOutput, `${mysqlSql}\n`)
fs.writeFileSync(migrationOutput, h2Migration)
console.log(`Generated ${modules.length} modules`)
console.log(h2Output)
console.log(mysqlOutput)
console.log(migrationOutput)
