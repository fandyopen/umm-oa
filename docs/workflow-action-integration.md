# 通用按钮审批接入

## 使用配置

1. 系统 → 模块管理 → 目标页面 → 新增按钮或修改按钮。
2. 业务执行方式选择“通用业务接口”，搜索并选择当前模块接口。
3. 路径参数填写提交数据字段，例如接口 {id} 映射到 customerId；支持 record.id。
4. 不添加请求字段映射时传递全部业务字段；添加后仅传递映射的字段。目标为接口请求字段，来源为提交数据字段。
5. 配置申请标题（例如 客户审批：{customerName}）和审批展示字段。
6. 角色授权需要同时满足配置按钮和原业务接口的操作权限。
7. 开启流程设计、保存按钮，进入设计器设置审批人，发布并启用审批。仅开启或保存草稿不会开始审批。

## 前端接入

继续使用 src/api/workflow/action.js 的 executeWorkflowAction，接口仍是 /workflow/action/execute，旧调用无需改名。
新表单推荐在组件创建时创建一次通用提交器，在点击提交且表单校验通过后调用。不要在已经保存成功的 onOk 中再次提交审批。

    import { executeWorkflowAction } from '/src/api/workflow/action.js'
    import { createWorkflowActionSubmitter } from '/src/utils/workflowActionSubmit.js'

    // 放在组件 data() 内，每个表单实例独立创建。
    const submitter = createWorkflowActionSubmitter(executeWorkflowAction)

    const response = await submitter.submit({
      moduleId: moduleMeta.moduleId,
      actionCode: 'your_action',
      payload: { recordId, ...form }
    })
    // completed 表示业务已执行；pendingApproval 表示申请已提交，正式数据尚未写入。
    // 成功后再通知父页面刷新和关闭；失败保留表单。

提交器合并正在进行的重复点击。相同数据在网络失败后重试使用同一 submissionKey，服务端返回同一提交结果；修改数据后生成新标识。

## 接口目录范围

目录自动发现 oa、hr、recruitment 业务包中的现有 Spring 写接口。接口必须有明确的 @RequiresModuleAction，返回 Map，参数仅包含数字/字符串 @PathVariable 或 Map 类型 @RequestBody。
GET、文件流、上传、RequestParam、多选权限要求、系统管理和已有专用执行器的动作不进入通用目录。接口目录中没有的复杂操作继续使用原有专用执行器。

满足条件的已有接口不用新增 WorkflowActionHandler。可选的 @WorkflowEndpoint(name="中文名称", validator="validateMethod") 提供中文名称及提交前业务校验；validator 是同一个控制器中具有相同参数签名的无副作用 public 方法。

参数映射、业务接口、标题和展示字段在申请时冻结；之后修改按钮不会改变在途申请的执行目标。业务执行仍校验申请人的账号、企业、原接口权限和业务内的数据权限，不能填写任意 URL 或执行脚本。

## 审批与执行结果

- 没有启用已发布审批：通用执行器直接调用已配置的本地业务接口。
- 已发布且启用审批：先保存申请；通过后由后端执行正式业务，驳回不执行。
- 为防止换接口绕过，启用通用审批后，同模块同原动作权限的直接写接口受保护，包括批量入口；其他按钮不能在无审批状态下执行同类写入。不同操作需要各自配置按钮。
- 执行与审批办理在同一事务内，业务失败会回滚任务完成和业务写入。任务仍待办，修正失败原因后由审批人重试；不显示虚假的保存成功。
- 重复办理已经完成的任务返回原结果，不会再次执行业务；审批任务和申请行加锁防止并发重复执行。
- 现有合同、人事、招聘、财务专用执行器、流程历史与调用参数保持兼容。

客户档案 track 作为内置使用示例：首次升级只给现存且未设置执行器的按钮绑定“保存客户跟进”，不恢复删除记录、不启用审批，也不替换用户已有执行器配置。
