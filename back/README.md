# UMM OA Spring Boot Backend

`umm-oa-back` 是根据 `lavieAll/qs-web`（以及当前 `fanyopen/umm-oa` 工作副本）反向整理的本地后端工程。

## 已实现

- Spring Boot 3.3.5 / Java 17 / Spring JDBC
- H2 内存数据库开箱即用，MySQL 8 初始化脚本可直接导入
- 图形验证码、登录、JWT、`getInfo`、`getRouters`、退出
- 用户、部门、角色、菜单、字典、参数、公告、岗位、登录日志、操作日志
- 应用中心、应用模块、企业切换、管理员组
- 项目、项目成员、合同、采购、报销、预算、仓库、待办
- 本地上传、下载和 `/profile/**` 静态资源访问
- 722 个前端 API 调用的静态清单；未实现业务端点由兼容控制器返回前端可消费的空结构
- 集成测试覆盖验证码、登录、用户信息、项目列表和匿名访问拦截

## 本地启动

要求 JDK 17。Maven 通过项目自带的 Wrapper 获取。

```powershell
cd E:\www\umm-oa\back
.\mvnw.cmd spring-boot:run
```

默认地址：`http://127.0.0.1:8080`

本地演示账号：

- 用户名：`17630135660`（超级管理员）
- 用户名：`17600000001`（人事经理，独立管理组织架构与成员）
- 密码均为 `admin123123`
- 验证码校验默认关闭，开发时 `debugCode` 会随验证码接口返回

### 模块与动作权限

系统唯一授权来源是：

- `sys_role_module`：角色可访问的页面模块
- `sys_module_action`：模块支持的按钮动作字典
- `sys_role_module_action`：角色在模块内被授予的具体动作
- `sys_role.data_scope`：组织数据范围

模块授权本身代表可以进入页面、查看列表和详情。角色权限树只在普通业务模块下增加
“新增、修改、删除、导出”动作；组织、角色和流程设置使用“管理”动作。
客户档案和商机管理按实际页面提供“新增、修改、删除、跟踪”四个动作，客户跟进写入独立校验 `track`。
客户档案、联系人、跟进、客户分配、公海、客户分析、风险提醒和商机构成 CRM 闭环。
人事管理使用独立一级菜单，成员管理复用组织架构的邀请成员、调整部门、转为离职公共组件。
`/getInfo.permissions` 同时包含模块读取权限和已勾选的动作权限，后端接口通过
`@RequiresModuleAction` 校验同一套授权。

`sys_menu` / `sys_role_menu` 不再参与登录后的业务鉴权。
组织权限诊断接口为 `GET /system/permission/organization`。

业务接口的读取操作只要求模块访问权限，删除、新增、修改分别校验对应动作；提交审批复用新增或修改权限。
`list:all`、`list:apply` 不再生成角色动作，数据范围和“仅本人”过滤统一由后端查询执行。
角色被停用或软删除后，其模块授权和按钮权限立即失效。
流程设置接口读取要求模块 `340771` 访问权限，修改要求 `manage` 动作。
模块授权入口统一为角色；原部门、成员模块树接口返回 `410`，避免保存不会生效的授权。
业务按钮通过统一的 `POST /workflow/action/execute` 执行。流程设计按
`moduleId + actionCode` 绑定，未启用审批时直接执行，启用后自动创建审批待办，
审批通过再执行原业务动作。具体规则见
[动作级流程绑定](docs/workflow-action-binding.md)。
自定义动作通过“系统 -> 流程设置 -> 动作管理”维护，执行逻辑由 `handlerKey`
注册表解析；可复用的动作不需要重新修改 Java。

接口示例：

```powershell
$captcha = Invoke-RestMethod http://127.0.0.1:8080/captchaImage
$body = @{
  username = "17630135660"
  password = "admin123123"
  uuid = $captcha.uuid
  code = $captcha.debugCode
} | ConvertTo-Json

$login = Invoke-RestMethod http://127.0.0.1:8080/login `
  -Method Post -ContentType application/json -Body $body

Invoke-RestMethod http://127.0.0.1:8080/getInfo `
  -Headers @{ Authorization = "Bearer $($login.token)" }
```

## 前端联调

前端 `.env.development` 的接口前缀仍为 `/api-qs`。本地联调时把 `vite.config.js` 中 `/api-qs` 代理改为：

```js
'/api-qs': {
  target: 'http://127.0.0.1:8080',
  changeOrigin: true,
  rewrite: path => path.replace(/^\/api-qs/, '')
}
```

当前工作区的 `vite.config.js` 已指向公开演示 API，本工程没有擅自覆盖该设置。

## MySQL

1. 导入 [db/mysql/umm_oa.sql](db/mysql/umm_oa.sql)。
2. 设置环境变量并启用 MySQL profile：

```powershell
$env:SPRING_PROFILES_ACTIVE = "mysql"
$env:DB_URL = "jdbc:mysql://127.0.0.1:3306/umm_oa?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your-password"
$env:CAPTCHA_VALIDATE = "true"
$env:JWT_SECRET = "replace-with-a-random-secret-at-least-32-bytes"
.\mvnw.cmd spring-boot:run
```

生产环境必须：

- 修改默认管理员密码
- 设置独立 `JWT_SECRET`
- 开启验证码校验
- 使用最小权限数据库账号
- 将上传目录放到持久化磁盘

## 文档与工具

- [接口清单](docs/api-inventory.md)：从 `src/api` 自动提取的 722 个调用
- [实现状态](docs/implementation-status.md)：真实实现与兼容层边界
- [模块动作目录](docs/module-action-catalog.md)：按实际页面按钮维护的模块动作清单
- [参考数据与比对](docs/reference-audit.md)：线上参考数据、页面和接口差异说明
- [文档索引](docs/README.md)：当前维护文档和生成数据说明
- `deploy/`：Linux systemd 服务配置和环境变量模板
- [开发工具说明](tools/README.md)：数据构建、采集、比对和部署脚本

## 重要边界

这个仓库不是通过反编译获得的后端原码。业务规则、流程引擎、报表统计和第三方集成只在能够从 UI、接口路径、字段名和公开响应确认的范围内还原；剩余端点不会伪造成功数据，而是通过兼容控制器返回空分页/空对象，并带 `X-UMM-OA-Compatible: fallback` 响应头。

