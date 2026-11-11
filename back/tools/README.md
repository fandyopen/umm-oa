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

## 商机推进回归

在项目根目录执行 `npm run test:business-stage`，验证按顺序推进、只读节点记录、查看历史保留草稿、连续保存保留弹窗、赢单与输单终态，以及失败重试和附件校验。
测试使用模拟接口，不修改业务数据库。
后端 `BusinessStageIntegrationTest` 使用独立内存库验证跳级、回退、重复推进拦截及节点历史保护；商务谈判后可选择赢单或输单，两者均为终态。

## 商机转合同回归

在项目根目录执行 npm run test:contract-conversion，验证创建/修改提示、创建后打开详情、重复提交保护和失败保留表单。后端 CustomerContractIntegrationTest 同时验证商机业务 ID 与存储 ID 不一致、历史 ID 回退、客户归属错误及商机访问权限；使用独立内存库，不写入运行数据库。

## 客户跟进提醒回归

客户与商机首次跟进提醒回归：`npm run test:customer-reminder`，验证待办/已办跳转、按钮权限、客户定位、商机详情定位和审批操作隔离。
后端 `CustomerFollowupReminderIntegrationTest`、`BusinessFollowupReminderIntegrationTest` 使用独立 H2 内存库，覆盖即时派发、跨日不重复、首次跟进自动完成、负责人转交、删除/结束收口及审批通过后的派发时机。
两类提醒共用 `OwnerFollowupTodoService`：业务保存和待办生成在同一事务中执行；每条业务仅保留一条有效首次跟进待办，普通修改保留原推送时间，转交后取消旧待办并新建接收人的待办。客户保存、分配、领取立即同步，新增跟进实际保存后完成；商机首次阶段推进成功后完成。客户服务启动时仅核对已有未办项并合并旧版每日重复待办，不再定时按天派发，已办历史保留。
下级按部门主管及下属部门成员识别；普通成员不因同部门、创建人或角色数据范围宽而额外获取客户权限，超级管理员保留全部访问权限。

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
