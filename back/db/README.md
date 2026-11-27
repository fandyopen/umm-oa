# 数据库文件说明

## 运行时

- `mysql/notice-publication-upgrade.sql`：通知公告发布状态、企业归属及发布人/时间的增量升级，原记录保留为草稿。H2 和 MySQL 启动时也会自动补齐字段，不自动发布历史内容。

- `mysql/commission-contract-upgrade.sql`：存量 MySQL 8 库启用合同销售按回款结算、交付按入职或验收结算；保留原招聘提成及工资历史。备份后执行并重启后端，使用方法见 [合同提成说明](../docs/contract-commission.md)。

- `mysql/upgrade-approval-inbox.sql`：存量 MySQL 库新增“代审批”菜单和实际审批人记录表，并更新旧菜单名称；不自动授予普通角色权限。执行后重启后端，在角色管理中勾选页面和“代审批”按钮。

- `mysql/upgrade-retired-business-tables.sql`：已有 MySQL 库备份后执行，物理删除已退役的 20 张项目、采购、报销、借还款、预算和仓库表；不会删除当前客户合同、财务、招聘、人事或提成表。详细范围见 [历史业务清理记录](../docs/retired-business-cleanup.md)。

- mysql/recruitment-upgrade.sql：已有 MySQL 数据库新增招聘管理表及三项菜单后执行，随后重启后端初始化可编辑按钮。招聘使用流程见 docs/recruitment-lifecycle.md。

- `mysql/umm_oa.sql`：完整 MySQL 8 初始化脚本，生产部署只使用该文件。
- `mysql/hr-upgrade.sql`：存量人事、考勤及工资升级，包含工作地省市及手动个税状态；可重复执行，保留历史已核对或已冻结工资。应用启动也会自动补齐这些字段。
- `mysql/upgrade-workflow-action.sql`：已有 MySQL 数据库增加动作级流程绑定表时执行一次。
- `mysql/upgrade-administrator-model.sql`：已有 MySQL 数据库收敛为唯一固定超级管理员时执行一次。
- `mysql/upgrade-department-principal.sql`：已有 MySQL 数据库统一为“一部门一主管”时执行一次。

## MySQL 构建源

`mysql/source/` 下的拆分 SQL 由 `tools/build-mysql-sql.mjs`
合并生成 `mysql/umm_oa.sql`。不要直接删除，除非同时调整构建脚本和当前数据库升级方案。
`mysql/source/*.sql` 会随 Git 仓库保存，保证项目可以在其他环境重新生成数据库；
部署脚本上传服务器时只保留 `mysql/umm_oa.sql`。

## H2 构建与部署辅助文件

- `h2/capability.sql`、`h2/module.sql`：远程部署脚本仍会导入。

`src/main/resources` 下的 SQL 是 Spring Boot 本地 H2 运行时使用的最终版本；
`db/mysql` 下的拆分 SQL 是 MySQL 方言构建源，两者用途不同。
