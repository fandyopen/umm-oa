<template>
  <app-shell title="审批详情" back>
    <view v-if="error" class="error" @click="load">{{ error }}</view>
    <view v-if="task">
      <view class="card">
        <view class="row">
          <text class="h2">{{ task.title || (task.form && task.form.title) || task.processName }}</text>
          <text class="tag">{{ task.taskName || '业务提醒' }}</text>
        </view>
        <view class="muted">{{ task.applyUserName || '' }} · {{ task.createTime }}</view>
        <view class="divider" />
        <read-only :value="task.form || task.data || task" :schema="task.fields || []" />
        <button v-if="businessLink" class="secondary" @click="$go('detail', businessLink)">打开关联业务</button>
      </view>
      <view v-if="instance" class="card">
        <view class="h3">审批进度</view>
        <view v-if="historyError" class="notice">{{ historyError }}</view>
        <view class="timeline">
          <view v-for="(node, index) in history" :key="index" class="node">
            <view class="h3">{{ node.activityName }}</view>
            <view class="muted">{{ node.assigneeName }} · {{ node.endTime || node.startTime }}</view>
            <view>{{ node.comment }}</view>
          </view>
          <view v-if="task.taskName && kind === 'todo'" class="node">
            <view class="h3">{{ task.taskName }}</view>
            <view class="muted">等待办理 · {{ task.assigneeName }}</view>
          </view>
        </view>
      </view>
      <view v-if="approvable" class="card">
        <view class="h3">审批意见</view>
        <textarea class="control" v-model="comment" maxlength="2000" placeholder="请填写意见（退回时必填）" />
        <view class="actions">
          <button class="danger" :disabled="busy" @click="complete(false)">退回</button>
          <button class="btn" :loading="busy" :disabled="busy" @click="complete(true)">同意</button>
        </view>
      </view>
      <view v-else class="notice">
        {{
          kind === 'todo'
            ? businessLink
              ? '请打开关联业务完成办理，业务完成后提醒会自动关闭。'
              : '此业务需要在平台端工作台办理，可在这里查看提醒和审批记录。'
            : '此记录仅供查看。'
        }}
      </view>
    </view>
    <view v-else-if="!error" class="empty">正在加载…</view>
  </app-shell>
</template>
<script>
import { bootstrap, request } from '../../services/runtime'
import { displayRows } from '../../services/business'
import { pageRows, positiveId, taskPaths } from '../../services/catalog'
import ReadOnly from '../../components/ReadOnly.vue'
export default {
  components: { ReadOnly },
  data: () => ({ kind: 'todo', key: '', task: null, error: '', historyError: '', history: [], comment: '', busy: false }),
  computed: {
    instance() {
      return this.task && (this.task.instanceId || this.task.processInstanceId)
    },
    approvable() {
      return (
        this.kind === 'todo' &&
        this.task &&
        this.task.taskId &&
        ['lowcode', 'workflowAction', 'crmContractApproval'].includes(this.task.businessType) &&
        !!this.task.instanceId
      )
    },
    details() {
      return displayRows(this.task && (this.task.form || this.task.data || this.task), (this.task && this.task.fields) || [])
    },
    businessLink() {
      if (!this.task) return null
      const form = this.task.form || {}
      if (positiveId(form.contractId)) return { module: 'contracts', id: form.contractId }
      if (positiveId(form.customerId)) return { module: 'customers', id: form.customerId }
      const types = { crmCustomerFollowup: 'customers', crmRisk: 'customers', recruitment: 'applications', hrPayslip: 'payslips' }
      const module = types[this.task.businessType]
      const id = positiveId(this.task.businessId)
      return module && id ? { module, id } : null
    }
  },
  onLoad(q) {
    this.kind = taskPaths[q.kind] ? q.kind : 'todo'
    this.key = q.key || ''
    this.load()
  },
  methods: {
    async load() {
      this.error = ''
      this.historyError = ''
      this.task = null
      this.history = []
      try {
        await bootstrap()
        if (!/^[a-f0-9]{64}$/.test(this.key)) throw new Error('待办编号无效')
        this.task = (await request('/mobile/tasks/' + this.kind + '/' + this.key)).data
        await request('/mobile/tasks/' + this.kind + '/' + this.key + '/read', 'POST')
        if (this.instance) {
          try {
            this.history = pageRows(await request('/activiti/process/listHistory', 'POST', { processInstanceId: this.instance })).rows
          } catch (e) {
            this.historyError = e.message
          }
        }
      } catch (e) {
        this.error = e.message
      }
    },
    complete(pass) {
      if (this.busy) return
      if (!pass && !this.comment.trim()) return this.$toast('退回请填写原因')
      uni.showModal({
        confirmText: '确定',
        cancelText: '取消',
        title: pass ? '同意审批' : '退回审批',
        content: '确认提交当前审批意见？',
        success: async (r) => {
          if (!r.confirm) return
          this.busy = true
          try {
            await request('/activiti/process/complete', 'POST', {
              taskId: this.task.taskId,
              instanceId: this.instance,
              variables: { pass, comment: this.comment.trim() || (pass ? '同意' : '退回') }
            })
            this.$toast('已处理')
            uni.navigateBack()
          } catch (e) {
            this.error = e.message
          } finally {
            this.busy = false
          }
        }
      })
    }
  }
}
</script>
