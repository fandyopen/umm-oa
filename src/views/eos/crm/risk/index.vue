<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-spin :spinning="loading">
      <div class="risk-header">
        <div>
          <h2>客户风险提醒</h2>
          <p>统一跟进客户及合同风险，记录催收、续约处理过程；实际回款、续约或结案后依据业务条件解除提醒。</p>
        </div>
        <a-button type="primary" icon="sync" :loading="syncing || !!moduleActionBusy.synchronize" @click="synchronize">立即检查</a-button>
      </div>

      <div class="risk-summary">
        <div class="risk-summary-item">
          <span>风险总数</span>
          <strong>{{ risks.length }}</strong>
        </div>
        <div class="risk-summary-item risk-high">
          <span>高风险</span>
          <strong>{{ highRiskCount }}</strong>
        </div>
        <div class="risk-summary-item risk-medium">
          <span>中风险</span>
          <strong>{{ mediumRiskCount }}</strong>
        </div>
        <div class="risk-summary-item">
          <span>关联负责人</span>
          <strong>{{ ownerCount }}</strong>
        </div>
      </div>

      <div class="risk-filter">
        <a-input-search v-model="keyword" placeholder="搜索客户、合同、负责人或风险原因" allow-clear style="width: 300px" />
        <a-select v-model="customerId" placeholder="全部客户" allow-clear show-search option-filter-prop="children" style="width: 200px" @change="contractId = undefined"><a-select-option v-for="item in customerOptions" :key="item.id" :value="item.id">{{ item.name }}</a-select-option></a-select>
        <a-select v-model="contractId" placeholder="全部合同" allow-clear show-search option-filter-prop="children" style="width: 220px"><a-select-option v-for="item in contractOptions" :key="item.id" :value="item.id">{{ item.name }}</a-select-option></a-select>
        <a-select v-model="riskType" placeholder="全部风险类型" allow-clear style="width: 180px">
          <a-select-option value="no_followup">长期未跟进</a-select-option>
          <a-select-option value="contract_expiry">合同即将到期</a-select-option>
          <a-select-option value="no_payment">长期未回款</a-select-option>
          <a-select-option value="payment_due">收费即将到期</a-select-option>
          <a-select-option value="payment_overdue">收费逾期</a-select-option>
        </a-select>
        <a-button icon="reload" @click="resetFilters">重置</a-button>
      </div>

      <a-table :columns="columns" :data-source="filteredRisks" row-key="taskId" :pagination="false" size="middle">
        <span slot="level" slot-scope="text">
          <a-tag :color="text === 'high' ? 'red' : 'orange'">
            {{ text === 'high' ? '高风险' : '中风险' }}
          </a-tag>
        </span>
        <span slot="customer" slot-scope="text, record">
          <a v-if="showModuleAction('customer')" class="customer-link" @click="openCustomer(record.customerId)">{{ text }}</a><span v-else>{{ text }}</span>
        </span>
        <span slot="owner" slot-scope="text">{{ text || '未分配' }}</span>
        <span slot="action" slot-scope="text, record">
          <a-button v-if="record.contractId" type="link" @click="openContract(record)">处理合同</a-button>
          <a-button v-if="record.contractId && showModuleAction('reminder', 'handle')" type="link" @click="startHandle(record)" :loading="!!moduleActionBusy.startHandle">记录处理</a-button>
          <a-button v-if="showModuleAction('customer')" type="link" @click="openCustomer(record.customerId)">查看客户</a-button>
        </span>
      </a-table>
      <a-empty v-if="!filteredRisks.length" description="当前没有客户风险" />
    </a-spin>
    <a-modal :visible="!!handleTarget" title="记录合同风险处理" :confirm-loading="saving || !!moduleActionBusy.saveHandle" @cancel="handleTarget = null" @ok="saveHandle">
      <p>{{ handleTarget ? `${handleTarget.customerName} · ${handleTarget.contractName} · ${handleTarget.title}` : '' }}</p>
      <a-alert type="info" message="记录沟通结果不会关闭风险；实际回款、续约或结案后依据业务条件解除提醒。" style="margin-bottom: 16px" />
      <a-textarea v-model="handleContent" :rows="4" :max-length="2000" placeholder="填写催收进度、付款计划或续约沟通结果" />
    </a-modal>
  </page-header-wrapper>
</template>

<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { crmModules } from '/src/views/eos/crm/moduleContexts.js'
import { listCustomerRisks, syncCustomerRisks } from '/src/api/eos/crm/index.js'
import CustomerDetail from '../customer/modules/CustomerDetail.vue'
import ContractDetail from '../contract/modules/ContractDetail.vue'
import { contractAction, normalizeContractId } from '/src/api/eos/crm/contracts.js'

