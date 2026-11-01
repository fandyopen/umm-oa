<template>
  <page-header-wrapper>
    <a-card :bordered="false">
      <div class="action-toolbar">
        <div class="action-toolbar__filter">
          <span class="action-toolbar__label">所属页面</span>
          <a-select
            v-model="moduleId"
            show-search
            option-filter-prop="children"
            style="width: 360px"
            @change="handleModuleChange"
          >
            <a-select-option v-for="item in moduleOptions" :key="item.moduleId" :value="item.moduleId">
              {{ item.displayName }}
            </a-select-option>
          </a-select>
        </div>
        <a-button icon="reload" :loading="loading" @click="loadActions">刷新</a-button>
        <a-input v-model="actionKeyword" allow-clear placeholder="搜索按钮名称 / 动作编码" style="width: 240px" />
        <a-button icon="setting" @click="openModuleManager">模块管理</a-button>
        <a-button v-if="canManageStructure" type="primary" icon="plus" :disabled="!moduleId" @click="openCreate">
          新增按钮
        </a-button>
      </div>

      <a-alert
        class="action-hint"
        type="info"
        show-icon
        message="这里维护页面内的按钮权限与流程动作。新增按钮时，动作编码由系统自动生成并显示。"
        description="所有按钮统一维护，不再区分内置或自定义。在修改页开启流程设计后，可配置该按钮的审批；关闭会保留原设计与审批记录。"
      />

      <a-table
        row-key="actionCode"
        :loading="loading"
        :columns="columns"
        :data-source="filteredActions"
        :pagination="false"
      >
        <span slot="workflow" slot-scope="text, record">
          <a-tag :color="record.flowDesignEnabled ? 'blue' : 'default'">{{ record.workflowState || '未开启' }}</a-tag>
        </span>
        <span slot="status" slot-scope="text">
          <a-tag :color="String(text) === '0' ? 'green' : 'default'">
            {{ String(text) === '0' ? '启用' : '停用' }}
          </a-tag>
        </span>
        <span slot="handlerKey" slot-scope="text">
          <a-tag v-if="text" color="geekblue">{{ text }}</a-tag>
          <span v-else class="action-empty">未配置</span>
        </span>
        <span slot="operation" slot-scope="text, record">
          <a v-if="canManageWorkflow && record.flowDesignEnabled && String(record.status) === '0'" @click="configureWorkflow(record)">流程设计</a>
          <a-divider v-if="canManageWorkflow && record.flowDesignEnabled && String(record.status) === '0'" type="vertical" />
          <a v-if="canManageStructure || canManageWorkflow" @click="openEdit(record)">修改</a>
          <a-divider v-if="canManageStructure" type="vertical" />
          <a-popconfirm
            v-if="canManageStructure"
            title="确认删除该按钮？已有审批记录会保留。"
            ok-text="删除"
            cancel-text="取消"
            @confirm="removeAction(record)"
          >
            <a class="action-danger">删除</a>
          </a-popconfirm>
        </span>
      </a-table>
    </a-card>

    <module-action-form ref="actionForm" :can-manage-structure="canManageStructure" :can-manage-workflow="canManageWorkflow" @ok="loadActions" />
  </page-header-wrapper>
</template>

<script>
import { deleteModuleAction, getModuleManagementTree, getModuleActions } from '/src/api/system/menu.js'
import ModuleActionForm from '../module/modules/ModuleActionForm.vue'

