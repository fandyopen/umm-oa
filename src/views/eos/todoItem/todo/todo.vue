<!-- 待办事项 -->
<template>
  <div>
    <template v-if="!simple">
      <s-search placeholder="搜索此列表..." clickTips="搜索姓名、流程名称、审批" @onSearch="onSearch" />
      <a-select v-model="businessCategory" class="tw-w-[200px] tw-mb-4" @change="changeCategory"><a-select-option value="">全部待办</a-select-option><a-select-option value="contract">合同审批</a-select-option></a-select>
    </template>
    <!-- 数据表格 -->
    <no-data index v-show="noData" />
    <s-table :customRow="rowClick" class="todo-list" :class="{ 'dashboard-table': simple }" v-show="!noData" :data="loadData" ref="table" :size="simple ? 'small' : 'default'" tableLayout="fixed" :scroll="{ x: simple ? 720 : 1100 }" :showPagination="!simple" :rowKey="item => item.taskId || item.id" :columns="columns">
      <span slot="action" slot-scope="text, item" class="todo-actions">
        <a v-if="isHrReminder(item)" @click.stop="openHrReminder(item)">进入办理</a>
        <a v-else-if="item.businessType === 'crmCustomerFollowup'" @click.stop="openCustomerReminder(item)">跟进客户</a>
        <a v-else-if="item.businessType === 'crmOpportunityFollowup'" @click.stop="openOpportunityReminder(item)">跟进商机</a>
        <a v-else-if="['crmRisk', 'crmContractApproval'].includes(item.businessType)" @click.stop="handleDetail(item, 'CrmTodoDetail')">{{ item.businessType === 'crmContractApproval' ? '审批合同' : '处理风险' }}</a>
        <activiti-apply
          :preAudit="{ comment: item.lastHistoricActivity.comment, assigneeName: item.lastHistoricActivity.assigneeName }"
          :simple="simple"
          class="tw-mr-2.5 tw-ml-auto"
          slot="actions"
          v-else-if="item && item.taskId"
          :todoItem="item"
          @getList="refresh"
          @markUnread="handleMarkUnread(item)"
        />
      </span>
    </s-table>
  </div>
</template>

<script lang="jsx">
import moment from 'moment'
import { listTodo } from '/src/api/activiti/process.js'
import { busFlowMap, handleProcessHistory } from '/src/views/eos/config/busUtil.js'
import { updateReadUsers } from '/src/api/activiti/workflow.js'

import STable from '/src/components/Table/index.jsx'
import SSearch from '/src/components/Form/Search.vue'
import ActivitiApply from '/src/components/Activiti/index.vue'
import BusDetail from './busDetail.vue'
import customerReminder, { CUSTOMER_FOLLOWUP_TODO, OPPORTUNITY_FOLLOWUP_TODO } from '../customerReminder.js'
import hrReminder from '../hrReminder.js'

