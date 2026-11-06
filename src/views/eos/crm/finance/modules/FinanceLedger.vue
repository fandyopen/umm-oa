<template>
  <div>
    <div class="filters">
      <a-input-search v-model="query.keyword" allow-clear placeholder="搜索客户、合同或收费节点" class="tw-w-[270px]" @search="search" />
      <a-select v-model="query.paymentStatus" allow-clear placeholder="全部收款状态" class="tw-w-[145px]" @change="search"
        ><a-select-option value="unpaid">待收</a-select-option><a-select-option value="overdue">逾期</a-select-option><a-select-option value="paid">已结清</a-select-option
        ><a-select-option value="draft">草稿应收</a-select-option></a-select
      >
      <a-date-picker v-model="query.startDate" value-format="YYYY-MM-DD" placeholder="到期开始" @change="search" /><a-date-picker v-model="query.endDate" value-format="YYYY-MM-DD" placeholder="到期结束" @change="search" />
      <a-button icon="reload" @click="reset">重置</a-button>
      <a-button v-if="showModuleAction('finance', 'add')" type="primary" icon="plus" @click="openReceivable()" v-hasModuleAction="moduleActionBinding('finance', 'add')">新建手工应收</a-button>
    </div>
    <a-table :loading="loading" :data-source="rows" :pagination="pagination" row-key="sourceKey" :scroll="{ x: 1450 }" size="small" @change="page">
      <a-table-column title="客户" data-index="customerName" width="170" /><a-table-column title="应收来源" width="250"
        ><template slot-scope="r"
          >{{ r.manual ? '手工应收' : '生效合同收费节点' }}<small class="subline">{{ r.contractCode || r.sourceEvent }}</small></template
        ></a-table-column
      >
      <a-table-column title="应收项目" data-index="title" width="180" /><a-table-column title="到期日" data-index="dueDate" width="110" />
      <a-table-column title="应收" data-index="receivableAmount" width="120"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      ><a-table-column title="净回款" data-index="netReceivedAmount" width="120"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      >
      <a-table-column title="已核销" data-index="writeoffAmount" width="110"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      ><a-table-column title="待收" data-index="outstandingAmount" width="120"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      >
      <a-table-column title="逾期" data-index="overdueDays" width="90"
        ><template slot-scope="v">{{ v ? v + ' 天' : '—' }}</template></a-table-column
      >
      <a-table-column title="状态" data-index="paymentStatus" width="100"
        ><template slot-scope="v"
          ><a-tag :color="colors[v]">{{ labels[v] || v }}</a-tag></template
        ></a-table-column
      >
      <a-table-column title="操作" fixed="right" width="190"
        ><template slot-scope="r">
          <a-button v-if="r.manual && r.status === 'draft' && showModuleAction('finance', 'confirm')" type="link" size="small" @click="confirmReceivable(r)" v-hasModuleAction="moduleActionBinding('finance', 'confirm')"
            >确认应收</a-button
          >
          <a-button v-if="r.manual && r.status === 'draft' && showModuleAction('finance', 'edit')" type="link" size="small" @click="openReceivable(r)" v-hasModuleAction="moduleActionBinding('finance', 'edit')">修改</a-button>
          <a-button v-if="r.manual && r.status === 'confirmed' && showModuleAction('finance', 'confirm')" type="link" size="small" @click="revokeReceivable(r)" v-hasModuleAction="moduleActionBinding('finance', 'confirm')"
            >撤销确认</a-button
          >
        </template></a-table-column
      >
    </a-table>
    <a-modal :visible="receivableVisible" title="手工应收" :confirm-loading="saving" @cancel="receivableVisible = false" @ok="saveReceivable">
      <a-form-model :model="form" :rules="rules" ref="form" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-model-item label="客户" prop="customerId"
          ><a-select v-model="form.customerId" show-search option-filter-prop="children" placeholder="选择客户"
            ><a-select-option v-for="c in customers" :key="c.id" :value="c.id">{{ c.name }}</a-select-option></a-select
          ></a-form-model-item
        >
        <a-form-model-item label="应收名称" prop="title"><a-input v-model="form.title" maxlength="200" /></a-form-model-item
        ><a-form-model-item label="来源事件"><a-input v-model="form.sourceEvent" maxlength="200" placeholder="业务事件或费用依据" /></a-form-model-item>
        <a-form-model-item label="金额" prop="amount"><a-input-number v-model="form.amount" :min="0.01" :precision="2" class="tw-w-full" /></a-form-model-item
        ><a-form-model-item label="到期日" prop="dueDate"><a-date-picker v-model="form.dueDate" value-format="YYYY-MM-DD" class="tw-w-full" /></a-form-model-item>
        <a-form-model-item label="凭证"
          ><a-upload :action="uploadUrl" :headers="uploadHeaders" :file-list="files" @change="upload"><a-button icon="upload">上传凭证</a-button></a-upload></a-form-model-item
        ><a-form-model-item label="备注"><a-textarea v-model="form.remark" :max-length="1000" /></a-form-model-item>
      </a-form-model>
      <template slot="footer"><a-button @click="receivableVisible = false">取消</a-button><a-button type="primary" :loading="saving" @click="saveReceivable">保存草稿</a-button></template>
    </a-modal>
  </div>
