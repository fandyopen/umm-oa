<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-card :bordered="false">
      <div class="workspace-head">
        <div>
          <h2>合同列表</h2>
          <p>以客户为中心管理合同，从起草、审批、签署到履约、回款与续约。</p>
        </div>
        <a-button v-if="showModuleAction('contract', 'add')" type="primary" icon="plus" @click="create" v-hasModuleAction="moduleActionBinding('contract', 'add')" :loading="!!moduleActionBusy.openForm">新建合同</a-button>
      </div>
      <div class="lifecycle">
        <span>客户建档</span><a-icon type="right" /><span>合同起草</span><a-icon type="right" /><span>审批签署</span><a-icon type="right" /><span>收费回款</span><a-icon type="right" /><span>到期续约</span>
      </div>
      <a-alert class="workspace-guide" type="info" show-icon message="合同审批在统一待办办理；收费、回款和版本在合同详情查看；到期与逾期风险统一进入风险提醒。" />
      <div class="workspace-metrics">
        <div v-for="item in metrics" :key="item.label" class="workspace-metric">
          <span>{{ item.label }}</span
          ><strong>{{ item.value }}</strong>
        </div>
      </div>
      <div class="workspace-filters">
        <a-input-search v-model="query.keyword" placeholder="搜索合同名称、编号或客户" allow-clear class="tw-w-[290px]" @search="refresh" />
        <a-select v-model="query.customerId" allow-clear show-search :filter-option="false" placeholder="全部客户" class="tw-w-[220px]" @search="searchCustomers" @change="refresh"
          ><a-select-option v-for="item in customers" :key="item.id" :value="item.id">{{ item.name }}</a-select-option></a-select
        >
        <a-select v-model="query.status" allow-clear placeholder="全部合同状态" class="tw-w-[150px]" @change="refresh"
          ><a-select-option v-for="(label, key) in statuses" :key="key">{{ label }}</a-select-option></a-select
        >
        <a-button icon="reload" @click="reset">重置</a-button>
      </div>
      <a-table :loading="loading" :columns="columns" :data-source="rows" row-key="id" :pagination="pagination" :scroll="{ x: 1150 }" size="middle" @change="changePage">
        <span slot="contract" slot-scope="text, record"
          ><a @click="openDetail(record.id)">{{ text }}</a
          ><small class="subline">{{ record.code }}</small></span
        >
        <span slot="customer" slot-scope="text, record"
          ><a v-if="showModuleAction('customer')" @click="openCustomer(record.customerId)">{{ text }}</a
          ><span v-else>{{ text }}</span></span
        >
        <span slot="money" slot-scope="value">{{ money(value) }}</span>
        <span slot="status" slot-scope="value, record"
          ><a-tag :color="statusColors[value]">{{ statuses[value] }}</a-tag
          ><a-tag v-if="record.signingPending" color="blue">签署审批中</a-tag><small v-if="record.effectiveVersion && !['active', 'completed'].includes(value)" class="subline">V{{ record.effectiveVersion }} 继续生效</small></span
        >
        <span slot="action" slot-scope="_, record">
          <a-button type="link" size="small" @click="openDetail(record.id, detailTab)">查看详情</a-button>
          <a-button
            v-if="record.status === 'draft' && record.versionNo === 1 && !record.effectiveVersion && showModuleAction('contract', 'remove')"
            type="link"
            size="small"
            @click="remove(record)"
            v-hasModuleAction="moduleActionBinding('contract', 'remove')"
            >删除草稿</a-button
          >
        </span>
      </a-table>
    </a-card>
  </page-header-wrapper>
</template>

<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { crmModules } from '/src/views/eos/crm/moduleContexts.js'
import { listContracts, listContractCustomers, deleteContract, normalizeContractId } from '/src/api/eos/crm/contracts.js'
import { statuses, statusColors, money } from '../shared.js'
import { contractDetailTabs } from '../navigation.js'
import ContractForm from './ContractForm.vue'
import ContractDetail from './ContractDetail.vue'
import CustomerDetail from '../../customer/modules/CustomerDetail.vue'

