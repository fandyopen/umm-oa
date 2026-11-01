<template>
  <a-spin tip="正在进入模块管理…" />
</template>

<script>
export default {
  name: 'LegacyWorkflowSettings',
  beforeRouteEnter(to, from, next) {
    next({ path: '/system/module', query: to.query, replace: true })
  }
}
</script>
