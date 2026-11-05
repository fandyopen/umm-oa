<template>
  <div class="finance-record-history">
    <div class="record-summary">
      <div><div class="record-type">{{ recordTypes[record.recordType] || '财务记录' }}<a-tag :color="statusColors[record.status]">{{ statuses[record.status] || '未记录状态' }}</a-tag></div><strong>￥{{ money(record.amount) }}</strong></div>
      <span>记录编号 #{{ record.id }}</span>
    </div>
    <a-descriptions size="small" bordered :column="{ xs: 1, sm: 2 }">
      <a-descriptions-item label="客户">{{ record.customerName || '—' }}</a-descriptions-item>
      <a-descriptions-item label="业务日期">{{ record.businessDate || '—' }}</a-descriptions-item>
      <a-descriptions-item v-if="record.contractName || record.contractCode" label="关联合同" :span="2">{{ [record.contractCode, record.contractName].filter(Boolean).join(' · ') }}</a-descriptions-item>
      <a-descriptions-item label="备注 / 原因" :span="2"><span class="record-note">{{ record.remark || record.reason || '—' }}</span></a-descriptions-item>
      <a-descriptions-item v-if="files.length" label="当前凭证" :span="2"><div class="record-files"><a v-for="(file, index) in files" :key="index" :href="file.url" target="_blank" rel="noopener noreferrer"><a-icon type="paper-clip" />{{ file.name }}</a></div></a-descriptions-item>
    </a-descriptions>
    <finance-history-timeline :history="record.history || []" />
  </div>
</template>
<script>
import FinanceHistoryTimeline from './FinanceHistoryTimeline.vue'
import { financeRecordTypes, financeStatuses, financeStatusColors, financeMoney, financeFiles } from '../history.js'

export default {
  name: 'FinanceRecordHistory',
  components: { FinanceHistoryTimeline },
  props: { record: { type: Object, required: true } },
  data: () => ({ recordTypes: financeRecordTypes, statuses: financeStatuses, statusColors: financeStatusColors }),
  computed: { files() { return financeFiles(this.record.attachment) } },
  methods: { money: financeMoney }
}
</script>
<style scoped>
.record-summary { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; margin-bottom: 20px; padding: 18px 20px; border: 1px solid #e8edf3; border-radius: 6px; background: #fafcff; }
.record-type { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; color: #595959; }
.record-type .ant-tag { margin-right: 0; }
.record-summary strong { color: #262626; font-size: 26px; line-height: 1.2; overflow-wrap: anywhere; }
.record-summary > span { font-size: 12px; color: #8c8c8c; }
.record-note, .record-files a { white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word; }
.record-files { display: flex; flex-wrap: wrap; gap: 8px 16px; }
.record-files .anticon { margin-right: 5px; }
</style>
