<template>
  <div>
    <template v-if="isApplication">
      <a-descriptions title="申请信息" bordered :column="{ xs: 1, sm: 2 }" size="small" class="approval-summary">
        <a-descriptions-item label="申请事项" :span="2">{{ detailRow.title || todoItem.processName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="发起人">{{ todoItem.applyUserName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="推送时间">{{ pushTime }}</a-descriptions-item>
        <a-descriptions-item label="当前节点" :span="2">{{ todoItem.taskName || '—' }}</a-descriptions-item>
      </a-descriptions>
      <a-alert v-if="todoItem.businessType === 'workflowAction'" type="info" show-icon message="以下为本次提交时保存的申请内容，后续修改按钮配置不会改变本次申请。" class="approval-snapshot-hint" />
    </template>
    <component :is="detailCompName" :row="detailRow" :formId="formId" :formKey="formKey" :todo-item="todoItem" @changed="handleGetList" @close="handleClose" />
    <template v-if="!['crmRisk', 'crmContractApproval', 'crmCustomerFollowup', 'crmOpportunityFollowup'].includes(todoItem.businessType)">
      <a-divider>流程记录与审批</a-divider>
      <view-verify :isOnlyShowProcess="isOnlyShowProcess" :isShowReApply="false" :todoItem="todoItem" :allowDelegate="!(todoItem.businessType === 'workflowAction' && detailRow.contractId)" @getList="handleGetList" @delegateTask="handleDelegateTask" @close="handleClose" />
    </template>
  </div>
</template>

<script>
import moment from 'moment'
import { approvalPayload } from '/src/utils/workflowApproval.js'
import ViewVerify from '/src/components/Activiti/ViewVerify/index.vue'
import UserSelect from '/src/components/Activiti/UserSelect'
import LowCodeTodoDetail from '/src/views/lowCode/todoDetail.vue'
import WorkflowActionTodoDetail from '/src/views/workflow/todoDetail.vue'
import CrmTodoDetail from '/src/views/eos/crm/contract/modules/CrmTodoDetail.vue'

export default {
  props: {
    // 待办项
    todoItem: {
      type: Object,
      default: () => {}
    },
    // 是否只展示流程
    isOnlyShowProcess: {
      type: Boolean,
      default: false
    },
    detailCompName: {
      type: String,
      required: true
    },
    row: {
      type: Object,
      default: () => {}
    },
    formId: {
      type: Number,
      default: null
    },
    formKey: {
      type: String,
      default: null
    }
  },
  components: {
    ViewVerify,
    LowCodeTodoDetail,
    WorkflowActionTodoDetail,
    CrmTodoDetail
  },
  computed: {
    isApplication() {
      return ['workflowAction', 'lowcode'].includes(this.todoItem.businessType)
    },
    detailRow() {
      return this.isApplication ? approvalPayload(this.todoItem, this.row) : this.row
    },
    pushTime() {
      const time = this.todoItem.createTime ? moment(this.todoItem.createTime) : null
      return time && time.isValid() ? time.format('YYYY-MM-DD HH:mm:ss') : '—'
    }
  },
  methods: {
    handleGetList() {
      this.$emit('getList')
    },
    handleDelegateTask() {
      this.handleViewUserList()
    },
    handleClose() {
      this.$emit('close')
    },
    handleViewUserList() {
      this.$createAntdDrawer({
        stopPropagation: true,
        drawerProps: {
          title: '选择转办的人员',
          width: '50%',
          maskClosable: true
        },
        content: {
          template: UserSelect,
          props: {
            taskId: this.todoItem.taskId,
            dataId: this.todoItem.formId,
            businessType: this.todoItem.businessType
          },
          on: {
            getList: () => {
              this.$emit('getList')
              this.$emit('close')
              this.$emit('ok')
            }
            // 注意，这里的close事件内部组件传递不出来
            // close: () => {}
          }
        }
      })
    }
  }
}
</script>

<style scoped>
.approval-summary { margin-bottom: 16px; }
.approval-snapshot-hint { margin-bottom: 16px; }
</style>
