# 权限码映射表

## 1. 统一授权规则

系统只有一套业务授权来源：`sys_role_module`。

| 层级 | 数据来源 | 用途 |
| --- | --- | --- |
| 角色授权 | `sys_role_module` | 决定角色可访问的模块 |
| 页面访问 | 已授权模块的 `sys_module.perms` | `/getRouters` 下发菜单和页面 |
| 模块动作字典 | `sys_module_action` | 只定义页面上真实存在的新增、修改、删除、导出或管理动作 |
| 角色动作授权 | `sys_role_module_action` | 决定角色在模块内可执行哪些动作 |
| 按钮动作 | `sys_module_action.permission_code` | `/getInfo.permissions` 配合 `v-hasPermi` |
| 数据范围 | `sys_role.data_scope` | 决定组织数据可见范围 |
| 超级管理员 | `admin` 角色 / `sys_admin_identity` | 返回 `*:*:*`，不受模块过滤 |

判断规则：

- `sys_menu` 和 `sys_role_menu` 不再参与登录后的业务鉴权。
- 模块授权本身代表可以进入页面、查看列表和详情，不再单独设置“查看列表/查看详情”动作。
- 普通角色被授予模块后，只有需要写操作时才勾选新增、修改、删除、导出等动作。
- 提交审批复用新增或修改权限，不再生成独立的 `submit` 动作。
- `v-hasPermi="['xxx']"` 检查 `/getInfo.permissions`，权限码来自角色动作授权。
- `admin` 角色和固定超级管理员身份直接返回 `*:*:*`。
- 组织架构页签由模块 `340782` 与 `sys_role.data_scope` 判断。
- 角色权限页签由模块 `340597` 与 `sys_role.data_scope` 判断。
- 后端接口通过 `@RequiresModuleAction` 校验同一套动作，不能通过直接调用接口绕过前端按钮。
- 项目、合同、采购、资金、报销、仓库等业务路径会按请求方法自动映射模块动作；显式注解优先于路径规则。
- 停用或软删除的角色不再下发权限，角色删除时会同步清理模块动作和用户关联，旧 `sys_role_menu` 记录不参与鉴权。
- 流程设置和菜单管理接口使用模块 `340771` 的 `manage` 动作，读取只需要模块访问权限。
- 模块授权统一到角色页签；`/app/user/deptMenuTree` 和 `/app/user/userMenuTree` 已停用并返回 `410`。
- `list:all`、`list:apply` 不属于动作权限，全部/本部门/本部门及以下/仅本人由 `sys_role.data_scope` 在后端查询中过滤。
- 人事管理使用一级模块 `341000` 和成员模块 `341001`；组织架构与人事管理共用邀请、调整部门和转为离职动作。

`sys_role.data_scope`：`1` 全部 / `2` 自定义 / `3` 本部门 / `4` 本部门及以下 / `5` 仅本人。
`5`（仅本人）视为无权查看他人组织关系；角色授权额外要求 `data_scope = 1`。

诊断接口：`GET /system/permission/organization` 返回模块命中情况、数据范围和最终结果。

## 2. 通用动作后缀

普通业务模块只生成以下动作：

| 后缀 | 含义 |
| --- | --- |
| `add` | 新增 |
| `edit` | 修改 |
| `remove` | 删除 |
| `export` | 导出 |

例如客户管理和商机管理：

```text
project:merchant:add
project:merchant:edit
project:merchant:remove
project:merchant:track
project:business:add
project:business:edit
project:business:remove
project:business:track
```

`resetPwd`、`update`、`list`、`query`、`list:all`、`list:apply` 不再作为通用角色动作生成。
导入类文件解析属于页面内操作；确有独立导入接口时，再为该模块单独增加专用动作。

## 3. 系统与管理权限

