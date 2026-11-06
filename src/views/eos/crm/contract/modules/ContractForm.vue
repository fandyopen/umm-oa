<template>
  <a-spin :spinning="loading">
    <a-form-model ref="form" :model="form" :rules="rules" layout="vertical">
      <a-alert v-if="form.effectiveVersion" type="info" show-icon :message="`正在编辑新版本，V${form.effectiveVersion} 继续生效，新版本审批并签署后替换。`" class="tw-mb-4" />
      <a-row :gutter="20">
        <a-col :xs="24" :md="12">
          <a-form-model-item label="关联客户" prop="customerId">
            <a-select v-model="form.customerId" show-search :disabled="!!contractId || !!initial.customerId" :filter-option="false" placeholder="搜索并选择客户" @search="searchCustomers">
              <a-select-option v-for="item in customers" :key="item.id" :value="item.id">{{ item.name }} · {{ item.ownerUserName || '未分配' }}</a-select-option>
            </a-select>
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12"
          ><a-form-model-item label="合同名称" prop="name"><a-input v-model="form.name" :max-length="200" placeholder="例如：年度招聘服务合同" /></a-form-model-item
        ></a-col>
        <a-col :xs="24" :md="12"
          ><a-form-model-item label="合同编号"><a-input v-model="form.code" :disabled="!!contractId" :max-length="80" placeholder="留空自动生成唯一编号" /></a-form-model-item
        ></a-col>
        <a-col :xs="24" :md="12"
          ><a-form-model-item label="服务类型" prop="serviceType"
            ><a-select v-model="form.serviceType"
              ><a-select-option v-for="item in services" :key="item">{{ item }}</a-select-option></a-select
            ></a-form-model-item
          ></a-col
        >
        <a-col :xs="24" :md="8"
          ><a-form-model-item label="合同金额（元）" prop="amount"><a-input-number v-model="form.amount" :min="0.01" :precision="2" class="tw-w-full" /></a-form-model-item
        ></a-col>
        <a-col :xs="24" :md="8"
          ><a-form-model-item label="服务开始日期" prop="startDate"><a-date-picker v-model="form.startDate" value-format="YYYY-MM-DD" class="tw-w-full" /></a-form-model-item
        ></a-col>
        <a-col :xs="24" :md="8"
          ><a-form-model-item label="服务结束日期" prop="endDate"><a-date-picker v-model="form.endDate" value-format="YYYY-MM-DD" class="tw-w-full" /></a-form-model-item
        ></a-col>
      </a-row>
      <div class="section-head">
        <h3>收费条款</h3>
        <a-button icon="plus" @click="form.feeTerms.push(newTerm())">添加收费节点</a-button>
      </div>
      <p class="muted">明确每次收费的触发条件和日期；提交审批前，收费合计须与合同金额一致。</p>
      <div v-for="(term, index) in form.feeTerms" :key="term.key" class="fee-editor">
        <div class="section-head">
          <strong>收费节点 {{ index + 1 }}</strong
          ><a-button type="link" icon="delete" @click="form.feeTerms.splice(index, 1)">删除</a-button>
        </div>
        <a-row :gutter="12">
          <a-col :xs="24" :md="12"
            ><a-form-model-item label="收费名称"><a-input v-model="term.title" :max-length="200" placeholder="例如：首期服务费" /></a-form-model-item
          ></a-col>
          <a-col :xs="24" :md="12"
            ><a-form-model-item label="收费触发条件"><a-input v-model="term.trigger" :max-length="500" placeholder="例如：候选人入职后30日" /></a-form-model-item
          ></a-col>
          <a-col :xs="24" :md="8"
            ><a-form-model-item label="计费方式"
              ><a-select v-model="term.chargeMode" @change="calculate(term)"><a-select-option value="fixed">固定金额</a-select-option><a-select-option value="rate">基数 × 比例</a-select-option></a-select></a-form-model-item
            ></a-col
          >
          <template v-if="term.chargeMode === 'rate'">
            <a-col :xs="24" :md="8"
              ><a-form-model-item label="计费基数（元）"><a-input-number v-model="term.baseAmount" :min="0" :precision="2" class="tw-w-full" @change="calculate(term)" /></a-form-model-item
            ></a-col>
            <a-col :xs="24" :md="8"
              ><a-form-model-item label="收费比例（%）"><a-input-number v-model="term.rate" :min="0.01" :max="100" :precision="2" class="tw-w-full" @change="calculate(term)" /></a-form-model-item
            ></a-col>
          </template>
          <a-col :xs="24" :md="8"
            ><a-form-model-item label="收费金额（元）"><a-input-number v-model="term.amount" :disabled="term.chargeMode === 'rate'" :min="0.01" :precision="2" class="tw-w-full" /></a-form-model-item
          ></a-col>
          <a-col :xs="24" :md="8"
            ><a-form-model-item label="收费到期日"><a-date-picker v-model="term.dueDate" value-format="YYYY-MM-DD" class="tw-w-full" /></a-form-model-item
          ></a-col>
          <a-col :xs="24" :md="8"
            ><a-form-model-item label="保证期（天）"><a-input-number v-model="term.guaranteeDays" :min="0" :max="3650" :precision="0" class="tw-w-full" /></a-form-model-item
          ></a-col>
          <a-col :span="24"
            ><a-form-model-item label="保证与收费说明"><a-input v-model="term.remark" :max-length="1000" placeholder="例如：保证期内离职免费补荐一次" /></a-form-model-item
          ></a-col>
        </a-row>
      </div>
      <a-alert :type="balanced ? 'success' : 'warning'" show-icon :message="`收费合计 ${money(feeTotal)} / 合同金额 ${money(form.amount)}${balanced ? '，金额一致' : '，保存后仍可完善，提交前需一致'}`" class="tw-mb-5" />
      <a-form-model-item label="合同附件">
        <a-upload :action="uploadUrl" :headers="uploadHeaders" :file-list="fileList" @change="onUpload" @remove="removeFile"><a-button icon="upload">上传合同文件</a-button></a-upload>
      </a-form-model-item>
      <a-form-model-item label="合同备注 / 本次变更说明"><a-textarea v-model="form.remark" :max-length="2000" :rows="3" /></a-form-model-item>
      <div class="form-footer">
        <a-button :disabled="saving" @click="$emit('close')">取消</a-button><a-button type="primary" :loading="saving" :disabled="loading" @click="save">{{ contractId ? '保存修改' : '创建合同' }}</a-button>
      </div>
    </a-form-model>
  </a-spin>
