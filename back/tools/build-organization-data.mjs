import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const reference = JSON.parse(
  fs.readFileSync(path.join(projectDir, 'docs/reference-departments.json'), 'utf8')
)
const referenceUsers = JSON.parse(
  fs.readFileSync(path.join(projectDir, 'docs/reference-users.json'), 'utf8')
).rows
const referenceUsersByName = new Map(referenceUsers.map(user => [user.userName, user]))
const h2Output = path.join(projectDir, 'src/main/resources/organization-data.sql')
const mysqlOutput = path.join(projectDir, 'db/mysql/source/organization-data.sql')

const departments = []
function walk(items, parentId, ancestors, depth) {
  for (const [index, item] of (items || []).entries()) {
    const itemAncestors = parentId === 0 ? '0' : `${ancestors},${parentId}`
    departments.push({
      deptId: Number(item.id),
      parentId,
      ancestors: itemAncestors,
      deptName: item.label,
      orderNum: index
    })
    walk(item.children, Number(item.id), itemAncestors, depth + 1)
  }
}
walk(reference.data, 0, '0', 0)

const localUserProfiles = new Map([
  [1, { deptId: 329, userName: '17630135660', nickName: '方铭渴' }],
  [2, { deptId: 323, userName: '17673605591', nickName: '孟声怎' }],
  [3, { deptId: 325, userName: '15685266238', nickName: '曹好疲' }],
  [4, { deptId: 325, userName: '17689037697', nickName: '卞在重' }],
  [5, { deptId: 323, userName: '15646844893', nickName: '费有感' }],
  [6, { deptId: 323, userName: '13689213154', nickName: '雷记那' }],
  [7, { deptId: 330, userName: '15650389435', nickName: '昌空紧' }],
  [8, { deptId: 324, userName: '15609825919', nickName: '吕听敢' }],
  [9, { deptId: 324, userName: '17655872713', nickName: '苏记勇' }]
])

const groups = [
  { groupId: 49, groupName: '财务角色组', sort: 1 },
  { groupId: 50, groupName: '其他', sort: 5 },
  { groupId: 53, groupName: '流程设计', sort: 3 },
  { groupId: 56, groupName: '测试角色组', sort: 0 }
]

const groupMembers = new Map([
  [49, [7, 8]],
  [50, [1, 2, 11, 14, 15]],
  [53, [3, 4, 5, 6, 9, 10, 12, 13, 16, 17, 18]],
  [56, []]
])

const localUsernames = new Set([
  '17630135660',
  '17673605591',
  '15685266238',
  '17689037697',
  '15646844893',
  '13689213154',
  '15650389435',
  '15609825919',
  '17655872713'
])
const roleIdByName = new Map([
  ['普通员工', 2],
  ['总经理', 3],
  ['财务部经理', 4],
  ['副总-销售', 5],
  ['项目总管', 6],
  ['财务会计', 7],
  ['财务出纳', 8],
  ['合同归档', 9],
  ['副总-生产', 10],
  ['采购', 11],
  ['综合部经理', 12],
  ['盖章确认', 13],
  ['回收作废', 14],
  ['销售', 15],
  ['执行董事', 16],
  ['党建-项目总管', 17],
  ['党建-副总', 18]
])
const passwordHash = '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy'
const extraUsers = referenceUsers
  .filter(user => !localUsernames.has(user.userName))
  .map((user, index) => ({
    ...user,
    localUserId: 10 + index,
    localDeptId: user.dept?.deptId || 329,
    localRoleIds: (user.roles || [])
      .map(role => roleIdByName.get(role.roleName))
      .filter(Boolean)
  }))

function sqlString(value) {
  return `'${String(value).replaceAll('\\', '\\\\').replaceAll("'", "''")}'`
}

const h2 = []
const mysql = []

for (const group of groups) {
  h2.push(
    'MERGE INTO sys_role_group (group_id, tenant_id, group_name, sort) ' +
    `KEY (group_id) VALUES (${group.groupId}, 1, ${sqlString(group.groupName)}, ${group.sort});`
  )
  mysql.push(
    'INSERT INTO sys_role_group (group_id, tenant_id, group_name, sort) ' +
    `VALUES (${group.groupId}, 1, ${sqlString(group.groupName)}, ${group.sort}) ` +
    'ON DUPLICATE KEY UPDATE group_name = VALUES(group_name), sort = VALUES(sort);'
  )
}

h2.push('DELETE FROM sys_role_group_member;')
mysql.push('DELETE FROM sys_role_group_member;')
for (const [groupId, roleIds] of groupMembers) {
  for (const roleId of roleIds) {
    h2.push(
      `MERGE INTO sys_role_group_member (group_id, role_id) KEY (group_id, role_id) VALUES (${groupId}, ${roleId});`
    )
    mysql.push(
      `INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (${groupId}, ${roleId});`
    )
  }
}

for (const department of departments) {
  const values = [
    department.deptId,
    1,
    department.parentId,
    department.ancestors,
    department.deptName,
    department.orderNum
  ]
  h2.push(
    'MERGE INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, status, del_flag) ' +
    `KEY (dept_id) VALUES (${values[0]}, ${values[1]}, ${values[2]}, ${sqlString(values[3])}, ` +
    `${sqlString(values[4])}, ${values[5]}, '0', '0');`
  )
  mysql.push(
    'INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, status, del_flag) ' +
    `VALUES (${values[0]}, ${values[1]}, ${values[2]}, ${sqlString(values[3])}, ` +
    `${sqlString(values[4])}, ${values[5]}, '0', '0') ` +
    'ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), ' +
    'dept_name = VALUES(dept_name), order_num = VALUES(order_num), del_flag = VALUES(del_flag);'
  )
}