| 功能 | 页面访问权限 | 按钮/动作权限 | 当前状态 |
| --- | --- | --- | --- |
| 组织机构 | 模块 `340782` | 管理 | 读取由模块授权控制 |
| 角色权限 | 模块 `340597` | 管理 | 读取由模块授权控制 |
| 流程设置 | 模块 `340771` | 管理 | 读取由模块授权控制 |
| 基础数据 | `system:dict:list` | `system:dict:{add,edit,remove,export}` | 已下发 |
| 参数设置 | `system:config:list` | `system:config:{add,edit,remove,export}` | 已下发 |
| 通知公告 | `system:notice:list` | `system:notice:{add,edit,remove}` | 已下发 |
| 操作日志 | `monitor:operlog:list` | `monitor:operlog:{remove,export}` | 已下发 |
| 登录日志 | `monitor:logininfor:list` | `monitor:logininfor:{remove,export}` | 已下发 |

`sys:module` 在本地同时控制“角色权限”和“流程设置”页面，以及流程设置中的新增、修改、删除和流程设计配置。

## 4. 业务页面权限

| 资源前缀 | 页面 | 页面访问权限 | 动作权限 |
| --- | --- | --- | --- |
| `todoItem:todo` | 我的待办 | `todoItem:todo:list` | 无独立按钮权限 |
| `todoItem:done` | 我的已办、我的发起 | `todoItem:done:list` | 无独立按钮权限 |
| `project:business` | 商机管理 | `project:business:list` | `{add,edit,remove,track}` |
| `project:bid` | 招标投标 | `project:bid:list` | `{add,edit,remove,export}` |
| `project:merchant` | 客户档案 | `project:merchant:list` | `{add,edit,remove,track}` |
| `project:project` | 项目登记、项目变更、项目查询 | `project:project:list` | `{add,edit,remove,export}` |
| `project:mem` | 人员安排 | `project:mem:list` | `{add,edit,remove,export}` |
| `project:exec` | 报量结算 | `project:exec:list` | `{add,edit,remove,export}` |
| `project:employee` | 零星用工 | 无页面权限码 | `{add,edit,remove,export}` |
| `project:quantities` | 分包综合查询 | `project:quantities:list` | 无 |
| `project:schedule` | 任务计划 | `project:schedule:list` | `{add,edit,remove,export}` |
| `project:processWrite` | 工作量填报 | `project:processWrite:list` | `{add,edit,remove,export}` |
| `project:warehouse` | 库房管理 | `project:warehouse:list` | `{add,edit,remove,export}` |
| `project:warehouseInApply` | 入库申请 | `project:warehouseInApply:list` | `{add,edit,remove,export}` |
| `project:warehouseOutapply` | 出库申请 | `project:warehouseOutapply:list` | `{add,edit,remove,export}` |
| `project:warehouseTransferApply` | 划拨申请 | `project:warehouseTransferApply:list` | `{add,edit,remove,export}` |
| `project:contract` | 合同拟定、合同查询 | `project:contract:list` | `{add,edit,remove,export}` |
| `project:contractChange` | 合同变更 | `project:contractChange:list` | `{add,edit,remove,export}` |
| `project:paymentApply` | 采购申请、采购查询 | `project:paymentApply:list` | `{add,edit,remove,export}` |
| `project:fund` | 资金计划 | `project:fund:list` | `{add,edit,remove,export}` |
| `project:income` | 资金计划收入 | `project:income:list` | `{add,edit,remove,export}` |
| `project:outcome` | 资金计划支出 | `project:outcome:list` | `{add,edit,remove,export}` |
| `project:earnestMoney` | 保证金申请 | `project:earnestMoney:list` | `{add,edit,remove,export}` |
| `project:earnestReturn` | 保证金退还 | `project:earnestReturn:list` | `{add,edit,remove,export}` |
| `project:type` | 预算科目 | `project:type:list` | 无 |
| `project:apply` | 预算制订、预算变更 | `project:apply:list` | `{add,edit,remove,export}` |
| `project:expense` | 个人报销、报销查询 | `project:expense:list` | `{add,edit,remove,export}` |
| `project:loan` | 借款申请 | `project:loan:list` | `{add,edit,remove,export}` |
| `project:repay` | 直接还款 | `project:repay:list` | `{add,edit,remove,export}` |
| `project:payment` | 业务支付、付款查询 | `project:payment:list` | `{add,edit,remove,export}` |
| `project:make` | 开票申请 | `project:make:list` | `{add,edit,remove,export}` |
| `project:get` | 票据登记 | `project:get:list` | `{add,edit,remove,export}` |
| `project:invoice` | 票据综合查询 | `project:invoice:list` | `{add,edit,remove,export}` |
| `project:history` | 变更历史 | `project:history:list` | `{add,edit,remove,export}` |
| `project:real` | 收款登记 | 无页面权限码 | `{add,edit,remove}` |

