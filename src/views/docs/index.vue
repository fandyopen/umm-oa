<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <div v-if="canReadDocs" class="docs-workspace">
      <main class="docs-content"><slot /></main>
    </div>
    <a-result v-else status="403" title="没有文档访问权限" sub-title="请联系管理员授权当前文档页面。">
      <template #extra><a-button type="primary" @click="$router.push('/index')">返回首页</a-button></template>
    </a-result>
  </page-header-wrapper>
</template>

<script>
import { mapGetters } from 'vuex'
export default {
  name: 'DocsPage',
  computed: {
    ...mapGetters(['permissions', 'roles']),
    canReadDocs() {
      const permission = this.$route.meta?.perms
      return Boolean(this.$route.meta?.moduleId) && (this.roles.includes('admin') || this.permissions.includes('*:*:*') || !permission || this.permissions.includes(permission))
    }
  }
}
</script>

<style scoped>
.docs-workspace { min-height: calc(100vh - 190px); overflow: hidden; border: 1px solid #e5e9ee; border-radius: 6px; background: #fff; }
.docs-content { min-width: 0; padding: 22px; overflow: auto; overflow-wrap: anywhere; }
@media (max-width: 900px) {
  .docs-content { padding: 16px; }
}
</style>
