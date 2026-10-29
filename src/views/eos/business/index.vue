<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-card :bordered="false">
      <div class="page-intro">
        <div>
          <h2>商机管理</h2>
          <p>将客户潜在需求推进为正式合作，跟踪阶段、预计成交时间和负责人。</p>
        </div>
        <a-button type="primary" icon="plus" @click="handleAdd" v-hasModuleAction="'add'">新增商机</a-button>
      </div>

      <div class="opportunity-filter">
        <a-input-search v-model="queryParam.queryAll" placeholder="搜索商机名称、编号或客户" allow-clear style="width: 300px" @search="refresh" />
        <a-select v-model="queryParam.customerId" placeholder="全部客户" allow-clear show-search :filter-option="false" style="width: 220px" @search="searchCustomers" @change="refresh">
          <a-select-option v-for="customer in customers" :key="customer.id" :value="customer.id">
            {{ customer.name }}
          </a-select-option>
        </a-select>
        <a-select v-model="queryParam.stage" placeholder="全部阶段" allow-clear style="width: 150px" @change="refresh">
          <a-select-option v-for="stage in stages" :key="stage" :value="stage">{{ stage }}</a-select-option>
        </a-select>
        <a-button icon="reload" @click="resetFilters">重置</a-button>
      </div>

      <no-data v-show="noData" />
      <s-table v-show="!noData" :customRow="rowClick" ref="table" size="default" rowKey="id" :columns="columns" :data="loadData">
        <span slot="stage" slot-scope="text, record">
          <a-tag :color="stageColor(text || record.businessState)">{{ text || record.businessState || '未设置' }}</a-tag>
        </span>
        <span slot="amount" slot-scope="text">
          {{ formatMoney(text || 0) }}
        </span>
        <span slot="action" slot-scope="text, record">
          <s-action-columns :actions="actions" :record="record" />
        </span>
      </s-table>
    </a-card>
  </page-header-wrapper>
</template>

<script lang="jsx">
import { delBusiness, listBusiness } from '/src/api/eos/business/business.js'
import { listCustomerOptions } from '/src/api/eos/crm/index.js'
import { Ellipsis, SActionColumns, STable } from '/src/components/index.js'
import BusinessDetail from './modules/BusinessDetail.vue'
import BusinessForm from './modules/BusinessForm.vue'
import BusinessStageForm from './modules/BusinessStageForm.vue'

