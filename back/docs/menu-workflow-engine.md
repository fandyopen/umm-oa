# 菜单流程引擎说明

## 1. 设计目标

流程设计不能只保存一张流程图，必须形成完整闭环：

```text
页面配置流程设计
  -> 发布流程模板
  -> 业务单据保存
  -> 提交审批
  -> 创建流程实例
  -> 生成审批任务
  -> 审批人收到待办
  -> 同意或驳回
  -> 更新流程状态
  -> 回写业务单据状态
  -> 进入已办和审批历史
```

## 2. 核心数据

| 数据 | 作用 |
| --- | --- |
| `sys_module.has_flow` | 是否显示流程设计入口；`0` 表示启用 |
| `sys_module.flow_conditions` | 可选择为流程分支条件的业务字段 |
| `oa_process_design` | 按 `tenant_app_id + menu_id` 保存已发布模板 |
| `oa_process_instance` | 每次业务审批对应的流程实例 |
| `oa_process_task` | 当前及历史审批节点 |
| `oa_todo` | 审批人的待办记录 |
| `oa_process_definition` | 发布后的流程定义快照 |
| `biz_record` | 资金计划等业务单据及审批状态回写 |

## 3. 流程标识

低代码表单流程：

```text
form:<formKey>
```

业务菜单流程：

```text
business:<menuId>
```

资金计划菜单：

```text
menuId   = 340396
process  = business:340396
business = fund
```

## 4. 资金计划闭环

| 步骤 | 入口 | 结果 |
| --- | --- | --- |
| 1 | 流程设置 -> 资金计划 -> 流程设计 | 保存 `process-1-340396` |
| 2 | 资金计划 -> 新增 -> 保存申请 | 写入资金计划，状态为空或草稿 |
| 3 | 资金计划列表 -> 提交 | 调用 `/project/fund/submitApply/{id}` |
| 4 | 流程引擎读取设计 | 读取 `oa_process_design.menu_id=340396` |
| 5 | 解析第一个审批节点 | 例如审批人“严音泪” |
| 6 | 创建 `oa_process_instance` | 生成 `biz-*` 流程实例 |
| 7 | 创建 `oa_process_task` 和 `oa_todo` | 严音泪进入我的待办 |
| 8 | 审批人打开待办 | 显示资金计划详情和审批节点 |
| 9 | 审批人同意 | 流程实例变为 `approved` |
| 10 | 流程引擎回写 `biz_record` | 资金计划状态变为“审批通过” |

驳回时流程实例变为 `rejected`，资金计划状态回写为“审批未通过”。

## 5. 接口

| 接口 | 方法 | 作用 |
| --- | --- | --- |
| `/project/fund` | POST | 新增资金计划 |
| `/project/fund` | PUT | 修改资金计划 |
| `/project/fund/{id}` | GET | 查询资金计划 |
| `/project/fund/{id}` | DELETE | 删除资金计划 |
| `/project/fund/list` | GET | 查询资金计划列表，新记录优先 |
| `/project/fund/submitApply/{id}` | POST | 提交资金计划审批 |
| `/project/activity/taskWaitList` | GET | 查询当前用户待办 |
| `/project/activity/taskDoneList` | GET | 查询当前用户已办 |
| `/project/activity/complete` | POST | 同意、驳回或结束任务 |
| `/activiti/process/listHistory` | POST | 查询流程审批历史 |

## 6. 审批人解析

流程节点 `approver` 支持：

- 用户手机号
- 登录账号
- 用户姓名
- 角色名称或角色标识
- `部门负责人`、`直接主管`、`负责人`

资金计划测试节点使用：

```json
{
  "id": "approval_1",
  "type": "approval",
  "name": "部门负责人",
  "approver": "严音泪",
  "approvalMode": "any"
}
```

系统会解析为严音泪的用户 ID，并生成对应待办。

## 7. 新业务页面接入步骤

1. 在 `sys_module` 中新增页面，设置唯一 `module_id`。
2. 设置 `has_flow=0`。
3. 在流程设置中设计并发布模板。
4. 业务新增接口保存业务单据。
5. 业务提交接口调用：

```java
workflowService.submitBusiness(
    menuId,
    businessType,
    businessId,
    businessTitle,
    AuthContext.userId(),
    AuthContext.username()
);
```

6. 在 `LowCodeWorkflowService.businessModule()` 中增加业务类型与 `biz_record.module` 的映射。
7. 在前端 `busFlowMap` 中增加业务类型对应的详情组件。

## 8. 当前边界

已完成：

- 业务菜单模板读取
- 顺序审批流转
- 待办、已办、我的发起
- 同意、驳回
- 审批历史
- 资金计划状态回写
- 待办详情打开资金计划

仍需继续补全：

- 条件分支的实际条件计算
- `approvalMode=all` 的会签执行
- 多实例并行节点
- 流程撤回、转办和加签
- 流程版本冻结，避免重新发布影响正在运行的实例
