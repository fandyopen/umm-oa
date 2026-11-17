<template>
  <a-spin :spinning="loading">
    <a-alert v-if="error" :message="error" type="error" show-icon />
    <template v-if="data.contract">
      <a-alert type="info" show-icon message="销售按合同净回款结算，不等待候选人入职；交付按入职或服务验收结算，共用一份合同提成预算。" class="tw-mb-4" />
      <a-alert v-if="data.legacy" type="warning" show-icon message="此合同已有原方式的招聘提成归属，继续在业绩明细核算，保留历史，避免重复计提。" class="tw-mb-4" />
      <a-alert v-if="!data.contract.effectiveVersion" type="info" show-icon message="合同签署生效后，可在这里绑定规则并确认销售归属。" class="tw-mb-4" />
      <a-descriptions bordered size="small" :column="2" class="tw-mb-4">
        <a-descriptions-item label="合同">{{ data.contract.name }}</a-descriptions-item>
        <a-descriptions-item label="生效金额">{{ money(data.contract.amount) }} 元</a-descriptions-item>
        <a-descriptions-item label="已确认净回款">{{ money(data.netReceived) }} 元</a-descriptions-item>
        <a-descriptions-item label="归属状态">{{ locked ? '已确认，规则与人员已冻结' : data.legacy ? '沿用原核算' : '待确认' }}</a-descriptions-item>
        <a-descriptions-item label="预计总提成池">{{ estimate ? money(estimate.pool) + ' 元' : '请先选择规则' }}</a-descriptions-item>
        <a-descriptions-item label="预计拓展 / 交付">{{ estimate ? money(estimate.development) + ' / ' + money(estimate.delivery) + ' 元' : '请先选择规则' }}</a-descriptions-item>
        <a-descriptions-item label="销售累计满足回款条件">{{ money(data.developmentEarned) }} 元</a-descriptions-item>
        <a-descriptions-item label="交付已分摊提成预算">{{ money(data.deliveryEarned) }} 元</a-descriptions-item>
      </a-descriptions>
      <p>预计金额以生效合同额试算；可结算金额以实际净回款及交付条件为准，审批通过后仍需纳入工资并实际发放。</p>
      <p v-if="data.canViewFinance"><a @click="showPerformance">查看此合同回款分摊与核算明细</a></p>
      <a-form-model v-if="!data.legacy && data.contract.effectiveVersion" layout="vertical">
        <a-form-model-item label="提成规则">
          <a-select v-if="!locked && data.canManage" v-model="form.ruleId" show-search option-filter-prop="children" placeholder="请选择首单或续单提成规则">
            <a-select-option v-for="r in availableRules" :key="r.id" :value="r.id">{{ r.name }} · {{ r.businessKind === 'first' ? '新客首单' : '老客续单' }} · 总率 {{ r.poolRate }}% · 拓展 {{ r.developmentShare }}% / 交付 {{ 100 - Number(r.developmentShare) }}%</a-select-option>
          </a-select>
          <span v-else>{{ rule ? rule.name : '尚未配置' }}</span>
        </a-form-model-item>
        <a-alert v-if="rule" class="tw-mb-4" type="info" :message="'销售：净回款 × ' + rule.poolRate + '% × ' + rule.developmentShare + '%；交付占提成池 ' + (100 - Number(rule.developmentShare)) + '%，入职首期释放 ' + rule.initialRelease + '%，服务验收后释放对应交付预算100%。'" />
        <a-form-model-item label="交付结算条件">
          <a-select v-model="form.settlementMode" :disabled="locked || !data.canManage">
            <a-select-option value="entry">候选人实际入职，按保证期分阶段释放</a-select-option>
            <a-select-option value="acceptance">服务验收，按验收成果分配交付预算</a-select-option>
          </a-select>
        </a-form-model-item>
        <a-form-model-item label="拓展销售归属（部门内份额合计100%）">
          <div v-for="(p, index) in form.participants" :key="index" class="participant">
            <a-select v-if="!locked && data.canManage" v-model="p.userId" show-search option-filter-prop="children" placeholder="请选择销售成员"><a-select-option v-for="u in data.people" :key="u.id" :value="u.id">{{ u.name }} · {{ u.deptName }}</a-select-option></a-select>
            <span v-else>{{ p.name }}</span>
            <a-input-number v-model="p.share" :disabled="locked || !data.canManage" :min="0" :max="100" :precision="4" /><span>%</span>
            <span v-if="estimate">预计 {{ money(estimate.development * Number(p.share || 0) / 100) }} 元</span>
            <a v-if="!locked && data.canManage" @click="form.participants.splice(index, 1)">移除</a>
          </div>
          <a-button v-if="!locked && data.canManage" @click="form.participants.push({ side: 'development', share: undefined, userId: undefined })">增加协作销售</a-button>
        </a-form-model-item>
        <a-form-model-item label="归属依据"><a-textarea v-model="form.reason" :disabled="locked || !data.canManage" :max-length="1000" /></a-form-model-item>
        <a-space>
          <a-button v-if="!locked && data.canManage" :loading="busy" @click="save(false)">保存归属草稿</a-button>
          <a-button v-if="!locked && data.canManage" type="primary" :loading="busy" @click="save(true)">保存并确认归属</a-button>
          <a-button icon="reload" @click="load">刷新</a-button>
        </a-space>
      </a-form-model>
      <template v-if="locked && form.settlementMode === 'acceptance'">
        <h3 class="tw-mt-6">服务验收记录</h3>
        <a-table :data-source="data.acceptances || []" row-key="id" :pagination="false" size="small">
          <a-table-column title="归属编号" data-index="id" /><a-table-column title="验收日期" data-index="basisDate" /><a-table-column title="验收凭据 / 记录" data-index="acceptanceReference" />
          <a-table-column title="交付人员"><template slot-scope="_, row">{{ row.participants.map(p => p.name + ' ' + p.share + '%').join('、') }}</template></a-table-column>
        </a-table>
        <a-button v-if="data.canManage" class="tw-mt-3" @click="openAcceptance">登记服务验收</a-button>
        <p class="tw-mt-3">登记验收后，到提成核算 → 业绩明细，将实际回款分摊至对应验收记录。同一笔回款分摊合计不得超过到账。</p>
      </template>
      <a-modal v-model="acceptanceVisible" title="确认服务验收" :confirm-loading="busy" @ok="accept">
        <a-form-model layout="vertical">
          <a-form-model-item label="验收日期"><a-date-picker v-model="acceptance.acceptanceDate" value-format="YYYY-MM-DD" /></a-form-model-item>
          <a-form-model-item label="合同收费条款"><a-select v-model="acceptance.termKey"><a-select-option v-for="t in data.contract.feeTerms" :key="t.key" :value="t.key">{{ t.title }}</a-select-option></a-select></a-form-model-item>
          <a-form-model-item label="验收凭据（如客户确认单编号、验收记录）"><a-input v-model="acceptance.acceptanceReference" :max-length="1000" /></a-form-model-item>
          <a-form-model-item label="交付人员及部门内份额（合计100%）">
            <div v-for="(p, index) in acceptance.participants" :key="index" class="participant">
              <a-select v-model="p.userId" show-search option-filter-prop="children"><a-select-option v-for="u in data.people" :key="u.id" :value="u.id">{{ u.name }}</a-select-option></a-select>
              <a-input-number v-model="p.share" :min="0" :max="100" :precision="4" /><span>%</span><a @click="acceptance.participants.splice(index, 1)">移除</a>
            </div>
            <a-button @click="acceptance.participants.push({ side: 'delivery' })">增加交付人员</a-button>
          </a-form-model-item>
          <a-form-model-item label="验收说明"><a-textarea v-model="acceptance.reason" :max-length="1000" /></a-form-model-item>
        </a-form-model>
      </a-modal>
    </template>
  </a-spin>
