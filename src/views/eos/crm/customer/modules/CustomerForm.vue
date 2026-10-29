<template>
  <a-spin :spinning="loading">
    <a-form-model ref="form" :model="form" :rules="rules" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
      <div class="form-section-title">客户主档</div>
      <a-row :gutter="16">
        <a-col :xs="24" :md="12">
          <a-form-model-item label="公司名称" prop="name">
            <a-input v-model="form.name" placeholder="请输入公司全称" />
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="客户编号">
            <a-input v-model="form.serial" placeholder="留空自动生成" />
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="行业" prop="industry">
            <a-auto-complete
              v-model="form.industry"
              :data-source="options.industries"
              allow-clear
              placeholder="选择或输入行业，例如：软件与信息服务"
            />
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="规模">
            <a-select v-model="form.companyScale" allow-clear placeholder="请选择公司规模">
              <a-select-option v-for="item in options.scales" :key="item" :value="item">{{ item }}</a-select-option>
            </a-select>
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="地区">
            <a-auto-complete v-model="form.region" :data-source="options.regions" placeholder="选择或输入地区" />
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="企业性质">
            <a-select v-model="form.enterpriseNature" allow-clear placeholder="请选择企业性质">
              <a-select-option v-for="item in options.natures" :key="item" :value="item">{{ item }}</a-select-option>
            </a-select>
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="客户来源">
            <a-select v-model="form.customerSource" allow-clear placeholder="请选择客户来源">
              <a-select-option v-for="item in options.sources" :key="item" :value="item">{{ item }}</a-select-option>
            </a-select>
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="合作状态">
            <a-select v-model="form.cooperationStatus" placeholder="请选择合作状态">
              <a-select-option v-for="item in options.statuses" :key="item" :value="item">{{ item }}</a-select-option>
            </a-select>
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="客户负责人">
            <a-select v-model="form.ownerUserId" show-search :filter-option="false" placeholder="请选择负责人" @search="searchOwners">
              <a-select-option v-for="owner in owners" :key="owner.userId" :value="owner.userId">
                {{ owner.nickName }} · {{ owner.deptName || '未分配部门' }}
              </a-select-option>
            </a-select>
          </a-form-model-item>
        </a-col>
        <a-col :span="24">
          <a-form-model-item label="地址" :label-col="{ span: 3 }" :wrapper-col="{ span: 21 }">
            <a-input v-model="form.address" placeholder="请输入客户地址" />
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="税号">
            <a-input v-model="form.taxNumber" placeholder="用于防止重复建档" />
          </a-form-model-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-model-item label="纳税人类别">
            <a-select v-model="form.taxpayerType" allow-clear>
              <a-select-option value="一般纳税人">一般纳税人</a-select-option>
              <a-select-option value="小规模纳税人">小规模纳税人</a-select-option>
            </a-select>
          </a-form-model-item>
        </a-col>
        <a-col :span="24">
          <a-form-model-item label="备注" :label-col="{ span: 3 }" :wrapper-col="{ span: 21 }">
            <a-textarea v-model="form.remark" :auto-size="{ minRows: 2, maxRows: 5 }" />
          </a-form-model-item>
        </a-col>
      </a-row>

      <div class="form-section-head">
        <div>
          <div class="form-section-title">联系人</div>
          <div class="form-section-desc">一个客户可以维护多个联系人，决策权限为自由标签。</div>
        </div>
        <a-button size="small" ghost type="primary" icon="plus" @click="addContact">添加联系人</a-button>
      </div>
      <div v-if="!form.sealMerchantUsers.length" class="empty-block">暂无联系人</div>
      <div v-for="(contact, index) in form.sealMerchantUsers" :key="contact._key || index" class="contact-row">
        <a-row :gutter="10">
          <a-col :xs="24" :md="5">
            <a-input v-model="contact.name" placeholder="姓名" />
          </a-col>
          <a-col :xs="24" :md="5">
            <a-input v-model="contact.position" placeholder="职位" />
          </a-col>
          <a-col :xs="24" :md="4">
            <a-input v-model="contact.phone" placeholder="电话" />
          </a-col>
          <a-col :xs="24" :md="4">
            <a-input v-model="contact.wechat" placeholder="微信" />
          </a-col>
          <a-col :xs="24" :md="4">
            <a-input v-model="contact.email" placeholder="邮箱" />
          </a-col>
          <a-col :xs="24" :md="2">
            <a-checkbox
              :checked="contact.primaryContact === '1'"
              @change="event => setPrimaryContact(index, event.target.checked)"
            >
              首要
            </a-checkbox>
          </a-col>
        </a-row>
        <a-row :gutter="10">
          <a-col :xs="24" :md="18">
            <a-input v-model="contact.decisionRole" placeholder="决策权限，例如：最终决策、技术评估、商务谈判" />
          </a-col>
          <a-col :xs="20" :md="5">
            <a-input v-model="contact.remark" placeholder="备注" />
          </a-col>
          <a-col :xs="4" :md="1">
            <a-button type="link" icon="delete" @click="removeContact(index)" />
          </a-col>
        </a-row>
      </div>

      <a-collapse :bordered="false" class="form-collapse">
        <a-collapse-panel key="cooperation" header="合作数据">
          <a-row :gutter="16">
            <a-col :xs="24" :md="8">
              <a-form-model-item label="合作职位数">
                <a-input-number v-model="form.cooperationPositions" :min="0" style="width: 100%" />
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-model-item label="推荐人数">
                <a-input-number v-model="form.referralCount" :min="0" style="width: 100%" />
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-model-item label="入职人数">
                <a-input-number v-model="form.hireCount" :min="0" style="width: 100%" />
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-model-item label="合同金额">
                <a-input-number v-model="form.contractAmount" :min="0" :precision="2" style="width: 100%" />
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-model-item label="回款金额">
                <a-input-number v-model="form.receivedAmount" :min="0" :precision="2" style="width: 100%" />
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-model-item label="合同到期">
                <a-date-picker v-model="form.contractExpireDate" value-format="YYYY-MM-DD" style="width: 100%" />
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-model-item label="最近回款">
                <a-date-picker v-model="form.lastPaymentDate" value-format="YYYY-MM-DD" style="width: 100%" />
              </a-form-model-item>
            </a-col>
          </a-row>
        </a-collapse-panel>
        <a-collapse-panel key="bank" header="银行账户">
          <div class="form-section-head">
            <span class="form-section-desc">沿用原客商银行账户能力。</span>
            <a-button size="small" ghost type="primary" icon="plus" @click="form.sealMerchantAccounts.push({})">添加账户</a-button>
          </div>
          <div v-for="(account, index) in form.sealMerchantAccounts" :key="account.id || index" class="account-row">
            <a-input v-model="account.account" placeholder="银行账号" />
            <a-input v-model="account.accountName" placeholder="账号名称" />
            <a-input v-model="account.bank" placeholder="开户银行" />
            <a-button type="link" icon="delete" @click="form.sealMerchantAccounts.splice(index, 1)" />
          </div>
        </a-collapse-panel>
      </a-collapse>

      <div class="form-actions">
        <a-button @click="$emit('close')">取消</a-button>
        <a-button type="primary" :loading="saving" @click="handleSubmit">保存</a-button>
      </div>
    </a-form-model>
  </a-spin>
