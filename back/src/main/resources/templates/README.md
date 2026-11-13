# 固定模板目录

所有固定 Excel 模板在本目录随后端 JAR 一起发布，不依赖 `data/uploads` 或 `/profile/template`。

- `hr/catalog.json`：人事模板名称、说明、字段顺序、表头、类型、必填规则、导入别名与菜单分组的唯一来源。前端通过认证接口读取，实际报表也使用这里的字段定义。
- `hr/*.xlsx`：15 种空白模板；`hr/all.xlsx` 为同版本合集。
- `members/catalog.json`、`members/address-book.xlsx`：邀请成员通讯录模板。
- `business/catalog.json`、`business/*.xlsx`：客户档案、职位和人才导入模板及统一字段定义。招聘页面读取后端固定模板后，按当前可见数据附加“可选编号”工作表；该工作表不存入固定模板资源。

接口：`GET /system/templates/hr/catalog`、`GET /system/templates/hr/{type}`（`all` 下载合集）、`GET /system/templates/members/address-book`。要求登录及对应业务模块访问权限；空模板不包含任何真实业务数据。

客户与招聘接口：`GET /system/templates/business/{type}/catalog`（单项字段定义）、`GET /system/templates/business/{type}`（固定模板），type 为 `customer`、`jobs`、`talents`，分别验证对应模块的访问权限。

调整字段时先修改目录定义，再使用开发工具 `back/tools/build-fixed-templates.mjs` 重新生成并提交 Excel 文件。工具需要 `@oai/artifact-tool`，可在安装该包的开发环境运行 `node back/tools/build-fixed-templates.mjs back/src/main/resources/templates`，或设置 `ARTIFACT_TOOL_MODULE` 指向该模块。生产后端无需安装此工具。

运行 `node --test back/tools/hr-templates.test.mjs` 校验表头、文本格式、空白数据、薪资往返兼容和报表合计。目录定义与生成文件必须一起更新。用工类型说明应与后端 `HrEmploymentType` 保持一致。

模板第一行为表头，附有“填写说明”，前 500 行编号、手机、银行卡、日期等按文本保存。薪资标准保留 `monthly/daily`；工资回传应使用具体工资草稿导出的实际批次文件，保留批次与版本。

实际人员、薪资、考勤、工资报表仍由原业务接口按当前筛选和权限取数，前端使用后端提供的字段定义生成文件；不要将实际报表或人员数据保存到本目录。

通讯录“部门ID/部门”必填，可填同一企业的有效部门ID或完整名称；名称不存在、停用或重名时整批拒绝，重名须填写ID。兼容旧表头“部门ID”“部门”“部门名称”。前端读取 `GET /system/templates/members/catalog` 的字段定义，上传文件在浏览器解析，通过 `POST /system/user/member-import/preview?moduleId=...` 整批校验后展示预览；确认时复用 `submitConfiguredAction('invite', ...)`，遵循该模块已有邀请审批设置。部门ID及名称在申请时冻结，执行时重新校验，不会把审批中被改名或删除的部门自动替换为其他部门。
