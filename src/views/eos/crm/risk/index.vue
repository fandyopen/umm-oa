<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-spin :spinning="loading">
      <div class="risk-header">
        <div>
          <h2>客户风险提醒</h2>
          <p>系统每天自动检查长期未跟进、合同到期和长期未回款，并生成负责人待办。</p>
        </div>
        <a-button type="primary" icon="sync" :loading="syncing" @click="synchronize">立即检查</a-button>
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
        <a-input-search v-model="keyword" placeholder="搜索客户或负责人" allow-clear style="width: 280px" />
        <a-select v-model="riskType" placeholder="全部风险类型" allow-clear style="width: 180px">
          <a-select-option value="no_followup">长期未跟进</a-select-option>
          <a-select-option value="contract_expiry">合同即将到期</a-select-option>
          <a-select-option value="no_payment">长期未回款</a-select-option>
        </a-select>
      </div>

      <a-table :columns="columns" :data-source="filteredRisks" row-key="taskId" :pagination="false" size="middle">
        <span slot="level" slot-scope="text">
          <a-tag :color="text === 'high' ? 'red' : 'orange'">
            {{ text === 'high' ? '高风险' : '中风险' }}
          </a-tag>
        </span>
        <span slot="customer" slot-scope="text, record">
          <a class="customer-link" @click="openCustomer(record.customerId)">{{ text }}</a>
        </span>
        <span slot="owner" slot-scope="text">{{ text || '未分配' }}</span>
        <span slot="action" slot-scope="text, record">
          <a-button type="link" @click="openCustomer(record.customerId)">查看客户</a-button>
        </span>
      </a-table>
      <a-empty v-if="!filteredRisks.length" description="当前没有客户风险" />
    </a-spin>
  </page-header-wrapper>
</template>

<script>
import { listCustomerRisks, syncCustomerRisks } from '/src/api/eos/crm/index.js'
import CustomerDetail from '../customer/modules/CustomerDetail.vue'

export default {
  name: 'CrmRisks',
  data() {
    return {
      loading: false,
      syncing: false,
      risks: [],
      keyword: '',
      riskType: '',
      columns: [
        { title: '风险等级', dataIndex: 'level', width: 100, scopedSlots: { customRender: 'level' } },
        { title: '客户', dataIndex: 'customerName', width: 220, scopedSlots: { customRender: 'customer' } },
        { title: '风险类型', dataIndex: 'title', width: 150 },
        { title: '风险原因', dataIndex: 'reason' },
        { title: '负责人', dataIndex: 'ownerUserName', width: 120, scopedSlots: { customRender: 'owner' } },
        { title: '操作', width: 100, scopedSlots: { customRender: 'action' } }
      ]
    }
  },
  computed: {
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
        if (this.riskType && item.riskType !== this.riskType) {
          return false
        }
        if (!keyword) {
          return true
        }
        return [item.customerName, item.ownerUserName, item.title, item.reason]
          .join(' ')
          .toLowerCase()
          .includes(keyword)
      })
    }
  },
  mounted() {
    this.loadData()
  },
  methods: {
    loadData() {
      this.loading = true
      listCustomerRisks()
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
          props: { formId: customerId }
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
