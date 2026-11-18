<template>
  <a-table row-key="key" :columns="columns" :data-source="rows" :loading="loading" :pagination="pagination" :scroll="{ x: 1450 }" size="small" @change="$emit('change', $event)">
    <template slot="candidate" slot-scope="_, row"><strong>{{ row.talentName }}</strong><div class="muted">{{ types[row.entityType] }}</div></template>
    <template slot="job" slot-scope="_, row">{{ row.jobTitle || '—' }}<div class="muted">{{ row.customerName || '人才跟进事项' }}</div></template>
    <template slot="person" slot-scope="_, row">{{ row.userName }}<div class="muted">{{ row.deptName || '未分部门' }}</div></template>
    <template slot="stage" slot-scope="value">{{ labels[value] || value || '—' }}</template>
    <template slot="followup" slot-scope="_, row">{{ row.lastFollowupTime ? formatTime(row.lastFollowupTime) : '暂无有效跟进' }}<div v-if="row.operatorName" class="muted">操作人：{{ row.operatorName }}</div></template>
    <template slot="time" slot-scope="value">{{ formatTime(value) }}</template>
    <template slot="idle" slot-scope="_, row"><a-tag v-if="row.stagnant" color="orange">{{ row.idleDays }} 天未跟进</a-tag><span v-else>{{ row.idleDays == null ? '—' : row.idleDays + ' 天' }}</span></template>
    <template slot="risks" slot-scope="_, row"><div v-for="reason in row.overdueReasons || []" :key="reason" class="risk">{{ reason }}</div><span v-if="!(row.overdueReasons || []).length">—</span></template>
    <template slot="action" slot-scope="_, row"><a v-if="showModuleAction(moduleFor(row))" @click="openRecord(row)">查看详情</a><span v-else class="muted">查看行内记录</span></template>
    <template slot="expandedRowRender" slot-scope="row">
      <div class="record-note">
        <p v-if="row.taskTitle"><strong>待办内容：</strong>{{ row.taskTitle }}</p>
        <p><strong>最近有效跟进：</strong>{{ row.lastFollowupContent || '暂无有效跟进记录' }}</p>
        <p v-if="row.metricContent"><strong>节点记录：</strong>{{ row.metricContent }}</p>
        <p v-if="row.dueDate"><strong>处理期限：</strong>{{ row.dueDate }}</p>
        <p v-if="row.trackingSince" class="muted">停滞计时起点：{{ formatTime(row.trackingSince) }}。暂无有效跟进时，从记录创建时间起算。</p>
      </div>
    </template>
  </a-table>
</template>

<script>
import { stages, talentStates, recruitmentModules } from '../shared.js'
import { formatMonitorTime } from './metrics.js'
import { withModuleContexts } from '/src/utils/moduleContext.js'
export default {
  name: 'RecruitmentMonitorRecords',
  mixins: [withModuleContexts({ applications: recruitmentModules.applications, talents: recruitmentModules.talents })],
  props: { rows: { type: Array, default: () => [] }, loading: Boolean, pagination: { type: [Object, Boolean], default: false } },
  data: () => ({
    labels: { ...stages, ...talentStates, todo: '待跟进', open: '待处理' },
    types: { application: '招聘流程', talent: '人才联系', task: '跟进任务', exception: '异常跟进' },
    columns: [
      { title: '候选人', key: 'candidate', scopedSlots: { customRender: 'candidate' }, width: 140 },
      { title: '职位 / 客户', key: 'job', scopedSlots: { customRender: 'job' }, width: 200 },
      { title: '负责人 / 部门', key: 'person', scopedSlots: { customRender: 'person' }, width: 145 },
      { title: '当前阶段', dataIndex: 'stage', scopedSlots: { customRender: 'stage' }, width: 110 },
      { title: '最近有效跟进', key: 'followup', scopedSlots: { customRender: 'followup' }, width: 190 },
      { title: '未跟进时长', key: 'idle', scopedSlots: { customRender: 'idle' }, width: 140 },
      { title: '超期提醒', key: 'risks', scopedSlots: { customRender: 'risks' }, width: 150 },
      { title: '统计发生时间', dataIndex: 'metricTime', width: 185, scopedSlots: { customRender: 'time' } },
      { title: '操作', key: 'action', scopedSlots: { customRender: 'action' }, width: 120, fixed: 'right' }
    ]
  }),
  methods: {
    formatTime: formatMonitorTime,
    moduleFor(row) { return ['application', 'exception'].includes(row.entityType) ? 'applications' : 'talents' },
    async openRecord(row) {
      const module = this.moduleFor(row)
      if (!(await this.checkModuleAction({ module, label: '查看业务详情' }))) return
      const path = module === 'applications' ? '/recruitment/pipeline' : '/recruitment/talents'
      const recordId = row.entityType === 'application' ? row.recordId : row.entityType === 'exception' ? row.applicationId : row.talentId
      this.$router.push({ path, query: { recordId: String(recordId) } })
    }
  }
}
</script>

<style scoped>
.muted { color: #84909e; font-size: 12px; margin-top: 4px; }
.risk { color: #cf5144; font-size: 12px; }
.record-note { padding: 8px 12px; white-space: pre-wrap; overflow-wrap: anywhere; }
.record-note p { margin: 5px 0; }
</style>
