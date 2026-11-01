<template>
  <section class="docs-page">
    <header class="docs-page-header">
      <div>
        <h2>业务模块说明</h2>
        <p>按实际菜单说明使用人员、前置准备、操作步骤和业务闭环，展开功能即可查看办理流程。</p>
      </div>
      <a-tag color="blue">{{ moduleGroups.length }} 个模块 · {{ workflowCount }} 项功能流程</a-tag>
    </header>

    <a-input-search v-model="keyword" class="module-search" placeholder="搜索业务流程，例如：客户交接、合同审批、回款、邀请成员" allow-clear />
    <nav class="module-navigation" aria-label="业务模块筛选">
      <a-button :type="activeGroup === 'all' ? 'primary' : 'default'" size="small" @click="activeGroup = 'all'">全部模块</a-button>
      <a-button v-for="item in moduleGroups" :key="item.id" :type="activeGroup === item.id ? 'primary' : 'default'" size="small" @click="activeGroup = item.id">{{ item.name }}</a-button>
    </nav>

    <div class="module-list">
      <article v-for="module in filteredModules" :key="module.id" class="module-item">
        <div class="module-head">
          <span class="module-icon"><a-icon :type="module.icon" /></span>
          <div>
            <h3>{{ module.name }}</h3>
            <p>{{ module.purpose }}</p>
          </div>
        </div>

        <div class="module-context">
          <div><strong>适用人员</strong><span>{{ module.audience }}</span></div>
          <div><strong>前置准备</strong><ul><li v-for="item in module.prerequisites" :key="item">{{ item }}</li></ul></div>
        </div>
        <div class="business-flow" aria-label="业务闭环"><template v-for="(step, index) in module.flow"><a-icon v-if="index" :key="`${index}-arrow`" type="right" /><span :key="step">{{ step }}</span></template></div>
        <a-collapse :key="`${module.id}-${keyword}`" class="workflow-list" :default-active-key="keyword ? module.workflows.map(item => item.name) : [module.workflows[0].name]">
          <a-collapse-panel v-for="workflow in module.workflows" :key="workflow.name" :header="workflow.name">
            <div class="workflow-entry"><a-icon type="environment" /><span>{{ workflow.entry }}</span></div>
            <p class="workflow-roles">办理人员：{{ workflow.roles }}</p>
            <ol class="workflow-steps">
              <li v-for="(step, index) in workflow.steps" :key="step.title"><span class="step-number">{{ index + 1 }}</span><div><h4>{{ step.title }}</h4><p>{{ step.description }}</p></div></li>
            </ol>
            <div class="workflow-result"><strong>完成结果</strong><span>{{ workflow.outcome }}</span></div>
            <div v-if="workflow.tips && workflow.tips.length" class="workflow-tips"><strong>操作要点</strong><ul><li v-for="tip in workflow.tips" :key="tip">{{ tip }}</li></ul></div>
          </a-collapse-panel>
        </a-collapse>
        <a-collapse class="technical-details" :bordered="false"><a-collapse-panel key="reference" header="技术与权限参考">
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
        </a-collapse-panel></a-collapse>

        <div class="module-note">{{ module.note }}</div>
      </article>
    </div>
    <a-empty v-if="!filteredModules.length" description="没有匹配的模块" />
  </section>
</template>

<script>
import { moduleGroups } from '../moduleGuides.js'

const searchable = value => typeof value === 'string' ? value : JSON.stringify(value)
const matches = (values, keyword) => values.map(searchable).join(' ').toLowerCase().includes(keyword)

export default {
  name: 'ModuleDocs',
  data() {
    return {
      moduleGroups,
      keyword: '',
      activeGroup: 'all'
    }
  },
  computed: {
    workflowCount() { return this.moduleGroups.reduce((count, module) => count + module.workflows.length, 0) },
    filteredModules() {
      const keyword = this.keyword.trim().toLowerCase()
      return this.moduleGroups.filter(module => this.activeGroup === 'all' || module.id === this.activeGroup).map(module => {
        if (!keyword || matches([module.name, module.purpose, module.audience, module.prerequisites, module.flow, module.permissions, module.backend, module.data, module.note], keyword)) return module
        return { ...module, workflows: module.workflows.filter(workflow => matches([workflow], keyword)) }
      }).filter(module => module.workflows.length)
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
  width: 640px;
  max-width: 100%;
  margin-bottom: 14px;
}

.module-navigation { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 18px; }
.module-context { margin-top: 16px; padding: 12px 14px; background: #f8fafc; border-radius: 5px; color: #475467; }
.module-context > div { display: flex; gap: 12px; margin: 5px 0; }
.module-context strong { flex: 0 0 60px; font-size: 12px; color: #667085; }
.module-context ul, .workflow-tips ul { padding-left: 18px; margin: 0; }
.module-context li, .workflow-tips li { margin-bottom: 4px; }
.business-flow { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 16px 0; color: #42617b; font-size: 12px; }
.business-flow > span { padding: 5px 9px; background: #edf5fc; border-radius: 4px; }
.business-flow .anticon { color: #94a3b8; font-size: 10px; }
.workflow-list { background: #fff; }
.workflow-entry { display: flex; align-items: baseline; gap: 8px; color: #1769aa; overflow-wrap: anywhere; }
.workflow-roles { margin: 8px 0 16px; color: #667085; font-size: 12px; }
.workflow-steps { padding: 0; margin: 0; list-style: none; }
.workflow-steps li { display: flex; gap: 12px; padding-bottom: 16px; }
.step-number { display: inline-flex; align-items: center; justify-content: center; flex: 0 0 25px; height: 25px; border-radius: 50%; color: #1769aa; background: #eaf3fb; font-size: 12px; font-weight: 600; }
.workflow-steps h4 { margin: 1px 0 4px; font-weight: 600; color: #344054; }
.workflow-steps p { margin: 0; color: #475467; line-height: 1.8; }
.workflow-result { padding: 10px 12px; background: #f0f8f4; border-left: 3px solid #70b38b; color: #35664b; }
.workflow-result strong { margin-right: 12px; }
.workflow-tips { margin-top: 12px; padding: 10px 12px; background: #fffbf2; color: #785d26; line-height: 1.8; font-size: 12px; }
.workflow-tips > strong { display: block; margin-bottom: 4px; }
.technical-details { margin-top: 14px; background: #f8fafc; }
.technical-details .module-grid { margin-top: 0; padding-top: 0; border-top: 0; }

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
  .docs-page-header {
    flex-direction: column;
  }

  .module-grid {
    grid-template-columns: 1fr;
  }
}
</style>
