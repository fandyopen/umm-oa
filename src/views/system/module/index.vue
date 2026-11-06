<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-card :bordered="false">
      <div class="module-toolbar">
        <a-input v-model="keyword" allow-clear placeholder="搜索模块、路径、权限标识或动作编码" class="tw-w-80" />
        <a-button @click="toggleExpandAll">{{ allExpanded ? '全部收起' : '全部展开' }}</a-button>
        <a-button icon="reload" :loading="loading" @click="loadTree">刷新</a-button>
        <a-button v-if="canManageStructure" type="primary" icon="plus" @click="$refs.moduleForm.handleAdd()"> 新增模块 </a-button>
      </div>

      <a-alert
        class="module-hint"
        type="info"
        show-icon
        message="模块管理维护的是与“角色权限 → 角色模块”同源的数据树。"
        description="目录和页面维护导航，按钮统一维护操作权限与流程配置；在按钮修改页开启流程设计后，可为该按钮设计审批流程。"
      />

      <a-table row-key="key" :loading="loading" :columns="columns" :data-source="filteredTree" :expanded-row-keys="expandedRowKeys" :pagination="false" @expand="handleExpand">
        <span slot="name" slot-scope="text, record">
          <icon-font v-if="record.nodeType === 'module' && record.icon" :type="record.icon" class="module-icon" />
          <a-tag v-if="record.nodeType === 'action'" color="green">按钮</a-tag>
          <span>{{ record.nodeType === 'action' ? record.actionName : record.moduleName }}</span>
          <span v-if="record.nodeType === 'action'" class="module-code">{{ record.actionCode }}</span>
        </span>
        <span slot="type" slot-scope="text, record">
          <a-tag :color="typeColor(record)">
            {{ typeLabel(record) }}
          </a-tag>
        </span>
        <span slot="identity" slot-scope="text, record">
          <code>{{ record.nodeType === 'action' ? record.actionCode : record.path }}</code>
        </span>
        <span slot="permissionCode" slot-scope="text, record">
          {{ record.nodeType === 'action' ? record.permissionCode : record.perms }}
        </span>
        <span slot="status" slot-scope="text, record">
          <a-tag :color="String(record.status) === '0' ? 'green' : undefined">
            {{ String(record.status) === '0' ? '启用' : '停用' }}
          </a-tag>
        </span>
        <span slot="workflow" slot-scope="text, record">
          <a-tag v-if="record.nodeType === 'action'" :color="record.flowDesignEnabled ? 'blue' : undefined">
            {{ record.workflowState || '未开启' }}
          </a-tag>
          <span v-else>—</span>
        </span>
        <span slot="operation" slot-scope="text, record">
          <template v-if="record.nodeType === 'module' && canManageStructure">
            <a @click="$refs.moduleForm.handleUpdate(record)">修改</a>
            <a-divider type="vertical" />
            <a @click="$refs.moduleForm.handleAdd(record)">新增下级</a>
            <template v-if="record.menuType === 'C'">
              <a-divider type="vertical" />
              <a @click="openActionForm(record, true)">新增按钮</a>
            </template>
            <a-divider type="vertical" v-if="record.moduleId !== 340309" />
            <a-popconfirm v-if="record.moduleId !== 340309" title="删除模块会同时删除其下级和按钮，确认继续？" ok-text="删除" cancel-text="取消" @confirm="removeModule(record)">
              <a class="module-danger">删除</a>
            </a-popconfirm>
          </template>
          <template v-else-if="record.nodeType === 'action'">
            <template>
              <a v-if="canManageStructure || canManageWorkflow" @click="openActionForm(record)">修改</a>
              <template v-if="canManageWorkflow && record.flowDesignEnabled && String(record.status) === '0'">
                <a-divider type="vertical" />
                <a @click="openFlowDesign(record)">流程设计</a>
              </template>
              <a-divider v-if="canManageStructure" type="vertical" />
              <a-popconfirm v-if="canManageStructure" title="确认删除该按钮？" ok-text="删除" cancel-text="取消" @confirm="removeAction(record)">
                <a class="module-danger">删除</a>
              </a-popconfirm>
            </template>
          </template>
        </span>
      </a-table>
    </a-card>

    <module-form ref="moduleForm" :tree-data="treeData" @ok="loadTree" />
    <module-action-form ref="actionForm" :can-manage-structure="canManageStructure" :can-manage-workflow="canManageWorkflow" @ok="handleActionSaved" />
  </page-header-wrapper>
</template>

<script>
import { delMenu, deleteModuleAction, getModuleManagementTree } from '/src/api/system/menu.js'
import ModuleForm from './modules/ModuleForm.vue'
import ModuleActionForm from './modules/ModuleActionForm.vue'