for (const [userId, profile] of localUserProfiles) {
  const referenceUser = referenceUsersByName.get(profile.userName)
  const isAdmin = referenceUser?.isAdmin === '1' ? '1' : '0'
  const isOwner = referenceUser?.isOwner === '1' ? '1' : '0'
  const update = `UPDATE sys_user SET dept_id = ${profile.deptId}, nick_name = ${sqlString(profile.nickName)}, ` +
    `is_admin = ${sqlString(isAdmin)}, is_owner = ${sqlString(isOwner)} WHERE user_id = ${userId};`
  h2.push(update)
  mysql.push(update)
}

for (const user of extraUsers) {
  const values = [
    user.localUserId,
    1,
    user.localDeptId,
    user.userName,
    user.nickName || user.userName,
    user.email || '',
    user.phonenumber || user.userName,
    user.sex || '0',
    user.status || '0',
    user.isAdmin === '1' ? '1' : '0',
    user.isOwner === '1' ? '1' : '0'
  ]
  h2.push(
    'MERGE INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, ' +
    'email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) ' +
    `KEY (user_id) VALUES (${values[0]}, ${values[1]}, ${values[2]}, ${sqlString(values[3])}, ` +
    `${sqlString(values[4])}, ${sqlString(passwordHash)}, ${sqlString(values[5])}, ` +
    `${sqlString(values[6])}, ${sqlString(values[7])}, '', ${sqlString(values[8])}, '0', ` +
    `${sqlString(values[9])}, ${sqlString(values[10])}, ` +
    `'reference', '参考站演示成员');`
  )
  mysql.push(
    'INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, ' +
    'email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) ' +
    `VALUES (${values[0]}, ${values[1]}, ${values[2]}, ${sqlString(values[3])}, ` +
    `${sqlString(values[4])}, ${sqlString(passwordHash)}, ${sqlString(values[5])}, ` +
    `${sqlString(values[6])}, ${sqlString(values[7])}, '', ${sqlString(values[8])}, '0', ` +
    `${sqlString(values[9])}, ${sqlString(values[10])}, ` +
    `'reference', '参考站演示成员') ` +
    'ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), ' +
    'email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), ' +
    'is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);'
  )
}

h2.push(`DELETE FROM sys_user_role WHERE user_id BETWEEN 10 AND ${9 + extraUsers.length};`)
mysql.push(`DELETE FROM sys_user_role WHERE user_id BETWEEN 10 AND ${9 + extraUsers.length};`)
for (const user of extraUsers) {
  for (const roleId of user.localRoleIds) {
    h2.push(
      `MERGE INTO sys_user_role (user_id, role_id) KEY (user_id, role_id) VALUES (${user.localUserId}, ${roleId});`
    )
    mysql.push(
      `INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (${user.localUserId}, ${roleId});`
    )
  }
}

h2.push('DELETE FROM sys_dept WHERE dept_id BETWEEN 100 AND 104;')
mysql.push('DELETE FROM sys_dept WHERE dept_id BETWEEN 100 AND 104;')

h2.push("UPDATE sys_tenant SET tenant_name = '海豹科技有限公司' WHERE tenant_id = 1;")
mysql.push("UPDATE sys_tenant SET tenant_name = '海豹科技有限公司' WHERE tenant_id = 1;")
h2.push('DELETE FROM sys_dept_module;')
mysql.push('DELETE FROM sys_dept_module;')
h2.push(`INSERT INTO sys_dept_module (dept_id, module_id)
SELECT DISTINCT u.dept_id, rm.module_id
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.user_id
JOIN sys_role_module rm ON rm.role_id = ur.role_id
WHERE u.del_flag = '0';`)
mysql.push(`INSERT INTO sys_dept_module (dept_id, module_id)
SELECT DISTINCT u.dept_id, rm.module_id
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.user_id
JOIN sys_role_module rm ON rm.role_id = ur.role_id
WHERE u.del_flag = '0';`)
h2.push(`INSERT INTO sys_dept_module (dept_id, module_id)
SELECT 329, m.module_id FROM sys_module m
WHERE m.status = '0' AND NOT EXISTS (
  SELECT 1 FROM sys_dept_module dm WHERE dm.dept_id = 329 AND dm.module_id = m.module_id
);`)
mysql.push(`INSERT INTO sys_dept_module (dept_id, module_id)
SELECT 329, m.module_id FROM sys_module m
WHERE m.status = '0' AND NOT EXISTS (
  SELECT 1 FROM sys_dept_module dm WHERE dm.dept_id = 329 AND dm.module_id = m.module_id
);`)

fs.writeFileSync(h2Output, `${h2.join('\n')}\n`)
fs.writeFileSync(mysqlOutput, `${mysql.join('\n')}\n`)

console.log(
  `Generated ${departments.length} departments, ${groups.length} role groups, ` +
  `${extraUsers.length} additional users`
)
