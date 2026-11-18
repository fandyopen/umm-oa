<template>
  <a-table row-key="key" :columns="columns" :data-source="rows" :loading="loading" :pagination="pagination" :scroll="{ x: 1400 }" size="small" @change="$emit('change', $event)">
    <template slot="business" slot-scope="_, row"><strong>{{ row.name }}</strong><div class="muted">{{ types[row.entityType] }}<template v-if="row.entityType !== 'customer'"> · {{ row.customerName || '未关联客户' }}</template></div></template>
    <template slot="person" slot-scope="_, row">{{ row.userName }}<div class="muted">{{ row.deptName || '未分部门' }}</div></template>
    <template slot="stage" slot-scope="value">{{ states[value] || value || '—' }}</template>
    <template slot="time" slot-scope="value">{{ formatTime(value) }}</template>
    <template slot="idle" slot-scope="value">{{ value == null ? '—' : value + ' 天' }}</template>
    <template slot="amount" slot-scope="value">{{ value == null ? '—' : money(value) }}</template>
    <template slot="reason" slot-scope="value"><span class="risk">{{ value || '—' }}</span></template>
    <template slot="action" slot-scope="_, row"><a v-if="showModuleAction(moduleFor(row))" @click="openRecord(row)">查看详情</a><span v-else class="muted">查看行内记录</span></template>
    <template slot="expandedRowRender" slot-scope="row">
      <div class="record-note">
        <p><strong>统计依据：</strong>{{ row.metricContent || row.reason || '当前负责的业务记录' }}</p>
        <p><strong>实际操作人：</strong>{{ row.operatorName || '历史未记录' }}<span class="muted">（负责人归属与操作人可能不同）</span></p>
        <p v-if="row.entityType === 'customer' || row.entityType === 'opportunity'"><strong>最近有效跟进 / 推进：</strong>{{ formatTime(row.lastFollowupTime) }} · {{ row.lastFollowupContent || '暂无内容' }}</p>
        <p v-if="row.trackingSince"><strong>监管起点：</strong>{{ formatTime(row.trackingSince) }}；客户从本次分配起算，商机从建档起算，无有效记录时使用此时间。</p>
        <p v-if="row.dueTime"><strong>下次跟进期限：</strong>{{ formatTime(row.dueTime) }}</p>
      </div>
    </template>
  </a-table>
</template>

<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { crmModules } from '../moduleContexts.js'
import { formatTime, money } from './metrics.js'
export default {
  name: 'SalesMonitorRecords',
  mixins: [withModuleContexts({ customer: crmModules.customer, opportunity: crmModules.opportunity, contract: crmModules.contract })],
  props: { rows: { type: Array, default: () => [] }, loading: Boolean, pagination: { type: [Object, Boolean], default: false } },
  data: () => ({
    types: { customer: '客户', opportunity: '商机', contract: '合同', finance: '到账记录' },
    states: { draft: '草稿', pending: '审批中', approved: '待签署', active: '履约中', completed: '已完成', terminated: '已终止', rejected: '已驳回', withdrawn: '已撤回', void: '已作废' },
    columns: [
      { title: '业务 / 客户', key: 'business', width: 230, scopedSlots: { customRender: 'business' } },
      { title: '负责人 / 部门', key: 'person', width: 145, scopedSlots: { customRender: 'person' } },
      { title: '当前阶段', dataIndex: 'stage', width: 110, scopedSlots: { customRender: 'stage' } },
      { title: '统计发生时间', dataIndex: 'metricTime', width: 180, scopedSlots: { customRender: 'time' } },
      { title: '最近有效跟进 / 推进', dataIndex: 'lastFollowupTime', width: 185, scopedSlots: { customRender: 'time' } },
      { title: '未跟进时长', dataIndex: 'idleDays', width: 105, scopedSlots: { customRender: 'idle' } },
      { title: '金额（元）', dataIndex: 'amount', width: 140, scopedSlots: { customRender: 'amount' } },
      { title: '风险提醒', dataIndex: 'reason', width: 195, scopedSlots: { customRender: 'reason' } },
      { title: '操作', key: 'action', width: 105, fixed: 'right', scopedSlots: { customRender: 'action' } }
    ]
  }),
  methods: {
    formatTime, money,
    moduleFor(row) { return row.entityType === 'finance' ? (row.contractId ? 'contract' : 'customer') : row.entityType },
    async openRecord(row) {
      const module = this.moduleFor(row)
      if (!(await this.checkModuleAction({ module, label: '查看业务详情' }))) return
      const paths = { customer: '/crm/customers', opportunity: '/crm/opportunities', contract: '/crm/contracts/list' }
      const query = module === 'customer' ? { customerId: String(row.customerId), tab: 'followups' } : module === 'opportunity' ? { businessId: String(row.recordId) } : { contractId: String(row.entityType === 'finance' ? row.contractId : row.recordId) }
      this.$router.push({ path: paths[module], query })
    }
  }
}
</script>

<style scoped>
.muted { color: #84909e; font-size: 12px; margin-top: 4px; }
.risk { color: #c95b4e; font-size: 12px; }
.record-note { padding: 8px 12px; white-space: pre-wrap; overflow-wrap: anywhere; }
.record-note p { margin: 5px 0; }
</style>
