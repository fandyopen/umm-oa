<template>
  <section class="docs-page">
    <header class="docs-page-header">
      <div>
        <h2>路由地图</h2>
        <p>从菜单路由定位 Vue 组件、前端 API 和后端入口。</p>
      </div>
      <a-tag color="blue">{{ filteredEntries.length }} 个页面</a-tag>
    </header>

    <div class="route-toolbar">
      <a-input-search v-model="keyword" placeholder="搜索路由、组件、接口或后端类" allow-clear />
      <div class="route-groups">
        <a-button
          v-for="group in groups"
          :key="group"
          size="small"
          :type="activeGroup === group ? 'primary' : 'default'"
          @click="activeGroup = group"
        >
          {{ group }}
        </a-button>
      </div>
    </div>

    <div v-if="filteredEntries.length" class="route-list">
      <article v-for="entry in filteredEntries" :key="`${entry.group}-${entry.route}`" class="route-item">
        <div class="route-summary">
          <div class="route-heading">
            <a class="route-link" href="javascript:void(0)" @click="openRoute(entry.route)">{{ entry.route }}</a>
            <a-tag color="blue">{{ entry.group }}</a-tag>
            <a-tag v-if="entry.permission" color="gold">{{ entry.permission }}</a-tag>
          </div>
          <div class="route-menu">{{ entry.menu }}</div>
          <code>{{ entry.component }}</code>
        </div>

        <div class="route-details">
          <div class="detail-row">
            <span class="detail-label">组件文件</span>
            <div class="detail-value">
              <div v-for="item in [entry.file]" :key="item" class="path-line">
                <code>{{ item }}</code>
                <a-tooltip title="复制路径">
                  <a-icon type="copy" class="copy-icon" @click="copyPath(item)" />
                </a-tooltip>
              </div>
            </div>
          </div>
          <div class="detail-row">
            <span class="detail-label">关联组件</span>
            <div class="detail-value">
              <code v-for="item in entry.related" :key="item">{{ item }}</code>
              <span v-if="!entry.related.length" class="muted">无</span>
            </div>
          </div>
          <div class="detail-row">
            <span class="detail-label">前端 API</span>
            <div class="detail-value">
              <div v-for="item in entry.api" :key="item" class="path-line">
                <code>{{ item }}</code>
                <a-tooltip title="复制路径">
                  <a-icon type="copy" class="copy-icon" @click="copyPath(item)" />
                </a-tooltip>
              </div>
            </div>
          </div>
          <div class="detail-row">
            <span class="detail-label">后端入口</span>
            <div class="detail-value">
              <a-tag v-for="item in entry.backend" :key="item">{{ item }}</a-tag>
            </div>
          </div>
          <div class="detail-note">{{ entry.note }}</div>
        </div>
      </article>
    </div>
    <a-empty v-else description="没有匹配的路由" />
  </section>
</template>

<script>
import { routeMapEntries } from '../data.js'

export default {
  name: 'RouteMap',
  data() {
    return {
      entries: routeMapEntries,
      groups: ['全部', '工作台', '待办', '营销', '人事', '系统', '全局'],
      activeGroup: '全部',
      keyword: ''
    }
  },
  computed: {
    filteredEntries() {
      const keyword = this.keyword.trim().toLowerCase()
      return this.entries.filter(entry => {
        if (this.activeGroup !== '全部' && entry.group !== this.activeGroup) {
          return false
        }
        if (!keyword) {
          return true
        }
        return [
          entry.group,
          entry.menu,
          entry.route,
          entry.component,
          entry.file,
          ...entry.related,
          ...entry.api,
          ...entry.backend,
          entry.permission,
          entry.note
        ]
          .join(' ')
          .toLowerCase()
          .includes(keyword)
      })
    }
  },
  methods: {
    openRoute(route) {
      this.$router.push(route)
    },
    async copyPath(value) {
      try {
        await navigator.clipboard.writeText(value)
      } catch {
        const input = document.createElement('textarea')
        input.value = value
        input.style.position = 'fixed'
        input.style.opacity = '0'
        document.body.appendChild(input)
        input.select()
        document.execCommand('copy')
        input.remove()
      }
      this.$message.success('路径已复制')
    }
  }
}
</script>

<style lang="less" scoped>
.docs-page {
  min-width: 0;
}

.docs-page-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;

  h2 {
    margin: 0;
    color: #17212b;
    font-weight: 600;
    font-size: 22px;
  }

  p {
    margin: 5px 0 0;
    color: #667085;
  }
}

.route-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  padding: 12px;
  background: #f7f9fb;
  border: 1px solid #e1e7ec;
  border-radius: 6px;

  .ant-input-search {
    width: 420px;
    max-width: 100%;
  }
}

.route-groups {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.route-list {
  display: grid;
  gap: 10px;
}

.route-item {
  display: grid;
  grid-template-columns: minmax(220px, 0.7fr) minmax(0, 1.8fr);
  gap: 18px;
  padding: 16px;
  background: #fff;
  border: 1px solid #e4e9ee;
  border-radius: 6px;
}

.route-summary {
  min-width: 0;
}

.route-heading {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  align-items: center;
}

.route-link {
  color: #1769aa;
  font-weight: 600;
  font-size: 16px;
}

.route-menu {
  margin: 7px 0;
  color: #344054;
}

.route-summary code {
  color: #237a57;
  overflow-wrap: anywhere;
}

.route-details {
  min-width: 0;
}

.detail-row {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 10px;
  padding: 5px 0;
}

.detail-label {
  color: #7b8794;
  font-size: 12px;
}

.detail-value {
  min-width: 0;
}

.detail-value > code {
  display: block;
  margin-bottom: 4px;
  color: #344054;
  overflow-wrap: anywhere;
}

.path-line {
  display: flex;
  gap: 7px;
  align-items: flex-start;
  margin-bottom: 4px;
}

.path-line code {
  min-width: 0;
  color: #344054;
  overflow-wrap: anywhere;
}

.copy-icon {
  margin-top: 3px;
  color: #667085;
  cursor: pointer;

  &:hover {
    color: #1769aa;
  }
}

.detail-note {
  margin-top: 7px;
  padding-top: 8px;
  color: #667085;
  font-size: 12px;
  border-top: 1px dashed #e5e9ed;
}

.muted {
  color: #98a2b3;
}

@media (max-width: 900px) {
  .route-item {
    grid-template-columns: 1fr;
  }
}
</style>
