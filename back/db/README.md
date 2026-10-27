# 数据库文件说明

## 运行时

- `mysql/umm_oa.sql`：完整 MySQL 8 初始化脚本，生产部署只使用该文件。
- `mysql/upgrade-workflow-action.sql`：已有 MySQL 数据库增加动作级流程绑定表时执行一次。

## MySQL 构建源

`mysql/source/` 下的拆分 SQL 由 `tools/build-mysql-sql.mjs`
合并生成 `mysql/umm_oa.sql`。不要直接删除，除非同时调整构建脚本和当前数据库升级方案。
`mysql/source/*.sql` 会随 Git 仓库保存，保证项目可以在其他环境重新生成数据库；
部署脚本上传服务器时只保留 `mysql/umm_oa.sql`。

## H2 构建与部署辅助文件

- `h2/capability.sql`、`h2/module.sql`：远程部署脚本仍会导入。
- `h2/workflow.sql`：工作流校验数据构建源。

`src/main/resources` 下的 SQL 是 Spring Boot 本地 H2 运行时使用的最终版本；
`db/mysql` 下的拆分 SQL 是 MySQL 方言构建源，两者用途不同。