</template>

<script>
import { addCustomer, getCustomer, listCustomerOwners, updateCustomer } from '/src/api/eos/crm/index.js'

const options = {
  industries: ['互联网', '软件与信息服务', '制造业', '建筑工程', '金融', '教育', '医疗健康', '批发零售', '其他'],
  scales: ['1-50人', '51-100人', '101-500人', '501-1000人', '1000人以上'],
  regions: ['华北', '华东', '华南', '华中', '西南', '西北', '东北', '海外'],
  natures: ['民营企业', '国有企业', '外资企业', '合资企业', '事业单位', '政府机构', '其他'],
  sources: ['官网咨询', '客户转介绍', '展会活动', '销售拓展', '渠道合作', '广告投放', '公开信息', '组织分配', '其他'],
  statuses: ['潜在客户', '初步接洽', '合作中', '重点客户', '暂停合作', '已流失']
}

function emptyForm() {
  return {
    serial: '',
    name: '',
    type: '客户',
    industry: '',
    companyScale: '',
    region: '',
    enterpriseNature: '',
    customerSource: '',
    cooperationStatus: '潜在客户',
    ownerUserId: null,
    address: '',
    taxNumber: '',
    taxpayerType: '',
    remark: '',
    cooperationPositions: 0,
    referralCount: 0,
    hireCount: 0,
    contractAmount: 0,
    receivedAmount: 0,
    contractExpireDate: '',
    lastPaymentDate: '',
    sealMerchantUsers: [],
    sealMerchantAccounts: [],
    collaboratorIds: []
  }
}

