# 历史业务表清理

2026-11-13，核对当前平台及移动端入口、Java SQL 引用、审批与权限依赖、初始化脚本和数据生成工具后，移除已退役业务的 20 张表。

| 已退役业务 | 删除的表 |
| --- | --- |
| 项目及成员 | `oa_project`、`oa_project_member` |
| 原项目合同 | `oa_contract`、`oa_contract_item` |
| 原采购申请及付款 | `oa_purchase_apply`、`oa_purchase_apply_item`、`oa_purchase_payment` |
| 原报销及发票 | `oa_expense`、`oa_expense_item`、`oa_expense_invoice` |
| 原借款及还款 | `oa_loan`、`oa_loan_detail`、`oa_repayment` |
| 原预算及资金计划 | `oa_budget`、`oa_budget_item`、`oa_fund_plan` |
| 原仓库及出入库 | `oa_warehouse`、`oa_warehouse_in`、`oa_warehouse_out` |
| 原项目发票 | `oa_invoice` |

本机检查时上述表全部为空；数据库从 110 张业务及系统表减少至 90 张。当前合同使用 `oa_crm_contract*`，当前财务使用 `oa_finance_*`，均保留。招聘、人事、提成、审批日志和业务变更历史仍在使用，空表不代表无用。

## 源码与升级

- `schema.sql` 移除上述建表及索引，`data.sql` 移除对应演示数据。
- `DataTableRegistry` 移除上述表的标签、历史标志和关系；实际表清理后不再出现在数据表管理中。
- 当前 Java 中没有这些退役表的业务控制器和服务 SQL；仍在使用的客户、商机、合同及财务代码保留。
- `retire-business-modules.sql` 对这些表改为 `DROP TABLE IF EXISTS`，兼容存量升级和重复启动。`LocalDatabaseBootstrap` 仍沿用原初始化/升级分支，不重新导入演示数据。
- `back/db/retired-modules.json` 是退役范围的唯一清单。执行 `node back/tools/build-retired-module-sql.mjs` 后再执行 `node back/tools/build-mysql-sql.mjs`，同步 H2、MySQL 及独立 MySQL 表清理脚本，防止重新生成历史表。
- 已有 MySQL 安装应先备份，再执行 `back/db/mysql/upgrade-retired-business-tables.sql`；不要为了清理而导入完整初始化 SQL。

保留 `sys_menu`、`sys_role_menu`、原角色组和部门授权等表：现有初始化、权限迁移与成员删除清理仍依赖它们。保留 `biz_record`：当前商机、按钮流程草稿与审批意见仍在使用。保留 `oa_process_definition`：当前流程设计与审批读取仍有依赖。

## 备份与恢复

本机清理前的 H2 在线一致性备份、停机后的原始数据库副本 `umm_oa-before-cleanup.mv.db`、原后端 JAR 和清理前后表记录数量清单保存在 `back/data/backups/retired-business-20261113/`，该备份目录不纳入 Git，主数据库 `back/data/umm_oa.mv.db` 仍按项目既有方式纳入 Git。

如需恢复，先停止本项目后端并另外保存清理后的数据库，再将 ZIP 内的 `umm_oa.mv.db` 恢复到 `back/data/`。恢复数据库时也需配套使用清理前的后端源码/JAR，否则新版启动升级会再次移除退役表。不要在运行中直接覆盖数据库文件。

## 验证

`RetiredBusinessSchemaTest` 覆盖新库不创建退役表、旧库存在退役表时升级删除、连续两次升级可执行，以及成员、客户、角色授权、审批请求和消息阅读状态保留。`AuthFlowIntegrationTest` 改为验证退役表不存在，数据表管理及其他业务沿用现有集成测试回归。

本次验证：31 组共 348 项后端测试通过。本机重启后登录及 `/system/data-tables` 返回成功，共 90 张表，20 张退役表均不存在；保留业务表和权限表的记录数量与备份前一致。MySQL 同步更新了初始化与升级脚本，本次没有连接实际 MySQL 服务执行迁移。
