<template><a-spin tip="正在打开对应业务页面…" /></template>
<script>
import { contractEntryTarget } from '../navigation.js'

export default {
  name: 'LegacyContractEntry',
  props: { mode: { type: String, required: true } },
  mounted() { this.$router.replace(contractEntryTarget(this.mode, this.$route.query)) }
}
</script>
