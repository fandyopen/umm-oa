<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <div v-if="canReadDocs" class="docs-center">
      <aside class="docs-sidebar">
        <div class="docs-sidebar-title">
          <a-icon type="book" />
          <span>{{ docsTitle }}</span>
        </div>
        <a-menu :selected-keys="[activeSection]" mode="inline" @click="handleMenuClick">
          <a-menu-item key="route-map">
            <a-icon type="node-index" />
            路由地图
          </a-menu-item>
          <a-menu-item key="api-docs">
            <a-icon type="api" />
            API 文档
          </a-menu-item>
          <a-menu-item key="module-docs">
            <a-icon type="appstore" />
            系统模块说明
          </a-menu-item>
        </a-menu>
        <div class="docs-sidebar-note">
          <span>入口配置</span>
          <code>sys.docs.enabled</code>
          <code>sys.docs.title</code>
        </div>
      </aside>

      <main class="docs-content">
        <component :is="activeSection" />
      </main>
    </div>

    <a-result v-else status="403" title="没有文档访问权限" sub-title="请联系管理员授权“使用文档”模块。">
      <template #extra>
        <a-button type="primary" @click="$router.push('/index')">返回首页</a-button>
      </template>
    </a-result>
  </page-header-wrapper>
</template>

<script>
import { mapGetters } from 'vuex'
import RouteMap from './components/RouteMap.vue'
import ApiDocs from './components/ApiDocs.vue'
import ModuleDocs from './components/ModuleDocs.vue'

const validSections = ['route-map', 'api-docs', 'module-docs']

export default {
  name: 'DocsCenter',
  components: {
    RouteMap,
    ApiDocs,
    ModuleDocs
  },
  data() {
    return {
      activeSection: validSections.includes(this.$route.query.tab) ? this.$route.query.tab : 'route-map',
      docsTitle: '使用文档'
    }
  },
  computed: {
    ...mapGetters(['permissions', 'roles']),
    canReadDocs() {
      const permissions = this.permissions || []
      const roles = this.roles || []
      return (
        roles.includes('admin') ||
        permissions.includes('*:*:*') ||
        permissions.includes('system:docs:list')
      )
    }
  },
  mounted() {
    this.getConfigKey('sys.docs.title')
      .then(response => {
        const title = String(response.data || '').trim()
        if (title) {
          this.docsTitle = title
        }
      })
      .catch(() => {})
  },
  methods: {
    handleMenuClick({ key }) {
      this.activeSection = key
      this.$router.replace({ path: '/system/docs', query: { tab: key } }).catch(() => {})
    }
  }
}
</script>

<style lang="less" scoped>
.docs-center {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  min-height: calc(100vh - 190px);
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e9ee;
  border-radius: 6px;
}

.docs-sidebar {
  display: flex;
  flex-direction: column;
  background: #fafbfc;
  border-right: 1px solid #e5e9ee;
}

.docs-sidebar-title {
  display: flex;
  gap: 9px;
  align-items: center;
  padding: 18px 20px 14px;
  color: #17212b;
  font-weight: 600;
  font-size: 16px;

  .anticon {
    color: #1769aa;
  }
}

.docs-sidebar-note {
  display: grid;
  gap: 5px;
  margin-top: auto;
  padding: 14px 20px 18px;
  color: #7b8794;
  border-top: 1px solid #e8edf1;
  font-size: 11px;

  code {
    color: #475467;
    overflow-wrap: anywhere;
  }
}

.docs-content {
  min-width: 0;
  padding: 22px;
  overflow: auto;
}

::v-deep .ant-menu-inline {
  border-right: 0;
  background: transparent;
}

::v-deep .ant-menu-item-selected {
  color: #1769aa;
  background: #eaf3fb;
}

@media (max-width: 900px) {
  .docs-center {
    grid-template-columns: 1fr;
  }

  .docs-sidebar {
    border-right: 0;
    border-bottom: 1px solid #e5e9ee;
  }

  ::v-deep .ant-menu-inline {
    display: flex;
    overflow-x: auto;
  }

  ::v-deep .ant-menu-item {
    flex: 0 0 auto;
    margin: 6px 4px;
  }

  .docs-sidebar-note {
    display: none;
  }

  .docs-content {
    padding: 16px;
  }
}
</style>