export default {
  name: 'Business',
  components: { SActionColumns, STable },
  data() {
    return {
      noData: false,
      customers: [],
      stages: ['初步接洽', '需求确认', '方案报价', '商务谈判', '赢单', '输单'],
      queryParam: {},
      rowClick: record => ({
        on: {
          click: () => this.handleDetail(record)
        },
        style: { cursor: 'pointer' }
      }),
      actions: [
        {
          label: '推进阶段',
          icon: 'iconfont-zhiyeshengyamubiao',
          actionCode: 'track',
          action: this.handleTrace
        },
        {
          label: '修改',
          icon: 'iconfont-bianji',
          actionCode: 'edit',
          action: this.handleUpdate
        },
        {
          label: '删除',
          icon: 'iconfont-shanchu',
          actionCode: 'remove',
          action: this.handleDelete
        }
      ],
      loadData: param => {
        return listBusiness({ ...param, ...this.queryParam }).then(res => {
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
          title: '商机名称',
          dataIndex: 'name',
          align: 'left',
          customRender: (text, record) => (
            <div>
              <div class="opportunity-name">{text}</div>
              <div class="opportunity-code">{record.code || '无编号'}</div>
            </div>
          )
        },
        {
          title: '客户',
          dataIndex: 'customerName',
          align: 'left',
          customRender: (text, record) => text || record.ownerUnit || '-'
        },
        {
          title: '阶段',
          dataIndex: 'stage',
          width: 120,
          align: 'center',
          scopedSlots: { customRender: 'stage' }
        },
        {
          title: '潜在合作',
          dataIndex: 'potentialCooperation',
          align: 'left',
          customRender: text =>
            text ? (
              <Ellipsis length={22} tooltip>
                {text}
              </Ellipsis>
            ) : (
              '-'
            )
        },
        {
          title: '合作意向',
          dataIndex: 'cooperationIntent',
          width: 100,
          align: 'center'
        },
        {
          title: '预计成交时间',
          dataIndex: 'expectedCloseDate',
          width: 130,
          align: 'center'
        },
        {
          title: '预计金额',
          dataIndex: 'expectedAmount',
          width: 140,
          align: 'right',
          scopedSlots: { customRender: 'amount' }
        },
        {
          title: '负责人',
          dataIndex: 'ownerUserName',
          width: 120,
          align: 'center',
          customRender: (text, record) => text || record.sysUser?.nickName || '-'
        },
        {
          title: '操作',
          align: 'center',
          width: 100,
          scopedSlots: { customRender: 'action' }
        }
      ]
    }
  },
  mounted() {
    this.loadCustomers()
  },
  methods: {
    loadCustomers(keyword = '') {
      listCustomerOptions({ name: keyword }).then(res => {
        this.customers = res.data || []
      })
    },
    searchCustomers(keyword) {
      this.loadCustomers(keyword)
    },
    resetFilters() {
      this.queryParam = {}
      this.refresh()
    },
    handleAdd() {
      this.openForm({})
    },
    handleUpdate(row) {
      this.openForm({ isUpdate: true, updateRow: row })
    },
    openForm(props) {
      this.$createAntdDrawer({
        stopPropagation: true,
        drawerProps: {
          title: props.isUpdate ? '修改商机' : '新增商机',
          width: '66%',
          maskClosable: true
        },
        content: {
          template: BusinessForm,
          props,
          on: {
            ok: () => this.refresh(),
            close: () => {}
          }
        }
      })
    },
    handleDetail(row) {
      this.$createAntdDrawer({
        stopPropagation: true,
        drawerProps: {
          title: '商机详情',
          width: '64%',
          maskClosable: true
        },
        content: {
          template: BusinessDetail,
          props: { formId: row.id }
        }
      })
    },
    handleTrace(row) {
      this.$createAntdModal({
        stopPropagation: true,
        modalProps: {
          title: '推进商机阶段',
          width: 620,
          centered: true,
          maskClosable: true
        },
        footer: false,
        content: {
          template: BusinessStageForm,
          props: {
            formId: row.id,
            businessCode: row.code
          },
          on: {
            saveSuccess: () => this.refresh()
          }
        }
      })
    },
    handleDelete(row) {
      this.$antdconfirm({
        title: '删除商机',
        content: `确认删除“${row.name}”吗？`,
        onOk: () =>
          delBusiness(row.id).then(() => {
            this.refresh()
            this.$message.success('商机已删除')
          })
      })
    },
    refresh() {
      this.$refs.table.refresh(true)
    },
    stageColor(stage) {
      return {
        赢单: 'green',
        输单: 'red',
        商务谈判: 'orange',
        方案报价: 'purple',
        需求确认: 'blue',
        初步接洽: 'default'
      }[stage] || 'default'
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
.page-intro {
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

.opportunity-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  align-items: center;
  margin-bottom: 15px;
  padding: 12px;
  background: #f8fafb;
  border: 1px solid #e8edf1;
  border-radius: 6px;
}

.opportunity-name {
  color: #1769aa;
  font-weight: 600;
}

.opportunity-code {
  margin-top: 2px;
  color: #98a2b3;
  font-size: 11px;
}

@media (max-width: 700px) {
  .page-intro {
    flex-direction: column;
  }

  .opportunity-filter :deep(.ant-input-search),
  .opportunity-filter :deep(.ant-select) {
    width: 100% !important;
  }
}
</style>
