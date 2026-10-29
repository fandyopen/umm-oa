<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-spin :spinning="loading">
      <div class="analysis-header">
        <div>
          <h2>客户分析</h2>
          <p>查看客户贡献、合作活跃度、合作转化和商机规模。</p>
        </div>
        <a-button icon="reload" @click="loadData">刷新数据</a-button>
      </div>

      <div class="metric-grid">
        <div v-for="item in summary" :key="item.name" class="metric-card">
          <div class="metric-name">{{ item.name }}</div>
          <div class="metric-value">
            {{ formatMetric(item) }}
            <span>{{ item.unit }}</span>
          </div>
        </div>
      </div>

      <div class="distribution-grid">
        <section class="analysis-panel">
          <h3>合作状态分布</h3>
          <div v-for="item in statusDistribution" :key="item.name" class="distribution-row">
            <div class="distribution-label">
              <span>{{ item.name }}</span>
              <strong>{{ item.value }}</strong>
            </div>
            <a-progress :percent="percent(item.value, statusDistribution)" :show-info="false" size="small" />
          </div>
        </section>
        <section class="analysis-panel">
          <h3>客户来源分布</h3>
          <div v-for="item in sourceDistribution" :key="item.name" class="distribution-row">
            <div class="distribution-label">
              <span>{{ item.name }}</span>
              <strong>{{ item.value }}</strong>
            </div>
            <a-progress :percent="percent(item.value, sourceDistribution)" :show-info="false" size="small" stroke-color="#33a06f" />
          </div>
        </section>
        <section class="analysis-panel">
          <h3>行业分布</h3>
          <div v-for="item in industryDistribution" :key="item.name" class="distribution-row">
            <div class="distribution-label">
              <span>{{ item.name }}</span>
              <strong>{{ item.value }}</strong>
            </div>
            <a-progress :percent="percent(item.value, industryDistribution)" :show-info="false" size="small" stroke-color="#d58a2f" />
          </div>
        </section>
      </div>

      <section class="analysis-panel top-panel">
        <div class="panel-head">
          <h3>客户贡献排行</h3>
          <span>按合同金额与回款金额综合排序</span>
        </div>
        <a-table :columns="columns" :data-source="topCustomers" :pagination="false" row-key="id" size="middle">
          <span slot="contractAmount" slot-scope="text">{{ formatMoney(text) }}</span>
          <span slot="receivedAmount" slot-scope="text">{{ formatMoney(text) }}</span>
          <span slot="activityScore" slot-scope="text">
            <a-progress :percent="Number(text || 0)" size="small" :stroke-color="scoreColor(text)" />
          </span>
        </a-table>
      </section>
    </a-spin>
  </page-header-wrapper>
</template>

<script>
import { getCustomerAnalysis } from '/src/api/eos/crm/index.js'

export default {
  name: 'CrmAnalysis',
  data() {
    return {
      loading: false,
      summary: [],
      statusDistribution: [],
      sourceDistribution: [],
      industryDistribution: [],
      topCustomers: [],
      columns: [
        { title: '客户', dataIndex: 'name', width: 220 },
        { title: '行业', dataIndex: 'industry', width: 130 },
        { title: '合作状态', dataIndex: 'cooperationStatus', width: 120 },
        { title: '合作职位', dataIndex: 'cooperationPositions', width: 100, align: 'center' },
        { title: '推荐人数', dataIndex: 'referralCount', width: 100, align: 'center' },
        { title: '入职人数', dataIndex: 'hireCount', width: 100, align: 'center' },
        { title: '合同金额', dataIndex: 'contractAmount', width: 150, align: 'right', scopedSlots: { customRender: 'contractAmount' } },
        { title: '回款金额', dataIndex: 'receivedAmount', width: 150, align: 'right', scopedSlots: { customRender: 'receivedAmount' } },
        { title: '活跃度', dataIndex: 'activityScore', width: 170, scopedSlots: { customRender: 'activityScore' } }
      ]
    }
  },
  mounted() {
    this.loadData()
  },
  methods: {
    loadData() {
      this.loading = true
      getCustomerAnalysis()
        .then(res => {
          const data = res.data || {}
          this.summary = data.summary || []
          this.statusDistribution = data.statusDistribution || []
          this.sourceDistribution = data.sourceDistribution || []
          this.industryDistribution = data.industryDistribution || []
          this.topCustomers = data.topCustomers || []
        })
        .finally(() => {
          this.loading = false
        })
    },
    formatMetric(item) {
      if (['合同金额', '回款金额'].includes(item.name)) {
        return this.formatMoney(item.value)
      }
      return Number(item.value || 0).toLocaleString('zh-CN')
    },
    formatMoney(value) {
      return `￥${Number(value || 0).toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      })}`
    },
    percent(value, list) {
      const total = list.reduce((sum, item) => sum + Number(item.value || 0), 0)
      return total ? Math.round((Number(value || 0) / total) * 100) : 0
    },
    scoreColor(value) {
      const score = Number(value || 0)
      return score >= 70 ? '#237a57' : score >= 40 ? '#d58a2f' : '#c04a4a'
    }
  }
}
</script>

<style lang="less" scoped>
.analysis-header {
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

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.metric-card {
  min-width: 0;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e5e9ee;
  border-radius: 6px;
}

.metric-name {
  color: #7b8794;
  font-size: 12px;
}

.metric-value {
  margin-top: 5px;
  overflow: hidden;
  color: #17212b;
  font-weight: 650;
  font-size: 23px;
  text-overflow: ellipsis;
  white-space: nowrap;

  span {
    margin-left: 3px;
    color: #8a96a3;
    font-weight: 400;
    font-size: 12px;
  }
}

.distribution-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.analysis-panel {
  min-width: 0;
  padding: 16px;
  background: #fff;
  border: 1px solid #e5e9ee;
  border-radius: 6px;

  h3 {
    margin: 0 0 14px;
    color: #17212b;
    font-weight: 600;
    font-size: 16px;
  }
}

.distribution-row + .distribution-row {
  margin-top: 11px;
}

.distribution-label {
  display: flex;
  gap: 10px;
  justify-content: space-between;
  margin-bottom: 3px;
  color: #475467;
  font-size: 12px;
}

.panel-head {
  display: flex;
  gap: 10px;
  align-items: baseline;
  justify-content: space-between;

  span {
    color: #8a96a3;
    font-size: 12px;
  }
}

.top-panel {
  overflow-x: auto;
}

@media (max-width: 1100px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .distribution-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .analysis-header {
    flex-direction: column;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
