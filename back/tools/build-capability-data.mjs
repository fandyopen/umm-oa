import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const h2Output = path.join(projectDir, 'src/main/resources/capability-data.sql')
const mysqlOutput = path.join(projectDir, 'db/mysql/source/capability-data.sql')
const migrationOutput = path.join(projectDir, 'db/h2/capability.sql')
const routerSource = JSON.parse(
  fs.readFileSync(path.join(projectDir, 'db/source/remote-routers.json'), 'utf8')
).data
const accountSource = JSON.parse(
  fs.readFileSync(path.join(projectDir, 'docs/reference-account-capabilities.json'), 'utf8')
)
const retiredConfig = JSON.parse(
  fs.readFileSync(path.join(projectDir, 'db/retired-modules.json'), 'utf8')
)
const retiredModuleIds = new Set(retiredConfig.moduleIds.map(Number))
const retiredPermissionPrefixes = retiredConfig.sysMenuPermissionPrefixes

function isRetiredModule(moduleId) {
  return retiredModuleIds.has(Number(moduleId))
}

function isRetiredPermission(permission) {
  return retiredPermissionPrefixes.some(prefix => permission.startsWith(prefix))
}

const roles = [
  { roleId: 7, roleName: '财务会计', roleKey: 'finance_accountant', roleSort: 7, remark: '财务流程和查询协同' },
  { roleId: 8, roleName: '财务出纳', roleKey: 'cashier', roleSort: 8, remark: '收付款与资金确认协同' },
  { roleId: 9, roleName: '合同归档', roleKey: 'contract_archive', roleSort: 9, remark: '文档归档和审批协同' },
  { roleId: 10, roleName: '副总-生产', roleKey: 'production_vp', roleSort: 10, remark: '生产协同和审批' },
  { roleId: 11, roleName: '采购', roleKey: 'purchasing', roleSort: 11, remark: '客商和采购审批协同' },
  { roleId: 12, roleName: '综合部经理', roleKey: 'admin_manager', roleSort: 12, remark: '组织协同、通知公告和公共审批' },
  { roleId: 13, roleName: '盖章确认', roleKey: 'seal_confirm', roleSort: 13, remark: '审批盖章确认' },
  { roleId: 14, roleName: '回收作废', roleKey: 'void_recovery', roleSort: 14, remark: '审批回收与作废' },
  { roleId: 15, roleName: '销售', roleKey: 'sales', roleSort: 15, remark: '销售流程和客户协同' },
  { roleId: 16, roleName: '执行董事', roleKey: 'executive_director', roleSort: 16, remark: '全局经营查看和审批' },
  { roleId: 17, roleName: '党建-项目总管', roleKey: 'party_project_manager', roleSort: 17, remark: '党建审批协同' },
  { roleId: 18, roleName: '党建-副总', roleKey: 'party_vice_president', roleSort: 18, remark: '党建协同与审批' }
]

const userRoles = new Map([
  [1, [1]],
  [2, [3]],
  [3, [4, 7]],
  [4, [2, 8, 9]],
  [5, [5]],
  [6, [2, 10]],
  [7, [6]],
  [8, [2, 11]],
  [9, [12, 13, 14]]
])

function visibleModuleIds(username) {
  const account = accountSource.find(item => item.username === username)
  return new Set(
    (account?.visibleModules || [])
      .map(module => Number(module.moduleId))
      .filter(moduleId => !isRetiredModule(moduleId))
  )
}

function union(...sets) {
  return new Set(sets.flatMap(set => [...set]))
}

function modulesByTitle(titles) {
  const wanted = new Set(titles)
  const ids = new Set()
  function walk(items) {
    for (const item of items || []) {
      const title = item.meta?.title || item.moduleName
      if (wanted.has(title) && !isRetiredModule(item.moduleId)) {
        ids.add(Number(item.moduleId))
      }
      walk(item.children)
    }
  }
  walk(routerSource)
  return ids
}

const financeModules = visibleModuleIds('17689037697')
const salesModules = visibleModuleIds('15646844893')
const productionModules = visibleModuleIds('13689213154')
const purchasingModules = visibleModuleIds('15609825919')
const managerModules = union(financeModules, salesModules, productionModules)
const projectManagerModules = union(productionModules, purchasingModules)
const comprehensiveModules = modulesByTitle([
  '我的待办',
  '我的已办',
  '我的发起',
  '抄送我的',
  '项目登记',
  '项目变更',
  '项目综合查询',
  '人员安排',
  '合同拟定',
  '合同变更',
  '合同综合查询',
  '组织机构',
  '通知公告',
  '工作台'
])
const roleModules = new Map([
  [3, managerModules],
  [4, financeModules],
  [5, salesModules],
  [6, projectManagerModules],
  [7, financeModules],
  [8, financeModules],
  [9, financeModules],
  [10, productionModules],
  [11, purchasingModules],
  [12, comprehensiveModules],
  [13, comprehensiveModules],
  [14, comprehensiveModules],
  [15, salesModules],
  [16, managerModules],
  [17, projectManagerModules],
  [18, managerModules]
])

function sqlString(value) {
  return `'${String(value).replaceAll('\\', '\\\\').replaceAll("'", "''")}'`
}

