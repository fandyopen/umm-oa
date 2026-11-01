<template>
  <a-spin :spinning="loading">
    <div class="customer-hero">
      <div>
        <div class="customer-name">{{ customer.name || '-' }}</div>
        <div class="customer-meta">
          <a-tag color="blue">{{ customer.industry || '未设置行业' }}</a-tag>
          <a-tag :color="statusColor">{{ customer.cooperationStatus || '未设置状态' }}</a-tag>
          <span>{{ customer.companyScale || '规模未设置' }}</span>
          <span>{{ customer.region || '地区未设置' }}</span>
        </div>
      </div>
      <div class="customer-owner">
        <span>负责人</span>
        <strong>{{ customer.ownerUserName || '未分配' }}</strong>
        <small>{{ customer.ownerDept?.deptName || '' }}</small>
      </div>
    </div>

    <div class="metric-grid">
      <div class="metric-item">
        <span>联系人</span>
        <strong>{{ customer.contactCount || contacts.length }}</strong>
      </div>
      <div class="metric-item">
        <span>合作职位</span>
        <strong>{{ customer.cooperationPositions || 0 }}</strong>
      </div>
      <div class="metric-item">
        <span>推荐 / 入职</span>
        <strong>{{ customer.referralCount || 0 }} / {{ customer.hireCount || 0 }}</strong>
      </div>
      <div class="metric-item">
        <span>合同金额</span>
        <strong>{{ formatMoney(customer.contractAmount) }}</strong>
      </div>
      <div class="metric-item">
        <span>回款金额</span>
        <strong>{{ formatMoney(customer.receivedAmount) }}</strong>
      </div>
      <div class="metric-item">
        <span>最近跟进</span>
        <strong class="metric-date">{{ customer.lastFollowupTime || '暂无' }}</strong>
      </div>
    </div>

    <a-tabs v-model="activeTab">
      <a-tab-pane v-if="canView(341201)" key="contracts" :tab="`关联合同（${contractsTotal}）`">
        <div class="tab-toolbar"><span>合同、收费与回款统一关联当前客户</span><div><a-button type="link" @click="viewContracts">查看全部合同</a-button><a-button v-if="canContractAdd && customer.publicPool !== '1'" type="primary" icon="plus" @click="createContract">新建合同</a-button></div></div>
        <a-alert v-if="customer.contractManaged" type="info" message="客户合同金额、回款和到期日期由已签署生效的合同自动汇总。" style="margin-bottom: 16px" />
        <a-table :columns="contractColumns" :data-source="contracts" :pagination="false" row-key="id" size="small"><span slot="contractName" slot-scope="text, record"><a @click="openContract(record.id)">{{ text }}</a></span><span slot="contractStatus" slot-scope="text"><a-tag :color="contractColors[text]">{{ contractStatuses[text] }}</a-tag></span><span slot="money" slot-scope="text">{{ formatMoney(text) }}</span></a-table>
      </a-tab-pane>
      <a-tab-pane v-if="canView(341101)" key="risks" :tab="`客户风险（${risks.length}）`">
        <a-table :columns="riskColumns" :data-source="risks" :pagination="false" row-key="taskId" size="small"><span slot="riskLevel" slot-scope="value"><a-tag :color="value === 'high' ? 'red' : 'orange'">{{ value === 'high' ? '高风险' : '中风险' }}</a-tag></span><span slot="riskAction" slot-scope="_, record"><a-button type="link" @click="record.contractId ? openContract(record.contractId) : activeTab = 'followups'">{{ record.contractId ? '处理关联合同' : '跟进客户' }}</a-button></span></a-table>
      </a-tab-pane>
      <a-tab-pane key="overview" tab="客户档案">
        <a-descriptions bordered :column="2" size="small">
          <a-descriptions-item label="客户编号">{{ customer.serial || '-' }}</a-descriptions-item>
          <a-descriptions-item label="客户来源">{{ customer.customerSource || '-' }}</a-descriptions-item>
          <a-descriptions-item label="企业性质">{{ customer.enterpriseNature || '-' }}</a-descriptions-item>
          <a-descriptions-item label="税号">{{ customer.taxNumber || '-' }}</a-descriptions-item>
          <a-descriptions-item label="纳税人类型">{{ customer.taxpayerType || '-' }}</a-descriptions-item>
          <a-descriptions-item label="创建日期">{{ customer.createTime || '-' }}</a-descriptions-item>
          <a-descriptions-item label="合同到期">{{ customer.contractExpireDate || '-' }}</a-descriptions-item>
          <a-descriptions-item label="最近回款">{{ customer.lastPaymentDate || '-' }}</a-descriptions-item>
          <a-descriptions-item label="地址" :span="2">{{ customer.address || '-' }}</a-descriptions-item>
          <a-descriptions-item label="协作人员" :span="2">
            <a-tag v-for="item in collaborators" :key="item.userId">{{ item.nickName || item.userName }}</a-tag>
            <span v-if="!collaborators.length">无</span>
          </a-descriptions-item>
          <a-descriptions-item label="备注" :span="2">{{ customer.remark || '-' }}</a-descriptions-item>
        </a-descriptions>
        <div v-if="accounts.length" class="detail-section">
          <h3>银行账户</h3>
          <a-table :columns="accountColumns" :data-source="accounts" :pagination="false" row-key="id" size="small" />
        </div>
      </a-tab-pane>

      <a-tab-pane key="contacts" tab="联系人">
        <a-table :columns="contactColumns" :data-source="contacts" :pagination="false" row-key="id" size="small">
          <span slot="primaryContact" slot-scope="text">
            <a-tag v-if="text === '1'" color="green">首要联系人</a-tag>
            <span v-else>普通联系人</span>
          </span>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="followups" tab="跟进时间轴">
        <div class="tab-toolbar">
          <span>共 {{ followups.length }} 条跟进记录</span>
          <a-button v-if="canTrack" type="primary" ghost icon="plus" @click="openFollowup">新增跟进</a-button>
        </div>
        <a-empty v-if="!followups.length" description="还没有跟进记录" />
        <a-timeline v-else class="followup-timeline">
          <a-timeline-item v-for="item in followups" :key="item.id" :color="item.method === '拜访' ? 'green' : 'blue'">
            <template #dot>
              <a-icon :type="methodIcon(item.method)" />
            </template>
            <div class="timeline-head">
              <strong>{{ item.followupTime }}</strong>
              <a-tag>{{ item.method }}</a-tag>
              <a-button v-if="canTrack" type="link" size="small" icon="delete" @click="removeFollowup(item)" />
            </div>
            <div class="timeline-content">{{ item.content }}</div>
            <div v-if="item.nextPlan" class="timeline-next">下一步：{{ item.nextPlan }}</div>
            <div v-if="item.nextFollowupTime" class="timeline-next">计划跟进：{{ item.nextFollowupTime }}</div>
          </a-timeline-item>
        </a-timeline>
      </a-tab-pane>

      <a-tab-pane key="opportunities" tab="关联商机">
        <a-table :columns="opportunityColumns" :data-source="opportunities" :pagination="false" row-key="id" size="small">
          <span slot="expectedAmount" slot-scope="text">{{ formatMoney(text) }}</span>
        </a-table>
      </a-tab-pane>
    </a-tabs>
  </a-spin>
