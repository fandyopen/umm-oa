<template>
  <div class="commission-workspace">
    <div class="commission-heading">
      <div><h2>提成核算</h2><p>实际净回款形成提成池，拓展与交付分别分配，入职和保证期分阶段释放。</p></div>
      <a-space><a-button icon="book" @click="$router.push('/system/docs?tab=commission-rules')">核算规则与流程</a-button><a-button icon="reload" :loading="loading" @click="load">刷新</a-button></a-space>
    </div>
    <a-alert type="info" show-icon class="tw-mb-4" message="先配置公司规则，再确认业绩归属、分摊回款、生成月度核算。提成率及两部门比例由公司填写；系统不预设制度比例。" />
    <a-tabs v-model="tab">
      <a-tab-pane key="rules" tab="提成规则">
        <a-button v-if="allowed('rule')" type="primary" icon="plus" @click="newRule">新增规则版本</a-button>
        <a-table :data-source="options.rules || []" row-key="id" :pagination="{ pageSize: 10 }" class="tw-mt-4" :scroll="{ x: 1000 }">
          <a-table-column title="版本" data-index="id" /><a-table-column title="规则名称" data-index="name" />
          <a-table-column title="业务类型"><template slot-scope="_, r">{{ kindName(r.businessKind) }}</template></a-table-column>
          <a-table-column title="生效日" data-index="effectiveDate" />
          <a-table-column title="总提成率"><template slot-scope="_, r">{{ r.poolRate }}%</template></a-table-column>
          <a-table-column title="拓展 / 交付"><template slot-scope="_, r">{{ r.developmentShare }}% / {{ 100 - Number(r.developmentShare) }}%</template></a-table-column>
          <a-table-column title="首期 / 保证期后"><template slot-scope="_, r">{{ r.initialRelease }}% / {{ 100 - Number(r.initialRelease) }}%</template></a-table-column>
          <a-table-column title="状态"><template slot-scope="_, r">{{ r.enabled ? '可选用' : '已停用' }}</template></a-table-column>
          <a-table-column title="操作"><template slot-scope="_, r"><a-button v-if="allowed('rule')" type="link" @click="toggleRule(r)">{{ r.enabled ? '停用' : '启用' }}</a-button></template></a-table-column>
        </a-table>
        <p class="commission-note">规则每次新增为独立版本；停用只阻止新的选用，不改写已冻结业绩。第一版按实际到账金额计提，不自动扣税或成本。</p>
      </a-tab-pane>
      <a-tab-pane key="performance" tab="业绩明细">
        <div class="commission-toolbar">
          <a-input-search v-model="keyword" allow-clear placeholder="搜索客户、职位、候选人" class="tw-w-[280px]" />
          <a-button v-if="$route.query.customerId || $route.query.applicationId" @click="clearLocation">查看全部</a-button>
        </div>
        <h3>招聘成果与业绩归属</h3>
        <a-table :data-source="applicationRows" row-key="id" :pagination="{ pageSize: 10 }" size="small" :scroll="{ x: 1050 }">
          <a-table-column title="客户" data-index="customerName" /><a-table-column title="职位" data-index="jobTitle" /><a-table-column title="候选人" data-index="talentName" />
          <a-table-column title="实际入职" data-index="actualEntryDate" /><a-table-column title="保证期"><template slot-scope="_, r">{{ guaranteeName(r.guaranteeStatus) }}</template></a-table-column>
          <a-table-column title="归属"><template slot-scope="_, r">{{ r.attributionId ? '已建档，点击核对' : '待维护' }}</template></a-table-column>
          <a-table-column title="操作"><template slot-scope="_, r"><a-button type="link" @click="editAttribution(r.id)">业绩归属</a-button></template></a-table-column>
        </a-table>
        <h3 class="tw-mt-6">实际回款分摊</h3>
        <a-alert type="info" show-icon message="仅展示合同实际到账。必须分摊到相同合同、收费条款下已确认的招聘成果；退款和冲销按原到账分摊比例自动扣减。" />
        <a-table :data-source="receiptRows" row-key="id" :pagination="{ pageSize: 8 }" size="small" class="tw-mt-3">
          <a-table-column title="到账编号" data-index="id" /><a-table-column title="客户" data-index="customerName" /><a-table-column title="合同" data-index="contractName" />
          <a-table-column title="到账金额"><template slot-scope="_, r">{{ money(r.amount) }}</template></a-table-column>
          <a-table-column title="已分摊"><template slot-scope="_, r">{{ money(r.allocatedAmount) }}</template></a-table-column>
          <a-table-column title="待分摊"><template slot-scope="_, r">{{ money(Number(r.amount) - Number(r.allocatedAmount)) }}</template></a-table-column>
          <a-table-column title="操作"><template slot-scope="_, r"><a-button type="link" @click="editAllocation(r)">查看 / 分摊</a-button></template></a-table-column>
        </a-table>
        <h3 class="tw-mt-6">累计提成与本次可核算差额</h3><a-alert v-if="calculationError" type="error" show-icon :message="calculationError" class="tw-mb-4" />
        <a-table :data-source="performanceRows" :row-key="r => r.attributionId + ':' + r.userId + ':' + r.side" :pagination="{ pageSize: 10 }" size="small" :scroll="{ x: 1550 }">
          <a-table-column title="客户 / 候选人" :width="200"><template slot-scope="_, r">{{ r.customerName }}<small>{{ r.jobTitle }} · {{ r.talentName }}</small></template></a-table-column>
          <a-table-column title="人员" data-index="name" /><a-table-column title="贡献"><template slot-scope="_, r">{{ sideName(r.side) }}</template></a-table-column>
          <a-table-column title="分摊净回款"><template slot-scope="_, r">{{ money(r.netAllocated) }}</template></a-table-column>
          <a-table-column title="累计提成"><template slot-scope="_, r">{{ money(r.totalCommission) }}</template></a-table-column>
          <a-table-column title="条件已满足"><template slot-scope="_, r">{{ money(r.eligible) }}</template></a-table-column>
          <a-table-column title="已核算 / 占用"><template slot-scope="_, r">{{ money(r.reserved) }}</template></a-table-column>
          <a-table-column title="本次差额"><template slot-scope="_, r">{{ money(Number(r.eligible) - Number(r.reserved)) }}</template></a-table-column>
          <a-table-column title="暂缓"><template slot-scope="_, r">{{ money(r.held) }}<small>{{ r.holdReason }}</small></template></a-table-column>
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="batches" tab="核算批次">
        <a-space><a-button v-if="allowed('generate')" type="primary" @click="openGenerate()">生成月度核算</a-button><span>草稿占用可核算额度；先处理当前草稿或审批，再生成下一批。</span></a-space>
        <a-table :data-source="batches" row-key="id" :pagination="{ pageSize: 10 }" class="tw-mt-4">
          <a-table-column title="编号" data-index="id" /><a-table-column title="月份" data-index="period" /><a-table-column title="截止日" data-index="cutoffDate" />
          <a-table-column title="本期差额"><template slot-scope="_, r">{{ money(r.amount) }}</template></a-table-column>
          <a-table-column title="状态"><template slot-scope="_, r"><a-tag>{{ statusName(r.status) }}</a-tag></template></a-table-column>
          <a-table-column title="工资批次" data-index="payrollId" /><a-table-column title="操作"><template slot-scope="_, r"><a-button type="link" @click="openBatch(r.id)">详情</a-button></template></a-table-column>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <a-modal v-model="ruleVisible" title="新增提成规则版本" :confirm-loading="busy" width="680px" @ok="saveRule">
      <a-form-model layout="vertical">
        <a-form-model-item label="规则名称"><a-input v-model="ruleForm.name" :max-length="150" /></a-form-model-item>
        <a-form-model-item label="适用业务"><a-select v-model="ruleForm.businessKind"><a-select-option value="first">新客首单</a-select-option><a-select-option value="repeat">老客续单</a-select-option></a-select></a-form-model-item>
        <a-form-model-item label="生效日期"><a-date-picker v-model="ruleForm.effectiveDate" value-format="YYYY-MM-DD" /></a-form-model-item>
        <a-form-model-item label="总提成率（占分摊净回款的百分比）"><a-input-number v-model="ruleForm.poolRate" :min="0" :max="100" :precision="4" /></a-form-model-item>
        <a-form-model-item label="拓展部门占提成池比例（%，交付部门为剩余比例）"><a-input-number v-model="ruleForm.developmentShare" :min="0" :max="100" :precision="4" /></a-form-model-item>
        <a-form-model-item label="入职且到账后首期释放比例（%，剩余部分保证期完成后释放）"><a-input-number v-model="ruleForm.initialRelease" :min="0" :max="100" :precision="4" /></a-form-model-item>
      </a-form-model>
    </a-modal>
    <a-modal v-model="attributionVisible" title="招聘成果业绩归属" width="900px" :footer="null">
      <template v-if="currentApplication">
        <a-descriptions :column="2"><a-descriptions-item label="客户">{{ currentApplication.customerName }}</a-descriptions-item><a-descriptions-item label="候选人">{{ currentApplication.talentName }}</a-descriptions-item><a-descriptions-item label="职位">{{ currentApplication.jobTitle }}</a-descriptions-item><a-descriptions-item label="入职日期">{{ currentApplication.actualEntryDate || '尚未确认' }}</a-descriptions-item></a-descriptions>
        <a-alert type="info" show-icon class="tw-mb-4" message="默认人员仅供核对，请明确本次开发、维护及交付贡献。部门内比例分别合计100%；确认后冻结，不随负责人或规则变化。" />
        <a-form-model layout="vertical">
          <a-form-model-item label="业务类型"><a-select v-model="attributionForm.businessKind" :disabled="attributionLocked" @change="attributionForm.ruleId = undefined"><a-select-option value="first">新客首单</a-select-option><a-select-option value="repeat">老客续单</a-select-option></a-select></a-form-model-item>
          <a-form-model-item label="提成规则"><a-select v-model="attributionForm.ruleId" :disabled="attributionLocked" show-search option-filter-prop="children"><a-select-option v-for="r in attributionRules" :key="r.id" :value="r.id">{{ r.name }} · 版本 #{{ r.id }} · {{ r.effectiveDate }}</a-select-option></a-select></a-form-model-item>
          <div v-for="(p, index) in attributionForm.participants" :key="index" class="commission-participant">
            <a-select v-model="p.side" :disabled="attributionLocked" class="tw-w-[150px]"><a-select-option value="development">客户拓展贡献</a-select-option><a-select-option value="delivery">招聘交付贡献</a-select-option></a-select>
            <a-select v-model="p.userId" :disabled="attributionLocked" show-search option-filter-prop="children" class="tw-w-[260px]"><a-select-option v-for="u in options.people || []" :key="u.id" :value="u.id">{{ u.name }} · {{ u.deptName }}</a-select-option></a-select>
            <a-input-number v-model="p.share" :disabled="attributionLocked" :min="0" :max="100" :precision="4" /><span>%</span>
            <a-button v-if="!attributionLocked" type="link" @click="attributionForm.participants.splice(index, 1)">移除</a-button>
          </div>
          <a-button v-if="!attributionLocked && allowed('attribute')" class="tw-mt-2" @click="attributionForm.participants.push({ side: 'delivery', userId: undefined, share: undefined })">增加协作人员</a-button>
          <a-form-model-item label="归属依据 / 更正原因"><a-textarea v-model="attributionForm.reason" :max-length="1000" /></a-form-model-item>
        </a-form-model>
        <a-space>
          <a-button v-if="!attributionLocked && allowed('attribute')" type="primary" :loading="busy" @click="saveAttribution">保存归属草稿</a-button>
          <a-button v-if="!attributionLocked && attributionForm.id && allowed('attribute')" :loading="busy" @click="confirmAttribution(true)">保存并确认归属</a-button>
          <a-button v-if="attributionLocked && allowed('attribute')" :loading="busy" @click="confirmAttribution(false)">撤销确认</a-button>
          <a-button @click="attributionVisible = false">关闭</a-button>
        </a-space>
      </template>
    </a-modal>
    <a-modal v-model="allocationVisible" title="实际回款业绩分摊" width="850px" :footer="null">
      <template v-if="allocationForm.receipt">
        <p>到账 #{{ allocationForm.receipt.id }} · 原到账 {{ money(allocationForm.receipt.amount) }} 元</p>
        <a-alert type="warning" show-icon message="重新分摊会影响下一次差额核算，历史批准金额保持不变。免费替换需在原交付与替换交付之间转移原有金额，不能重复分配。" class="tw-mb-4" />
        <div v-for="(r, index) in allocationForm.rows" :key="index" class="commission-participant">
          <a-select v-model="r.attributionId" show-search option-filter-prop="children" class="tw-w-[440px]"><a-select-option v-for="a in allocationCandidates" :key="a.attributionId" :value="a.attributionId">{{ a.customerName }} · {{ a.jobTitle }} · {{ a.talentName }} · 归属 #{{ a.attributionId }}</a-select-option></a-select>
          <a-input-number v-model="r.amount" :min="0" :precision="2" /><a-button type="link" @click="allocationForm.rows.splice(index, 1)">移除</a-button>
        </div>
        <a-button @click="allocationForm.rows.push({ attributionId: undefined, amount: undefined })">增加分摊</a-button>
        <p class="tw-mt-3">本次合计：{{ money(allocationTotal) }} 元</p>
        <a-form-model layout="vertical"><a-form-model-item label="分摊生效日期"><a-date-picker v-model="allocationForm.allocationDate" value-format="YYYY-MM-DD" /></a-form-model-item><a-form-model-item label="分摊 / 调整依据"><a-textarea v-model="allocationForm.reason" :max-length="1000" /></a-form-model-item></a-form-model>
        <a-space><a-button v-if="allowed('allocate')" type="primary" :loading="busy" @click="saveAllocation">保存分摊</a-button><a-button @click="allocationVisible = false">关闭</a-button></a-space>
      </template>
    </a-modal>
    <a-modal v-model="generateVisible" title="生成 / 重算提成核算" :confirm-loading="busy" @ok="generate">
      <a-form-model layout="vertical"><a-form-model-item label="核算月份"><a-month-picker v-model="generateForm.period" value-format="YYYY-MM" /></a-form-model-item><a-form-model-item label="本月截止日期"><a-date-picker v-model="generateForm.cutoffDate" value-format="YYYY-MM-DD" /></a-form-model-item><a-form-model-item label="核算依据"><a-textarea v-model="generateForm.reason" :max-length="1000" /></a-form-model-item></a-form-model>
    </a-modal>
    <a-drawer :visible="!!detail" title="提成核算明细" width="90%" @close="detail = null">
      <template v-if="detail">
        <h3>{{ detail.period }} · 核算 #{{ detail.id }} · {{ statusName(detail.status) }}</h3>
        <a-space class="tw-mb-4">
          <a-button v-if="['draft', 'rejected'].includes(detail.status) && allowed('generate')" @click="openGenerate(detail)">重新核算</a-button>
          <a-button v-if="['draft', 'rejected'].includes(detail.status) && allowed('submit')" type="primary" :loading="busy" @click="submitBatch">提交核算</a-button>
          <a-button v-if="['draft', 'rejected'].includes(detail.status) && allowed('void')" @click="voidBatch">作废</a-button>
          <a-button v-if="detail.status === 'approved' && allowed('payroll')" :loading="busy" @click="toPayroll">纳入工资</a-button>
          <a-button v-if="detail.payrollId" @click="openPayroll">查看工资批次 #{{ detail.payrollId }}</a-button>
          <a-button v-if="allowed('export')" icon="download" @click="exportDetail">导出明细</a-button>
        </a-space>
        <a-alert type="info" show-icon message="正数为本次新增提成，负数为退款、冲销或重新分摊产生的扣回。纳入工资仍须核对个税、审批及实际发放，工资未结清时不会标记提成已结清。" />
        <a-table :data-source="detail.lines" row-key="id" :pagination="{ pageSize: 15 }" size="small" class="tw-mt-4" :scroll="{ x: 1500 }">
          <a-table-column title="人员" data-index="employeeName" /><a-table-column title="贡献"><template slot-scope="_, r">{{ sideName(r.side) }}</template></a-table-column>
          <a-table-column title="业务来源"><template slot-scope="_, r">{{ r.snapshot.customerName }}<small>{{ r.snapshot.jobTitle }} · {{ r.snapshot.talentName }}</small></template></a-table-column>
          <a-table-column title="分摊净回款"><template slot-scope="_, r">{{ money(r.snapshot.netAllocated) }}</template></a-table-column>
          <a-table-column title="规则与比例"><template slot-scope="_, r">{{ r.snapshot.rule.name }}<small>总率 {{ r.snapshot.rule.poolRate }}% · 部门 {{ r.side === 'development' ? r.snapshot.rule.developmentShare : 100 - Number(r.snapshot.rule.developmentShare) }}% · 个人 {{ r.snapshot.share }}%</small></template></a-table-column>
          <a-table-column title="累计满足条件"><template slot-scope="_, r">{{ money(r.snapshot.eligible) }}</template></a-table-column>
          <a-table-column title="以前核算"><template slot-scope="_, r">{{ money(r.snapshot.reserved) }}</template></a-table-column>
          <a-table-column title="本次差额"><template slot-scope="_, r">{{ money(r.amount) }}</template></a-table-column>
          <a-table-column title="发放"><template slot-scope="_, r">{{ { unlinked: '待纳入工资', unpaid: '工资待结清', paid: '已结清' }[r.paymentStatus] }}</template></a-table-column>
        </a-table>
        <h3 class="tw-mt-5">操作记录</h3>
        <a-timeline><a-timeline-item v-for="h in detail.history" :key="h.id">{{ h.action }} · 操作人 #{{ h.operatorId }} · {{ h.createTime }}</a-timeline-item></a-timeline>
      </template>
    </a-drawer>
  </div>
