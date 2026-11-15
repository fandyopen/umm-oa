<template>
  <a-card :bordered="false" class="customer-cleanup">
    <h2>特殊功能 · 客户数据清理</h2>
    <p class="help">查找并选定一个客户，预览关联范围后清理。清理关联数据会保留客户主档，完成后可彻底删除客户。</p>
    <a-alert type="warning" show-icon message="强制清理会删除该客户的业务历史，不受合同、招聘、回款或审批状态限制。" description="仅限固定超级管理员。执行前自动备份并记录原因；共享人才档案、规则和系统配置保留。共享工资的处理方式会在预览中列明。" />
    <a-tabs default-active-key="cleanup" @change="tabChanged">
      <a-tab-pane key="cleanup" tab="客户数据清理">
        <div class="search-bar">
          <a-input-search v-model="keyword" :disabled="busy || previewing" placeholder="输入客户ID或客户名称，按回车查询" enter-button="查找客户" :loading="searching" @search="search" />
        </div>
        <a-table v-if="searched" :data-source="customers" :loading="searching" :pagination="false" row-key="id" size="small">
          <a-table-column title="客户ID" data-index="id" :width="90" />
          <a-table-column title="客户名称" data-index="name" />
          <a-table-column title="客户编号" data-index="serial" :width="150" />
          <a-table-column title="负责人" data-index="owner_user_name" :width="120" />
          <a-table-column title="操作" :width="120"><template slot-scope="row"><a-button type="link" :disabled="busy || previewing || searching" @click="select(row)">预览清理范围</a-button></template></a-table-column>
        </a-table>
        <a-spin :spinning="previewing">
          <section v-if="preview" class="preview">
            <h3>当前客户：{{ preview.customer.name }} <small>（ID：{{ preview.customer.id }}）</small></h3>
            <a-descriptions size="small" :column="{ xs: 1, sm: 3 }" bordered>
              <a-descriptions-item label="客户编号">{{ preview.customer.serial || '—' }}</a-descriptions-item>
              <a-descriptions-item label="关联记录">{{ preview.total }} 条</a-descriptions-item>
              <a-descriptions-item label="合同净到账">{{ money(preview.amounts.netReceipts) }} 元</a-descriptions-item>
            </a-descriptions>
            <a-table :data-source="preview.tables" :pagination="false" row-key="table" size="small" class="impact-table">
              <a-table-column title="清理内容" data-index="label" />
              <a-table-column title="数据表" data-index="table" />
              <a-table-column title="删除数量" data-index="count" :width="100" />
            </a-table>
            <a-alert v-if="!preview.total" type="success" show-icon message="没有待清理的业务关联，可以彻底删除客户档案。" />
            <div v-if="preview.payroll.sharedBatches || preview.payroll.items.length" class="shared">
              <h4>共享提成与工资处理</h4>
              <p>涉及 {{ preview.payroll.sharedBatches }} 个提成批次，仅移除本客户的来源及明细，保留其他客户的数据。未发放工资重算后，相关批次需重新核对、审批及发布；已发放或部分发放的工资项保留金额和付款事实。</p>
              <a-table :data-source="preview.payroll.items" :pagination="false" row-key="itemId" size="small">
                <a-table-column title="工资批次" data-index="payrollId" :width="100" />
                <a-table-column title="工资项" data-index="itemId" :width="100" />
                <a-table-column title="处理方式" data-index="action" />
              </a-table>
            </div>
            <p class="help">仅属于该客户、尚未发放的提成及工资批次会一起删除。备份包含待删除记录与受影响的共享记录；共享人才简历和已有上传文件保留。</p>
            <div class="actions">
              <a-button :disabled="busy" @click="refreshPreview">重新预览</a-button>
              <a-button type="danger" :disabled="busy || !preview.total" @click="confirm('cleanup')">清理关联数据，保留档案</a-button>
              <a-button type="danger" :disabled="busy || !!preview.total" @click="confirm('delete')">彻底删除客户档案</a-button>
            </div>
          </section>
        </a-spin>
        <a-alert v-if="result" class="result" type="success" show-icon :message="result.customerDeleted ? '客户档案已彻底删除' : '关联数据已清理，客户档案已保留'">
          <template slot="description">本次删除 {{ result.deletedCount }} 条关联记录，剩余 {{ result.remainingCount }} 条。<a @click="download(result.id)">下载清理前备份</a><span v-if="!result.customerDeleted">；可继续点击“彻底删除客户档案”。</span></template>
        </a-alert>
        <a-table v-if="result && result.deleted.length" :data-source="result.deleted" :pagination="false" row-key="table" size="small" class="impact-table">
          <a-table-column title="已清理内容" data-index="label" /><a-table-column title="实际删除数量" data-index="count" />
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="history" tab="清理记录">
        <a-button icon="reload" :loading="historyLoading" @click="loadHistory">刷新记录</a-button>
        <a-table :data-source="history" :loading="historyLoading" row-key="id" size="small" class="impact-table">
          <a-table-column title="客户ID" data-index="customer_id" :width="90" />
          <a-table-column title="客户名称" data-index="customer_name" :width="180" />
          <a-table-column title="操作" :width="130"><template slot-scope="row">{{ row.operation === 'delete' ? '彻底删除客户' : '清理关联数据' }}</template></a-table-column>
          <a-table-column title="原因" data-index="reason" />
          <a-table-column title="操作人" data-index="operator_name" :width="130" />
          <a-table-column title="时间" data-index="create_time" :width="170"><template slot-scope="v">{{ formatTime(v) }}</template></a-table-column>
          <a-table-column title="结果 / 备份" :width="160"><template slot-scope="row"><a @click="showHistory(row)">查看结果</a><a-divider type="vertical" /><a @click="download(row.id)">下载备份</a></template></a-table-column>
        </a-table>
      </a-tab-pane>
    </a-tabs>
    <a-modal :visible="!!historyDetail" title="清理结果" :footer="null" width="700px" @cancel="historyDetail = null">
      <template v-if="historyDetail">
        <p>{{ historyDetail.customer_name }}（ID：{{ historyDetail.customer_id }}）</p>
        <p>原因：{{ historyDetail.reason }}</p>
        <p>关联数据删除 {{ historyDetail.result.deletedCount }} 条，客户档案{{ historyDetail.result.customerDeleted ? '已彻底删除' : '已保留' }}。</p>
        <a-table :data-source="historyDetail.result.deleted || []" :pagination="false" row-key="table" size="small">
          <a-table-column title="清理内容" data-index="label" /><a-table-column title="删除数量" data-index="count" />
        </a-table>
        <a @click="download(historyDetail.id)">下载清理前备份</a>
      </template>
    </a-modal>
    <a-modal :visible="!!confirmation" :title="confirmation === 'delete' ? '确认彻底删除客户' : '确认清理客户关联数据'" :confirm-loading="busy" :closable="!busy" :mask-closable="false" :keyboard="!busy" @cancel="cancel" @ok="execute">
      <template v-if="preview">
        <a-alert type="warning" show-icon :message="'操作客户：' + preview.customer.name + '（ID：' + preview.customer.id + '）'" />
        <a-form-model :model="form" layout="vertical" class="confirm-form">
          <a-form-model-item label="输入客户ID确认" required><a-input v-model="form.confirmId" :disabled="busy" /></a-form-model-item>
          <a-form-model-item label="输入完整客户名称确认" required><a-input v-model="form.confirmName" :disabled="busy" /></a-form-model-item>
          <a-form-model-item label="操作原因" required><a-textarea v-model="form.reason" :disabled="busy" :max-length="1000" :rows="3" /></a-form-model-item>
        </a-form-model>
        <p v-if="busy" class="help">正在备份并执行，请等待结果。</p>
      </template>
      <template slot="footer"><a-button :disabled="busy" @click="cancel">取消</a-button><a-button type="danger" :loading="busy" @click="execute">{{ confirmation === 'delete' ? '确认彻底删除' : '确认清理' }}</a-button></template>
    </a-modal>
  </a-card>
