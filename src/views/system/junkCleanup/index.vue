<template>
  <a-card :bordered="false" class="junk-cleanup">
    <div class="heading"><div><h2>垃圾数据清理</h2><p>仅扫描本项目中可再生成的缓存、构建文件和过期日志。清理前会复核清单，数据库业务数据不会被自动删除。</p></div><a-button type="primary" icon="search" :loading="scanning" @click="scan">扫描可清理内容</a-button></div>
    <a-alert v-if="report" type="info" show-icon class="summary" :message="'发现 ' + report.fileCount + ' 个文件，共 ' + formatBytes(report.totalBytes) + ' 可清理'" description="过期日志按最近修改时间判断；30 分钟内有更新的日志自动跳过。删除 dist 或 target 后需要重新构建应用。" />
    <a-alert v-if="report && report.protectedFiles && report.protectedFiles.length" class="protected-files" type="warning" show-icon message="有正在使用的后端文件，在线清理会保留它们" :description="protectedDescription" />
    <a-tabs v-model="activeTab">
      <a-tab-pane key="frontend" tab="前端"><a-table :columns="columns" :data-source="frontendItems" :row-selection="rowSelection" :pagination="false" row-key="id" size="middle"><template slot="bytes" slot-scope="v">{{ formatBytes(v) }}</template><template slot="fileCount" slot-scope="v">{{ v }} 个</template></a-table><a-empty v-if="report && !frontendItems.length" description="没有发现可清理的前端文件" /></a-tab-pane>
      <a-tab-pane key="backend" tab="后端"><a-table :columns="columns" :data-source="backendItems" :row-selection="rowSelection" :pagination="false" row-key="id" size="middle"><template slot="bytes" slot-scope="v">{{ formatBytes(v) }}</template><template slot="fileCount" slot-scope="v">{{ v }} 个</template></a-table><a-empty v-if="report && !backendItems.length" description="没有发现可清理的后端文件" /><a-card v-if="report" size="small" class="database-card" title="数据库占用（只读）"><a-descriptions size="small" :column="2"><a-descriptions-item label="数据库文件">{{ report.database.relativePath }}</a-descriptions-item><a-descriptions-item label="文件大小">{{ formatBytes(report.database.bytes) }}</a-descriptions-item><a-descriptions-item label="清理策略" :span="2">{{ report.database.message }}</a-descriptions-item></a-descriptions></a-card></a-tab-pane>
      <a-tab-pane key="history" tab="清理记录"><a-table :columns="historyColumns" :data-source="history" :loading="historyLoading" :pagination="{ pageSize: 10 }" row-key="id" size="middle"><template slot="createTime" slot-scope="v">{{ formatTime(v) }}</template><template slot="releasedBytes" slot-scope="v">{{ formatBytes(v) }}</template><template slot="resultJson" slot-scope="v"><a @click="showResult(v)">查看文件明细</a></template></a-table></a-tab-pane>
    </a-tabs>
    <div v-if="report && activeTab !== 'history'" class="footer"><span>已选择 {{ selectedIds.length }} 项</span><a-button type="danger" :disabled="!selectedIds.length || cleaning" @click="confirmVisible = true">清理所选内容</a-button></div>
    <a-alert v-if="result" class="result" :type="hasCleanupFailures ? 'warning' : 'success'" show-icon :message="'本次实际清理 ' + result.releasedFileCount + ' 个文件，释放 ' + formatBytes(result.releasedBytes)" :description="hasCleanupFailures ? '部分文件未能删除，请查看清理记录中的文件明细。' : ''" />
    <a-modal :visible="confirmVisible" title="确认清理文件" :confirm-loading="cleaning" :mask-closable="!cleaning" @cancel="confirmVisible = false" @ok="execute"><a-alert type="warning" show-icon message="清理后无法从页面恢复；前端和后端构建产物可重新生成。" /><p class="confirm-summary">本次选择 {{ selectedItems.length }} 项，预计 {{ selectedCount }} 个文件，{{ formatBytes(selectedBytes) }}。</p><a-form-model layout="vertical"><a-form-model-item label="清理原因" required><a-textarea v-model="reason" :max-length="500" :rows="3" placeholder="例如：发布前清理旧构建文件" /></a-form-model-item></a-form-model></a-modal>
    <a-modal :visible="!!detail" title="清理文件明细" :footer="null" width="760px" @cancel="detail = null"><a-table v-if="detail" :columns="detailColumns" :data-source="detail" :pagination="false" row-key="relativePath" size="small"><template slot="bytes" slot-scope="v">{{ formatBytes(v) }}</template></a-table></a-modal>
  </a-card>
</template>

<script>
import { scanJunk, cleanJunk, listJunkCleanupHistory } from '/src/api/system/junkCleanup.js'

