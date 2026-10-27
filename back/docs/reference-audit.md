# 线上参考与比对说明

本项目通过公开页面和接口采集了页面结构、账号权限、组织数据、工作流和移动端接口等参考信息。
原始 JSON 保存在 `back/docs` 和 `back/db/source`，用于重新生成本地演示数据和执行差异比对。

## 保留的原始数据

- `reference-account-capabilities.json`：账号能力快照
- `reference-admin-groups.json`、`reference-roles.json`、`reference-role-admin-list.json`：角色与管理组
- `reference-departments.json`、`reference-users.json`：组织架构和演示账号
- `reference-system-settings.json`：字典和参数
- `reference-page-api.json`、`page-parity.json`：页面接口与页面差异
- `reference-workflow-2575064.json`：工作流样例
- `reference-todo-13686621113.json`：待办样例
- `mobile-api-parity.json`、`reference-mobile-inventory.json`：移动端接口证据
- `db/source/remote-routers.json`：线上模块路由原始快照

## 已合并内容

账号功能矩阵、页面截图说明、UI 证据、源码差异和移动运行时说明已合并到本文档。
这些内容只用于参考分析，不参与应用运行。

## 数据用途

- 组织参考数据生成：`tools/build-organization-data.mjs`
- 模块与页面数据生成：`tools/build-module-sql.mjs`
- 业务页面兼容数据生成：`tools/build-parity-data.mjs`
- 工作流和待办数据生成：`tools/build-workflow-parity-data.mjs`、`tools/build-todo-parity-data.mjs`
- 系统字典与参数生成：`tools/build-system-reference-data.mjs`

参考数据的采集时间、远端地址和抓取参数以各 JSON 文件内字段为准。
