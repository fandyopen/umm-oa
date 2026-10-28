<template>
  <section class="docs-page">
    <header class="docs-page-header">
      <div>
        <h2>系统模块说明</h2>
        <p>说明当前保留模块的职责、页面入口、权限码和核心数据入口。</p>
      </div>
      <a-tag color="blue">{{ moduleGroups.length }} 个模块</a-tag>
    </header>

    <a-input-search v-model="keyword" class="module-search" placeholder="搜索模块、页面、权限或后端入口" allow-clear />

    <div class="module-list">
      <article v-for="module in filteredModules" :key="module.name" class="module-item">
        <div class="module-head">
          <span class="module-icon"><a-icon :type="module.icon" /></span>
          <div>
            <h3>{{ module.name }}</h3>
            <p>{{ module.purpose }}</p>
          </div>
        </div>

        <div class="module-grid">
          <div class="module-field">
            <span>页面入口</span>
            <div class="tag-list">
              <a-tag v-for="page in module.pages" :key="page">{{ page }}</a-tag>
            </div>
          </div>
          <div class="module-field">
            <span>权限码</span>
            <div class="tag-list">
              <a-tag v-for="permission in module.permissions" :key="permission" color="gold">{{ permission }}</a-tag>
            </div>
          </div>
          <div class="module-field">
            <span>后端入口</span>
            <div class="tag-list">
              <a-tag v-for="backend in module.backend" :key="backend">{{ backend }}</a-tag>
            </div>
          </div>
          <div class="module-field">
            <span>核心数据表</span>
            <div class="tag-list">
              <a-tag v-for="table in module.data" :key="table">{{ table }}</a-tag>
              <span v-if="!module.data.length" class="muted">无独立数据表</span>
            </div>
          </div>
        </div>

        <div class="module-note">{{ module.note }}</div>
      </article>
    </div>
    <a-empty v-if="!filteredModules.length" description="没有匹配的模块" />
  </section>
</template>

<script>
import { moduleGroups } from '../data.js'

export default {
  name: 'ModuleDocs',
  data() {
    return {
      moduleGroups,
      keyword: ''
    }
  },
  computed: {
    filteredModules() {
      const keyword = this.keyword.trim().toLowerCase()
      if (!keyword) {
        return this.moduleGroups
      }
      return this.moduleGroups.filter(module =>
        [
          module.name,
          module.purpose,
          ...module.pages,
          ...module.permissions,
          ...module.backend,
          ...module.data,
          module.note
        ]
          .join(' ')
          .toLowerCase()
          .includes(keyword)
      )
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

.module-search {
  width: 480px;
  max-width: 100%;
  margin-bottom: 14px;
}

.module-list {
  display: grid;
  gap: 12px;
}

.module-item {
  padding: 16px;
  background: #fff;
  border: 1px solid #e4e9ee;
  border-radius: 6px;
}

.module-head {
  display: flex;
  gap: 13px;
  align-items: flex-start;

  h3 {
    margin: 0;
    color: #17212b;
    font-weight: 600;
    font-size: 17px;
  }

  p {
    margin: 4px 0 0;
    color: #667085;
  }
}

.module-icon {
  display: inline-flex;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  align-items: center;
  justify-content: center;
  color: #1769aa;
  background: #eaf3fb;
  border-radius: 5px;
  font-size: 19px;
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
  margin-top: 15px;
  padding-top: 12px;
  border-top: 1px solid #edf0f3;
}

.module-field {
  min-width: 0;

  > span {
    display: block;
    margin-bottom: 6px;
    color: #7b8794;
    font-size: 12px;
  }
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.module-note {
  margin-top: 12px;
  padding: 9px 11px;
  color: #475467;
  background: #f7f9fb;
  border-left: 3px solid #91bfe3;
  font-size: 12px;
}

.muted {
  color: #98a2b3;
}

@media (max-width: 700px) {
  .module-grid {
    grid-template-columns: 1fr;
  }
}
</style>