export default {
  mixins: [
    withModuleContexts(
      { risk: crmModules.risk, customer: crmModules.customer, contract: crmModules.contract, reminder: crmModules.reminder },
      {
        openCustomer: { module: 'customer', action: 'view', label: '查看客户详情' },
        openContract: { module: 'contract', action: 'view', label: '查看关联合同' },
        startHandle: { module: 'reminder', action: 'handle', label: '记录风险处理' },
        saveHandle: { module: 'reminder', action: 'handle', label: '保存风险处理' },
        synchronize: { module: 'risk', action: 'view', label: '检查风险提醒' }
      }
    )
  ],
  name: 'CrmRisks',
  data() {
    return {
      loading: false,
      syncing: false,
      risks: [],
      keyword: '',
      riskType: '',
      customerId: undefined,
      contractId: undefined,
      handleTarget: null,
      handleContent: '',
      saving: false,
      columns: [
        { title: '风险等级', dataIndex: 'level', width: 100, scopedSlots: { customRender: 'level' } },
        { title: '客户', dataIndex: 'customerName', width: 220, scopedSlots: { customRender: 'customer' } },
        { title: '风险类型', dataIndex: 'title', width: 150 },
        { title: '关联合同', dataIndex: 'contractName', width: 190 },
        { title: '风险原因', dataIndex: 'reason' },
        { title: '负责人', dataIndex: 'ownerUserName', width: 120, scopedSlots: { customRender: 'owner' } },
        { title: '操作', width: 260, scopedSlots: { customRender: 'action' } }
      ]
    }
  },
  computed: {
    customerOptions() {
      return [...new Map(this.risks.map(item => [item.customerId, { id: item.customerId, name: item.customerName }])).values()]
    },
    contractOptions() {
      return [...new Map(this.risks.filter(item => item.contractId && (!this.customerId || Number(item.customerId) === this.customerId)).map(item => [item.contractId, { id: item.contractId, name: item.contractName }])).values()]
    },
    highRiskCount() {
      return this.risks.filter(item => item.level === 'high').length
    },
    mediumRiskCount() {
      return this.risks.filter(item => item.level === 'medium').length
    },
    ownerCount() {
      return new Set(this.risks.map(item => item.ownerUserId).filter(Boolean)).size
    },
    filteredRisks() {
      const keyword = this.keyword.trim().toLowerCase()
      return this.risks.filter(item => {
        if (this.customerId && Number(item.customerId) !== this.customerId) return false
        if (this.contractId && Number(item.contractId) !== this.contractId) return false
        if (this.riskType && item.riskType !== this.riskType) {
          return false
        }
        if (!keyword) {
          return true
        }
        return [item.customerName, item.ownerUserName, item.contractName, item.title, item.reason].join(' ').toLowerCase().includes(keyword)
      })
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
    this.readRoute()
    this.loadData()
  },
  methods: {
    readRoute() {
      this.customerId = normalizeContractId(this.$route.query.customerId) || undefined
      this.contractId = normalizeContractId(this.$route.query.contractId) || undefined
      this.riskType = ['no_followup', 'contract_expiry', 'no_payment', 'payment_due', 'payment_overdue'].includes(this.$route.query.riskType) ? this.$route.query.riskType : ''
    },
    resetFilters() {
      this.keyword = ''
      this.customerId = undefined
      this.contractId = undefined
      this.riskType = ''
      if (Object.keys(this.$route.query).length) this.$router.replace({ path: this.$route.path, query: {} })
    },
    openContract(record) {
      const contractId = normalizeContractId(record.contractId)
      if (!contractId) return this.$message.warning('合同编号无效，请刷新后重试')
      this.$createAntdDrawer({
        stopPropagation: true,
        drawerProps: { title: '风险关联合同', width: '85%', maskClosable: true },
        content: { template: ContractDetail, props: { moduleMeta: this.moduleContexts.contract, contractId, initialTab: record.termKey ? 'terms' : 'reminders' }, on: { changed: () => this.loadData() } }
      })
    },
    startHandle(record) {
      this.handleTarget = record
      this.handleContent = ''
    },
    async saveHandle() {
      if (this.saving || !this.handleTarget) return
      const content = this.handleContent.trim()
      if (!content) return this.$message.warning('请填写处理内容')
      this.saving = true
      try {
        await contractAction(this.handleTarget.contractId, 'reminder-log', { taskId: this.handleTarget.taskId, content })
        this.handleTarget = null
        this.$message.success('处理记录已保存，实际业务条件解决后解除风险')
        await this.loadData()
      } finally {
        this.saving = false
      }
    },
    loadData() {
      this.loading = true
      return listCustomerRisks()
        .then(res => {
          this.risks = res.rows || []
        })
        .finally(() => {
          this.loading = false
        })
    },
    synchronize() {
      this.syncing = true
      syncCustomerRisks()
        .then(res => {
          this.$message.success(`检查完成，新增 ${res.data?.createdTodos || 0} 条待办`)
          this.loadData()
        })
        .finally(() => {
          this.syncing = false
        })
    },
    openCustomer(customerId) {
      this.$createAntdDrawer({
        stopPropagation: true,
        drawerProps: {
          title: '客户详情',
          width: '78%',
          maskClosable: true
        },
        content: {
          template: CustomerDetail,
          props: { moduleMeta: this.moduleContexts.customer, formId: customerId }
        }
      })
    }
  }
}
</script>

<style lang="less" scoped>
.risk-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;

  h2 {
    margin: 0;
    color: #17212b;
    font-weight: 600;
    font-size: 22px;
  }

  p {
    margin: 5px 0 0;
    color: #667085;
  }
}

.risk-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.risk-summary-item {
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e5e9ee;
  border-left: 3px solid #1769aa;
  border-radius: 5px;

  span {
    display: block;
    color: #7b8794;
    font-size: 12px;
  }

  strong {
    display: block;
    margin-top: 3px;
    color: #17212b;
    font-size: 23px;
  }
}

.risk-high {
  border-left-color: #c04a4a;
}

.risk-medium {
  border-left-color: #d58a2f;
}

.risk-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  margin-bottom: 14px;
  padding: 12px;
  background: #f8fafb;
  border: 1px solid #e8edf1;
  border-radius: 6px;
}

.customer-link {
  color: #1769aa;
  font-weight: 600;
}

@media (max-width: 800px) {
  .risk-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .risk-header {
    flex-direction: column;
  }

  .risk-filter :deep(.ant-input-search),
  .risk-filter :deep(.ant-select) {
    width: 100% !important;
  }
}
</style>