export default {
  name: 'CustomerForm',
  props: {
    isUpdate: { type: Boolean, default: false },
    updateRow: { type: Object, default: () => ({}) }
  },
  data() {
    return {
      loading: false,
      saving: false,
      form: emptyForm(),
      owners: [],
      options,
      rules: {
        name: [{ required: true, message: '请输入公司名称', trigger: 'blur' }],
        industry: [{ required: true, message: '请选择行业', trigger: 'change' }]
      }
    }
  },
  mounted() {
    this.loadOwners()
    if (this.isUpdate && this.updateRow.id) {
      this.loadCustomer(this.updateRow.id)
    } else if (this.$store.getters.userInfo?.userId) {
      this.form.ownerUserId = this.$store.getters.userInfo.userId
    }
  },
  methods: {
    loadOwners(keyword = '') {
      listCustomerOwners({ keyword }).then(res => {
        this.owners = res.data || []
        if (!this.form.ownerUserId && this.owners.length) {
          this.form.ownerUserId = this.owners[0].userId
        }
      })
    },
    searchOwners(keyword) {
      this.loadOwners(keyword)
    },
    loadCustomer(id) {
      this.loading = true
      getCustomer(id)
        .then(res => {
          const data = res.data || {}
          this.form = {
            ...emptyForm(),
            ...data,
            sealMerchantUsers: (data.sealMerchantUsers || []).map(contact => ({
              ...contact,
              primaryContact: String(contact.primaryContact || '0'),
              _key: `${contact.id || 'new'}-${Math.random()}`
            })),
            sealMerchantAccounts: data.sealMerchantAccounts || [],
            collaboratorIds: (data.collaborators || []).map(item => item.userId)
          }
        })
        .finally(() => {
          this.loading = false
        })
    },
    addContact() {
      this.form.sealMerchantUsers.push({
        name: '',
        position: '',
        phone: '',
        wechat: '',
        email: '',
        decisionRole: '',
        primaryContact: this.form.sealMerchantUsers.length ? '0' : '1',
        remark: '',
        _key: `new-${Date.now()}-${Math.random()}`
      })
    },
    removeContact(index) {
      this.form.sealMerchantUsers.splice(index, 1)
      if (this.form.sealMerchantUsers.length && !this.form.sealMerchantUsers.some(item => item.primaryContact === '1')) {
        this.form.sealMerchantUsers[0].primaryContact = '1'
      }
    },
    setPrimaryContact(index, checked) {
      this.form.sealMerchantUsers.forEach((item, itemIndex) => {
        item.primaryContact = checked && itemIndex === index ? '1' : '0'
      })
    },
    handleSubmit() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        if (this.form.sealMerchantUsers.some(contact => !contact.name)) {
          this.$message.warning('联系人姓名不能为空')
          return
        }
        this.saving = true
        const action = this.isUpdate ? updateCustomer : addCustomer
        action(this.form)
          .then(() => {
            this.$message.success(this.isUpdate ? '客户已更新' : '客户已建档')
            this.$emit('ok')
            this.$emit('close')
          })
          .catch(error => {
            this.$message.error(error.message || String(error))
          })
          .finally(() => {
            this.saving = false
          })
      })
    }
  }
}
</script>

<style lang="less" scoped>
.form-section title {
  display: block;
}

.form-section-title {
  margin: 4px 0 14px;
  color: #17212b;
  font-weight: 600;
  font-size: 16px;
}

.form-section-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  margin: 10px 0;
}

.form-section-desc {
  color: #7b8794;
  font-size: 12px;
}

.empty-block {
  margin-bottom: 14px;
  padding: 16px;
  color: #98a2b3;
  background: #fafbfc;
  border: 1px dashed #dfe5ea;
  text-align: center;
}

.contact-row {
  margin-bottom: 10px;
  padding: 10px;
  background: #fafbfc;
  border: 1px solid #edf0f3;
}

.contact-row .ant-row + .ant-row {
  margin-top: 8px;
}

.form-collapse {
  margin-top: 14px;
  background: #fafbfc;
}

.account-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 34px;
  gap: 8px;
  margin-bottom: 8px;
}

.form-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #edf0f3;
}

@media (max-width: 768px) {
  .account-row {
    grid-template-columns: 1fr;
  }
}
</style>
