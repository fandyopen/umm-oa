<template>
  <div>
    <a-descriptions title="申请与审批信息" bordered size="small" :column="{ xs: 1, sm: 2 }">
      <a-descriptions-item label="审批事项" :span="2">{{ summary.title || task.processName }}</a-descriptions-item>
      <a-descriptions-item label="发起人">{{ summary.starterName || '—' }} / {{ summary.starterDeptName || '未分配部门' }}</a-descriptions-item>
      <a-descriptions-item label="发起时间">{{ dateTime(summary.startTime) }}</a-descriptions-item>
      <a-descriptions-item label="指定审批人">{{ summary.assigneeName || '—' }} / {{ summary.approverDeptName || '未分配部门' }}</a-descriptions-item>
      <a-descriptions-item label="审批节点">{{ summary.taskName }}</a-descriptions-item>
      <a-descriptions-item label="实际审批人">{{ task.approvalAudit.operatorName || (task.type === 'todo' ? '待审批' : '历史记录未记载') }}</a-descriptions-item>
      <a-descriptions-item label="审批时间">{{ dateTime(summary.completeTime) }}</a-descriptions-item>
    </a-descriptions>
    <a-divider>各节点审批记录</a-divider>
    <a-table row-key="taskId" size="small" :data-source="task.approvalRecords || []" :pagination="false" :scroll="{ x: 930 }">
      <a-table-column title="节点" data-index="taskName" :width="150" />
      <a-table-column title="指定审批人 / 部门" :width="180"><template slot-scope="text, row">{{ row.assigneeName }} / {{ row.assigneeDeptName || '未分配部门' }}</template></a-table-column>
      <a-table-column title="实际审批人" :width="180"><template slot-scope="text, row">{{ row.audit.operatorName || (row.taskStatus === 'done' ? '历史记录未记载' : '—') }}<a-tag v-if="row.audit.operatorId && String(row.audit.operatorId) !== String(row.assigneeId)" color="orange">代审批</a-tag></template></a-table-column>
      <a-table-column title="结果" :width="100"><template slot-scope="text, row">{{ result(row) }}</template></a-table-column>
      <a-table-column title="审批意见" :width="220"><template slot-scope="text, row">{{ row.audit.comment || '—' }}</template></a-table-column>
      <a-table-column title="审批时间" :width="175"><template slot-scope="text, row">{{ dateTime(row.completeTime) }}</template></a-table-column>
    </a-table>
    <a-alert v-if="!readonly && task.canApprove" class="approval-hint" type="info" show-icon message="将以当前登录账号办理本节点，保留指定审批人与实际审批人记录，完成后继续原审批流程。" />
    <a-divider>申请内容</a-divider>
    <bus-detail :todo-item="task" :row="task.data || task.form || {}" :form-id="task.formId" :form-key="task.formKey" :detail-comp-name="detailComponent" :is-only-show-process="readonly || !task.canApprove" @getList="$emit('getList')" @close="$emit('close')" />
  </div>
</template>

<script>
import moment from 'moment'
import BusDetail from '../todo/busDetail.vue'
import { busFlowMap } from '/src/views/eos/config/busUtil.js'

export default {
  name: 'ApprovalInboxDetail',
  components: { BusDetail },
  props: { task: { type: Object, required: true }, readonly: { type: Boolean, default: true } },
  computed: {
    summary() { return this.task.approvalSummary || {} },
    detailComponent() { return busFlowMap[this.task.businessType]?.busDetailCompName || 'LowCodeTodoDetail' }
  },
  methods: {
    dateTime(value) { return value && moment(value).isValid() ? moment(value).format('YYYY-MM-DD HH:mm:ss') : '—' },
    result(row) { return row.audit.decision === 'approved' ? '已同意' : row.audit.decision === 'rejected' ? '已驳回' : row.taskStatus === 'done' ? '已审批' : row.taskStatus === 'todo' && this.summary.instanceStatus === 'running' ? '待审批' : '已停止' }
  }
}
</script>

<style scoped>.approval-hint { margin-top: 20px; }</style>
