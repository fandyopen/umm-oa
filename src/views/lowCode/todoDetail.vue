<template>
  <s-descriptions title="表单数据" :formItems="formItems" :formData="normalizedRow" />
</template>

<script>
import SDescriptions from '/src/components/Descriptions/index.jsx'

export default {
  name: 'LowCodeTodoDetail',
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
        result[key] = Array.isArray(value) ? value.join(' / ') : value
      })
      return result
    },
    formItems() {
      return Object.keys(this.row || {})
        .filter(key => !['id', 'state', 'status', 'title', '_id', 'createBy', 'createTime'].includes(key))
        .map(key => ({ label: key, prop: key }))
    }
  }
}
</script>