const columns = [
  { title: '清理项目', dataIndex: 'name', key: 'name' },
  { title: '路径', dataIndex: 'relativePath', key: 'relativePath' },
  { title: '文件数', dataIndex: 'fileCount', key: 'fileCount', scopedSlots: { customRender: 'fileCount' }, width: 110 },
  { title: '可释放大小', dataIndex: 'bytes', key: 'bytes', scopedSlots: { customRender: 'bytes' }, width: 140 }
]
export default {
  name: 'JunkCleanup',
  data: () => ({ report: null, scanning: false, cleaning: false, activeTab: 'frontend', selectedIds: [], confirmVisible: false, reason: '', result: null, history: [], historyLoading: false, detail: null, columns, historyColumns: [
    { title: '操作人', dataIndex: 'operator_name', key: 'operator_name', width: 130 }, { title: '清理原因', dataIndex: 'reason', key: 'reason' }, { title: '清理文件数', dataIndex: 'released_file_count', key: 'released_file_count', width: 110 }, { title: '释放空间', dataIndex: 'released_bytes', key: 'released_bytes', scopedSlots: { customRender: 'releasedBytes' }, width: 140 }, { title: '时间', dataIndex: 'create_time', key: 'create_time', scopedSlots: { customRender: 'createTime' }, width: 180 }, { title: '明细', dataIndex: 'result_json', key: 'result_json', scopedSlots: { customRender: 'resultJson' }, width: 110 }
  ], detailColumns: [{ title: '名称', dataIndex: 'name', key: 'name' }, { title: '文件路径', dataIndex: 'relativePath', key: 'relativePath' }, { title: '结果', dataIndex: 'status', key: 'status', width: 200 }, { title: '释放空间', dataIndex: 'bytes', key: 'bytes', scopedSlots: { customRender: 'bytes' }, width: 130 }] }),
  computed: {
    frontendItems() { return (this.report && this.report.candidates || []).filter(item => item.side === '前端') },
    backendItems() { return (this.report && this.report.candidates || []).filter(item => item.side === '后端') },
    selectedItems() { return (this.report && this.report.candidates || []).filter(item => this.selectedIds.includes(item.id)) },
    selectedBytes() { return this.selectedItems.reduce((sum, item) => sum + item.bytes, 0) },
    selectedCount() { return this.selectedItems.reduce((sum, item) => sum + item.fileCount, 0) },
    protectedDescription() { return this.report.protectedFiles.map(item => `${item.relativePath}（${this.formatBytes(item.bytes)}）：${item.reason}`).join('；') },
    hasCleanupFailures() { return Boolean(this.result && (this.result.files || []).some(item => item.status !== '已清理')) },
    rowSelection() { return { selectedRowKeys: this.selectedIds, onChange: keys => { this.selectedIds = keys } } }
  },
  watch: { activeTab(key) { if (key === 'history') this.loadHistory() } },
  methods: {
    async scan() { this.scanning = true; try { this.report = (await scanJunk()).data; this.selectedIds = []; this.result = null } catch (error) { this.$message.error(error.message || '扫描失败') } finally { this.scanning = false } },
    async execute() { if (!this.reason.trim()) return this.$message.warning('请填写清理原因'); this.cleaning = true; try { const completed = (await cleanJunk({ candidateIds: this.selectedIds, reason: this.reason.trim() })).data; this.confirmVisible = false; this.reason = ''; this.selectedIds = []; await this.scan(); this.result = completed; this.activeTab = 'history'; this.loadHistory(); this.$message.success('清理记录已保存') } catch (error) { this.$message.error(error.message || '清理失败，请重新扫描') } finally { this.cleaning = false } },
    async loadHistory() { this.historyLoading = true; try { this.history = (await listJunkCleanupHistory()).data || [] } catch (error) { this.$message.error(error.message || '加载清理记录失败') } finally { this.historyLoading = false } },
    showResult(value) { try { this.detail = JSON.parse(value).files || [] } catch (error) { this.detail = [] } },
    formatBytes(bytes) { const value = Number(bytes || 0); if (value < 1024) return value + ' B'; if (value < 1024 * 1024) return (value / 1024).toFixed(1) + ' KB'; if (value < 1024 * 1024 * 1024) return (value / 1024 / 1024).toFixed(2) + ' MB'; return (value / 1024 / 1024 / 1024).toFixed(2) + ' GB' },
    formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
  },
  created() { this.scan() }
}
</script>

<style scoped>
.heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.heading h2 { margin: 0 0 8px; }
.heading p { color: #7b8798; margin: 0; line-height: 1.8; }
.summary { margin-bottom: 16px; }
.database-card { margin-top: 18px; }
.footer { display: flex; align-items: center; justify-content: flex-end; gap: 16px; margin-top: 18px; }
.footer span, .confirm-summary { color: #657386; }
.result { margin-top: 18px; }
@media (max-width: 720px) { .heading { flex-direction: column; } .footer { justify-content: space-between; } }
</style>
