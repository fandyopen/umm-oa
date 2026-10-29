<template>
  <a-spin :spinning="loading">
    <a-form-model ref="form" :model="form" :rules="rules" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
      <a-row :gutter="16">
        <a-col :xs="24" :md="12">
          <a-form-model-item label="商机名称" prop="name">
            <a-input v-model="form.name" placeholder="请输入商机名称" />
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="商机编号">
            <a-input v-model="form.code" placeholder="留空自动生成" />
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="关联客户" prop="customerId">
            <a-select v-model="form.customerId" show-search :filter-option="false" placeholder="请选择客户" @search="searchCustomers" @change="handleCustomerChange">
              <a-select-option v-for="customer in customers" :key="customer.id" :value="customer.id">
                {{ customer.name }}
              </a-select-option>
            </a-select>
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="商机阶段" prop="stage">
            <a-select v-model="form.stage">
              <a-select-option v-for="item in stages" :key="item" :value="item">{{ item }}</a-select-option>
            </a-select>
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="潜在合作">
            <a-input v-model="form.potentialCooperation" placeholder="例如：年度招聘服务、岗位外包" />
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="合作意向">
            <a-select v-model="form.cooperationIntent" allow-clear>
              <a-select-option v-for="item in intents" :key="item" :value="item">{{ item }}</a-select-option>
            </a-select>
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="预计成交金额">
            <a-input-number v-model="form.expectedAmount" :min="0" :precision="2" style="width: 100%" />
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="预计成交时间">
            <a-date-picker v-model="form.expectedCloseDate" value-format="YYYY-MM-DD" style="width: 100%" />
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="商机负责人">
            <a-select v-model="form.ownerUserId" show-search :filter-option="false" placeholder="请选择负责人" @search="searchOwners" @change="handleOwnerChange">
              <a-select-option v-for="owner in owners" :key="owner.userId" :value="owner.userId">
                {{ owner.nickName }} · {{ owner.deptName || '未分配部门' }}
              </a-select-option>
            </a-select>
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="商机状态">
            <a-input v-model="form.statusLabel" placeholder="例如：推进中、已赢单、已输单" />
          </a-form-model-item>
        </a-col>
        <a-col :span="24">
          <a-form-model-item label="客户需求" :label-col="{ span: 3 }" :wrapper-col="{ span: 21 }">
            <a-textarea v-model="form.requirement" :auto-size="{ minRows: 3, maxRows: 7 }" placeholder="记录客户需求、岗位、人数、服务范围等" />
          </a-form-model-item>
        </a-col>
        <a-col :span="24">
          <a-form-model-item label="备注" :label-col="{ span: 3 }" :wrapper-col="{ span: 21 }">
            <a-textarea v-model="form.remark" :auto-size="{ minRows: 2, maxRows: 5 }" />
          </a-form-model-item>
        </a-col>
      </a-row>

      <div class="form-actions">
        <a-button @click="$emit('close')">取消</a-button>
        <a-button type="primary" :loading="saving" @click="handleSubmit">保存</a-button>
      </div>
    </a-form-model>
  </a-spin>
</template>

<script>
import { addBusiness, getBusiness, updateBusiness } from '/src/api/eos/business/business.js'
import { listCustomerOptions, listCustomerOwners } from '/src/api/eos/crm/index.js'

const stages = ['初步接洽', '需求确认', '方案报价', '商务谈判', '赢单', '输单']
const intents = ['高', '中', '低', '待评估']

function emptyForm() {
  return {
    name: '',
    code: '',
    customerId: null,
    customerName: '',
    stage: '初步接洽',
    potentialCooperation: '',
    requirement: '',
    cooperationIntent: '中',
    expectedAmount: 0,
    expectedCloseDate: '',
    ownerUserId: null,
    ownerUserName: '',
    statusLabel: '推进中',
    remark: ''
  }
}

export default {
  name: 'BusinessForm',
  props: {
    isUpdate: { type: Boolean, default: false },
    updateRow: { type: Object, default: () => ({}) }
  },
  data() {
    return {
      loading: false,
      saving: false,
      form: emptyForm(),
      customers: [],
      owners: [],
      stages,
      intents,
      rules: {
        name: [{ required: true, message: '请输入商机名称', trigger: 'blur' }],
        customerId: [{ required: true, message: '请选择关联客户', trigger: 'change' }],
        stage: [{ required: true, message: '请选择商机阶段', trigger: 'change' }]
      }
    }
  },
  mounted() {
    this.loadCustomers()
    this.loadOwners()
    if (this.isUpdate && this.updateRow.id) {
      this.loadBusiness(this.updateRow.id)
    }
  },
  methods: {
    loadCustomers(keyword = '') {
      listCustomerOptions({ name: keyword }).then(res => {
        this.customers = res.data || []
      })
    },
    searchCustomers(keyword) {
      this.loadCustomers(keyword)
    },
    loadOwners(keyword = '') {
      listCustomerOwners({ keyword }).then(res => {
        this.owners = res.data || []
        if (!this.form.ownerUserId && this.owners.length) {
          const current = this.$store.getters.userInfo
          const matched = this.owners.find(owner => owner.userId === current?.userId)
          this.form.ownerUserId = matched?.userId || this.owners[0].userId
          this.form.ownerUserName = matched?.nickName || this.owners[0].nickName
        }
      })
    },
    searchOwners(keyword) {
      this.loadOwners(keyword)
    },
    handleCustomerChange(customerId) {
      const customer = this.customers.find(item => item.id === customerId)
      this.form.customerName = customer?.name || ''
    },
    handleOwnerChange(ownerUserId) {
      const owner = this.owners.find(item => item.userId === ownerUserId)
      this.form.ownerUserName = owner?.nickName || ''
    },
    loadBusiness(id) {
      this.loading = true
      getBusiness(id)
        .then(res => {
          const data = res.data || {}
          this.form = {
            ...emptyForm(),
            ...data,
            customerId: data.customerId || this.findCustomerId(data.customerName || data.ownerUnit),
            stage: data.stage || data.businessState || '初步接洽',
            expectedCloseDate: data.expectedCloseDate || '',
            ownerUserId: data.ownerUserId || null
          }
          if (this.form.customerId) {
            this.handleCustomerChange(this.form.customerId)
          }
        })
        .finally(() => {
          this.loading = false
        })
    },
    findCustomerId(name) {
      return this.customers.find(customer => customer.name === name)?.id || null
    },
    handleSubmit() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        this.saving = true
        const action = this.isUpdate ? updateBusiness : addBusiness
        action({
          ...this.form,
          expectedCloseDate: this.form.expectedCloseDate || '',
          businessState: this.form.stage,
          remark: this.form.remark || this.form.requirement
        })
          .then(() => {
            this.$message.success(this.isUpdate ? '商机已更新' : '商机已创建')
            this.$emit('ok')
            this.$emit('close')
          })
          .catch(error => this.$message.error(error.message || String(error)))
          .finally(() => {
            this.saving = false
          })
      })
    }
  }
}
</script>

<style scoped>
.form-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid #edf0f3;
}
</style>
