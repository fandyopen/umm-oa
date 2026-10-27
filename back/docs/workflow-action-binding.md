# 动作级流程绑定

流程设计按 `moduleId + actionCode` 绑定。业务按钮不再分别创建流程提交接口，而是统一调用：

```http
POST /workflow/action/execute
```

```json
{
  "moduleId": 341001,
  "actionCode": "invite",
  "payload": {
    "nickName": "t1",
    "phonenumber": "13456789098",
    "deptId": 324
  }
}
```

## 执行规则

1. 后端校验当前账号是否拥有 `moduleId + actionCode` 动作权限。
2. 查询 `oa_module_action_flow` 中的动作绑定。
3. 未绑定或 `approval_enabled = 0` 时，直接执行业务动作。
4. 已启用审批时，创建 `oa_workflow_request`，再调用流程引擎生成流程实例和待办。
5. 最终审批通过后，流程引擎调用对应 `WorkflowActionHandler` 执行业务动作。
6. 审批驳回只更新申请状态，不修改业务数据。

## 配置方式

在“系统 -> 流程设置 -> 动作管理”中维护：

- 动作编码
- 动作名称
- 权限标识
- `handlerKey`
- 执行器 JSON 配置
- 启用状态

自定义动作保存在 `sys_module_action`。应用启动时只重新同步
`builtin = 1` 的内置动作，不会删除自定义动作。

已注册执行器可通过 `GET /workflow/action/handlers` 查询：

- `hr.member.invite`
- `hr.member.transfer`
- `hr.member.dismiss`
- `workflow.noop`：只完成流程，不修改业务数据，用于测试

在“系统 -> 流程设置 -> 流程设计”中：

1. 选择“触发动作”，例如“邀请成员”。
2. 配置审批节点。
3. 保持“启用审批”开启并发布。

动作选择区只展示启用了 `handlerKey` 的动作。未配置执行器的普通按钮动作不会出现在
流程设计中，此时页面只配置“页面默认审批”，用于兼容原有页面级流程。

页面级“页面默认审批”仅用于旧版流程兼容。成员管理的三个按钮使用：

- `invite`：邀请成员
- `transfer`：调整部门
- `dismiss`：转为离职

## 数据表

- `oa_module_action_flow`：模块动作与已发布流程设计的绑定。
- `oa_workflow_request`：待审批业务申请、审批状态和执行结果。

## 新增业务动作

已有 `handlerKey` 可以复用时，只需要在动作管理页面新增动作、授权角色并发布流程，
不需要修改 Java。

全新业务操作可以实现一个 `WorkflowActionHandler` 并声明新的 `handlerKey`。之后的
同类动作都可以复用该执行器。业务按钮统一调用 `POST /workflow/action/execute`，
不再需要为每个按钮新增流程提交接口。
