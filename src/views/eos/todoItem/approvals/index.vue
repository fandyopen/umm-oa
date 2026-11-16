<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-card :bordered="false" class="approval-inbox">
      <div class="approval-heading">
        <div>
          <h2>代审批</h2>
          <p>查看各部门的流程审批，追踪发起人、指定审批人与实际审批人。</p>
        </div>
        <a-button icon="reload" :loading="loading" @click="load">刷新</a-button>
      </div>
      <a-tabs v-model="tab" @change="search"> <a-tab-pane key="todo" tab="待审批" /><a-tab-pane key="done" tab="已审批" /><a-tab-pane key="all" tab="全部" /> </a-tabs>
      <div class="approval-filters">
        <a-input v-model="filters.keyword" allow-clear placeholder="审批事项、发起人或审批人" @pressEnter="search" />
        <a-tree-select v-model="filters.deptId" :tree-data="departmentTree" allow-clear tree-default-expand-all placeholder="审批部门（含下级）" @change="search" />
        <a-select v-model="filters.assigneeId" allow-clear show-search option-filter-prop="children" placeholder="指定审批人" @change="search">
          <a-select-option v-for="user in users" :key="user.userId" :value="user.userId">{{ user.nickName }} · {{ user.deptName || '未分配部门' }}（{{ user.userName }}）</a-select-option>
        </a-select>
        <a-select v-model="filters.starterId" allow-clear show-search option-filter-prop="children" placeholder="发起人" @change="search">
          <a-select-option v-for="user in users" :key="user.userId" :value="user.userId">{{ user.nickName }}（{{ user.userName }}）</a-select-option>
        </a-select>
        <a-select v-model="filters.moduleId" allow-clear show-search option-filter-prop="children" placeholder="业务模块" @change="search">
          <a-select-option v-for="item in businesses" :key="item.moduleId" :value="item.moduleId">{{ item.moduleName }}</a-select-option>
        </a-select>
        <a-range-picker v-model="dates" :placeholder="['发起开始日期', '发起结束日期']" @change="search" />
        <a-space><a-button type="primary" icon="search" @click="search">查询</a-button><a-button @click="reset">重置</a-button></a-space>
      </div>
      <a-alert v-if="loadError" :message="loadError" type="error" show-icon class="approval-error" />
      <a-table row-key="taskId" size="small" :columns="columns" :data-source="rows" :loading="loading" :scroll="{ x: 1830 }" :pagination="pagination" @change="pageChanged">
        <template slot="approvalTitle" slot-scope="text, row"
          ><a @click="open(row, false)">{{ text || '审批申请' }}</a></template
        >
        <template slot="starter" slot-scope="text, row"
          ><div>{{ row.starterName || '—' }}</div>
          <small>{{ row.starterDeptName || '未分配部门' }}</small></template
        >
        <template slot="assignee" slot-scope="text, row"
          ><div>{{ row.assigneeName || '—' }}</div>
          <small>{{ row.approverDeptName || '未分配部门' }}</small></template
        >
        <template slot="operator" slot-scope="text, row"
          ><div>{{ row.operatorName || (row.taskStatus === 'done' ? '历史记录未记载' : '—') }}</div>
          <small v-if="row.operatorName && String(row.operatorId) !== String(row.assigneeId)">代 {{ row.assigneeName }} 审批</small></template
        >
        <template slot="time" slot-scope="text">{{ dateTime(text) }}</template>
        <template slot="state" slot-scope="text, row"
          ><a-tag :color="state(row).color">{{ state(row).label }}</a-tag></template
        >
        <template slot="operation" slot-scope="text, row">
          <a @click="open(row, false)">查看</a><template v-if="row.canApprove"><a-divider type="vertical" /><a @click="open(row, true)">代审批</a></template>
        </template>
      </a-table>
    </a-card>
  </page-header-wrapper>
</template>

<script>
import moment from 'moment'
import { listApprovals, approvalOptions, approvalDetail } from '/src/api/activiti/approvals.js'