## 5. 特殊权限

| 权限码 | 映射功能 |
| --- | --- |
| `*:*:*` | 全部权限通配符 |
| `sys:module` | 角色权限、流程设置、模块编辑、流程设计配置 |
| `eos:budget:integratedQuery` | 预算综合查询 |
| `eos:contract:integratedQuery` | 合同综合查询 |
| `eos:earnest:integratedQuery` | 保证金综合查询 |
| `eos:pay:integratedQuery` | 支付综合查询 |
| `eos:porject:integratedQuery` | 项目综合查询；`porject` 是现有权限码中的拼写，不能自行改成 `project` |
| `eos:purchase:integratedQuery` | 采购综合查询 |
| `eos:gzt:gg` | 高管工作台 |
| `eos:gzt:xmzg` | 项目总管工作台 |
| `eos:gzt:cwzg` | 财务主管工作台 |
| `eos:gzt:xszg` | 销售主管工作台 |
| `eos:gzt:gy` | 普通员工工作台 |

## 6. 当前账号权限快照

| 账号 | 角色 | 授权结果 |
| --- | --- | --- |
| `17630135660` | `admin` | `*:*:*`，全部模块 |
| `13686621113` | `admin`、系统管理员 | `*:*:*`，全部模块 |
| `17655872713` | `admin_manager`、`seal_confirm`、`void_recovery` | 组织机构 `340782`、通知公告 `340316` 等已授权模块 |

`17655872713` 在模块授权下可获得组织架构页签，但未获得模块 `340597` 时不会显示角色权限管理能力。

## 7. 尚未映射为模块的动作权限

以下权限码仍被旧页面模板使用，但对应业务没有可授权的 `sys_module` 行。若继续保留这些页面，
应补充模块或移除旧模板入口，不能重新引入 `sys_menu` 授权：

| 权限族 | 使用位置 | 状态 |
| --- | --- | --- |
| `system:user:*` | 用户管理旧模板 | 组织机构改走模块 `340782` |
| `system:role:*` | 角色管理旧模板 | 角色授权改走模块 `340597` |
| `system:dept:*` | 部门管理旧模板 | 组织机构改走模块 `340782` |
| `system:post:*` | 岗位管理旧模板 | 当前没有对应模块 |
| `system:menu:*` | 菜单管理旧模板 | 流程设置改走 `sys:module` |
| `project:stage:*` | 阶段管理 | 当前没有对应模块 |
| `project:warehouse*Detail:*` | 仓库明细 | 当前没有对应模块 |

代码中还把操作日志导出写成了 `system:config:export`，应改为：

```text
monitor:operlog:export
```

## 8. 快速排查

按钮不显示时依次检查：

1. 浏览器当前登录账号是否正确。
2. `sys_role_module` 是否已授予目标模块。
3. `sys_role_module_action` 是否包含目标的 `module_id + action_code`。
4. `/getInfo` 是否返回 `*:*:*` 或对应动作的 `permission_code`。
5. `/system/permission/organization` 中的 `org_module_granted` / `role_module_granted` 和 `data_scope` 是否正确。
6. 检查页面是否仍在使用没有模块动作映射的旧权限码。