const col = (title, dataIndex, slot, width) => ({ title, dataIndex, width, ...(slot ? { scopedSlots: { customRender: slot } } : {}) })
export default {
  mixins: [
    withModuleContexts(
      { contract: crmModules.contract, customer: crmModules.customer },
      {
        openForm: { module: 'contract', action: 'add', label: '新建合同' },
        openDetail: { module: 'contract', action: 'view', label: '查看合同详情' },
        openCustomer: { module: 'customer', action: 'view', label: '查看客户详情' },
        remove: { module: 'contract', action: 'remove', label: '删除草稿' }
      }
    )
  ],
  name: 'CrmContractWorkspace',
  data() {
    return {
      loading: false,
      rows: [],
      total: 0,
      summary: {},
      customers: [],
      query: { keyword: '', customerId: normalizeContractId(this.$route.query.customerId) || undefined, status: undefined },
      pageNum: 1,
      pageSize: 20,
      openedContract: '',
      statuses,
      statusColors
    }
  },
  computed: {
    detailTab() {
      return contractDetailTabs.includes(this.$route.query.tab) ? this.$route.query.tab : 'overview'
    },
    pagination() {
      return { current: this.pageNum, pageSize: this.pageSize, total: this.total, showSizeChanger: true, showTotal: total => `共 ${total} 条` }
    },
    metrics() {
      return [
        { label: '符合筛选的合同', value: this.total },
        { label: '生效合同金额', value: money(this.summary.effectiveAmount) },
        { label: '已回款', value: money(this.summary.receivedAmount) },
        { label: '待回款', value: money(this.summary.outstandingAmount) }
      ]
    },
    columns() {
      return [
        col('合同名称 / 编号', 'name', 'contract', 240),
        col('客户', 'customerName', 'customer', 180),
        col('状态', 'status', 'status', 130),
        col('合同金额', 'amount', 'money', 125),
        col('负责人', 'ownerUserName', null, 110),
        col('服务结束', 'endDate', null, 115),
        col('生效已收', 'receivedAmount', 'money', 125),
        col('操作', 'action', 'action', 180)
      ]
    }
  },
  watch: {
    '$route.query': {
      handler() {
        this.readRoute()
      }
    }
  },
  mounted() {
    this.searchCustomers()
    this.readRoute()
  },
  methods: {
    money,
    async searchCustomers(name = '') {
      const res = await listContractCustomers({ name })
      this.customers = res.data || []
    },
    async readRoute() {
      this.query.customerId = normalizeContractId(this.$route.query.customerId) || undefined
      this.load()
      const id = this.$route.query.contractId
      const entry = `${id}:${this.detailTab}`
      if (id && entry !== this.openedContract) {
        this.openedContract = entry
        this.openDetail(id, this.detailTab, this.$route.query.termKey || '')
      }
      if (this.$route.query.create === '1') {
        const initial = {
          customerId: normalizeContractId(this.$route.query.customerId) || undefined,
          customerName: this.$route.query.customerName,
          opportunityId: normalizeContractId(this.$route.query.opportunityId) || undefined,
          name: this.$route.query.name || '',
          amount: Number(this.$route.query.amount) || 0
        }
        this.openForm(initial)
        this.$router.replace({ path: this.$route.path, query: initial.customerId ? { customerId: initial.customerId } : {} })
      }
    },
    async load() {
      this.loading = true
      try {
        const response = await listContracts({ ...this.query, pageNum: this.pageNum, pageSize: this.pageSize })
        this.rows = response.rows || []
        this.total = response.total || 0
        this.summary = response.summary || {}
        const lastPage = Math.max(1, Math.ceil(this.total / this.pageSize))
        if (this.pageNum > lastPage) {
          this.pageNum = lastPage
          await this.load()
        }
      } finally {
        this.loading = false
      }
    },
    refresh() {
      this.pageNum = 1
      this.load()
    },
    reset() {
      this.query = { keyword: '', customerId: undefined, status: undefined }
      this.pageNum = 1
      if (Object.keys(this.$route.query).length) this.$router.replace({ path: this.$route.path, query: {} })
      else this.load()
    },
    changePage(pagination) {
      this.pageNum = pagination.current
      this.pageSize = pagination.pageSize
      this.load()
    },
    create() {
      this.openForm(this.query.customerId ? { customerId: this.query.customerId, customerName: this.customers.find(item => item.id === this.query.customerId)?.name } : {})
    },
    openForm(initial) {
      this.$createAntdModal({
        modalProps: { title: '新建客户合同', width: 920, maskClosable: false, footer: null },
        onOk: ({ payload }) => {
          if (!normalizeContractId(payload?.id)) {
            this.$message.error('保存结果缺少有效合同编号，请刷新列表核对')
            return false
          }
          this.load()
          this.openDetail(payload.id)
        },
        content: { template: ContractForm, props: { initial } }
      })
    },
    openDetail(id, initialTab = 'overview', initialTermKey = '') {
      const contractId = normalizeContractId(id)
      if (!contractId) return this.$message.warning('合同编号无效，请刷新列表后重试')
      this.$createAntdDrawer({
        stopPropagation: true,
        drawerProps: { title: '合同详情', width: '85%', maskClosable: true },
        content: { template: ContractDetail, props: { moduleMeta: this.moduleContexts.contract, contractId, initialTab, initialTermKey }, on: { changed: () => this.load() } }
      })
    },
    openCustomer(id) {
      this.$createAntdDrawer({
        stopPropagation: true,
        drawerProps: { title: '客户详情', width: '82%', maskClosable: true },
        content: { template: CustomerDetail, props: { moduleMeta: this.moduleContexts.customer, formId: Number(id) } }
      })
    },
    remove(record) {
      this.$antdconfirm({
        title: '删除合同草稿',
        content: `确认删除“${record.name}”？仅未提交过的草稿可删除。`,
        onOk: async () => {
          await deleteContract(record.id)
          this.$message.success('草稿已删除')
          await this.load()
        }
      })
    }
  }
}
</script>

<style lang="less" scoped>
.workspace-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  h2 {
    color: #17212b;
    margin: 0;
    font-size: 22px;
    font-weight: 600;
  }
  p {
    margin: 7px 0 0;
    color: #667085;
    max-width: 900px;
  }
}
.lifecycle {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  padding: 14px 18px;
  background: #f4f8fc;
  color: #42617b;
  border: 1px solid #e5edf4;
  border-radius: 5px;
  margin: 20px 0 12px;
  font-size: 13px;
  .anticon {
    color: #a6b5c3;
    font-size: 10px;
  }
}
.workspace-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 22px;
}
.workspace-metric {
  padding: 16px 20px;
  border: 1px solid #e5e9ee;
  border-left: 3px solid #1769aa;
  border-radius: 5px;
  span {
    display: block;
    color: #667085;
    font-size: 12px;
  }
  strong {
    display: block;
    margin-top: 7px;
    font-size: 24px;
    font-weight: 600;
    color: #17212b;
  }
  .danger {
    color: #cf3e36;
  }
}
.workspace-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}
.workspace-guide {
  margin-bottom: 18px;
}
.subline {
  display: block;
  color: #98a2b3;
  font-size: 12px;
  margin-top: 4px;
}
@media (max-width: 900px) {
  .workspace-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .workspace-metric strong {
    font-size: 20px;
  }
}
</style>
