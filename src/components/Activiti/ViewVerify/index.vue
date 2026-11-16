<template>
  <div>
    <process-history :instanceId="todoItem.instanceId" :isShowProcessPic="false" :isShowCloseBtn="false" />
    <template v-if="!isOnlyShowProcess && isShowReApply && taskType === 'todo' && formState === busFlowState.reject">
      <a-radio-group v-model="pass">
        <a-radio :value="true">继续提交</a-radio>
        <a-radio :value="false">结束流程</a-radio>
      </a-radio-group>
      <a-textarea class="s-mt-16" placeholder="填写批注" v-model="comment" :auto-size="{ minRows: 8 }" />
    </template>
    <template v-if="!isOnlyShowProcess && taskType === 'todo' && formState !== busFlowState.reject">
      <a-textarea class="s-mb-16" placeholder="填写审批意见" v-model="comment" :auto-size="{ minRows: 8 }" />
      <!-- class="bottom-control" -->
      <a-space>
        <a-button :loading="submitting" :disabled="submitting" class="tw-bg-[#52c41a] tw-text-white tw-border-[#52c41a]" @click="complete(true)">{{ todoItem.approvalInbox ? '代审批同意' : '同 意' }}</a-button>
        <a-button v-if="allowDelegate && !todoItem.approvalInbox" type="primary" :disabled="submitting" @click="handleDelegateTaskClick">转 办</a-button>
        <a-button :disabled="submitting" class="tw-bg-[#fa541c] tw-text-white tw-border-[#fa541c]" @click="handleRejectTask()">{{ todoItem.approvalInbox ? '代审批驳回' : '驳 回' }}</a-button>
        <a-button v-if="!pass" class="tw-bg-[#fa541c] tw-text-white tw-border-[#fa541c]" @click="handleFinishTask()">结束流程</a-button>
        <a-button @click="close">取 消</a-button>
      </a-space>
    </template>
  </div>
</template>

<script>
import { completeTask, rejectTask, finishTask } from '/src/api/activiti/process.js'
import { decideApproval } from '/src/api/activiti/approvals.js'
import { busFlowState } from '../flow.js'
import ProcessHistory from '../HistoryG6Pro.vue'

export default {
  name: 'ViewVerify',
  components: {
    ProcessHistory
  },
  props: {
    allowDelegate: { type: Boolean, default: true },
    todoItem: {
      type: Object,
      default: () => {}
    },
    // 上一个审批意见
    preAudit: {
      type: Object,
      default: () => {}
    },
    // 是否只展示流程（done、processStarted已办、我的流程只展示流程图）
    isOnlyShowProcess: {
      type: Boolean,
      default: false
    },
    // 是否展示调整申请（busDetail详情页面不展示调整申请）
    isShowReApply: {
      type: Boolean,
      default: true
    },
    // 是否点击其他按钮（转办）后关闭该drawer
    isCloseDrawer: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    taskName() {
      return this.todoItem ? this.todoItem.taskName : ''
    },
    taskType() {
      return this.todoItem ? this.todoItem.type : ''
    },
    // 表单流程状态
    formState() {
      return this.todoItem ? this.todoItem.form.state : ''
    }
  },
  data() {
    return {
      busFlowState,
      submitting: false,
      pass: true,
      comment: null
    }
  },
  methods: {
    submitDecision(data, pass, operation) {
      if (this.submitting) return Promise.reject(new Error('审批正在提交'))
      this.submitting = true
      const request = this.todoItem.approvalInbox
        ? decideApproval({ taskId: this.todoItem.taskId, pass, comment: data.comment || JSON.parse(data.variablesStr || '{}').comment })
        : operation(data)
      return request.finally(() => { this.submitting = false })
    },
    close() {
      this.$emit('close')
    },
    // 转办
    handleDelegateTaskClick() {
      if (!this.allowDelegate) return
      this.$emit('delegateTask')
      if (this.isCloseDrawer) {
        this.close()
      }
    },
    complete(isPass) {
      if (!isPass && this.isEmpty(this.comment)) {
        return this.$message.error('请填写审批意见')
      }
      const commentTmp = isPass && this.isEmpty(this.comment) ? '同意' : this.comment
      const data = {
        taskId: this.todoItem.taskId,
        instanceId: this.todoItem.instanceId,
        variablesStr: JSON.stringify({
          comment: commentTmp,
          pass: isPass,
          formData: this.todoItem
        }),
        type: this.todoItem.businessType,
        dataId: this.todoItem.formId
      }
      return this.submitDecision(data, isPass, completeTask).then(() => {
        // 刷新待办事项列表
        this.$emit('getList')
        this.$emit('ok')
        this.$emit('close')
        this.$message.success('操作成功', 3)
      })
    },
    /* 驳回请求 */
    handleRejectTask() {
      if (this.isEmpty(this.comment)) {
        return this.$message.error('请填写审批意见')
      }
      const data = {
        taskId: this.todoItem.taskId,
        instanceId: this.todoItem.instanceId,
        comment: this.comment,
        type: this.todoItem.businessType,
        dataId: this.todoItem.formId
      }
      return this.submitDecision(data, false, rejectTask).then(() => {
        // 刷新待办事项列表
        this.$emit('getList')
        this.$emit('ok')
        this.$emit('close')
        this.$message.success('操作成功', 3)
      })
    },
    /* 结束流程 */
    handleFinishTask() {
      const data = {
        taskId: this.todoItem.taskId,
        type: this.todoItem.businessType,
        dataId: this.todoItem.formId
      }
      return finishTask(data).then(() => {
        // 刷新待办事项列表
        this.$emit('getList')
        this.$emit('ok')
        this.$emit('close')
        this.$message.success('操作成功', 3)
      })
    }
  }
}
</script>