const h2Statements = []
const mysqlStatements = []

h2Statements.push("UPDATE sys_menu SET perms = '' WHERE menu_id BETWEEN 1 AND 14;")
mysqlStatements.push("UPDATE sys_menu SET perms = '' WHERE menu_id BETWEEN 1 AND 14;")

for (const role of roles) {
  h2Statements.push(
    'MERGE INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) ' +
    `KEY (role_id) VALUES (${role.roleId}, 1, ${sqlString(role.roleName)}, ${sqlString(role.roleKey)}, ` +
    `${role.roleSort}, '1', '0', ${sqlString(role.remark)});`
  )
  mysqlStatements.push(
    'INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) ' +
    `VALUES (${role.roleId}, 1, ${sqlString(role.roleName)}, ${sqlString(role.roleKey)}, ` +
    `${role.roleSort}, '1', '0', ${sqlString(role.remark)}) ` +
    'ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), role_key = VALUES(role_key), ' +
    'role_sort = VALUES(role_sort), remark = VALUES(remark);'
  )
}

h2Statements.push('DELETE FROM sys_user_role WHERE user_id IN (1, 2, 3, 4, 5, 6, 7, 8, 9);')
mysqlStatements.push('DELETE FROM sys_user_role WHERE user_id IN (1, 2, 3, 4, 5, 6, 7, 8, 9);')

for (const [userId, roleIds] of userRoles) {
  for (const roleId of roleIds) {
    h2Statements.push(
      `MERGE INTO sys_user_role (user_id, role_id) KEY (user_id, role_id) VALUES (${userId}, ${roleId});`
    )
    mysqlStatements.push(
      `INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (${userId}, ${roleId});`
    )
  }
}

h2Statements.push('DELETE FROM sys_role_module;')
mysqlStatements.push('DELETE FROM sys_role_module;')
for (const [roleId, moduleIds] of roleModules) {
  for (const moduleId of moduleIds) {
    h2Statements.push(
      `MERGE INTO sys_role_module (role_id, module_id) KEY (role_id, module_id) VALUES (${roleId}, ${moduleId});`
    )
    mysqlStatements.push(
      `INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (${roleId}, ${moduleId});`
    )
  }
}

for (const role of roles) {
  for (let menuId = 1; menuId <= 9; menuId += 1) {
    h2Statements.push(
      `MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (${role.roleId}, ${menuId});`
    )
    mysqlStatements.push(
      `INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (${role.roleId}, ${menuId});`
    )
  }
}

const permissionDefinitions = new Map()
for (const account of accountSource) {
  for (const permission of account.permissions || []) {
    const normalized = String(permission || '').trim()
    if (normalized && !isRetiredPermission(normalized)) {
      permissionDefinitions.set(normalized, null)
    }
  }
}
const permissionIds = new Map(
  [...permissionDefinitions.keys()]
    .sort()
    .map((permission, index) => [permission, 10000 + index])
)

for (const [permission, menuId] of permissionIds) {
  h2Statements.push(
    'MERGE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, ' +
    'is_frame, is_cache, menu_type, visible, status, perms, icon) KEY (menu_id) VALUES (' +
    `${menuId}, ${sqlString(permission)}, 0, 0, '', '', 1, 0, 'F', '1', '0', ` +
    `${sqlString(permission)}, '');`
  )
  mysqlStatements.push(
    'INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, ' +
    'is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (' +
    `${menuId}, ${sqlString(permission)}, 0, 0, '', '', 1, 0, 'F', '1', '0', ` +
    `${sqlString(permission)}, '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), ` +
    'perms = VALUES(perms);'
  )
}

const accountPermissionGrants = new Map([
  ['17630135660', [1]],
  ['17689037697', [8, 9]],
  ['15646844893', [5]],
  ['13689213154', [10]],
  ['15609825919', [11]]
])
for (const [username, roleIds] of accountPermissionGrants) {
  const account = accountSource.find(item => item.username === username)
  const permissions = [...new Set(
    (account?.permissions || [])
      .map(permission => String(permission || '').trim())
      .filter(permission => permission && !isRetiredPermission(permission))
  )]
  for (const roleId of roleIds) {
    for (const permission of permissions) {
      const menuId = permissionIds.get(permission)
      if (!menuId) {
        continue
      }
      h2Statements.push(
        `MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (${roleId}, ${menuId});`
      )
      mysqlStatements.push(
        `INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (${roleId}, ${menuId});`
      )
    }
  }
}

fs.writeFileSync(h2Output, `${h2Statements.join('\n')}\n`)
fs.writeFileSync(mysqlOutput, `${mysqlStatements.join('\n')}\n`)
const migrationHeader = `CREATE TABLE IF NOT EXISTS sys_role_module (
    role_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, module_id)
);

`
fs.writeFileSync(migrationOutput, `${migrationHeader}${h2Statements.join('\n')}\n`)
console.log(
  `Generated ${roles.length} roles, ${userRoles.size} user assignments, ` +
  `${[...roleModules.values()].reduce((total, ids) => total + ids.size, 0)} module grants, ` +
  `${permissionDefinitions.size} permissions`
)
