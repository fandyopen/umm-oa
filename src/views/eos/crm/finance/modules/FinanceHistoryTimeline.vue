<template>
  <div class="finance-history">
    <div class="history-heading"><h3>操作记录</h3><span>共 {{ entries.length }} 条 · 最新在前</span></div>
    <a-timeline v-if="entries.length">
      <a-timeline-item v-for="(entry, index) in entries" :key="entry.id || index" :color="entry.color">
        <article class="history-card">
          <div class="history-title"><strong>{{ entry.action }}</strong><time>{{ entry.time }}</time></div>
          <div class="history-operator"><a-icon type="user" />{{ entry.operator }}</div>
          <dl v-if="entry.items.length" class="history-fields">
            <div v-for="item in entry.items" :key="item.key" :class="{ 'wide-field': ['remark', 'reason', 'content', 'previousContent'].includes(item.key) }">
              <dt>{{ item.label }}</dt><dd>{{ item.value }}</dd>
            </div>
          </dl>
          <p v-if="entry.note" class="history-note">{{ entry.note }}</p>
          <div v-if="entry.files.length" class="history-files">
            <span>凭证附件</span>
            <a v-for="(file, fileIndex) in entry.files" :key="fileIndex" :href="file.url" target="_blank" rel="noopener noreferrer"><a-icon type="paper-clip" />{{ file.name }}</a>
          </div>
        </article>
      </a-timeline-item>
    </a-timeline>
    <div v-else class="history-empty">暂无操作记录</div>
  </div>
</template>
<script>
import { Timeline } from 'ant-design-vue'
import { financeHistoryEntry } from '../history.js'

export default {
  name: 'FinanceHistoryTimeline',
  components: { ATimeline: Timeline, ATimelineItem: Timeline.Item },
  props: { history: { type: Array, default: () => [] } },
  computed: {
    entries() { return this.history.map(financeHistoryEntry) }
  }
}
</script>
<style scoped>
.history-heading, .history-title { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.history-heading { margin: 24px 0 20px; }
.history-heading h3 { margin: 0; font-size: 15px; font-weight: 600; }
.history-heading > span, .history-title time, .history-operator { font-size: 12px; color: #8c8c8c; }
.history-card { padding: 16px; border: 1px solid #e8edf3; border-radius: 6px; background: #fafcff; min-width: 0; }
.history-title strong { font-size: 14px; color: #262626; }
.history-operator { margin-top: 6px; }
.history-operator .anticon { margin-right: 6px; }
.history-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 24px; margin: 14px 0 0; }
.history-fields > div { display: flex; align-items: baseline; gap: 10px; min-width: 0; }
.history-fields dt { flex: 0 0 84px; font-weight: 400; color: #8c8c8c; }
.history-fields dd { margin: 0; min-width: 0; }
.history-fields .wide-field { grid-column: 1 / -1; }
.history-fields dd, .history-note, .history-files a { white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word; }
.history-note { margin: 12px 0 0; }
.history-files { display: flex; flex-wrap: wrap; gap: 8px 14px; margin-top: 14px; padding-top: 12px; border-top: 1px solid #edf0f3; }
.history-files > span { color: #8c8c8c; }
.history-files a .anticon { margin-right: 5px; }
.history-empty { text-align: center; padding: 30px 0; color: #8c8c8c; }
@media (max-width: 600px) {
  .history-fields { grid-template-columns: minmax(0, 1fr); }
  .history-card { padding: 12px; }
}
</style>
