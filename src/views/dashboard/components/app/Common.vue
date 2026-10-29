<template>
  <a-card class="card-container card-container-height" :bordered="false" :headStyle="{ border: 'none' }" :bodyStyle="{ overflowY: 'hidden', height: '100%' }">
    <h1 slot="title" class="card-title">常用应用</h1>
    <a-card-grid v-for="(item, i) in userMenuList" :key="i" class="project-card-grid">
      <a-card :bordered="false" :body-style="{ padding: 0, cursor: 'pointer' }">
        <icon-font @click="handleApp(item.path)" :type="item.meta.icon || 'iconfont-caidanguanli'" :style="{ fontSize: '14px' }" />
        <span class="s-ml-10" style="font-size: 14px" @click="handleApp(item.path)">
          <Ellipsis :length="item.menuName.length > 4 ? 6 : 8" tooltip>{{ item.menuName }}</Ellipsis>
        </span>
      </a-card>
    </a-card-grid>
  </a-card>
</template>

<script>
import Ellipsis from '/src/components/Ellipsis/Ellipsis.vue'
import { mapGetters } from 'vuex'

export default {
  components: { Ellipsis },
  data() { return { userMenuList: [] } },
  computed: { ...mapGetters(['addRouters']) },
  created() {
    const leaves = []
    const walk = routes => (routes || []).forEach(route => {
      if (route.hidden) return
      if (route.children && route.children.length) walk(route.children)
      else if (route.path && route.path !== '/index') {
        leaves.push({ path: route.path, menuName: route.meta?.title || route.name || '应用', meta: route.meta || {} })
      }
    })
    walk(this.addRouters)
    this.userMenuList = leaves.slice(0, 8)
  },
  methods: { handleApp(path) { this.$router.push({ path }) } }
}
</script>

<style scoped>
.project-card-grid { padding: 16px !important; }
</style>
