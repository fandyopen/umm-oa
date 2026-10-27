<template>
  <div>
    <component :is="detailCompName" :row="row" :formId="formId" :formKey="formKey" />
    <a-divider>审批意见</a-divider>
    <view-verify :isOnlyShowProcess="isOnlyShowProcess" :isShowReApply="false" :todoItem="todoItem" @getList="handleGetList" @delegateTask="handleDelegateTask" @close="handleClose" />
  </div>
</template>

<script>
import ViewVerify from '/src/components/Activiti/ViewVerify/index.vue'
import UserSelect from '/src/components/Activiti/UserSelect'
import LowCodeTodoDetail from '/src/views/lowCode/todoDetail.vue'
import WorkflowActionTodoDetail from '/src/views/workflow/todoDetail.vue'

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
    WorkflowActionTodoDetail
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