</template>
<script>
import storage from 'store'
import { ACCESS_TOKEN } from '/src/store/mutation-types.js'
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { listFinanceLedger, listFinanceOptions, createReceivable, updateReceivable, financeReceivableAction } from '/src/api/eos/crm/finance.js'
const context = { component: 'eos/crm/finance/index', label: '财务与回款管理' }
export default {
  name: 'FinanceLedger',
  mixins: [
    withModuleContexts(
      { finance: context },
      {
        openReceivable: function (row) {
          return { module: 'finance', action: row?.id ? 'edit' : 'add', label: '编辑手工应收' }
        },
        saveReceivable: function () {
          return { module: 'finance', action: this.form.id ? 'edit' : 'add', label: '保存手工应收' }
        },
        confirmReceivable: { module: 'finance', action: 'confirm', label: '确认应收' },
        revokeReceivable: { module: 'finance', action: 'confirm', label: '撤销应收确认' }
      }
    )
  ],
  data: () => ({
    loading: false,
    saving: false,
    rows: [],
    total: 0,
    pageNum: 1,
    pageSize: 20,
    customers: [],
    receivableVisible: false,
    files: [],
    query: { keyword: '', paymentStatus: undefined, startDate: undefined, endDate: undefined },
    form: {},
    labels: { draft: '草稿', unpaid: '待收', overdue: '逾期', paid: '已结清' },
    colors: { draft: undefined, unpaid: 'blue', overdue: 'red', paid: 'green' },
    rules: {
      customerId: [{ required: true, message: '请选择客户' }],
      title: [{ required: true, message: '请填写应收名称' }],
      amount: [{ required: true, message: '请填写应收金额' }],
      dueDate: [{ required: true, message: '请选择到期日' }]
    }
  }),
  computed: {
    pagination() {
      return { current: this.pageNum, pageSize: this.pageSize, total: this.total, showSizeChanger: true, showTotal: n => '共 ' + n + ' 条' }
    },
    uploadUrl() {
      return import.meta.env.VITE_APP_BASE_API + '/common/upload'
    },
    uploadHeaders() {
      return { Authorization: 'Bearer ' + storage.get(ACCESS_TOKEN) }
    }
  },
  mounted() {
    this.load()
    this.loadOptions()
  },
  methods: {
    money(v) {
      return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
    },
    async load() {
      this.loading = true
      try {
        const r = await listFinanceLedger({ ...this.query, pageNum: this.pageNum, pageSize: this.pageSize })
        this.rows = r.data?.rows || []
        this.total = r.data?.total || 0
      } finally {
        this.loading = false
      }
    },
    async loadOptions() {
      const r = await listFinanceOptions()
      this.customers = r.data?.customers || []
    },
    search() {
      this.pageNum = 1
      this.load()
    },
    reset() {
      this.query = { keyword: '', paymentStatus: undefined, startDate: undefined, endDate: undefined }
      this.pageNum = 1
      this.load()
    },
    page(p) {
      this.pageNum = p.current
      this.pageSize = p.pageSize
      this.load()
    },
    async openReceivable(row) {
      await this.loadOptions()
      this.form = row ? { ...row } : {}
      this.files = JSON.parse(this.form.attachment || '[]').map((f, i) => ({ ...f, uid: 'old-' + i, status: 'done' }))
      this.receivableVisible = true
    },
    async saveReceivable() {
      if (this.saving) return
      const valid = await new Promise(resolve => this.$refs.form.validate(resolve))
      if (!valid) return
      if (this.files.some(f => f.status !== 'done' || !(f.response?.fileName || f.fileName))) return this.$message.warning('请等待凭证上传完成，或移除上传失败的文件')
      this.saving = true
      try {
        const data = { ...this.form, attachment: JSON.stringify(this.files.map(f => ({ name: f.name, url: f.response?.url || f.url, fileName: f.response?.fileName || f.fileName }))) }
        if (data.id) await updateReceivable(data.id, data)
        else await createReceivable(data)
        this.receivableVisible = false
        this.$message.success('应收草稿已保存')
        await this.load()
        this.$emit('changed')
      } finally {
        this.saving = false
      }
    },
    upload(info) {
      this.files = info.fileList
      if (info.file.status === 'done' && info.file.response?.code !== 200) {
        this.files = this.files.filter(f => f.uid !== info.file.uid)
        this.$message.error(info.file.response?.msg || '上传失败')
      } else if (info.file.status === 'error') this.$message.error('凭证上传失败')
    },
    async confirmReceivable(r) {
      await financeReceivableAction(r.id, 'confirm', { revision: r.revision })
      this.$message.success('应收已确认')
      await this.load()
      this.$emit('changed')
    },
    revokeReceivable(r) {
      this.$antdconfirm({
        title: '撤销应收确认',
        content: '只有没有已提交财务业务的应收可以撤销。',
        onOk: async () => {
          await financeReceivableAction(r.id, 'revoke', { revision: r.revision })
          await this.load()
          this.$emit('changed')
        }
      })
    }
  }
}
</script>
<style scoped>
.filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.subline {
  display: block;
  color: #9aa0a6;
  font-size: 12px;
}
.filters .ant-btn-primary {
  margin-left: auto;
}
</style>
