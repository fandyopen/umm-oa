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

- 用户名：`17630135660`
- 密码：`admin123123`
- 验证码校验默认关闭，开发时 `debugCode` 会随验证码接口返回

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
- [UI 证据](docs/ui-evidence.md)：截图、页面字段和数据库表的对应关系
- [源码比对](docs/source-comparison.md)：与 `lavieAll/qs-web` 的接口和截图差异结果
- `tools/extract-api-inventory.mjs`：重新扫描前端接口
- `tools/build-mysql-sql.mjs`：从开发库脚本生成 MySQL 初始化 SQL

## 重要边界

这个仓库不是通过反编译获得的后端原码。业务规则、流程引擎、报表统计和第三方集成只在能够从 UI、接口路径、字段名和公开响应确认的范围内还原；剩余端点不会伪造成功数据，而是通过兼容控制器返回空分页/空对象，并带 `X-UMM-OA-Compatible: fallback` 响应头。