</template>
<script>
import { saveAs } from 'file-saver'
import { searchCleanupCustomers, previewCustomerCleanup, cleanCustomerRelations, deleteCleanedCustomer, listCustomerCleanups, downloadCustomerCleanupBackup } from '/src/api/system/customerCleanup.js'

export default {
  name: 'SystemSpecialFunctions',
  data: () => ({ keyword: '', customers: [], searched: false, searching: false, previewing: false, busy: false, preview: null, result: null, confirmation: '', form: {}, history: [], historyDetail: null, historyLoading: false, searchVersion: 0 }),
  methods: {
    error(e) { this.$message.error(e.message || '操作失败，请刷新后重试') },
    money(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) },
    formatTime(v) { return v ? new Date(v).toLocaleString('zh-CN', { hour12: false }) : '—' },
    async search() {
      if (this.busy || this.previewing) return
      const key = this.keyword.trim()
      if (!key) return this.$message.warning('请输入客户ID或名称')
      const version = ++this.searchVersion
      this.searching = true
      this.preview = null
      this.result = null
      try { const response = await searchCleanupCustomers(key); if (version === this.searchVersion) { this.customers = response.data || []; this.searched = true } }
      catch (e) { this.error(e) }
      finally { if (version === this.searchVersion) this.searching = false }
    },
    async select(row) {
      if (this.busy || this.previewing || this.searching) return
      this.previewing = true
      this.preview = null
      this.result = null
      try { this.preview = (await previewCustomerCleanup(row.id)).data }
      catch (e) { this.error(e) }
      finally { this.previewing = false }
    },
    async refreshPreview() { if (this.preview) await this.select(this.preview.customer) },
    confirm(operation) {
      if (this.busy || !this.preview || (operation === 'delete' && this.preview.total)) return
      this.form = { confirmId: '', confirmName: '', reason: '' }
      this.confirmation = operation
    },
    cancel() { if (!this.busy) this.confirmation = '' },
    async execute() {
      if (this.busy || !this.confirmation || !this.preview) return
      const { customer, signature } = this.preview
      if (this.form.confirmId.trim() !== String(customer.id) || this.form.confirmName !== customer.name) return this.$message.warning('请输入一致的客户ID和完整名称')
      if (!this.form.reason.trim()) return this.$message.warning('请填写操作原因')
      const operation = this.confirmation
      this.busy = true
      try {
        const action = operation === 'delete' ? deleteCleanedCustomer : cleanCustomerRelations
        const response = await action(customer.id, { ...this.form, signature })
        this.result = response.data
        this.confirmation = ''
        this.preview = null
        if (!this.result.customerDeleted) this.preview = (await previewCustomerCleanup(customer.id)).data
        else this.customers = this.customers.filter(row => row.id !== customer.id)
        this.$message.success(this.result.customerDeleted ? '客户已彻底删除' : '清理完成，客户档案已保留')
      } catch (e) {
        this.error(e)
        if (e.code === 409) {
          this.confirmation = ''
          this.preview = null
          try { this.preview = (await previewCustomerCleanup(customer.id)).data } catch (refreshError) { this.error(refreshError) }
        }
      } finally { this.busy = false }
    },
    tabChanged(key) { if (key === 'history') this.loadHistory() },
    showHistory(row) {
      try { this.historyDetail = { ...row, result: JSON.parse(row.result_json) } } catch (e) { this.error(new Error('清理结果无法读取')) }
    },
    async loadHistory() {
      this.historyLoading = true
      try { this.history = (await listCustomerCleanups()).data || [] } catch (e) { this.error(e) }
      finally { this.historyLoading = false }
    },
    async download(id) {
      try {
        const blob = await downloadCustomerCleanupBackup(id)
        if (blob.type && blob.type.includes('json')) {
          const error = JSON.parse(await blob.text())
          throw new Error(error.msg || '备份下载失败')
        }
        saveAs(blob, '客户清理备份-' + id + '.zip')
      } catch (e) { this.error(e) }
    }
  }
}
</script>
<style scoped>
.customer-cleanup h2 { margin-bottom: 8px; }
.help { color: #7b8798; line-height: 1.8; }
.search-bar { display: flex; margin: 8px 0 16px; max-width: 600px; }
.preview { margin-top: 24px; border-top: 1px solid #edf0f4; padding-top: 20px; }
.preview h3 { overflow-wrap: anywhere; }
.preview small { color: #7b8798; font-weight: normal; }
.impact-table, .result { margin-top: 16px; }
.shared { margin: 20px 0; padding: 16px; background: #f6fafc; border-radius: 6px; }
.actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 10px; margin-top: 20px; }
.confirm-form { margin-top: 16px; }
</style>
