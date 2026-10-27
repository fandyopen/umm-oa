<template>
  <page-header-wrapper>
    <a-card :bordered="false">
      <div class="action-toolbar">
        <div class="action-toolbar__filter">
          <span class="action-toolbar__label">业务模块</span>
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
        <a-button type="primary" icon="plus" :disabled="!moduleId" @click="openCreate">
          新增动作
        </a-button>
      </div>

      <a-alert
        class="action-hint"
        type="info"
        show-icon
        message="自定义动作保存在数据库中；内置动作由代码维护，应用启动时自动同步。"
      />

      <a-table
        row-key="actionCode"
        :loading="loading"
        :columns="columns"
        :data-source="actions"
        :pagination="false"
      >
        <span slot="builtin" slot-scope="text">
          <a-tag :color="Number(text) === 1 ? 'blue' : 'green'">
            {{ Number(text) === 1 ? '内置' : '自定义' }}
          </a-tag>
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
          <a @click="openEdit(record)" v-if="Number(record.builtin) !== 1">修改</a>
          <a-divider type="vertical" v-if="Number(record.builtin) !== 1" />
          <a-popconfirm
            v-if="Number(record.builtin) !== 1"
            title="确认删除该动作？"
            ok-text="删除"
            cancel-text="取消"
            @confirm="removeAction(record)"
          >
            <a class="action-danger">删除</a>
          </a-popconfirm>
          <span v-if="Number(record.builtin) === 1" class="action-empty">代码维护</span>
        </span>
      </a-table>
    </a-card>

    <a-modal
      v-model="editorVisible"
      :title="editing ? '修改动作' : '新增动作'"
      width="640px"
      :confirm-loading="saving"
      @ok="saveAction"
    >
      <a-form-model ref="actionForm" :model="form" :rules="rules" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-model-item label="动作编码" prop="actionCode">
          <a-input v-model="form.actionCode" :disabled="editing" placeholder="例如 archive" />
        </a-form-model-item>
        <a-form-model-item label="动作名称" prop="actionName">
          <a-input v-model="form.actionName" placeholder="例如 成员归档" />
        </a-form-model-item>
        <a-form-model-item label="权限标识" prop="permissionCode">
          <a-input v-model="form.permissionCode" placeholder="留空自动生成" />
        </a-form-model-item>
        <a-form-model-item label="handlerKey" prop="handlerKey">
          <a-auto-complete
            v-model="form.handlerKey"
            :data-source="handlerKeys"
            placeholder="选择或输入已注册的执行器"
            style="width: 100%"
          />
        </a-form-model-item>
        <a-form-model-item label="执行器配置" prop="configJson">
          <a-textarea v-model="form.configJson" :rows="6" placeholder='例如 {"operation":"invite"}' />
        </a-form-model-item>
        <a-form-model-item label="状态" prop="status">
          <a-radio-group v-model="form.status">
            <a-radio-button value="0">启用</a-radio-button>
            <a-radio-button value="1">停用</a-radio-button>
          </a-radio-group>
        </a-form-model-item>
      </a-form-model>
    </a-modal>
  </page-header-wrapper>
</template>

<script>
import {
  createModuleAction,
  deleteModuleAction,
  getModuleActions,
  updateModuleAction
} from '/src/api/system/menu.js'
import { getWorkflowActionHandlers } from '/src/api/workflow/action.js'
import { getRouters } from '/src/api/user/module.js'

export default {
  name: 'ModuleActions',
  data() {
    return {
      modules: [],
      moduleId: undefined,
      actions: [],
      handlerKeys: [],
      loading: false,
      saving: false,
      editorVisible: false,
      editing: false,
      form: this.emptyForm(),
      rules: {
        actionCode: [
          { required: true, message: '请输入动作编码', trigger: 'blur' },
          { pattern: /^[a-z][a-z0-9_]{0,31}$/, message: '使用小写字母、数字和下划线', trigger: 'blur' }
        ],
        actionName: [{ required: true, message: '请输入动作名称', trigger: 'blur' }],
        configJson: [{ validator: this.validateConfigJson, trigger: 'blur' }]
      },
      columns: [
        { title: '动作编码', dataIndex: 'actionCode', width: 150 },
        { title: '动作名称', dataIndex: 'actionName', width: 150 },
        { title: '权限标识', dataIndex: 'permissionCode', ellipsis: true },
        { title: 'handlerKey', dataIndex: 'handlerKey', scopedSlots: { customRender: 'handlerKey' }, width: 190 },
        { title: '来源', dataIndex: 'builtin', scopedSlots: { customRender: 'builtin' }, width: 90 },
        { title: '状态', dataIndex: 'status', scopedSlots: { customRender: 'status' }, width: 90 },
        { title: '操作', scopedSlots: { customRender: 'operation' }, width: 150, align: 'center' }
      ]
    }
  },
  computed: {
    moduleOptions() {
      return this.modules.map(item => ({
        ...item,
        displayName: item.displayName
      }))
    }
  },
  created() {
    this.loadModules()
    this.loadHandlers()
  },
  methods: {
    emptyForm() {
      return {
        actionCode: '',
        actionName: '',
        permissionCode: '',
        handlerKey: '',
        configJson: '{}',
        status: '0'
      }
    },
    async loadModules() {
      const response = await getRouters()
      const rows = []
      const walk = (nodes, parentName = '') => {
        ;(nodes || []).forEach(node => {
          const nodeTitle = node.meta?.title || node.moduleName || node.name
          const displayName = parentName ? `${parentName} / ${nodeTitle}` : nodeTitle
          if (node.moduleId && node.component && !['Layout', 'ParentView'].includes(node.component)) {
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
        this.loadActions()
      }
    },
    async loadHandlers() {
      const response = await getWorkflowActionHandlers()
      this.handlerKeys = response.data || []
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
      this.$router.replace({
        query: {
          ...this.$route.query,
          moduleId
        }
      })
      this.loadActions()
    },
    openCreate() {
      this.editing = false
      this.form = this.emptyForm()
      this.editorVisible = true
    },
    openEdit(row) {
      this.editing = true
      this.form = {
        actionCode: row.actionCode,
        actionName: row.actionName,
        permissionCode: row.permissionCode || '',
        handlerKey: row.handlerKey || '',
        configJson: row.configJson || '{}',
        status: String(row.status || '0')
      }
      this.editorVisible = true
    },
    saveAction() {
      this.$refs.actionForm.validate(async valid => {
        if (!valid) {
          return
        }
        this.saving = true
        try {
          if (this.editing) {
            await updateModuleAction(this.moduleId, this.form.actionCode, this.form)
            this.$message.success('动作修改成功')
          } else {
            await createModuleAction(this.moduleId, this.form)
            this.$message.success('动作新增成功')
          }
          this.editorVisible = false
          await this.loadActions()
        } finally {
          this.saving = false
        }
      })
    },
    async removeAction(row) {
      await deleteModuleAction(this.moduleId, row.actionCode)
      this.$message.success('动作已删除')
      this.loadActions()
    },
    validateConfigJson(rule, value, callback) {
      try {
        JSON.parse(value || '{}')
        callback()
      } catch (error) {
        callback(new Error('请输入有效 JSON'))
      }
    }
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
