<template>
  <a-spin :spinning="loading">
    <div class="opportunity-hero">
      <div>
        <h2>{{ data.name || '-' }}</h2>
        <div class="hero-meta">
          <a-tag color="blue">{{ data.customerName || data.ownerUnit || '未关联客户' }}</a-tag>
          <a-tag :color="stageColor">{{ data.stage || data.businessState || '未设置阶段' }}</a-tag>
          <a-tag color="gold">{{ data.cooperationIntent || '意向待评估' }}</a-tag>
        </div>
      </div>
      <div class="hero-amount">
        <span>预计金额</span>
        <strong>{{ formatMoney(data.expectedAmount || data.amount) }}</strong>
      </div>
    </div>

    <a-descriptions bordered :column="2" size="small">
      <a-descriptions-item label="商机编号">{{ data.code || '-' }}</a-descriptions-item>
      <a-descriptions-item label="预计成交时间">{{ data.expectedCloseDate || '-' }}</a-descriptions-item>
      <a-descriptions-item label="负责人">{{ data.ownerUserName || data.sysUser?.nickName || '-' }}</a-descriptions-item>
      <a-descriptions-item label="商机状态">{{ data.statusLabel || data.status || '-' }}</a-descriptions-item>
      <a-descriptions-item label="潜在合作" :span="2">{{ data.potentialCooperation || '-' }}</a-descriptions-item>
      <a-descriptions-item label="客户需求" :span="2">{{ data.requirement || data.remark || '-' }}</a-descriptions-item>
      <a-descriptions-item label="备注" :span="2">{{ data.remark || '-' }}</a-descriptions-item>
    </a-descriptions>

    <div v-if="stages.length" class="stage-section">
      <h3>阶段记录</h3>
      <a-timeline>
        <a-timeline-item v-for="item in [...stages].reverse()" :key="item.id || item.recordTime" color="blue">
          <strong>{{ item.recordTime }}</strong>
          <a-tag>{{ item.state }}</a-tag>
          <div>{{ item.remark }}</div>
        </a-timeline-item>
      </a-timeline>
    </div>
  </a-spin>
</template>

<script>
import { getBusiness } from '/src/api/eos/business/business.js'

export default {
  name: 'BusinessDetail',
  props: {
    formId: { type: Number, required: true }
  },
  data() {
    return {
      loading: false,
      data: {},
      stages: []
    }
  },
  computed: {
    stageColor() {
      return {
        赢单: 'green',
        输单: 'red',
        商务谈判: 'orange',
        方案报价: 'purple',
        需求确认: 'blue'
      }[this.data.stage || this.data.businessState] || 'default'
    }
  },
  mounted() {
    this.loading = true
    getBusiness(this.formId)
      .then(res => {
        this.data = res.data || {}
        this.stages = this.data.sealBusinessStages || []
      })
      .finally(() => {
        this.loading = false
      })
  },
  methods: {
    formatMoney(value) {
      return `￥${Number(value || 0).toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      })}`
    }
  }
}
</script>

<style lang="less" scoped>
.opportunity-hero {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #edf0f3;

  h2 {
    margin: 0;
    color: #17212b;
    font-weight: 650;
    font-size: 22px;
  }
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 8px;
}

.hero-amount {
  min-width: 150px;
  text-align: right;

  span {
    display: block;
    color: #8a96a3;
    font-size: 12px;
  }

  strong {
    color: #1769aa;
    font-size: 22px;
  }
}

.stage-section {
  margin-top: 20px;

  h3 {
    margin-bottom: 12px;
    font-size: 16px;
  }
}

@media (max-width: 600px) {
  .opportunity-hero {
    flex-direction: column;
  }

  .hero-amount {
    text-align: left;
  }
}
</style>
