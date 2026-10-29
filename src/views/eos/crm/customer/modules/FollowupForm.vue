<template>
  <a-form-model :label-col="{ span: 5 }" :wrapper-col="{ span: 19 }">
    <a-form-model-item label="跟进时间" required>
      <a-date-picker
        v-model="form.followupTime"
        show-time
        value-format="YYYY-MM-DD HH:mm:ss"
        style="width: 100%"
      />
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
      <a-date-picker v-model="form.nextFollowupTime" show-time value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
    </a-form-model-item>
    <div class="followup-actions">
      <a-button @click="$emit('close')">取消</a-button>
      <a-button type="primary" :loading="saving" @click="submit">保存跟进</a-button>
    </div>
  </a-form-model>
</template>

<script>
import moment from 'moment'
import { addFollowup } from '/src/api/eos/crm/index.js'

export default {
  name: 'FollowupForm',
  props: {
    customerId: { type: Number, required: true }
  },
  data() {
    return {
      saving: false,
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
    submit() {
      if (!this.form.content.trim()) {
        this.$message.warning('请填写沟通内容')
        return
      }
      this.saving = true
      addFollowup(this.customerId, this.form)
        .then(() => {
          this.$message.success('跟进记录已保存')
          this.$emit('ok')
          this.$emit('close')
        })
        .catch(error => this.$message.error(error.message || String(error)))
        .finally(() => {
          this.saving = false
        })
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