const emptyFilters = () => ({ keyword: '', deptId: undefined, assigneeId: undefined, starterId: undefined, moduleId: undefined })
export default {
  name: 'ApprovalInbox',
  data: () => ({
    tab: 'todo',
    filters: emptyFilters(),
    dates: [],
    departments: [],
    users: [],
    businesses: [],
    rows: [],
    loading: false,
    loadError: '',
    opening: false,
    sequence: 0,
    page: 1,
    pageSize: 15,
    total: 0,
    columns: [
      { title: '审批事项', dataIndex: 'title', width: 280, ellipsis: true, scopedSlots: { customRender: 'approvalTitle' } },
      { title: '业务模块', dataIndex: 'businessLabel', width: 150, ellipsis: true },
      { title: '发起人 / 部门', key: 'starter', width: 155, scopedSlots: { customRender: 'starter' } },
      { title: '发起时间', dataIndex: 'startTime', width: 175, scopedSlots: { customRender: 'time' } },
      { title: '指定审批人 / 部门', key: 'assignee', width: 165, scopedSlots: { customRender: 'assignee' } },
      { title: '审批节点', dataIndex: 'taskName', width: 160, ellipsis: true },
      { title: '推送时间', dataIndex: 'pushTime', width: 175, scopedSlots: { customRender: 'time' } },
      { title: '实际审批人', key: 'operator', width: 180, scopedSlots: { customRender: 'operator' } },
      { title: '审批时间', dataIndex: 'completeTime', width: 175, scopedSlots: { customRender: 'time' } },
      { title: '节点状态', key: 'state', width: 105, scopedSlots: { customRender: 'state' } },
      { title: '操作', key: 'operation', width: 150, fixed: 'right', align: 'center', scopedSlots: { customRender: 'operation' } }
    ]
  }),
  computed: {
    pagination() {
      return { current: this.page, pageSize: this.pageSize, total: this.total, showSizeChanger: true, showTotal: total => `共 ${total} 条审批任务` }
    },
    departmentTree() {
      const map = new Map(this.departments.map(d => [d.deptId, { title: d.deptName, value: d.deptId, key: d.deptId, children: [] }]))
      const roots = []
      this.departments.forEach(d => {
        const node = map.get(d.deptId)
        const parent = map.get(d.parentId)
        if (parent) parent.children.push(node)
        else roots.push(node)
      })
      return roots
    }
  },
  async created() {
    this.load()
    try {
      const { data } = await approvalOptions()
      this.departments = data.departments || []
      this.users = data.users || []
      this.businesses = data.businesses || []
    } catch (error) {
      this.$message.error(error.message || '筛选选项加载失败')
    }
  },
  beforeDestroy() {
    this.sequence++
  },
  methods: {
    dateTime(value) {
      return value && moment(value).isValid() ? moment(value).format('YYYY-MM-DD HH:mm:ss') : '—'
    },
    state(row) {
      if (row.taskStatus === 'done') return row.decision === 'rejected' ? { label: '已驳回', color: 'red' } : { label: row.decision === 'approved' ? '已同意' : '已审批', color: 'green' }
      if (row.taskStatus === 'todo' && row.instanceStatus === 'running') return { label: '待审批', color: 'blue' }
      return { label: ['withdrawn', 'cancelled', 'canceled'].includes(row.instanceStatus) ? '已撤回' : '已停止', color: undefined }
    },
    search() {
      this.page = 1
      this.load()
    },
    reset() {
      this.filters = emptyFilters()
      this.dates = []
      this.search()
    },
    pageChanged(pagination) {
      this.page = pagination.current
      this.pageSize = pagination.pageSize
      this.load()
    },
    async load() {
      const sequence = ++this.sequence
      this.loading = true
      this.loadError = ''
      try {
        const { data } = await listApprovals({ ...this.filters, tab: this.tab, pageNum: this.page, pageSize: this.pageSize, startDate: this.dates[0]?.format('YYYY-MM-DD'), endDate: this.dates[1]?.format('YYYY-MM-DD') })
        if (sequence !== this.sequence) return
        this.rows = data.rows || []
        this.total = data.total || 0
      } catch (error) {
        if (sequence === this.sequence) {
          this.rows = []
          this.total = 0
          this.loadError = error.message || '审批列表加载失败'
        }
      } finally {
        if (sequence === this.sequence) this.loading = false
      }
    },
    async open(row, approve) {
      if (this.opening) return
      this.opening = true
      try {
        const { data: task } = await approvalDetail(row.taskId)
        if (approve && !task.canApprove) {
          this.search()
          throw new Error('当前申请已办理或不允许代审批，请刷新后重试')
        }
        const { default: Detail } = await import('./ApprovalDetail.vue')
        this.$createAntdDrawer({
          stopPropagation: true,
          drawerProps: { title: approve ? '代审批' : '审批详情', width: 'min(1200px, 96vw)', maskClosable: true },
          content: { template: Detail, props: { task, readonly: !approve }, on: { getList: () => this.load() } }
        })
      } catch (error) {
        this.$message.error(error.message || '审批详情加载失败')
      } finally {
        this.opening = false
      }
    }
  }
}
</script>

<style scoped lang="less">
.approval-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  h2 {
    font-size: 20px;
    margin-bottom: 6px;
  }
  p {
    color: #7a8794;
    margin: 0;
  }
}
.approval-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 0 0 20px;
  > * {
    width: 210px;
  }
  > .ant-calendar-picker {
    width: 290px;
  }
  > .ant-space {
    width: auto;
  }
}
.approval-error {
  margin-bottom: 16px;
}
.approval-inbox /deep/ .ant-table {
  font-size: 13px;
  small {
    font-size: 12px;
    color: #7a8794;
  }
}
@media (max-width: 640px) {
  .approval-filters > * {
    width: 100%;
  }
  .approval-heading {
    align-items: flex-start;
  }
}
</style>
