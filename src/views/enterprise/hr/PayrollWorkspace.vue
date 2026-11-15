<template>
  <div>
    <div class="hr-heading">
      <div>
        <h2>{{ financeMode ? '工资发放与对账' : '工资核算与审批' }}</h2>
        <p>{{ financeMode ? '仅展示已批准工资，逐人登记实际付款、失败重试与差额扣回。' : '自动引用员工档案中的生效薪资与已确认考勤，核对调整后提交审批。' }}</p>
      </div>
      <a-space
        ><hr-template-downloads /><a-button v-if="!financeMode" icon="setting" @click="openRules">计薪规则</a-button><a-button v-if="!financeMode && allowed('generate')" type="primary" @click="openGenerate('generate')">生成工资草稿</a-button
        ><a-button v-if="!financeMode && allowed('generate')" @click="openGenerate('supplement')">补发 / 扣回</a-button
        ><a-button icon="download" @click="downloadBlank">下载{{ financeMode ? '发放回执' : '工资调整' }}模板</a-button></a-space
      >
    </div>
    <div class="hr-toolbar">
      <a-month-picker v-model="period" format="YYYY-MM" value-format="YYYY-MM" placeholder="工资月份（留空查全部）" @change="load" /><a-button icon="reload" @click="load">刷新</a-button
      ><a-button v-if="allowed('export')" icon="export" @click="exportSummary">导出月度工资汇总</a-button>
    </div>
    <a-table :loading="loading" :data-source="batches" row-key="id" :pagination="{ pageSize: 10 }"
      ><a-table-column title="批次" data-index="id" /><a-table-column title="工资月份" data-index="period" /><a-table-column title="类型" data-index="batchType"
        ><template slot-scope="v">{{ statusNames[v] }}</template></a-table-column
      ><a-table-column title="人数" data-index="employeeCount" /><a-table-column title="应付实发" data-index="netAmount"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      ><a-table-column title="实际已付" data-index="paidAmount"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      ><a-table-column title="审批状态" data-index="status"
        ><template slot-scope="v"
          ><a-tag>{{ statusNames[v] }}</a-tag></template
        ></a-table-column
      ><a-table-column title="操作"
        ><template slot-scope="_, r"
          ><a-button type="link" @click="open(r.id)">{{ financeMode ? '办理发放' : '查看工资表' }}</a-button></template
        ></a-table-column
      ></a-table
    >
    <template v-if="detail"
      ><a-divider />
      <div class="hr-heading">
        <div>
          <h3>{{ detail.period }} 工资表 · #{{ detail.id }} · 版本 {{ detail.revision }}</h3>
          <span class="hr-note">{{ statusNames[detail.status] }}</span><a-tag :color="detail.published ? 'green' : 'default'">{{ detail.published ? '工资条已发布' : '工资条未发布' }}</a-tag>
          <a-tag :color="allVerified ? 'green' : 'orange'" class="tw-ml-2">{{ allVerified ? '本期已核对' : '待核对' }} · {{ verifiedCount }}/{{ items.length }} 人</a-tag>
        </div>
        <a-space
          ><a-button v-if="allowed('export')" icon="export" @click="exportRows">{{ financeMode ? '导出发放对账' : '导出工资表（可回传）' }}</a-button
          ><a-button v-if="!financeMode && editable && allowed('edit')" @click="startImport">上传调整工资表</a-button
          ><a-button v-if="!financeMode && editable && allowed('generate') && detail.batchType === 'regular'" @click="openGenerate('recalculate')">重新核算</a-button></a-space
        >
      </div>
      <a-alert
        v-if="detail.attendanceChanged || detail.sourcesChanged"
        type="warning"
        show-icon
        message="引用的薪资标准、任职资料或考勤已有更新版本。草稿需重新核算，已批准或已发工资通过更正、差额批次处理，历史金额不会自动变化。"
        class="tw-mb-4"
      />
      <template v-if="detail.batchType === 'regular'">
        <a-alert v-if="detail.rulesChanged" type="info" show-icon message="公司计薪规则已有新版本，本批次仍使用保存的原规则。需要更新时，请在重新核算中选择本月适用规则；已批准工资按更正或差额批次处理。" class="tw-mb-4" />
        <a-collapse class="tw-mb-4"><a-collapse-panel key="rules" :header="'本批次计薪规则：' + ruleLabel(detail.rules)">
          <a-descriptions bordered :column="2" size="small"><a-descriptions-item v-for="f in ruleFields" :key="f.key" :label="f.label">{{ detail.rules[f.key] }}</a-descriptions-item></a-descriptions>
        </a-collapse-panel></a-collapse>
      </template>
      <div class="hr-metrics">
        <div>
          <span>本期应发</span><strong>{{ money(gross) }}</strong>
        </div>
        <div>
          <span>本期实发</span><strong>{{ money(detail.netAmount) }}</strong>
        </div>
        <div>
          <span>实际已付</span><strong>{{ money(detail.paidAmount) }}</strong>
        </div>
        <div>
          <span>剩余应付 / 扣回</span><strong>{{ money(Number(detail.netAmount) - Number(detail.paidAmount)) }}</strong>
        </div>
      </div>
      <div v-if="canBatchPay" class="hr-toolbar">
        <a-checkbox :checked="allPaymentSelected" :indeterminate="somePaymentSelected" :disabled="!payableItems.length" @change="selectAllPayments">全选可登记员工</a-checkbox>
        <a-button type="primary" icon="form" :disabled="!selectedPayableItems.length" @click="$refs.batchPayment.open(detail, selectedPayableItems)">批量登记发放{{ selectedPayableItems.length ? '（' + selectedPayableItems.length + '人）' : '' }}</a-button>
        <span class="hr-note">先勾选员工，每人填写实际流水号；同一批次可共用付款日期和凭证。</span>
      </div>
      <div v-if="!financeMode" class="hr-toolbar">
        <a-button v-if="editable && allowed('edit') && accountChanges.length" @click="openAccountSync">同步最新收款账户（{{ accountChanges.length }} 人）</a-button><a-button v-if="editable && allowed('edit')" :disabled="allVerified" :icon="allVerified ? 'check-circle' : undefined" @click="openReason('verify')">{{ allVerified ? '本期已核对' : '确认个税与本期项目已核对' }}</a-button
        ><a-button v-if="editable && allowed('submit')" type="primary" @click="submit">提交工资审批</a-button><a-button v-if="detail.status === 'approved' && allowed('publish') && !detail.published" type="primary" @click="action('publish')">发布工资条</a-button><a-tag v-else-if="detail.status === 'approved' && detail.published" color="green">工资条已发布</a-tag
        ><a-button v-if="detail.status === 'approved' && allowed('edit')" @click="openReason('reopen')">撤销批准并更正</a-button><span class="hr-note">个税及个人承担项目不自动推算，需核对后才可提交。</span>
      </div>
      <a-alert v-if="!financeMode && editable && accountChanges.length" type="warning" show-icon :message="'有 ' + accountChanges.length + ' 人的收款账户与员工档案不一致，请点击“同步最新收款账户”预览并确认。同步后仅变更人员需要重新核对。'" class="tw-mb-4" />
      <a-alert v-if="!financeMode && editable" :type="accountChanges.length || detail.attendanceChanged || detail.sourcesChanged ? 'warning' : allVerified ? 'success' : 'info'" show-icon :message="verificationMessage" class="tw-mb-4" />
      <a-table :data-source="items" row-key="id" :pagination="{ pageSize: 15 }" :scroll="{ x: payrollTableWidth }" size="small"
        ><a-table-column v-if="canBatchPay" fixed="left" :width="52"
          ><template slot-scope="_, r"><a-checkbox :checked="selectedPaymentIds.includes(r.id)" :disabled="!isPayable(r)" @change="togglePayment(r, $event)" /></template>
        ></a-table-column><a-table-column title="员工" fixed="left" :width="150"
          ><template slot-scope="_, r"
            ><a @click="showItem(r)">{{ r.employeeName }}</a>
            <div class="hr-note">{{ r.employeeNo }}</div></template
          ></a-table-column
        ><a-table-column title="部门" data-index="deptName" :width="140" /><a-table-column v-for="f in amountFields" :key="f.key" :title="f.label" :data-index="f.key" :width="115"
          ><template slot-scope="v, r"><template v-if="f.key === 'tax'"><a v-if="!financeMode && editable && allowed('edit')" @click="openTax(r)">{{ money(v) }}</a><span v-else>{{ money(v) }}</span></template><template v-else>{{ money(v) }}</template></template></a-table-column
        ><a-table-column title="核对" fixed="right" :width="90"
          ><template slot-scope="_, r"><a-tag :color="Number(r.verified) === 1 ? 'green' : 'orange'">{{ Number(r.verified) === 1 ? '已核对' : '待核对' }}</a-tag></template></a-table-column
        ><a-table-column title="操作" fixed="right" :width="150"
          ><template slot-scope="_, r"
            ><a-button v-if="detail.status === 'approved' && allowed('pay') && Number(r.netAmount) !== Number(r.paidAmount)" type="link" @click="openPay(r)">登记发放</a-button
            ><a-button type="link" @click="showItem(r)">明细</a-button></template
          ></a-table-column
        ></a-table
      >
    </template>
    <a-modal v-model="generateVisible" :title="generateMode === 'supplement' ? '创建补发或扣回批次' : '按考勤核算工资'" :confirm-loading="busy" :ok-button-props="{ props: { disabled: generateLoading || generateLoadFailed } }" width="700px" @ok="generate"
      ><a-alert type="info" show-icon message="公司计薪规则按工资月份自动引用，无须每月重复填写。薪资使用本月月底之前最新已确认标准；月中入离职或多段任职仍需要已确认的每日考勤。" class="tw-mb-4" /><a-form-model layout="vertical"
        ><a-form-model-item label="工资月份"><a-month-picker v-model="generateForm.period" :allow-clear="false" :disabled="generateMode === 'recalculate' || busy" format="YYYY-MM" value-format="YYYY-MM" @change="loadGenerateData" class="tw-w-full" /></a-form-model-item
        ><a-form-model-item v-if="generateMode !== 'supplement'" label="已确认月度考勤"
          ><a-select v-model="generateForm.attendanceId"
            ><a-select-option v-for="a in attendanceBatches" :key="a.id" :value="a.id">{{ a.period }} · 批次 #{{ a.id }}</a-select-option></a-select
          ></a-form-model-item
        >
        <template v-if="generateMode !== 'supplement'">
          <a-form-model-item v-if="generateMode === 'recalculate'" label="重新核算使用的计薪规则">
            <a-radio-group v-model="generateForm.ruleSource" @change="applyGenerateRules"><a-radio value="original">沿用原批次规则</a-radio><a-radio value="company">采用本月适用规则</a-radio></a-radio-group>
          </a-form-model-item>
          <a-spin :spinning="generateLoading">
            <a-alert v-if="generateLoadFailed" type="error" show-icon message="考勤或计薪规则加载失败，请重试" class="tw-mb-4" />
            <a-button v-if="generateLoadFailed" @click="loadGenerateData">重新加载</a-button>
            <template v-else-if="generateForm.rules && Object.keys(generateForm.rules).length">
              <p>{{ ruleLabel(generateForm.rules) }}</p>
              <a-descriptions bordered :column="2" size="small" class="tw-mb-4"><a-descriptions-item v-for="f in ruleFields" :key="f.key" :label="f.label">{{ generateForm.rules[f.key] }}</a-descriptions-item></a-descriptions>
            </template>
            <a-alert v-else-if="!generateLoading" type="warning" show-icon message="本月尚无适用计薪规则。请先设置公司规则和生效月份，再生成工资。" class="tw-mb-4" />
          </a-spin>
          <a-button v-if="allowed('rule_manage')" type="link" @click="openRules">{{ companyRule ? '维护公司计薪规则' : '设置公司计薪规则' }}</a-button>
        </template>
        <a-form-model-item v-if="generateMode !== 'generate'" label="补发、扣回或重算原因"><a-textarea v-model="generateForm.reason" :max-length="1000" /></a-form-model-item
        ><a-alert v-if="generateMode === 'recalculate'" type="warning" message="重算将生成新的核算快照，当前人工调整会清除并留存历史。请先导出保存需要保留的调整。" /></a-form-model
    ></a-modal>
    <payroll-batch-payment ref="batchPayment" @done="batchPaymentsDone" />
    <payroll-rules v-if="!financeMode" ref="payrollRules" :period="generateVisible ? generateForm.period : period" :can-manage="allowed('rule_manage')" @saved="rulesSaved" />
    <a-modal v-model="accountSyncVisible" title="同步最新收款账户" width="900px" ok-text="确认同步" :confirm-loading="busy" :ok-button-props="{ props: { disabled: busy || !accountSyncChanges.length } }" @ok="saveAccountSync">
      <a-alert type="warning" show-icon :message="'以下 ' + accountSyncChanges.length + ' 人的账户信息将更新为员工档案中的最新值，仅这些人员恢复为待核对。工资金额和个税保持不变。'" class="tw-mb-4" />
      <a-table :data-source="accountSyncRows" row-key="key" :pagination="false" size="small">
        <a-table-column title="员工" :width="150"><template slot-scope="_, row">{{ row.employeeName }}<div class="hr-note">{{ row.employeeNo }}</div></template></a-table-column>
        <a-table-column title="变更字段" data-index="label" :width="110" />
        <a-table-column title="本批次原值" :width="220"><template slot-scope="_, row">{{ row.before || '未填写' }}</template></a-table-column>
        <a-table-column title="档案最新值" :width="220"><template slot-scope="_, row">{{ row.after || '未填写' }}</template></a-table-column>
      </a-table>
    </a-modal>
    <a-modal v-model="taxVisible" title="填写 / 修改个税" :confirm-loading="busy" :ok-button-props="{ props: { disabled: busy } }" @ok="saveTax">
      <p>{{ taxItem.employeeName }} · {{ taxItem.employeeNo }}</p>
      <a-alert type="info" show-icon message="本期个税默认0，可按实际申报结果修改；留空按0保存。修改后请重新确认本期核对。" class="tw-mb-4" />
      <a-form-model layout="vertical"><a-form-model-item label="本期个税（元）"><a-input-number v-model="taxForm.tax" :min="0" :precision="2" class="tw-w-full" placeholder="默认0" /></a-form-model-item><a-form-model-item label="填写依据 / 修改原因（选填）"><a-textarea v-model="taxForm.reason" :rows="3" :max-length="1000" placeholder="可补充填写依据或修改原因" /></a-form-model-item></a-form-model>
    </a-modal>
    <a-modal v-model="reasonVisible" title="核对或更正说明" :confirm-loading="busy" @ok="saveReason"><a-textarea v-model="reason" :rows="4" :max-length="1000" placeholder="请填写核对依据或更正原因" /></a-modal>
    <a-modal v-model="payVisible" title="登记实际工资付款 / 扣回" :confirm-loading="busy" @ok="savePay"
      ><a-alert type="info" show-icon message="此操作登记线下真实结果，不发起银行转账。成功需上传凭证，失败需填写原因；重试不会重复累计成功金额。" class="tw-mb-4" /><a-form-model layout="vertical"
        ><a-form-model-item label="员工与收款账户"
          ><span>{{ payItem.employeeName }} · {{ payItem.bankName }} · {{ payItem.bankAccount }}</span></a-form-model-item
        ><a-form-model-item label="本次金额（扣回填负数）"><a-input-number v-model="payForm.amount" :precision="2" class="tw-w-full" /></a-form-model-item
        ><a-form-model-item label="实际日期"><a-date-picker v-model="payForm.paymentDate" format="YYYY-MM-DD" value-format="YYYY-MM-DD" class="tw-w-full" /></a-form-model-item
        ><a-form-model-item label="结果"
          ><a-select v-model="payForm.status"><a-select-option value="paid">成功</a-select-option><a-select-option value="failed">失败</a-select-option></a-select></a-form-model-item
        ><a-form-model-item label="银行流水号 / 失败单号"><a-input v-model="payForm.referenceNo" :max-length="128" /></a-form-model-item
        ><a-form-model-item label="备注 / 失败原因"><a-textarea v-model="payForm.reason" :max-length="1000" /></a-form-model-item
        ><a-upload :before-upload="paymentFile" :show-upload-list="false"><a-button icon="upload">上传私有凭证</a-button></a-upload
        ><span class="hr-note">{{ payForm.fileName }}</span></a-form-model
      ></a-modal
    >
    <a-drawer :visible="!!selectedItem" title="工资核算与发放明细" width="min(1000px,94vw)" @close="selectedItem = null"
      ><template v-if="selectedItem"
        ><h3>{{ selectedItem.employeeName }} · {{ selectedItem.employeeNo }}</h3>
        <a-descriptions bordered :column="2"
          ><a-descriptions-item v-for="f in amountFields" :key="f.key" :label="f.label">{{ money(selectedItem[f.key]) }}</a-descriptions-item
          ><a-descriptions-item label="个税填写说明">{{ selectedItem.taxReason || '—' }}</a-descriptions-item
          ><a-descriptions-item label="个税填写人 / 时间">{{ selectedItem.taxOperatorId || '—' }} / {{ selectedItem.taxUpdateTime || '—' }}</a-descriptions-item
          ><a-descriptions-item label="开户人">{{ selectedItem.accountName }}</a-descriptions-item
          ><a-descriptions-item label="银行">{{ selectedItem.bankName }}</a-descriptions-item
          ><a-descriptions-item label="银行卡号">{{ selectedItem.bankAccount }}</a-descriptions-item
          ><a-descriptions-item label="核对说明">{{ selectedItem.reason || '—' }}</a-descriptions-item></a-descriptions
        >
        <h3 class="hr-section-title">计薪分段快照</h3>
        <template v-if="selectedItem.commissionSources && selectedItem.commissionSources.length">
          <h3>提成业务来源</h3>
          <a-alert type="info" show-icon message="提成及扣回金额由提成核算锁定。请在本批次核对个税；后续退款等变化从提成核算生成新差额批次。" />
          <a-table :data-source="selectedItem.commissionSources" row-key="id" :pagination="false" size="small">
            <a-table-column title="核算批次" data-index="batchId" /><a-table-column title="客户" data-index="customerName" /><a-table-column title="职位" data-index="jobTitle" /><a-table-column title="候选人" data-index="talentName" />
            <a-table-column title="贡献"><template slot-scope="_, r">{{ r.side === 'development' ? '客户拓展' : '招聘交付' }}</template></a-table-column>
            <a-table-column title="本次提成 / 扣回"><template slot-scope="_, r">{{ money(r.amount) }}</template></a-table-column>
          </a-table>
        </template>
        <a-table :columns="segmentColumns" :data-source="selectedItem.snapshot.segments || []" :row-key="(_, index) => index" :pagination="false" size="small" />
        <h3 class="hr-section-title">付款与失败记录</h3>
        <a-table :data-source="selectedItem.payments" row-key="id" :pagination="false" size="small"
          ><a-table-column title="日期" data-index="paymentDate" /><a-table-column title="金额" data-index="amount" /><a-table-column title="结果" data-index="status"
            ><template slot-scope="v">{{ statusNames[v] }}</template></a-table-column
          ><a-table-column title="流水号" data-index="referenceNo" /><a-table-column title="凭证"
            ><template slot-scope="_, r"><a v-if="r.attachmentId" @click="downloadFile(r.attachmentId)">下载</a></template></a-table-column
          ></a-table
        ></template
      ></a-drawer
    >
    <hr-excel-import
      v-if="importType"
      :visible="!!importType"
      :type="importType"
      :extra="importExtra"
      :template-rows="exportRecords"
      :preview-api="importType === 'payroll' ? previewImport : undefined"
      :commit-api="commitImport"
      @close="importType = ''"
      @done="importDone"
    />
  </div>