</template>

<script>
import storage from 'store'
import { ACCESS_TOKEN } from '/src/store/mutation-types'
import { listContractCustomers, getContract, createContract, updateContract, normalizeContractId } from '/src/api/eos/crm/contracts.js'
import { newTerm, money, today } from '../shared.js'

export default {
  name: 'CrmContractForm',
  props: { contractId: { type: Number, default: 0 }, initial: { type: Object, default: () => ({}) } },
  data() {
    return {
      loading: false,
      saving: false,
      customers: [],
      fileList: [],
      services: ['猎头招聘', '招聘流程外包', '人力外包', '灵活用工', '其他服务'],
      uploadUrl: `${import.meta.env.VITE_APP_BASE_API}/common/upload`,
      uploadHeaders: { Authorization: `Bearer ${storage.get(ACCESS_TOKEN)}` },
      form: { customerId: undefined, name: '', code: '', serviceType: '猎头招聘', amount: 0, startDate: today(), endDate: '', feeTerms: [newTerm()], attachment: '', remark: '', ...this.initial },
      rules: Object.fromEntries(['customerId', 'name', 'serviceType', 'amount', 'startDate', 'endDate'].map(key => [key, [{ required: true, message: '请完善此项', trigger: 'change' }]]))
    }
  },
  computed: {
    feeTotal() {
      return this.form.feeTerms.reduce((sum, term) => sum + Math.round(Number(term.amount || 0) * 100), 0) / 100
    },
    balanced() {
      return Math.round(this.feeTotal * 100) === Math.round(Number(this.form.amount) * 100)
    }
  },
  async mounted() {
    this.loading = true
    try {
      await this.searchCustomers()
      if (this.contractId) {
        const res = await getContract(this.contractId)
        this.form = res.data
      }
      if (this.form.customerId && !this.customers.some(item => item.id === this.form.customerId))
        this.customers.push({ id: this.form.customerId, name: this.form.customerName || this.initial.customerName, ownerUserName: this.form.ownerUserName })
      try {
        this.fileList = JSON.parse(this.form.attachment || '[]').map((item, index) => ({ ...item, uid: `existing-${index}`, status: 'done' }))
      } catch (_) {
        this.fileList = []
      }
    } finally {
      this.loading = false
    }
  },
  methods: {
    money,
    newTerm,
    async searchCustomers(name = '') {
      const res = await listContractCustomers({ name })
      this.customers = res.data || []
    },
    calculate(term) {
      if (term.chargeMode === 'rate') term.amount = Math.round(Number(term.baseAmount || 0) * Number(term.rate || 0)) / 100
    },
    onUpload(info) {
      this.fileList = info.fileList
      if (info.file.status === 'done') {
        if (info.file.response?.code !== 200) {
          this.fileList = this.fileList.filter(item => item.uid !== info.file.uid)
          this.$message.error(info.file.response?.msg || '上传失败')
        }
      } else if (info.file.status === 'error') {
        this.$message.error('附件上传失败，请重试')
      }
    },
    removeFile(file) {
      this.fileList = this.fileList.filter(item => item.uid !== file.uid)
      return true
    },
    async save() {
      if (this.loading || this.saving) return false
      this.saving = true
      try {
        const valid = await new Promise(resolve => this.$refs.form.validate(resolve))
        if (!valid) return false
        if (this.fileList.some(item => item.status === 'uploading' || item.status === 'error')) return this.$message.warning('请等待附件上传完成或移除失败的文件')
        this.form.attachment = JSON.stringify(this.fileList.map(item => ({ name: item.name, url: item.response?.url || item.url, fileName: item.response?.fileName || item.fileName })))
        const res = this.contractId ? await updateContract(this.contractId, this.form) : await createContract(this.form)
        if (!normalizeContractId(res.data?.id)) return this.$message.error('保存结果未返回有效合同编号，请刷新合同列表核对，避免重复提交')
        this.$message.success(this.contractId ? '合同修改已保存' : '合同已创建，可继续完善并提交审批')
        this.$emit('ok', res.data)
        return true
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style lang="less" scoped>
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  h3 {
    margin: 0;
  }
}
.muted {
  color: #667085;
  margin: 6px 0 16px;
}
.fee-editor {
  border: 1px solid #e5e9ee;
  border-radius: 6px;
  background: #fafbfc;
  padding: 14px 18px 0;
  margin-bottom: 14px;
}
.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 16px;
  border-top: 1px solid #edf0f3;
}
</style>