</template>
<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { commission } from '/src/api/eos/crm/commission.js'
import { money } from '/src/views/enterprise/hr/hrShared.js'
import * as XLSX from 'xlsx'

const today = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' })
const emptyAttribution = () => ({ participants: [], reason: '', businessKind: 'first' })
export default {
  name: 'FinanceCommission',
  mixins: [withModuleContexts({ commission: { component: 'eos/crm/finance/commission', label: '提成核算' } })],
  data: () => ({
    calculationError: '', tab: 'performance', loading: false, busy: false, keyword: '', options: {}, overview: [], batches: [], detail: null,
    ruleVisible: false, ruleForm: {}, attributionVisible: false, attributionForm: emptyAttribution(), currentApplication: null,
    allocationVisible: false, allocationForm: { rows: [] }, generateVisible: false, generateForm: {}
  }),
  computed: {
    applicationRows() {
      return (this.options.applications || []).filter(a => (!this.$route.query.applicationId || String(a.id) === String(this.$route.query.applicationId)) && (!this.$route.query.customerId || String(a.customerId) === String(this.$route.query.customerId)) && this.matches(a))
    },
    receiptRows() {
      return (this.options.receipts || []).filter(a => (!this.$route.query.customerId || String(a.customerId) === String(this.$route.query.customerId)) && this.matches(a))
    },
    performanceRows() {
      const ids = new Set(this.applicationRows.map(a => a.id))
      return this.overview.filter(a => ids.has(a.applicationId)).flatMap(a => a.participants.map(p => ({ ...p, attributionId: a.id })))
    },
    attributionLocked() { return this.attributionForm.status === 'confirmed' || !this.allowed('attribute') },
    attributionRules() {
      return (this.options.rules || []).filter(r => r.businessKind === this.attributionForm.businessKind && (r.enabled || r.id === this.attributionForm.ruleId || r.id === this.currentApplication?.originalRuleId))
    },
    allocationCandidates() {
      return (this.options.applications || []).filter(a => a.attributionStatus === 'confirmed' && String(a.guaranteeTermKey || '') === String(this.allocationForm.receipt?.termKey || '') && Number(a.contractId) === Number(this.allocationForm.receipt?.contractId))
    },
    allocationTotal() { return this.allocationForm.rows.reduce((sum, r) => sum + Number(r.amount || 0), 0) }
  },
  async mounted() {
    await this.load()
    if (this.$route.query.applicationId) await this.editAttribution(Number(this.$route.query.applicationId))
    if (this.$route.query.receiptId) {
      const receipt = (this.options.receipts || []).find(r => String(r.id) === String(this.$route.query.receiptId))
      if (receipt) await this.editAllocation(receipt)
    }
  },
  methods: {
    money,
    allowed(action) { return this.showModuleAction('commission', action) },
    kindName(v) { return v === 'first' ? '新客首单' : '老客续单' },
    sideName(v) { return v === 'development' ? '客户拓展' : '招聘交付' },
    guaranteeName(v) { return { active: '保证期中', completed: '已完成', refund: '退费事项', replacement: '替换中', exit: '已退出' }[v] || '未确认入职' },
    statusName(v) { return { draft: '草稿', approving: '审批中', approved: '已批准待纳入工资', rejected: '已退回', linked: '已纳入工资', void: '已作废' }[v] || v },
    matches(a) { return !this.keyword || [a.customerName, a.jobTitle, a.talentName, a.contractName].some(v => String(v || '').toLowerCase().includes(this.keyword.trim().toLowerCase())) },
    async load() {
      this.loading = true
      try {
        const results = await Promise.allSettled([commission('/options'), commission('/overview'), commission('/batches')])
        if (results[0].status === 'fulfilled') this.options = results[0].value.data || {}
        this.overview = results[1].status === 'fulfilled' ? results[1].value.data || [] : []
        this.calculationError = results[1].status === 'rejected' ? results[1].reason?.message || '暂无法计算，请核对回款、退款和入职依据' : ''
        if (results[2].status === 'fulfilled') this.batches = results[2].value.data || []
      } finally { this.loading = false }
    },
    clearLocation() { this.$router.replace({ path: this.$route.path, query: {} }) },
    async run(fn) {
      if (this.busy) return
      this.busy = true
      try { return await fn() } finally { this.busy = false }
    },
    newRule() { this.ruleForm = { name: '', businessKind: 'first', effectiveDate: today(), poolRate: undefined, developmentShare: undefined, initialRelease: undefined }; this.ruleVisible = true },
    async saveRule() { await this.run(async () => { await commission('/rules', 'post', this.ruleForm); this.ruleVisible = false; await this.load(); this.$message.success('规则版本已新增') }) },
    async toggleRule(r) { await this.run(async () => { await commission('/rules/' + r.id + '/enabled', 'post', { enabled: !r.enabled }); await this.load() }) },
    async editAttribution(id) {
      const data = (await commission('/applications/' + id)).data
      this.currentApplication = data.application
      this.attributionForm = data.attribution ? JSON.parse(JSON.stringify(data.attribution)) : {
        ...emptyAttribution(), businessKind: data.application.originalBusinessKind || 'first', ruleId: data.application.originalRuleId || undefined, participants: [
          { side: 'development', userId: data.application.developmentUserId || undefined, share: 100 },
          { side: 'delivery', userId: data.application.deliveryUserId || undefined, share: 100 }
        ]
      }
      this.attributionVisible = true
    },
    async saveAttribution() {
      await this.run(async () => {
        const data = (await commission('/applications/' + this.currentApplication.id, 'post', this.attributionForm)).data
        this.attributionForm = data.attribution; await this.load(); this.$message.success('归属草稿已保存，请核对后确认')
      })
    },
    async confirmAttribution(confirm) {
      await this.run(async () => {
        if (confirm) this.attributionForm = (await commission('/applications/' + this.currentApplication.id, 'post', this.attributionForm)).data.attribution
        await commission('/attributions/' + this.attributionForm.id + (confirm ? '/confirm' : '/reopen'), 'post', { revision: this.attributionForm.revision, reason: this.attributionForm.reason })
        await this.editAttribution(this.currentApplication.id); await this.load()
      })
    },
    async editAllocation(r) {
      this.allocationForm = { ...(await commission('/receipts/' + r.id + '/allocation')).data, allocationDate: today(), reason: '' }
      this.allocationVisible = true
    },
    async saveAllocation() { await this.run(async () => { await commission('/receipts/' + this.allocationForm.receipt.id + '/allocation', 'post', this.allocationForm); this.allocationVisible = false; await this.load(); this.$message.success('回款分摊已保存') }) },
    openGenerate(row) { this.generateForm = row ? { id: row.id, revision: row.revision, period: row.period, cutoffDate: String(row.cutoffDate).slice(0, 10), reason: '' } : { period: today().slice(0, 7), cutoffDate: today(), reason: '' }; this.generateVisible = true },
    async generate() {
      await this.run(async () => {
        this.detail = (await commission('/batches' + (this.generateForm.id ? '/' + this.generateForm.id + '/recalculate' : ''), 'post', this.generateForm)).data
        this.generateVisible = false; this.tab = 'batches'; await this.load()
      })
    },
    async openBatch(id) { this.detail = (await commission('/batches/' + id)).data },
    async submitBatch() {
      await this.run(async () => {
        const result = await this.submitConfiguredAction('submit', { batchId: this.detail.id, revision: this.detail.revision, title: '提成核算：' + this.detail.period }, '提交提成核算')
        if (!result) return
        this.$message.success(result.data.status === 'pendingApproval' ? '已进入审批，请在待办办理' : '核算已确认')
        await this.openBatch(this.detail.id); await this.load()
      })
    },
    voidBatch() {
      const detail = this.detail
      this.$confirm({ title: '作废当前核算？', content: '作废后释放核算占用；可重新生成。', onOk: () => this.run(async () => { await commission('/batches/' + detail.id + '/void', 'post', { revision: detail.revision, reason: '核对后作废并释放占用' }); await this.openBatch(detail.id); await this.load() }) })
    },
    async toPayroll() {
      await this.run(async () => {
        const result = (await commission('/batches/' + this.detail.id + '/payroll', 'post', { revision: this.detail.revision })).data
        this.$message.success('已生成工资批次 #' + result.payrollId + '，请继续核对个税并提交工资审批')
        await this.openBatch(this.detail.id); await this.load()
      })
    },
    openPayroll() { this.$router.push({ path: '/hr/payroll', query: { payrollId: String(this.detail.payrollId) } }) },
    async exportDetail() {
      if (!(await this.checkModuleAction({ module: 'commission', action: 'export', label: '导出提成明细' }))) return
      const rows = this.detail.lines.map(r => ({
        核算编号: this.detail.id, 月份: this.detail.period, 员工: r.employeeName, 贡献: this.sideName(r.side),
        客户: r.snapshot.customerName, 职位: r.snapshot.jobTitle, 候选人: r.snapshot.talentName,
        净回款: r.snapshot.netAllocated, 规则: r.snapshot.rule.name, 总提成率: r.snapshot.rule.poolRate,
        个人分配比例: r.snapshot.share, 累计可结算: r.snapshot.eligible, 以前核算: r.snapshot.reserved, 本次差额: r.amount, 发放状态: r.paymentStatus
      }))
      const book = XLSX.utils.book_new(); XLSX.utils.book_append_sheet(book, XLSX.utils.json_to_sheet(rows), '提成明细'); XLSX.writeFile(book, '提成核算-' + this.detail.period + '.xlsx')
    }
  }
}
</script>
<style scoped>
.commission-heading { display:flex;justify-content:space-between;align-items:center;gap:16px;margin-bottom:18px }
.commission-heading h2 { margin:0 0 6px }.commission-heading p,.commission-note { color:#718096 }
.commission-toolbar,.commission-participant { display:flex;gap:10px;align-items:center;margin-bottom:12px;flex-wrap:wrap }
small { display:block;color:#8893a5;font-size:12px }
</style>
