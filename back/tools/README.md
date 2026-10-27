# 开发工具说明

这些脚本用于数据生成、参考站比对和部署，不参与 Spring Boot 应用启动。

## 数据构建

```powershell
node tools/build-module-sql.mjs
node tools/build-capability-data.mjs
node tools/build-organization-data.mjs
node tools/build-parity-data.mjs
node tools/build-system-reference-data.mjs
node tools/build-todo-parity-data.mjs
node tools/build-workflow-parity-data.mjs
node tools/build-mysql-sql.mjs
```

最后执行 `build-mysql-sql.mjs`，从各拆分 SQL 重新生成 `db/mysql/umm_oa.sql`。
拆分 SQL 位于 `db/mysql/source/` 并纳入 Git；部署时自动排除，最终导入文件固定为
`db/mysql/umm_oa.sql`。

## 参考数据采集与比对

- `capture-reference-page-api.mjs`
- `capture-reference-workflow.mjs`
- `probe-reference-accounts.mjs`
- `compare-module-parity.mjs`
- `mobile-audit.mjs extract`：抓取 H5 页面和接口清单
- `mobile-audit.mjs compare`：比较移动端接口映射
- `extract-api-inventory.mjs`

这些脚本需要访问参考站或本地页面，不属于生产运行依赖。

## 部署辅助

- `deploy_remote.py`：完整部署
- `deploy_remote.py <command>`：在服务器执行单条维护命令

部署脚本不会自动执行。实际同步服务器前必须先确认目标、备份和发布窗口。
完整部署不会上传 `back/tools`、`back/docs`、`db/source` 或
`db/mysql/source`，服务器只接收运行时文件和最终 SQL。
