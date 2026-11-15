<template>
  <a-modal v-model="visible" title="批量登记工资发放 / 扣回" width="1000px" ok-text="确认登记" :confirm-loading="saving" :mask-closable="!saving" :closable="!saving" :cancel-button-props="{ props: { disabled: saving } }" :ok-button-props="{ props: { disabled: saving || !rows.length } }" @ok="save">
    <a-alert type="info" show-icon message="登记已完成的实际付款，不发起银行转账。每人填写真实流水号；成功记录可共用本次批量付款凭证。任一项校验失败时，本次全部不登记。" class="tw-mb-4" />
    <p>本次 {{ rows.length }} 人 · 发放合计 {{ money(paymentTotal) }} 元 · 扣回合计 {{ money(recoveryTotal) }} 元</p>
    <a-form-model layout="inline" class="tw-mb-4">
      <a-form-model-item label="实际日期"><a-date-picker v-model="form.paymentDate" :disabled="saving" format="YYYY-MM-DD" value-format="YYYY-MM-DD" /></a-form-model-item>
      <a-form-model-item label="本次结果"><a-select v-model="form.status" :disabled="saving" style="width:120px"><a-select-option value="paid">成功</a-select-option><a-select-option value="failed">失败</a-select-option></a-select></a-form-model-item>
    </a-form-model>
    <a-table :data-source="rows" row-key="itemId" :pagination="{ pageSize: 10 }" size="small">
      <a-table-column title="员工" :width="130"><template slot-scope="_, row">{{ row.employeeName }}<div class="hr-note">{{ row.employeeNo }}</div></template></a-table-column>
      <a-table-column title="剩余应付 / 扣回" :width="140"><template slot-scope="_, row">{{ money(row.remainingAmount) }}</template></a-table-column>
      <a-table-column title="本次金额（扣回为负）" :width="180"><template slot-scope="_, row"><a-input-number v-model="row.amount" :disabled="saving" :precision="2" style="width:150px" /></template></a-table-column>
      <a-table-column :title="form.status === 'paid' ? '银行流水号' : '失败单号'" :width="200"><template slot-scope="_, row"><a-input v-model="row.referenceNo" :disabled="saving" :max-length="128" placeholder="填写真实单笔流水号" /></template></a-table-column>
      <a-table-column title="收款账户" :width="240"><template slot-scope="_, row">{{ row.accountName }} · {{ row.bankName }}<div class="hr-note">{{ row.bankAccount }}</div></template></a-table-column>
    </a-table>
    <a-form-model layout="vertical">
      <a-form-model-item :label="form.status === 'failed' ? '失败原因（必填）' : '备注（选填）'"><a-textarea v-model="form.reason" :disabled="saving" :max-length="1000" :rows="2" /></a-form-model-item>
      <a-form-model-item v-if="form.status === 'paid'" label="批量付款凭证（必填）"><a-upload :before-upload="upload" :show-upload-list="false" :disabled="saving"><a-button icon="upload" :disabled="saving">上传私有凭证</a-button></a-upload><span class="hr-note tw-ml-2">{{ form.fileName }}</span></a-form-model-item>
    </a-form-model>
  </a-modal>
</template>

<script>
import { hr } from '/src/api/enterprise/hr.js'
import { fileBody, money, newToken } from './hrShared.js'

export default {
  name: 'PayrollBatchPayment',
  data: () => ({ visible: false, saving: false, payrollId: null, revision: null, form: {}, rows: [] }),
  computed: {
    paymentTotal() { return this.rows.reduce((sum, row) => sum + Math.max(0, Number(row.amount) || 0), 0) },
    recoveryTotal() { return this.rows.reduce((sum, row) => sum + Math.max(0, -(Number(row.amount) || 0)), 0) }
  },
  methods: {
    money,
    open(batch, selected) {
      if (this.saving) return
      this.payrollId = batch.id
      this.revision = batch.revision
      this.form = { paymentDate: new Date().toLocaleDateString('sv-SE'), status: 'paid', reason: '' }
      this.rows = selected.map(row => ({ ...row, itemId: row.id, amount: Number(row.netAmount) - Number(row.paidAmount), remainingAmount: Number(row.netAmount) - Number(row.paidAmount), referenceNo: '', requestToken: newToken() }))
      this.visible = true
    },
    async upload(file) {
      if (file.size > 10 * 1024 * 1024) { this.$message.warning('文件不能超过10MB'); return false }
      this.form = { ...this.form, ...(await fileBody(file)) }
      return false
    },
    async save() {
      if (this.saving) return
      if (!this.form.paymentDate) return this.$message.warning('请选择实际日期')
      if (this.form.status === 'paid' && !this.form.fileContent) return this.$message.warning('请上传实际付款凭证')
      if (this.form.status === 'failed' && !this.form.reason?.trim()) return this.$message.warning('请填写失败原因')
      const references = new Set()
      for (const row of this.rows) {
        const amount = Number(row.amount)
        if (!Number.isFinite(amount) || !amount || Math.sign(amount) !== Math.sign(row.remainingAmount) || Math.abs(amount) > Math.abs(row.remainingAmount)) return this.$message.warning(row.employeeName + '的金额须在剩余应付或扣回范围内')
        const reference = row.referenceNo.trim()
        if (!reference || references.has(reference)) return this.$message.warning('请逐人填写不同的真实流水号或失败单号')
        references.add(reference)
      }
      this.saving = true
      try {
        const result = await hr('/payroll/' + this.payrollId + '/payments', 'post', { ...this.form, revision: this.revision, rows: this.rows.map(row => ({ itemId: row.itemId, amount: row.amount, referenceNo: row.referenceNo.trim(), requestToken: row.requestToken })) })
        this.visible = false
        this.$emit('done')
        this.$message.success('已登记 ' + result.data.count + ' 人的实际发放结果')
      } finally { this.saving = false }
    }
  }
}
</script>