export default {
  mixins: [customerReminder, hrReminder],
  name: 'Todo',
  props: {
    // 是否展示简单列
    simple: {
      type: Boolean,
      default: false
    }
  },
  components: {
    ActivitiApply,
    SSearch,
    STable
  },
  data() {
    return {
      /* 是否有数据 */
      noData: false,
      loading: false,
      rowClick: record => ({
        on: {
          click: () => {
            this.handleReadClick(record)
            const { busDetailCompName } = busFlowMap[record.businessType] || {}
            this.handleDetail(record, busDetailCompName)
          }
        },
        style: {
          cursor: 'pointer'
        }
      }),
      busFlowMap,
      // 查询参数
      businessCategory: !this.simple && this.$route.query.businessCategory === 'contract' ? 'contract' : '',
      queryParam: !this.simple && this.$route.query.businessCategory === 'contract' ? { businessCategory: 'contract' } : {},
      // 加载数据方法 必须为 Promise 对象
      loadData: param => {
        if (this.simple) {
          param.pageNum = 1
          param.pageSize = 4
        }
        return listTodo(Object.assign({}, param, this.queryParam)).then(res => {
          this.noData = res.total === 0
          this.$store.commit('SET_TODOTOTAL', res.unfilteredTotal ?? res.total)
          return {
            data: res.rows,
            totalCount: this.simple ? 0 : res.total,
            pageNum: param.pageNum,
            pageSize: param.pageSize
          }
        })
      },
      columns: [
        {
          title: '姓名',
          dataIndex: 'applyUserName',
          align: 'left',
          width: this.simple ? 96 : 150,
          customRender: (text, record) => {
            return <span class={['todo-cell', this.isReadTask(record)]} title={text}>{text || '—'}</span>
          }
        },
        {
          title: '流程名称',
          dataIndex: 'processName',
          align: 'left',
          customRender: (text, record) => {
            const processName = record.processName || busFlowMap[record.businessType]?.processName || '审批流程'
            return <span class={['todo-cell', this.isReadTask(record)]} title={processName}>{processName}</span>
          },
          width: this.simple ? 104 : 140
        },
        {
          title: '审批',
          dataIndex: 'taskName',
          align: 'left',
          width: this.simple ? 112 : 170,
          customRender: (text, record) => {
            return <span class={['todo-cell', this.isReadTask(record)]} title={text}>{text || '—'}</span>
          }
        },
        {
          title: '项目名称',
          dataIndex: 'name',
          align: 'left',
          customRender: (text, record) => {
            let templateProp = null
            const { busFieldProp, busFieldProp2 } = busFlowMap[record.businessType] || {}
            templateProp = this._.get(record.form, busFieldProp, '') ? busFieldProp : busFieldProp2
            const name = this._.get(record.form, templateProp, '') || record.title || text || '—'
            return (
              <div class={['todo-cell', 'todo-subject', this.isReadTask(record), 'tw-cursor-pointer']} title={name}>
                {name}
              </div>
            )
          }
        },
        {
          title: '推送时间',
          dataIndex: 'createTime',
          align: 'left',
          width: this.simple ? 144 : 180,
          customRender: text => {
            const time = text ? moment(text) : null
            const label = time && time.isValid() ? time.format('YYYY-MM-DD HH:mm:ss') : '—'
            return <span class="todo-cell todo-push-time" title={label}>{label}</span>
          }
        },
        {
          title: '操作',
          align: 'center',
          fixed: 'right',
          scopedSlots: { customRender: 'action' },
          width: this.simple ? 88 : 170
        }
      ]
    }
  },
  watch: {
    '$route.query.businessCategory'(value) {
      if (this.simple) return
      this.businessCategory = value === 'contract' ? 'contract' : ''
      this.queryParam = { ...this.queryParam, businessCategory: this.businessCategory }
      this.refresh(true)
    }
  },
  methods: {
    changeCategory(value) {
      this.queryParam = { ...this.queryParam, businessCategory: value }
      const query = { ...this.$route.query }
      if (value) query.businessCategory = value
      else delete query.businessCategory
      if (this.$route.query.businessCategory !== query.businessCategory) this.$router.replace({ path: this.$route.path, query })
      else this.refresh(true)
    },
    async handleDetail(row, detailCompName) {
      if (this.isHrReminder(row)) return this.openHrReminder(row)
      if (row.businessType === CUSTOMER_FOLLOWUP_TODO) return this.openCustomerReminder(row)
      if (row.businessType === OPPORTUNITY_FOLLOWUP_TODO) return this.openOpportunityReminder(row)
      if (this.isEmpty(detailCompName)) {
        return this.$message.error('请传递详情组件')
      }
      const fd = {
        row: row.form,
        formId: row.formId,
        formKey: row.formKey
      }
      if (!this.isEmpty(row.form.sealProject) && !this.isEmpty(row.form.sealProject.id)) {
        fd.projectId = row.form.sealProject.id
      }
      // 标记已读
      this.$emit('read')
      this.$createAntdDrawer({
        stopPropagation: true,
        drawerProps: {
          title: '待办详情',
          width: '80%',
          maskClosable: true
        },
        content: {
          template: BusDetail,
          props: {
            ...fd,
            detailCompName: detailCompName,
            todoItem: row,
            isOnlyShowProcess: false
          },
          on: {
            getList: () => {
              this.refresh()
              this.$emit('refresh')
              this.$emit('close')
            }
          }
        }
      })
    },
    // 查看审批历史
    handleProcessHistory,
    // 已读事件
    handleReadClick(item) {
      if (this.isHrReminder(item)) return
      if ([CUSTOMER_FOLLOWUP_TODO, OPPORTUNITY_FOLLOWUP_TODO].includes(item.businessType)) {
        this.$set(item, 'isRead', true)
        return
      }
      updateReadUsers(item.workflowId, item.taskId, 1).then(res => {
        // 设置本地缓存
        this.$set(item, 'isRead', true)
      })
    },
    // 未读事件
    handleMarkUnread(item) {
      updateReadUsers(item.workflowId, item.taskId, 0).then(res => {
        // 设置本地缓存
        this.$set(item, 'isRead', false)
      })
    },
    // 判断是否已读
    isReadTask(item) {
      if (item.isRead !== undefined && item.isRead !== null) {
        return item.isRead ? '' : 'info-unread'
      }
      if (!this.isEmpty(item.readUsers)) {
        const readUsers = item.readUsers
        const userObj = JSON.parse(readUsers)
        if (!this.isEmpty(userObj[item.taskId])) {
          if (userObj[item.taskId].indexOf(this.$store.getters.userName) !== -1) {
            return ''
          }
        }
      }
      return 'info-unread'
    },
    /* 查询按钮 */
    onSearch(data, event) {
      // 映射businessType为英文做条件查询
      let queryAllBusType = null
      for (const key in busFlowMap) {
        const value = busFlowMap[key]
        if (!this.isEmpty(value) && !this.isEmpty(value.processName) && value.processName.indexOf(data) !== -1) {
          queryAllBusType = key
          break
        }
      }
      this.queryParam = { ...this.queryParam, queryAll: data, queryAllBusType }
      this.refresh(true)
    },
    // 刷新页面
    refresh(resetPage = false) {
      this.$refs.table.refresh(resetPage)
    }
  }
}
</script>
<style lang="less">
.todo-list {
  min-width: 0;

  .todo-cell {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .todo-push-time {
    color: #86909c;
    font-variant-numeric: tabular-nums;
  }

  .todo-actions {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    white-space: nowrap;
  }
}

</style>
