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
    <div v-if="canAdvance" class="tw-mt-[18px]">
      <a-button type="primary" icon="arrow-right" :loading="!!moduleActionBusy.advanceStage" @click="advanceStage">推进商机阶段</a-button>
      <span class="tw-ml-3 tw-text-[#667085]">首次成功推进后，跟进待办自动完成</span>
    </div>
    <div v-if="data.customerId && canCreateContract" class="tw-mt-[18px]">
      <a-button type="primary" icon="file-text" @click="createContract" :loading="!!moduleActionBusy.createContract">从此商机创建合同</a-button
      ><span class="tw-ml-3 tw-text-[#667085]">自动关联客户与商机，创建后可继续完善并提交审批</span>
    </div>

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
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { crmModules } from '/src/views/eos/crm/moduleContexts.js'
import { getBusiness } from '/src/api/eos/business/business.js'
import ContractForm from '../../crm/contract/modules/ContractForm.vue'
import BusinessStageForm from './BusinessStageForm.vue'
import { normalizeContractId } from '/src/api/eos/crm/contracts.js'

const ContractDetail = () => import('../../crm/contract/modules/ContractDetail.vue')

export default {
  mixins: [
    withModuleContexts(
      { opportunity: crmModules.opportunity, contract: crmModules.contract },
      {
        createContract: { module: 'contract', action: 'add', label: '商机转合同' },
        advanceStage: { module: 'opportunity', action: 'track', label: '推进商机阶段' }
      }
    )
  ],
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
    canAdvance() {
      return ['初步接洽', '需求确认', '方案报价', '商务谈判'].includes(this.data.stage || this.data.businessState)
        && !['已赢单', '已输单'].includes(this.data.statusLabel) && this.showModuleAction('opportunity', 'track')
    },
    canCreateContract() {
      return this.showModuleAction('contract', 'add')
    },
    stageColor() {
      return {
        赢单: 'green',
        输单: 'red',
        商务谈判: 'orange',
        方案报价: 'purple',
        需求确认: 'blue'
      }[this.data.stage || this.data.businessState]
    }
  },
  mounted() {
    this.loadBusiness()
  },
  methods: {
    async loadBusiness() {
      this.loading = true
      try {
        const res = await getBusiness(this.formId)
        this.data = res.data || {}
        this.stages = this.data.sealBusinessStages || []
      } catch (error) {
        this.$message.error(error.message || '商机详情加载失败')
      } finally {
        this.loading = false
      }
    },
    advanceStage() {
      this.$createAntdModal({
        stopPropagation: true,
        modalProps: { title: '推进商机阶段', width: 880, centered: true, maskClosable: false, footer: null },
        content: {
          template: BusinessStageForm,
          props: { formId: this.formId },
          on: { saveSuccess: () => { this.loadBusiness(); this.$emit('changed') } }
        }
      })
    },
    createContract() {
      this.$createAntdModal({
        modalProps: { title: '商机转合同', width: 920, maskClosable: false, footer: null },
        onOk: ({ payload }) => {
          const contractId = normalizeContractId(payload?.id)
          if (!contractId) {
            this.$message.error('创建结果缺少有效合同编号，请刷新合同列表核对')
            return false
          }
          this.$createAntdDrawer({
            stopPropagation: true,
            drawerProps: { title: '合同详情', width: '85%', maskClosable: true },
            content: { template: ContractDetail, props: { moduleMeta: this.moduleContexts.contract, contractId } }
          })
        },
        content: {
          template: ContractForm,
          props: { initial: { customerId: Number(this.data.customerId), customerName: this.data.customerName, opportunityId: this.formId, name: this.data.name, amount: Number(this.data.expectedAmount || this.data.amount || 0) } }
        }
      })
    },
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