</template>

<script>
import { listBusiness } from '/src/api/eos/business/business.js'
import { deleteFollowup, getCustomer, listFollowups, listCustomerRisks } from '/src/api/eos/crm/index.js'
import { listContracts, normalizeContractId } from '/src/api/eos/crm/contracts.js'
import { can, canView, statuses, statusColors } from '../../contract/shared.js'
import ContractDetail from '../../contract/modules/ContractDetail.vue'
import ContractForm from '../../contract/modules/ContractForm.vue'
import store from '/src/store/index.js'
import { hasModuleAction } from '/src/utils/modulePermission.js'
import FollowupForm from './FollowupForm.vue'

export default {
  name: 'CustomerDetail',
  props: {
    formId: { type: Number, required: true },
    initialTab: { type: String, default: 'overview' }
  },
  data() {
    return {
      loading: false,
      activeTab: this.initialTab,
      contracts: [], contractsTotal: 0, risks: [], contractStatuses: statuses, contractColors: statusColors,
      contractColumns: [{ title: '合同', dataIndex: 'name', scopedSlots: { customRender: 'contractName' } }, { title: '状态', dataIndex: 'status', scopedSlots: { customRender: 'contractStatus' } }, { title: '生效合同金额', dataIndex: 'effectiveAmount', scopedSlots: { customRender: 'money' } }, { title: '已回款', dataIndex: 'receivedAmount', scopedSlots: { customRender: 'money' } }, { title: '生效到期日', dataIndex: 'effectiveEndDate' }],
      riskColumns: [{ title: '等级', dataIndex: 'level', scopedSlots: { customRender: 'riskLevel' } }, { title: '类型', dataIndex: 'title' }, { title: '关联合同', dataIndex: 'contractName' }, { title: '风险原因', dataIndex: 'reason' }, { title: '负责人', dataIndex: 'ownerUserName' }, { title: '操作', scopedSlots: { customRender: 'riskAction' } }],
      customer: {},
      contacts: [],
      accounts: [],
      collaborators: [],
      followups: [],
      opportunities: [],
      contactColumns: [
        { title: '姓名', dataIndex: 'name' },
        { title: '职位', dataIndex: 'position' },
        { title: '电话', dataIndex: 'phone' },
        { title: '微信', dataIndex: 'wechat' },
        { title: '邮箱', dataIndex: 'email' },
        { title: '决策权限', dataIndex: 'decisionRole' },
        { title: '类型', dataIndex: 'primaryContact', scopedSlots: { customRender: 'primaryContact' } }
      ],
      accountColumns: [
        { title: '银行账号', dataIndex: 'account' },
        { title: '账号名称', dataIndex: 'accountName' },
        { title: '开户银行', dataIndex: 'bank' }
      ],
      opportunityColumns: [
        { title: '商机名称', dataIndex: 'name' },
        { title: '阶段', dataIndex: 'stage' },
        { title: '预计成交', dataIndex: 'expectedCloseDate' },
        { title: '预计金额', dataIndex: 'expectedAmount', scopedSlots: { customRender: 'expectedAmount' } },
        { title: '负责人', dataIndex: 'ownerUserName' }
      ]
    }
  },
  computed: {
    canTrack() {
      return hasModuleAction(store.getters.permissions || [], { moduleId: 340352, perms: 'project:merchant:list' }, 'track')
    },
    canContractAdd() { return can(341201, 'add') },
    statusColor() {
      return {
        合作中: 'green',
        重点客户: 'purple',
        潜在客户: 'blue',
        已流失: 'red',
        暂停合作: 'orange'
      }[this.customer.cooperationStatus] || 'default'
    }
  },
  mounted() {
    this.loadCustomer()
  },
  methods: {
    canView,
    openContract(id) { const contractId = normalizeContractId(id); if (!contractId) return this.$message.warning('合同编号无效，请刷新客户详情后重试'); this.$createAntdDrawer({ stopPropagation: true, drawerProps: { title: '合同详情', width: '86%', maskClosable: true }, content: { template: ContractDetail, props: { contractId }, on: { changed: () => this.loadCustomer() } } }) },
    createContract() { this.$createAntdModal({ modalProps: { title: `新建合同 · ${this.customer.name}`, width: 920, maskClosable: false, footer: null }, onOk: ({ payload }) => { if (!normalizeContractId(payload?.id)) { this.$message.error('保存结果缺少有效合同编号，请刷新列表核对'); return false }; this.loadCustomer(); this.openContract(payload.id) }, content: { template: ContractForm, props: { initial: { customerId: this.formId, customerName: this.customer.name } } } }) },
    viewContracts() { this.$router.push({ path: '/crm/contracts/list', query: { customerId: this.formId } }); this.$emit('close') },
    loadCustomer() {
      this.loading = true
      Promise.all([
        getCustomer(this.formId),
        listFollowups(this.formId),
        (store.getters.permissions || []).some(p => p === '*:*:*' || p === 'project:business:list') ? listBusiness({ customerId: this.formId, pageNum: 1, pageSize: 200 }) : Promise.resolve({ rows: [] }),
        canView(341201) ? listContracts({ customerId: this.formId, pageNum: 1, pageSize: 20 }) : Promise.resolve({ rows: [], total: 0 }),
        canView(341101) ? listCustomerRisks({ customerId: this.formId }) : Promise.resolve({ rows: [] })
      ])
        .then(([customerResponse, followupResponse, opportunityResponse, contractResponse, riskResponse]) => {
          this.customer = customerResponse.data || {}
          this.contacts = this.customer.sealMerchantUsers || []
          this.accounts = this.customer.sealMerchantAccounts || []
          this.collaborators = this.customer.collaborators || []
          this.followups = followupResponse.data || []
          this.opportunities = opportunityResponse.rows || []
          this.contracts = contractResponse.rows || []
          this.contractsTotal = contractResponse.total || 0
          this.risks = riskResponse.rows || []
        })
        .finally(() => {
          this.loading = false
        })
    },
    openFollowup() {
      this.$createAntdModal({
        modalProps: {
          title: `新增跟进 · ${this.customer.name}`,
          width: 620,
          maskClosable: true
        },
        footer: false,
        content: {
          template: FollowupForm,
          props: { customerId: this.formId },
          on: {
            ok: () => this.loadCustomer()
          }
        }
      })
    },
    removeFollowup(item) {
      this.$antdconfirm({
        title: '删除跟进记录',
        content: '确认删除这条跟进记录吗？',
        onOk: () =>
          deleteFollowup(item.id).then(() => {
            this.$message.success('已删除')
            this.loadCustomer()
          })
      })
    },
    methodIcon(method) {
      return {
        电话: 'phone',
        微信: 'wechat',
        邮件: 'mail',
        拜访: 'environment',
        会议: 'team'
      }[method] || 'message'
    },
    formatMoney(value) {
      const number = Number(value || 0)
      return `￥${number.toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      })}`
    }
  }
}
</script>