export default {
  name: 'ModuleActions',
  components: { ModuleActionForm },
  data() {
    return {
      modules: [],
      moduleId: undefined,
      actions: [],
      actionKeyword: '',
      loading: false,
      canManageStructure: false,
      canManageWorkflow: false,
      columns: [
        { title: '动作编码', dataIndex: 'actionCode', width: 150 },
        { title: '按钮 / 动作名称', dataIndex: 'actionName', width: 170 },
        { title: '权限标识', dataIndex: 'permissionCode', ellipsis: true },
        { title: 'handlerKey', dataIndex: 'handlerKey', scopedSlots: { customRender: 'handlerKey' }, width: 190 },
        { title: '流程状态', scopedSlots: { customRender: 'workflow' }, width: 110 },
        { title: '状态', dataIndex: 'status', scopedSlots: { customRender: 'status' }, width: 90 },
        { title: '操作', scopedSlots: { customRender: 'operation' }, width: 260, align: 'center' }
      ]
    }
  },
  computed: {
    filteredActions() {
      const keyword = this.actionKeyword.trim().toLowerCase()
      return this.actions.filter(item => !keyword || [item.actionName, item.actionCode, item.permissionCode].some(value => String(value || '').toLowerCase().includes(keyword)))
    },
    moduleOptions() {
      return this.modules.map(item => ({
        ...item,
        displayName: item.displayName
      }))
    }
  },
  created() {
    this.loadModules()
  },
  methods: {
    configureWorkflow(action) {
      this.$router.push({ path: '/processDesign', query: {
        tenantAppId: this.$store.getters.tenantAppId || 1,
        menuId: this.moduleId,
        menuName: this.modules.find(item => item.moduleId === Number(this.moduleId))?.moduleName || '业务模块',
        actionCode: action.actionCode
      } })
    },
    openModuleManager() {
      this.$router.push({
        path: '/system/module',
        query: this.moduleId ? { moduleId: this.moduleId } : {}
      })
    },
    async loadModules() {
      const response = await getModuleManagementTree()
      this.canManageStructure = response.canManageStructure === true
      this.canManageWorkflow = response.canManageWorkflow === true
      const rows = []
      const walk = (nodes, parentName = '') => {
        ;(nodes || []).forEach(node => {
          if (node.nodeType === 'action') {
            return
          }
          const nodeTitle = node.moduleName
          const displayName = parentName ? `${parentName} / ${nodeTitle}` : nodeTitle
          if (node.moduleId && node.menuType === 'C') {
            rows.push({
              moduleId: Number(node.moduleId),
              moduleName: nodeTitle,
              displayName
            })
          }
          walk(node.children, displayName)
        })
      }
      walk(response.data)
      this.modules = rows
      const queryModuleId = Number(this.$route.query.moduleId || 0)
      this.moduleId = queryModuleId || rows.find(item => item.moduleId === 341001)?.moduleId || rows[0]?.moduleId
      if (this.moduleId) {
        await this.loadActions()
        this.openRequestedEditor()
      }
    },
    async loadActions() {
      if (!this.moduleId) {
        this.actions = []
        return
      }
      this.loading = true
      try {
        const response = await getModuleActions(this.moduleId, { includeDisabled: true })
        this.actions = response.data || []
      } finally {
        this.loading = false
      }
    },
    handleModuleChange(moduleId) {
      this.actionKeyword = ''
      this.$router.replace({
        query: {
          ...this.$route.query,
          moduleId
        }
      })
      this.loadActions()
    },
    openRequestedEditor() {
      if (String(this.$route.query.create) === '1' && this.canManageStructure) {
        this.openCreate()
        return
      }
      const actionCode = this.$route.query.actionCode
      if (!actionCode) {
        return
      }
      const action = this.actions.find(item => item.actionCode === actionCode)
      if (action && (this.canManageStructure || this.canManageWorkflow)) {
        this.openEdit(action)
      }
    },
    openCreate() {
      this.$refs.actionForm.handleAdd({ moduleId: this.moduleId,
        moduleName: this.modules.find(item => item.moduleId === Number(this.moduleId))?.moduleName || '当前页面' })
    },
    openEdit(row) {
      this.$refs.actionForm.handleUpdate({ ...row, moduleId: this.moduleId,
        moduleName: this.modules.find(item => item.moduleId === Number(this.moduleId))?.moduleName || '当前页面' })
    },
    async removeAction(row) {
      await deleteModuleAction(this.moduleId, row.actionCode)
      this.$message.success('动作已删除')
      await this.$store.dispatch('BootstrapSession')
      this.loadActions()
    },
  }
}
</script>

<style lang="less" scoped>
.action-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.action-toolbar__filter {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-right: auto;
}

.action-toolbar__label {
  font-weight: 600;
  color: #333;
}

.action-hint {
  margin-bottom: 14px;
}

.action-empty {
  color: #999;
}



.action-danger {
  color: #ff4d4f;
}
</style>