</template>
<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { hr } from '/src/api/enterprise/hr.js'
import PayrollRules from './PayrollRules.vue'
import { payrollRuleFields, payrollRuleLabel } from './payrollRules.js'
import HrTemplateDownloads from '/src/components/HrTemplateDownloads/index.vue'
import { payrollReportEntries, payrollSummary } from './reports.js'
import HrExcelImport from '/src/components/HrExcelImport/index.vue'
import { exportTemplate, exportWorkbook } from './templates.js'
import { money, statusNames, newToken, fileBody } from './hrShared.js'
import { saveAs } from 'file-saver'
import PayrollBatchPayment from './PayrollBatchPayment.vue'
export default {
  name: 'PayrollWorkspace',
  components: { HrExcelImport, HrTemplateDownloads, PayrollRules, PayrollBatchPayment },
  props: { financeMode: Boolean },
  mixins: [withModuleContexts({ payroll: { component: 'enterprise/hr/payroll', label: '工资管理' }, finance: { component: 'eos/crm/finance/payroll', label: '工资发放' } })],
  data: () => ({
    period: new Date().toLocaleDateString('sv-SE').slice(0, 7),
    batches: [],
    detail: null,
    loading: false,
    busy: false,
    generateVisible: false,
    generateMode: 'generate',
    generateForm: { rules: {} },
    companyRule: null,
    generateLoading: false,
    generateLoadFailed: false,
    generateRequest: 0,
    attendanceBatches: [],
    reasonVisible: false,
    reason: '',
    reasonAction: '',
    payVisible: false,
    payForm: {},
    payItem: {},
    taxVisible: false,
    taxForm: {},
    taxItem: {},
    accountSyncVisible: false,
    accountSyncChanges: [],
    accountSyncToken: '',
    accountSyncRevision: null,
    selectedItem: null,
    importType: '',
    importExtra: {},
    selectedPaymentIds: [],
    statusNames,
    ruleFields: payrollRuleFields,
    amountFields: [
      { key: 'baseAmount', label: '基本工资' },
      { key: 'allowance', label: '固定补贴' },
      { key: 'overtime', label: '加班工资' },
      { key: 'bonus', label: '奖金' },
      { key: 'commission', label: '提成' },
      { key: 'adjustment', label: '本期调整' },
      { key: 'grossAmount', label: '应发' },
      { key: 'deduction', label: '其他扣款' },
      { key: 'tax', label: '个税' },
      { key: 'contribution', label: '个人承担' },
      { key: 'netAmount', label: '实发' },
      { key: 'paidAmount', label: '实际已付' }
    ],
    segmentColumns: [
      { title: '日期', dataIndex: 'date' },
      { title: '开始', dataIndex: 'from' },
      { title: '结束', dataIndex: 'to' },
      { title: '薪资版本', dataIndex: 'salaryId' },
      { title: '计薪小时', dataIndex: 'paidHours' },
      { title: '基本工资', dataIndex: 'baseAmount' }
    ]
  }),
  computed: {
    contextKey() {
      return this.financeMode ? 'finance' : 'payroll'
    },
    editable() {
      return ['draft', 'rejected'].includes(this.detail?.status)
    },
    verifiedCount() {
      return this.items.filter(r => Number(r.verified) === 1).length
    },
    allVerified() {
      return this.items.length > 0 && this.verifiedCount === this.items.length
    },
    canBatchPay() { return this.detail?.status === 'approved' && this.allowed('pay') },
    payableItems() { return this.items.filter(row => this.isPayable(row)) },
    selectedPayableItems() { return this.payableItems.filter(row => this.selectedPaymentIds.includes(row.id)) },
    allPaymentSelected() { return this.payableItems.length > 0 && this.selectedPaymentIds.length === this.payableItems.length },
    somePaymentSelected() { return this.selectedPaymentIds.length > 0 && !this.allPaymentSelected },
    payrollTableWidth() {
      const selectionWidth = this.canBatchPay ? 52 : 0
      const columnCount = this.amountFields.length + (this.canBatchPay ? 5 : 4)
      return selectionWidth + 150 + 140 + this.amountFields.length * 115 + 90 + 150 + columnCount * 32
    },
    accountChanges() {
      return this.detail?.accountChanges || []
    },
    accountSyncRows() {
      const labels = { accountName: '开户人', bankName: '开户银行', bankAccount: '银行卡号' }
      return this.accountSyncChanges.flatMap(change => Object.entries(labels).filter(([key]) => change.before[key] !== change.after[key]).map(([key, label]) => ({ key: change.itemId + '-' + key, employeeName: change.employeeName, employeeNo: change.employeeNo, label, before: change.before[key], after: change.after[key] })))
    },
    verificationMessage() {
      if (this.allVerified) {
        if (this.detail.attendanceChanged || this.detail.sourcesChanged) return '本期已完成核对，但薪资、任职资料或考勤已有变化，请先重新核算后再提交工资审批。'
        if (this.accountChanges.length) return '本期已完成核对，但收款账户已有变化，请先同步最新收款账户并重新核对变更人员。'
        return '本期 ' + this.items.length + ' 人已全部核对，下一步请点击“提交工资审批”。修改个税或同步收款账户后，变更人员需重新核对。'
      }
      return '个税默认0，按需修改。已核对 ' + this.verifiedCount + '/' + this.items.length + ' 人。请确认本期项目已核对，再提交工资审批。'
    },
    items() {
      return (this.detail?.rows || []).map(row => ({ ...row, ...row.snapshot, employeeName: row.snapshot.nickName, remainingAmount: Number(row.netAmount) - Number(row.paidAmount) }))
    },
    gross() {
      return this.items.reduce((sum, row) => sum + Number(row.grossAmount), 0)
    },
    exportRecords() {
      return this.items.map(row => ({ ...row, tax: row.tax ?? 0, taxStatus: Number(row.taxEntered) === 1 ? '已填写' : '默认0', batchId: this.detail.id, period: this.detail.period, revision: this.detail.revision }))
    }
  },
  mounted() {
    if (/^\d{4}-\d{2}$/.test(String(this.$route.query.period || ''))) this.period = String(this.$route.query.period)
    this.load()
    if (this.$route.query.payrollId) this.open(Number(this.$route.query.payrollId))
  },
  methods: {
    money,
    async openTax(row) {
      if (!(await this.guard('edit', '填写个税'))) return
      this.taxItem = row
      this.taxForm = { tax: Number(row.tax || 0), reason: '', revision: this.detail.revision }
      this.taxVisible = true
    },
    async saveTax() {
      this.busy = true
      try {
        this.detail = (await hr('/payroll/' + this.detail.id + '/items/' + this.taxItem.id + '/tax', 'put', this.taxForm)).data
        this.taxVisible = false
        if (this.selectedItem?.id === this.taxItem.id) this.selectedItem = this.items.find(r => r.id === this.taxItem.id)
        await this.load()
        this.$message.success('个税已保存，实发金额已更新，请重新确认本期核对')
      } finally {
        this.busy = false
      }
    },
    ruleLabel: payrollRuleLabel,
    openRules() {
      this.$refs.payrollRules.open()
    },
    async rulesSaved() {
      if (this.generateVisible && this.generateMode !== 'supplement') await this.loadGenerateData()
      if (this.detail) await this.open(this.detail.id)
    },
    allowed(action) {
      return this.showModuleAction(this.contextKey, action)
    },
    async guard(action, label) {
      return this.checkModuleAction({ module: this.contextKey, action, label })
    },
    async load() {
      this.loading = true
      try {
        this.batches = (await hr(this.financeMode ? '/finance/payroll' : '/payroll', 'get', { period: this.period })).data || []
      } finally {
        this.loading = false
      }
    },
    async open(id) {
      this.detail = (await hr((this.financeMode ? '/finance' : '') + '/payroll/' + id)).data
      this.selectedPaymentIds = []
    },
    isPayable(row) { return Number(row.netAmount) !== Number(row.paidAmount) },
    togglePayment(row, event) {
      const selected = event.target.checked
      this.selectedPaymentIds = selected ? [...new Set([...this.selectedPaymentIds, row.id])] : this.selectedPaymentIds.filter(id => id !== row.id)
    },
    selectAllPayments(event) { this.selectedPaymentIds = event.target.checked ? this.payableItems.map(row => row.id) : [] },
    async batchPaymentsDone() {
      this.selectedPaymentIds = []
      if (this.detail) await this.open(this.detail.id)
      await this.load()
    },
    async openGenerate(mode) {
      if (!(await this.guard('generate', '核算工资'))) return
      this.generateMode = mode
      this.generateForm = {
        period: mode === 'recalculate' ? this.detail.period : this.period || new Date().toLocaleDateString('sv-SE').slice(0, 7),
        rules: { ...(mode === 'recalculate' ? this.detail.rules : {}) },
        ruleSource: mode === 'recalculate' ? 'original' : 'company',
        attendanceId: undefined,
        reason: ''
      }
      this.generateVisible = true
      await this.loadGenerateData()
    },
    async loadGenerateData() {
      const request = ++this.generateRequest
      if (this.generateMode === 'supplement') {
        this.generateLoading = false
        this.generateLoadFailed = false
        return
      }
      const month = this.generateForm.period
      const selectedAttendance = this.generateForm.attendanceId
      this.generateLoading = true
      this.generateLoadFailed = false
      this.attendanceBatches = []
      this.generateForm.attendanceId = undefined
      this.companyRule = null
      try {
        const [attendance, rules] = await Promise.all([hr('/payroll/attendance-options', 'get', { period: month }), hr('/payroll/rules', 'get', { period: month })])
        if (request !== this.generateRequest) return
        this.attendanceBatches = (attendance.data || []).filter(a => a.status === 'confirmed')
        this.generateForm.attendanceId = this.attendanceBatches.some(a => a.id === selectedAttendance) ? selectedAttendance : this.attendanceBatches[0]?.id
        this.companyRule = rules.data.applicableRule || null
        this.applyGenerateRules()
      } catch (e) {
        if (request === this.generateRequest) this.generateLoadFailed = true
      } finally {
        if (request === this.generateRequest) this.generateLoading = false
      }
    },
    applyGenerateRules() {
      if (this.generateMode === 'recalculate' && this.generateForm.ruleSource === 'original') {
        this.generateForm.rules = { ...this.detail.rules }
      } else {
        this.generateForm.rules = this.companyRule ? { ...this.companyRule.rules, _rule: { id: this.companyRule.id, versionNo: this.companyRule.versionNo, name: this.companyRule.name, effectiveMonth: this.companyRule.effectiveMonth } } : {}
      }
    },
    async generate() {
      if (this.busy || this.generateLoading || this.generateLoadFailed) return
      if (!this.generateForm.period) return this.$message.warning('请选择月份')
      if (this.generateMode === 'supplement') {
        if (!this.generateForm.reason) return this.$message.warning('请填写补发扣回原因')
        this.importExtra = { period: this.generateForm.period, reason: this.generateForm.reason }
        this.generateVisible = false
        this.importType = 'supplement'
        return
      }
      if (this.ruleFields.some(f => this.generateForm.rules[f.key] === undefined || this.generateForm.rules[f.key] === null)) return this.$message.warning('请先设置本月适用的公司计薪规则')
      if (!this.generateForm.attendanceId) return this.$message.warning('请选择本月已确认的月度考勤')
      this.busy = true
      try {
        const response = await hr(this.generateMode === 'recalculate' ? '/payroll/' + this.detail.id + '/recalculate' : '/payroll', 'post', {
          period: this.generateForm.period,
          attendanceId: this.generateForm.attendanceId,
          reason: this.generateForm.reason,
          ...(this.generateForm.ruleSource === 'company' ? { ruleId: this.companyRule.id } : {}),
          revision: this.detail?.revision,
          discardAdjustments: this.generateMode === 'recalculate'
        })
        this.detail = response.data
        this.period = this.detail.period
        this.generateVisible = false
        this.load()
        this.$message.success('工资草稿已核算，请核对个税与本期项目')
      } finally {
        this.busy = false
      }
    },
    async startImport() {
      if (!(await this.guard('edit', '回传工资调整'))) return
      this.importExtra = { revision: this.detail.revision }
      this.importType = 'payroll'
    },
    previewImport(payload) {
      return hr('/payroll/' + this.detail.id + '/preview', 'post', payload)
    },
    async commitImport(payload) {
      const response = await hr(this.importType === 'supplement' ? '/payroll/supplement' : '/payroll/' + this.detail.id + '/import', 'post', payload)
      if (this.importType === 'supplement') this.detail = response.data
      return response
    },
    async importDone() {
      await this.load()
      if (this.detail) await this.open(this.detail.id)
    },
    downloadBlank() {
      if (this.financeMode) exportTemplate('payment')
      else if (this.detail) exportTemplate('payroll', this.exportRecords)
      else {
        exportTemplate('payroll')
        this.$message.info('回传时请使用具体工资草稿导出的批次数据')
      }
    },
    async exportRows() {
      if (!(await this.guard('export', '导出工资报表'))) return
      if (!this.detail) return this.exportSummary()
      const report = (await hr('/reports/payroll/' + this.detail.id, 'get', { finance: this.financeMode })).data
      exportWorkbook(payrollReportEntries(report, this.financeMode), '工资报表-' + report.period + '-' + report.id)
    },
    async exportSummary() {
      if (!(await this.guard('export', '导出月度工资汇总'))) return
      const response = await hr('/reports/payroll', 'get', { period: this.period, finance: this.financeMode })
      exportTemplate('payrollSummary', payrollSummary(response.data || []), '月度工资汇总-' + (this.period || '全部'))
    },
    openReason(action) {
      this.reasonAction = action
      this.reason = ''
      this.reasonVisible = true
    },
    async saveReason() {
      if (!this.reason) return this.$message.warning('请填写说明')
      this.busy = true
      try {
        await this.action(this.reasonAction, { reason: this.reason })
        this.reasonVisible = false
      } finally {
        this.busy = false
      }
    },
    async action(action, extra = {}) {
      await hr('/payroll/' + this.detail.id + '/' + action, 'post', { revision: this.detail.revision, ...extra })
      await this.open(this.detail.id)
      this.load()
      this.$message.success('操作完成')
    },
    async openAccountSync() {
      if (!(await this.guard('edit', '同步最新收款账户'))) return
      await this.open(this.detail.id)
      if (!this.accountChanges.length) return this.$message.info('收款账户已是最新，无需同步')
      this.accountSyncChanges = this.accountChanges
      this.accountSyncToken = this.detail.accountChangesToken
      this.accountSyncRevision = this.detail.revision
      this.accountSyncVisible = true
    },
    async saveAccountSync() {
      this.busy = true
      try {
        const response = await hr('/payroll/' + this.detail.id + '/accounts', 'post', { revision: this.accountSyncRevision, changesToken: this.accountSyncToken })
        this.accountSyncVisible = false
        await this.open(this.detail.id)
        if (this.selectedItem) this.selectedItem = this.items.find(row => row.id === this.selectedItem.id) || null
        await this.load()
        const count = response.data.updatedCount
        this.$message.success(count ? '已同步 ' + count + ' 人的收款账户，请重新核对这些人员' : '收款账户已是最新，无需同步')
      } finally {
        this.busy = false
      }
    },
    async submit() {
      await this.open(this.detail.id)
      if (this.accountChanges.length) return this.$message.warning('收款账户已变更，请先同步最新收款账户并重新核对')
      const response = await this.submitConfiguredAction('submit', { payrollId: this.detail.id, revision: this.detail.revision, title: this.detail.period + '工资审批' }, '提交工资审批')
      if (!response) return
      this.$message.success('已按配置提交审批或批准')
      await this.open(this.detail.id)
      this.load()
    },
    showItem(row) {
      this.selectedItem = row
    },
    async openPay(row) {
      if (!(await this.guard('pay', '登记工资发放'))) return
      this.payItem = row
      this.payForm = { requestToken: newToken(), amount: Number(row.netAmount) - Number(row.paidAmount), status: 'paid', paymentDate: new Date().toLocaleDateString('sv-SE') }
      this.payVisible = true
    },
    async paymentFile(file) {
      if (file.size > 10 * 1024 * 1024) {
        this.$message.warning('文件不能超过10MB')
        return false
      }
      this.payForm = { ...this.payForm, ...(await fileBody(file)) }
      return false
    },
    async savePay() {
      if (this.payForm.status === 'paid' && !this.payForm.fileContent) return this.$message.warning('请上传实际付款凭证')
      this.busy = true
      try {
        await hr('/payroll/items/' + this.payItem.id + '/pay', 'post', this.payForm)
        this.payVisible = false
        await this.open(this.detail.id)
        await this.load()
        this.$message.success('实际发放结果已登记')
      } finally {
        this.busy = false
      }
    },
    async downloadFile(id) {
      const file = (await hr('/files/' + id)).data
      saveAs(new Blob([Uint8Array.from(atob(file.fileContent), c => c.charCodeAt(0))]), file.fileName)
    }
  }
}
</script>
<style src="./hr.css"></style>
