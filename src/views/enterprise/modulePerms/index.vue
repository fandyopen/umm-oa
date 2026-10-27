<!-- 分配菜单权限 -->
<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <div class="tab-container">
      <RolePerms />
    </div>
  </page-header-wrapper>
</template>

<script>
import RolePerms from './modules/rolePerms.vue'

export default {
  components: {
    RolePerms
  }
}
</script>

<style lang="less">
.tab-container {
  background: #fff;
  margin-top: 0%;
}

</style>
