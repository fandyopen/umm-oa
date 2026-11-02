# 开发工具说明

这些脚本用于数据生成、参考站比对和部署，不参与 Spring Boot 应用启动。

## 源码检查

在项目根目录运行 npm run audit:source，或运行 node back/tools/audit-source.mjs --details 查看详细报告。
检查入口包括固定路由、全局注册组件、实际模块目录及其依赖，报告输出到 back/target/source-audit.json。
人事、共享成员和客户合同业务源码同时检查数字模块权限常量；新增页面应按业务组件解析模块上下文。
动态组件、详情页、预览和历史书签可能没有菜单入口，不能只因隐藏就删除。
如数据库添加过自定义模块，应先补充 back/target/source-audit-runtime.json 的模块数组，再检查页面可达性。
审计工具仅生成报告，不会修改源文件、数据库或执行退役 SQL。

## 模块上下文回归

在项目根目录执行 `npm run test:module-context`，验证动态 ID、跨页面权限、异步加载、缓存隔离及成员工作流入参。
同时验证初始化静默、按钮级提示、缺失模块点击重试、重复点击保护、主数据独立加载及成员操作弹层。
后端 `ModuleContextIntegrationTest` 使用独立内存库验证查询和权限边界。业务约定见 [业务模块上下文](../docs/module-context.md)。

## 数据构建脚本

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
