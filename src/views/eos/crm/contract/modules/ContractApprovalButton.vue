<template>
  <a-button v-if="record.approvalTask && record.approvalTask.taskId" :type="type" :size="size" :loading="opening" @click.stop="open">去审批</a-button>
</template>

<script>
import { getContractApprovalTask } from '/src/api/eos/crm/contracts.js'

export default {
  name: 'ContractApprovalButton',
  props: {
    record: { type: Object, required: true },
    type: { type: String, default: 'link' },
    size: { type: String, default: 'small' }
  },
  data: () => ({ opening: false }),
  methods: {
    async open() {
      if (this.opening) return
      this.opening = true
      try {
        const { data: task } = await getContractApprovalTask(this.record.id)
        if (!task || !task.taskId || task.type !== 'todo') throw new Error('审批任务已变化，请刷新列表后重试')
        const { default: BusDetail } = await import('/src/views/eos/todoItem/todo/busDetail.vue')
        this.$createAntdDrawer({
          stopPropagation: true,
          drawerProps: { title: '合同审批', width: '80%', maskClosable: true },
          content: {
            template: BusDetail,
            props: { todoItem: task, detailCompName: 'WorkflowActionTodoDetail', row: task.data || task.form || {}, formId: task.formId, formKey: task.formKey, isOnlyShowProcess: false },
            on: { getList: () => this.$emit('changed') }
          }
        })
      } catch (error) {
        this.$message.error(error.message || '审批内容加载失败，请重试')
        this.$emit('changed')
      } finally {
        this.opening = false
      }
    }
  }
}
</script>