</template>
<script>
import { commission } from '/src/api/eos/crm/commission.js'
import { money } from '/src/views/enterprise/hr/hrShared.js'
const today = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' })
export default {
  name: 'ContractCommission',
  props: { contractId: { type: Number, required: true } },
  data: () => ({ data: {}, form: { participants: [], settlementMode: 'entry' }, acceptance: { participants: [] }, acceptanceVisible: false, loading: false, busy: false, error: '' }),
  computed: {
    locked() { return this.data.plan?.status === 'confirmed' },
    availableRules() { return (this.data.rules || []).filter(r => r.enabled || r.id === this.form.ruleId) },
    rule() { return this.locked || !this.data.canManage ? this.data.plan?.rule : (this.data.rules || []).find(r => r.id === this.form.ruleId) },
    estimate() {
      if (!this.rule) return null
      const pool = Math.round(Number(this.data.contract.amount) * Number(this.rule.poolRate)) / 100
      const development = Math.round(pool * Number(this.rule.developmentShare)) / 100
      return { pool, development, delivery: pool - development }
    }
  },
  watch: { contractId: { immediate: true, handler: 'load' } },
  methods: {
    money,
    showPerformance() { this.$emit('performance'); this.$router.push({ path: '/finance/commission', query: { contractId: String(this.contractId), tab: 'performance' } }) },
    async load() {
      const id = this.contractId
      this.loading = true; this.error = ''
      try {
        const { data } = await commission('/contracts/' + id)
        if (id !== this.contractId) return
        this.data = data
        this.form = data.plan ? { ...data.plan, participants: data.plan.participants.map(p => ({ ...p })) } : { settlementMode: 'entry', participants: [{ side: 'development', userId: data.people.some(p => p.id === data.contract.ownerUserId) ? data.contract.ownerUserId : undefined, share: 100 }], reason: '', ruleId: undefined }
      } catch (e) { this.error = e.message || '合同提成加载失败' } finally { this.loading = false }
    },
    async save(confirm) {
      if (this.busy) return
      this.busy = true
      try {
        const { data } = await commission('/contracts/' + this.contractId, 'post', this.form)
        this.data = data; this.form = { ...data.plan, participants: data.plan.participants.map(p => ({ ...p })) }
        if (confirm) await commission('/contracts/' + this.contractId + '/confirm', 'post', { revision: this.form.revision, reason: this.form.reason })
        await this.load(); this.$emit('changed'); this.$message.success(confirm ? '合同提成规则与销售归属已确认' : '归属草稿已保存')
      } finally { this.busy = false }
    },
    openAcceptance() { this.acceptance = { acceptanceDate: today(), termKey: undefined, acceptanceReference: '', reason: '', participants: [{ side: 'delivery', userId: undefined, share: 100 }] }; this.acceptanceVisible = true },
    async accept() {
      if (this.busy) return
      this.busy = true
      try { await commission('/contracts/' + this.contractId + '/acceptances', 'post', this.acceptance); this.acceptanceVisible = false; await this.load(); this.$emit('changed'); this.$message.success('服务验收已登记，请分摊实际回款') } finally { this.busy = false }
    }
  }
}
</script>
<style scoped>
.participant { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }
.participant .ant-select { min-width: 190px; flex: 1; }
</style>
