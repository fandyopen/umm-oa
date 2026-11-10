<template>
  <a-form-model :label-col="{ span: 5 }" :wrapper-col="{ span: 19 }">
    <a-form-model-item label="跟进时间" required>
      <a-date-picker v-model="form.followupTime" show-time value-format="YYYY-MM-DD HH:mm:ss" class="tw-w-full" />
    </a-form-model-item>
    <a-form-model-item label="跟进方式">
      <a-select v-model="form.method">
        <a-select-option v-for="item in methods" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
    </a-form-model-item>
    <a-form-model-item label="沟通内容" required>
      <a-textarea v-model="form.content" :auto-size="{ minRows: 4, maxRows: 8 }" placeholder="记录客户需求、反馈和沟通结论" />
    </a-form-model-item>
    <a-form-model-item label="下一步计划">
      <a-textarea v-model="form.nextPlan" :auto-size="{ minRows: 3, maxRows: 6 }" placeholder="下一步要做什么、何时跟进" />
    </a-form-model-item>
    <a-form-model-item label="下次跟进">
      <a-date-picker v-model="form.nextFollowupTime" show-time value-format="YYYY-MM-DD HH:mm:ss" class="tw-w-full" />
    </a-form-model-item>
    <div class="followup-actions">
      <a-button :disabled="saving" @click="$emit('close')">取消</a-button>
      <a-button type="primary" :loading="saving" :disabled="submitted" @click="submit">保存跟进</a-button>
    </div>
  </a-form-model>
</template>

<script>
import moment from 'moment'
import { executeWorkflowAction } from '/src/api/workflow/action.js'
import { createWorkflowActionSubmitter } from '/src/utils/workflowActionSubmit.js'
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { crmModules } from '/src/views/eos/crm/moduleContexts.js'

export default {
  name: 'FollowupForm',
  mixins: [withModuleContexts({ customer: crmModules.customer })],
  props: {
    customerId: { type: Number, required: true },
    customerName: { type: String, default: '' }
  },
  data() {
    return {
      saving: false,
      submitted: false,
      submitter: createWorkflowActionSubmitter(executeWorkflowAction),
      methods: ['电话', '微信', '邮件', '拜访', '会议', '其他'],
      form: {
        followupTime: moment().format('YYYY-MM-DD HH:mm:ss'),
        method: '电话',
        content: '',
        nextPlan: '',
        nextFollowupTime: ''
      }
    }
  },
  methods: {
    async submit() {
      if (this.saving || this.submitted) return
      if (!this.form.content.trim()) {
        this.$message.warning('请填写沟通内容')
        return
      }
      this.saving = true
      try {
        if (!(await this.checkModuleAction({ module: 'customer', action: 'track', label: '保存跟进' }))) return
        const response = await this.submitter.submit({
          moduleId: this.moduleContexts.customer.moduleId,
          actionCode: 'track',
          payload: { ...this.form, customerId: this.customerId, customerName: this.customerName }
        })
        this.$message.success(response.data.status === 'pendingApproval' ? '跟进已提交审批，通过后正式保存' : '跟进记录已保存')
        this.submitted = true
        this.$emit('ok', response.data)
      } catch (error) {
        this.$message.error(error.message || String(error))
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.followup-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>