<style lang="less" scoped>
.customer-hero {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 4px 0 18px;
  border-bottom: 1px solid #edf0f3;
}

.customer-name {
  color: #17212b;
  font-weight: 650;
  font-size: 24px;
}

.customer-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 7px 12px;
  align-items: center;
  margin-top: 8px;
  color: #667085;
}

.customer-owner {
  min-width: 140px;
  padding-left: 16px;
  text-align: right;
  border-left: 1px solid #edf0f3;

  span,
  small {
    display: block;
    color: #8a96a3;
    font-size: 12px;
  }

  strong {
    display: block;
    margin: 3px 0;
    color: #344054;
    font-size: 17px;
  }
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin: 16px 0;
}

.metric-item {
  min-width: 0;
  padding: 11px 12px;
  background: #f8fafb;
  border: 1px solid #e8edf1;
  border-radius: 5px;

  span {
    display: block;
    margin-bottom: 4px;
    color: #7b8794;
    font-size: 11px;
  }

  strong {
    display: block;
    overflow: hidden;
    color: #17212b;
    font-size: 18px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .metric-date {
    font-weight: 500;
    font-size: 13px;
  }
}

.detail-section {
  margin-top: 18px;

  h3 {
    margin-bottom: 8px;
    font-size: 15px;
  }
}

.tab-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  color: #667085;
}

.followup-timeline {
  max-width: 900px;
  padding-top: 8px;
}

.timeline-head {
  display: flex;
  gap: 8px;
  align-items: center;
}

.timeline-content {
  margin-top: 6px;
  color: #344054;
  white-space: pre-wrap;
}

.timeline-next {
  margin-top: 5px;
  color: #667085;
  font-size: 12px;
}

@media (max-width: 1100px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 650px) {
  .customer-hero {
    flex-direction: column;
  }

  .customer-owner {
    padding: 10px 0 0;
    text-align: left;
    border-top: 1px solid #edf0f3;
    border-left: 0;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
