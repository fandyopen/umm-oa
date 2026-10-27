<template>
  <s-descriptions title="业务变更申请" :formItems="formItems" :formData="normalizedRow" />
</template>

<script>
import SDescriptions from '/src/components/Descriptions/index.jsx'

const labels = {
  title: '申请事项',
  actionName: '审批动作',
  nickName: '成员姓名',
  phonenumber: '手机号',
  email: '邮箱',
  deptId: '目标部门',
  userIds: '成员ID',
  remark: '备注'
}

export default {
  name: 'WorkflowActionTodoDetail',
  components: { SDescriptions },
  props: {
    row: {
      type: Object,
      default: () => ({})
    }
  },
  computed: {
    normalizedRow() {
      const result = {}
      Object.keys(this.row || {}).forEach(key => {
        const value = this.row[key]
        result[key] = Array.isArray(value) ? value.join('、') : value
      })
      return result
    },
    formItems() {
      return Object.keys(this.row || {})
        .filter(key => labels[key])
        .map(key => ({ label: labels[key], prop: key }))
    }
  }
}
</script>
