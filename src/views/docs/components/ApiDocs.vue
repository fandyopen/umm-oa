<template>
  <section class="docs-page">
    <header class="docs-page-header">
      <div>
        <h2>API 文档</h2>
        <p>按业务域查看当前后端接口、权限要求和源码入口。</p>
      </div>
      <a-tag color="blue">{{ visibleApiCount }} 个接口</a-tag>
    </header>

    <div class="api-toolbar">
      <a-input-search v-model="keyword" placeholder="搜索接口、说明、控制器" allow-clear />
      <a-select v-model="activeGroup" style="width: 210px">
        <a-select-option v-for="group in groups" :key="group" :value="group">{{ group }}</a-select-option>
      </a-select>
    </div>

    <div class="api-groups">
      <section v-for="group in filteredGroups" :key="group.name" class="api-group">
        <div class="api-group-title">
          <h3>{{ group.name }}</h3>
          <span>{{ group.items.length }} 个接口</span>
        </div>
        <div class="api-table-wrap">
          <table class="api-table">
            <thead>
              <tr>
                <th>方法</th>
                <th>路径</th>
                <th>说明</th>
                <th>权限</th>
                <th>后端入口</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in group.items" :key="`${item.method}-${item.path}`">
                <td><a-tag :color="methodColor(item.method)">{{ item.method }}</a-tag></td>
                <td><code>{{ item.path }}</code></td>
                <td>{{ item.desc }}</td>
                <td><span class="permission-text">{{ item.auth }}</span></td>
                <td><span class="backend-text">{{ item.source }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
    <a-empty v-if="!filteredGroups.length" description="没有匹配的接口" />
  </section>
</template>

<script>
import { apiGroups } from '../data.js'

export default {
  name: 'ApiDocs',
  data() {
    return {
      groupsData: apiGroups,
      activeGroup: '全部',
      keyword: ''
    }
  },
  computed: {
    groups() {
      return ['全部', ...this.groupsData.map(group => group.name)]
    },
    filteredGroups() {
      const keyword = this.keyword.trim().toLowerCase()
      return this.groupsData
        .filter(group => this.activeGroup === '全部' || group.name === this.activeGroup)
        .map(group => ({
          ...group,
          items: group.items.filter(item => {
            if (!keyword) {
              return true
            }
            return [group.name, item.method, item.path, item.desc, item.auth, item.source]
              .join(' ')
              .toLowerCase()
              .includes(keyword)
          })
        }))
        .filter(group => group.items.length)
    },
    visibleApiCount() {
      return this.filteredGroups.reduce((total, group) => total + group.items.length, 0)
    }
  },
  methods: {
    methodColor(method) {
      return {
        GET: 'blue',
        POST: 'green',
        PUT: 'gold',
        DELETE: 'red'
      }[method]
    }
  }
}
</script>

<style lang="less" scoped>
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

.api-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
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

.api-groups {
  display: grid;
  gap: 14px;
}

.api-group {
  overflow: hidden;
  background: #fff;
  border: 1px solid #e4e9ee;
  border-radius: 6px;
}

.api-group-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fbfcfd;
  border-bottom: 1px solid #e8edf1;

  h3 {
    margin: 0;
    font-weight: 600;
    font-size: 16px;
  }

  span {
    color: #7b8794;
    font-size: 12px;
  }
}

.api-table-wrap {
  overflow-x: auto;
}

.api-table {
  width: 100%;
  min-width: 920px;
  border-collapse: collapse;

  th,
  td {
    padding: 10px 14px;
    text-align: left;
    border-bottom: 1px solid #edf0f3;
  }

  th {
    color: #667085;
    background: #fcfdfe;
    font-size: 12px;
    font-weight: 600;
  }

  tbody tr:last-child td {
    border-bottom: 0;
  }

  code {
    color: #1d4f72;
  }
}

.permission-text {
  color: #98620c;
  font-size: 12px;
}

.backend-text {
  color: #475467;
  font-size: 12px;
}

@media (max-width: 640px) {
  .api-toolbar {
    .ant-input-search,
    .ant-select {
      width: 100% !important;
    }
  }
}
</style>
