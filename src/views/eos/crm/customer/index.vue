<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-card :bordered="false">
      <div class="crm-header">
        <div>
          <h2>客户档案</h2>
          <p>统一维护客户主档、联系人、负责人和合作数据，避免重复建档。</p>
        </div>
        <div class="crm-header-actions">
          <a-button v-if="canAction('edit')" ghost type="primary" icon="swap" @click="openHandover">批量移交</a-button>
          <a-upload
            v-if="canAction('add')"
            accept=".xlsx,.xls"
            :show-upload-list="false"
            :before-upload="handleImportFile"
          >
            <a-button icon="upload" :loading="importing">导入</a-button>
          </a-upload>
          <a-button icon="file-excel" @click="downloadImportTemplate">导入模板</a-button>
          <a-button v-if="canAction('export')" icon="download" @click="handleExport">导出</a-button>
          <a-button type="primary" icon="plus" @click="handleAdd" v-hasModuleAction="'add'">新增客户</a-button>
        </div>
      </div>

      <a-tabs v-model="activePool" :animated="false" @change="handlePoolChange">
        <a-tab-pane key="mine" tab="客户档案" />
        <a-tab-pane key="public" tab="客户公海" />
      </a-tabs>

      <a-form layout="inline" class="crm-filter" :form="filterForm">
        <a-form-item label="公司名称">
          <a-input v-model="queryParam.name" allow-clear placeholder="名称" style="width: 170px" />
        </a-form-item>
        <a-form-item label="行业">
          <a-select v-model="queryParam.industry" allow-clear placeholder="全部" style="width: 150px">
            <a-select-option v-for="item in options.industries" :key="item" :value="item">{{ item }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="规模">
          <a-select v-model="queryParam.companyScale" allow-clear placeholder="全部" style="width: 130px">
            <a-select-option v-for="item in options.scales" :key="item" :value="item">{{ item }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="地区">
          <a-select v-model="queryParam.region" allow-clear placeholder="全部" style="width: 120px">
            <a-select-option v-for="item in options.regions" :key="item" :value="item">{{ item }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="企业性质">
          <a-select v-model="queryParam.enterpriseNature" allow-clear placeholder="全部" style="width: 140px">
            <a-select-option v-for="item in options.natures" :key="item" :value="item">{{ item }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="客户来源">
          <a-select v-model="queryParam.customerSource" allow-clear placeholder="全部" style="width: 140px">
            <a-select-option v-for="item in options.sources" :key="item" :value="item">{{ item }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="合作状态">
          <a-select v-model="queryParam.cooperationStatus" allow-clear placeholder="全部" style="width: 130px">
            <a-select-option v-for="item in options.statuses" :key="item" :value="item">{{ item }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" icon="search" @click="handleSearch">查询</a-button>
            <a-button icon="reload" @click="resetSearch">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>

      <no-data v-show="noData" />
      <s-table
        v-show="!noData"
        ref="table"
        :customRow="rowClick"
        size="default"
        rowKey="id"
        :columns="columns"
        :data="loadData"
      >
        <span slot="status" slot-scope="text">
          <a-tag :color="statusColor(text)">{{ text || '未设置' }}</a-tag>
        </span>
        <span slot="owner" slot-scope="text">
          <div class="owner-cell">
            <a-avatar size="small" :style="{ backgroundColor: '#eaf3fb', color: '#1769aa' }">
              {{ (text || '?').slice(0, 1) }}
            </a-avatar>
            <span>{{ text || '未分配' }}</span>
          </div>
        </span>
        <span slot="activity" slot-scope="text, record">
          <span :class="{ 'activity-stale': isStale(record.lastFollowupTime) }">
            {{ text || '暂无跟进' }}
          </span>
        </span>
        <span slot="action" slot-scope="text, record">
          <a-dropdown :trigger="['click']">
            <a-button type="link" icon="ellipsis" title="更多操作" @click.stop />
            <a-menu slot="overlay" @click="({ key }) => handleAction(key, record)">
              <a-menu-item key="detail"><a-icon type="profile" /> 查看详情</a-menu-item>
              <a-menu-item v-if="canAction('track')" key="followup"><a-icon type="message" /> 新增跟进</a-menu-item>
              <a-menu-item v-if="canAction('edit')" key="edit"><a-icon type="edit" /> 修改档案</a-menu-item>
              <a-menu-item v-if="canAction('edit') && record.publicPool !== '1'" key="assign"><a-icon type="user-switch" /> 客户分配</a-menu-item>
              <a-menu-item v-if="record.publicPool === '1'" key="claim"><a-icon type="import" /> 领取客户</a-menu-item>
              <a-menu-item v-if="canAction('edit') && record.publicPool !== '1'" key="release"><a-icon type="inbox" /> 移入公海</a-menu-item>
              <a-menu-divider v-if="canAction('edit') || canAction('track') || canAction('remove')" />
              <a-menu-item v-if="canAction('remove')" key="delete" danger><a-icon type="delete" /> 删除客户</a-menu-item>
            </a-menu>
          </a-dropdown>
        </span>
      </s-table>
    </a-card>
  </page-header-wrapper>
</template>

<script lang="jsx">
import STable from '/src/components/Table/index.jsx'
import { read as readXlsx, utils as xlsxUtils, writeFile as writeXlsxFile } from 'xlsx'
import store from '/src/store/index.js'
import { hasModuleAction } from '/src/utils/modulePermission.js'
import {
  claimCustomer,
  deleteCustomer,
  exportCustomers,
  importCustomers,
  listCustomers,
  releaseCustomer
} from '/src/api/eos/crm/index.js'
import AssignForm from './modules/AssignForm.vue'
import CustomerDetail from './modules/CustomerDetail.vue'
import CustomerForm from './modules/CustomerForm.vue'
import FollowupForm from './modules/FollowupForm.vue'
import HandoverForm from './modules/HandoverForm.vue'

const options = {
  industries: ['互联网', '软件与信息服务', '制造业', '建筑工程', '金融', '教育', '医疗健康', '批发零售', '其他'],
  scales: ['1-50人', '51-100人', '101-500人', '501-1000人', '1000人以上'],
  regions: ['华北', '华东', '华南', '华中', '西南', '西北', '东北', '海外'],
  natures: ['民营企业', '国有企业', '外资企业', '合资企业', '事业单位', '政府机构', '其他'],
  sources: ['官网咨询', '客户转介绍', '展会活动', '销售拓展', '渠道合作', '广告投放', '公开信息', '组织分配', '其他'],
  statuses: ['潜在客户', '初步接洽', '合作中', '重点客户', '暂停合作', '已流失']
}

const excelColumns = [
  { key: 'name', label: '公司名称' },
  { key: 'serial', label: '客户编号' },
  { key: 'industry', label: '行业' },
  { key: 'companyScale', label: '规模' },
  { key: 'region', label: '地区' },
  { key: 'enterpriseNature', label: '企业性质' },
  { key: 'customerSource', label: '客户来源' },
  { key: 'cooperationStatus', label: '合作状态' },
  { key: 'address', label: '地址' },
  { key: 'taxNumber', label: '税号' },
  { key: 'taxpayerType', label: '纳税人类别' },
  { key: 'cooperationPositions', label: '合作职位数' },
  { key: 'referralCount', label: '推荐人数' },
  { key: 'hireCount', label: '入职人数' },
  { key: 'contractAmount', label: '合同金额' },
  { key: 'receivedAmount', label: '回款金额' },
  { key: 'contractExpireDate', label: '合同到期' },
  { key: 'lastPaymentDate', label: '最近回款' },
  { key: 'remark', label: '备注' }
]

export default {
  name: 'CrmCustomers',
  components: { STable },
  data() {
    return {
      activePool: 'mine',
      noData: false,
      importing: false,
      options,
      filterForm: this.$form.createForm(this),
      queryParam: {},
      rowClick: record => ({
        on: {
          click: () => this.handleDetail(record)
        },
        style: { cursor: 'pointer' }
      }),
      loadData: param => {
        const query = {
          ...param,
          ...this.queryParam,
          publicPool: this.activePool === 'public' ? '1' : '0'
        }
        return listCustomers(query).then(res => {
          this.noData = res.total === 0
          return {
            data: res.rows || [],
            totalCount: res.total,
            pageNum: param.pageNum,
            pageSize: param.pageSize
          }
        })
      },
      columns: [
        {
          title: '公司名称',
          dataIndex: 'name',
          align: 'left',
          customRender: (text, record) => (
            <div>
              <div class="customer-link">{text}</div>
              <div class="customer-sub">{record.serial || '无客户编号'}</div>
            </div>
          )
        },
        { title: '行业', dataIndex: 'industry', width: 120 },
        { title: '规模', dataIndex: 'companyScale', width: 110 },
        { title: '地区', dataIndex: 'region', width: 90 },
        { title: '合作状态', dataIndex: 'cooperationStatus', width: 120, scopedSlots: { customRender: 'status' } },
        {
          title: '负责人',
          dataIndex: 'ownerUserName',
          width: 145,
          scopedSlots: { customRender: 'owner' }
        },
        { title: '联系人', dataIndex: 'contactCount', width: 80, align: 'center' },
        {
          title: '最近跟进',
          dataIndex: 'lastFollowupTime',
          width: 165,
          scopedSlots: { customRender: 'activity' }
        },
        {
          title: '操作',
          width: 60,
          align: 'center',
          scopedSlots: { customRender: 'action' }
        }
      ]
    }
  },
  methods: {
    canAction(action) {
      return hasModuleAction(store.getters.permissions || [], this.$route.meta, action)
    },
    downloadImportTemplate() {
      const worksheet = xlsxUtils.aoa_to_sheet([excelColumns.map(column => column.label)])
      const workbook = xlsxUtils.book_new()
      xlsxUtils.book_append_sheet(workbook, worksheet, '客户档案')
      writeXlsxFile(workbook, '客户档案导入模板.xlsx')
    },
    handleImportFile(file) {
      const reader = new FileReader()
      this.importing = true
      reader.onload = async event => {
        try {
          const workbook = readXlsx(event.target.result, { type: 'array', cellDates: false })
          const firstSheet = workbook.Sheets[workbook.SheetNames[0]]
          const sourceRows = xlsxUtils.sheet_to_json(firstSheet, { defval: '', raw: false })
          const rows = sourceRows
            .map(source => {
              const row = {}
              excelColumns.forEach(column => {
                row[column.key] = source[column.label] ?? ''
              })
              return row
            })
            .filter(row => Object.values(row).some(value => String(value).trim()))
          if (!rows.length) {
            this.$message.warning('文件中没有客户档案数据')
            return
          }
          if (rows.length > 500) {
            this.$message.warning('单次最多导入 500 条，请拆分文件后重试')
            return
          }
          const result = await importCustomers(rows)
          this.$message.success(`导入 ${result.data.imported} 条，跳过 ${result.data.skipped} 条`)
          if (result.data.errors.length) {
            this.$notification.warning({
              message: '部分客户未导入',
              description: result.data.errors
                .slice(0, 12)
                .map(item => `第 ${item.row} 行 ${item.name || ''}：${item.reason}`)
                .join('\n'),
              duration: 0
            })
          }
          this.refresh()
        } catch (error) {
          this.$message.error(error.message || '无法读取或导入此 Excel 文件')
        } finally {
          this.importing = false
        }
      }
      reader.onerror = () => {
        this.importing = false
        this.$message.error('读取 Excel 文件失败')
      }
      reader.readAsArrayBuffer(file)
      return false
    },
    handleExport() {
      const query = {
        ...this.queryParam,
        publicPool: this.activePool === 'public' ? '1' : '0'
      }
      exportCustomers(query).then(response => {
        const rows = response.data || []
        const worksheet = xlsxUtils.aoa_to_sheet([
          excelColumns.map(column => column.label),
          ...rows.map(row => excelColumns.map(column => row[column.key] ?? ''))
        ])
        const workbook = xlsxUtils.book_new()
        xlsxUtils.book_append_sheet(workbook, worksheet, '客户档案')
        writeXlsxFile(workbook, `客户档案_${new Date().toISOString().slice(0, 10)}.xlsx`)
        this.$message.success(`已导出 ${rows.length} 条客户档案`)
      })
    },
    handleSearch() {
      this.queryParam = Object.fromEntries(
        Object.entries(this.queryParam).filter(([, value]) => value !== '' && value != null)
      )
      this.$refs.table.refresh(true)
    },
    resetSearch() {
      this.queryParam = {}
      this.$refs.table.refresh(true)
    },
    handlePoolChange() {
      this.queryParam = {}
      this.$nextTick(() => this.$refs.table.refresh(true))
    },
    handleAction(key, record) {
      const actions = {
        detail: () => this.handleDetail(record),
        followup: () => this.openFollowup(record),
        edit: () => this.handleUpdate(record),
        assign: () => this.openAssign(record),
        claim: () => this.handleClaim(record),
        release: () => this.handleRelease(record),
        delete: () => this.handleDelete(record)
      }
      actions[key]?.()
    },
    handleAdd() {
      this.openCustomerForm({})
    },
    handleUpdate(record) {
      this.openCustomerForm({ isUpdate: true, updateRow: record })
    },
    openCustomerForm(props) {
      this.$createAntdDrawer({
        stopPropagation: true,
        drawerProps: {
          title: props.isUpdate ? '修改客户档案' : '新建客户档案',
          width: '72%',
          maskClosable: true
        },
        content: {
          template: CustomerForm,
          props,
          on: {
            ok: () => this.refresh(),
            close: () => {}
          }
        }
      })
    },
    handleDetail(record) {
      this.$createAntdDrawer({
        stopPropagation: true,
        drawerProps: {
          title: '客户详情',
          width: '78%',
          maskClosable: true
        },
        content: {
          template: CustomerDetail,
          props: { formId: record.id }
        }
      })
    },
    openFollowup(record) {
      this.$createAntdModal({
        modalProps: {
          title: `新增跟进 · ${record.name}`,
          width: 620,
          maskClosable: true
        },
        footer: false,
        content: {
          template: FollowupForm,
          props: { customerId: record.id },
          on: {
            ok: () => this.refresh()
          }
        }
      })
    },
    openAssign(record) {
      this.$createAntdModal({
        modalProps: {
          title: `客户分配 · ${record.name}`,
          width: 560,
          maskClosable: true
        },
        footer: false,
        content: {
          template: AssignForm,
          props: { customer: record },
          on: {
            ok: () => this.refresh()
          }
        }
      })
    },
    openHandover() {
      this.$createAntdModal({
        modalProps: {
          title: '客户批量移交',
          width: 560,
          maskClosable: true
        },
        footer: false,
        content: {
          template: HandoverForm,
          on: {
            ok: () => this.refresh()
          }
        }
      })
    },
    handleClaim(record) {
      this.$antdconfirm({
        title: '领取客户',
        content: `确认领取“${record.name}”吗？领取后你将成为客户负责人。`,
        onOk: () =>
          claimCustomer(record.id).then(() => {
            this.$message.success('客户已领取')
            this.refresh()
          })
      })
    },
    handleRelease(record) {
      this.$antdconfirm({
        title: '移入客户公海',
        content: `确认将“${record.name}”移入公海吗？`,
        onOk: () =>
          releaseCustomer(record.id).then(() => {
            this.$message.success('客户已移入公海')
            this.refresh()
          })
      })
    },
    handleDelete(record) {
      this.$antdconfirm({
        title: '删除客户',
        content: `确认删除“${record.name}”吗？无关联业务的档案会删除，已有跟进或商机的档案会停用并保留历史。`,
        onOk: () =>
          deleteCustomer(record.id).then(res => {
            this.$message.success(res.data?.disabled ? '客户已停用，历史记录已保留' : '客户已删除')
            this.refresh()
          })
      })
    },
    refresh() {
      this.$refs.table.refresh(true)
    },
    statusColor(status) {
      return {
        合作中: 'green',
        重点客户: 'purple',
        潜在客户: 'blue',
        暂停合作: 'orange',
        已流失: 'red'
      }[status] || 'default'
    },
    isStale(time) {
      if (!time) return true
      return Date.now() - new Date(time.replace(' ', 'T')).getTime() > 30 * 24 * 60 * 60 * 1000
    }
  }
}
</script>

<style lang="less" scoped>
.crm-header {
  display: flex;
  gap: 18px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 6px;

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

.crm-header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.crm-filter {
  margin: 8px 0 16px;
  padding: 12px 14px 0;
  background: #f8fafb;
  border: 1px solid #e8edf1;
  border-radius: 6px;
}

.customer-link {
  color: #1769aa;
  font-weight: 600;
}

.customer-sub {
  margin-top: 2px;
  color: #98a2b3;
  font-size: 11px;
}

.owner-cell {
  display: flex;
  gap: 7px;
  align-items: center;
}

.activity-stale {
  color: #c26b18;
}

@media (max-width: 768px) {
  .crm-header {
    flex-direction: column;
  }

  .crm-header-actions {
    width: 100%;
  }

  .crm-filter :deep(.ant-form-item) {
    width: 100%;
    margin-right: 0;
  }

  .crm-filter :deep(.ant-select),
  .crm-filter :deep(.ant-input) {
    width: 100% !important;
  }
}
</style>