export default {
  name: 'ModuleManage',
  components: { ModuleForm, ModuleActionForm },
  data() {
    return {
      treeData: [],
      expandedRowKeys: [],
      keyword: '',
      loading: false,
      canManageStructure: false,
      canManageWorkflow: false,
      columns: [
        { title: '模块 / 按钮', dataIndex: 'moduleName', scopedSlots: { customRender: 'name' }, width: 260 },
        { title: '类型', scopedSlots: { customRender: 'type' }, width: 90 },
        { title: '路径 / 动作编码', scopedSlots: { customRender: 'identity' }, width: 220 },
        { title: '权限标识', scopedSlots: { customRender: 'permissionCode' }, ellipsis: true },
        { title: '状态', scopedSlots: { customRender: 'status' }, width: 90 },
        { title: '流程状态', scopedSlots: { customRender: 'workflow' }, width: 120 },
        { title: '操作', scopedSlots: { customRender: 'operation' }, width: 300, align: 'center' }
      ]
    }
  },
  computed: {
    filteredTree() {
      const keyword = this.keyword.trim().toLowerCase()
      if (!keyword) {
        return this.treeData
      }
      return this.filterNodes(this.treeData, keyword)
    },
    allExpanded() {
      const keys = this.collectKeys(this.filteredTree)
      return keys.length > 0 && keys.every(key => this.expandedRowKeys.includes(key))
    }
  },
  created() {
    this.loadTree()
  },
  activated() {
    if (this.treeData.length) this.loadTree()
  },
  methods: {
    async loadTree({ preserveExpansion = false, expandModuleId } = {}) {
      this.loading = true
      try {
        const response = await getModuleManagementTree()
        this.treeData = response.data || []
        this.canManageStructure = response.canManageStructure === true
        this.canManageWorkflow = response.canManageWorkflow === true
        const keys = this.collectKeys(this.treeData)
        this.expandedRowKeys = preserveExpansion ? Array.from(new Set([...this.expandedRowKeys, 'module:' + expandModuleId])).filter(key => keys.includes(key)) : keys
      } finally {
        this.loading = false
      }
    },
    collectKeys(nodes) {
      const keys = []
      const walk = items => {
        ;(items || []).forEach(node => {
          if (node.children && node.children.length) {
            keys.push(node.key)
            walk(node.children)
          }
        })
      }
      walk(nodes)
      return keys
    },
    filterNodes(nodes, keyword) {
      return (nodes || []).reduce((result, node) => {
        const children = this.filterNodes(node.children, keyword)
        const values = [node.moduleName, node.path, node.permissionCode, node.actionName, node.actionCode]
        const matched = values.some(value =>
          String(value || '')
            .toLowerCase()
            .includes(keyword)
        )
        if (matched || children.length) {
          result.push({ ...node, children })
        }
        return result
      }, [])
    },
    handleExpand(expanded, record) {
      if (expanded) {
        this.expandedRowKeys = Array.from(new Set([...this.expandedRowKeys, record.key]))
      } else {
        this.expandedRowKeys = this.expandedRowKeys.filter(key => key !== record.key)
      }
    },
    toggleExpandAll() {
      this.expandedRowKeys = this.allExpanded ? [] : this.collectKeys(this.filteredTree)
    },
    typeLabel(record) {
      if (record.nodeType === 'action') {
        return '按钮'
      }
      return record.menuType === 'M' ? '目录' : '页面'
    },
    typeColor(record) {
      if (record.nodeType === 'action') {
        return 'green'
      }
      return record.menuType === 'M' ? 'blue' : 'cyan'
    },
    openActionForm(record, create = false) {
      if (create) this.$refs.actionForm.handleAdd(record)
      else this.$refs.actionForm.handleUpdate(record)
    },
    handleActionSaved(record) {
      this.loadTree({ preserveExpansion: true, expandModuleId: record.moduleId })
    },
    openFlowDesign(record) {
      this.$router.push({
        path: '/processDesign',
        query: {
          tenantAppId: this.$store.getters.tenantAppId || 1,
          menuId: record.moduleId,
          menuName: record.moduleName,
          actionCode: record.actionCode
        }
      })
    },
    async removeModule(record) {
      await delMenu(record.moduleId)
      this.$message.success('模块已删除')
      this.loadTree()
    },
    async removeAction(record) {
      await deleteModuleAction(record.moduleId, record.actionCode)
      this.$message.success('按钮已删除')
      await this.$store.dispatch('BootstrapSession')
      this.loadTree()
    }
  }
}
</script>

<style lang="less" scoped>
.module-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.module-toolbar .ant-input {
  margin-right: auto;
}

.module-hint {
  margin-bottom: 16px;
}

.module-icon {
  margin-right: 8px;
}

.module-code {
  margin-left: 8px;
  color: #8c8c8c;
  font-family: monospace;
}

.module-muted {
  color: #bfbfbf;
}

.module-danger {
  color: #ff4d4f;
}
</style>
