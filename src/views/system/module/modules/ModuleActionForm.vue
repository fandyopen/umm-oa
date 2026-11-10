<template>
  <a-drawer width="560px" placement="right" :visible="open" :mask-closable="!saving" :closable="!saving" :body-style="{ paddingBottom: '80px' }" @close="cancel">
    <a-divider orientation="left"
      ><b>{{ editing ? '修改按钮' : '新增按钮' }}</b></a-divider
    >
    <p class="action-flow-hint">所属页面：{{ moduleName }}</p>
    <a-alert type="info" show-icon message="新建时可自定义动作编码，也可使用系统建议编码；保存后不可修改。" description="动作编码用于匹配页面按钮、权限及审批历史。填写已删除按钮的原编码可恢复该按钮，恢复后需重新授权并检查审批配置。" class="tw-mb-4" />
    <a-form-model ref="actionForm" :model="form" :rules="rules" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <a-form-model-item label="动作编码" prop="actionCode">
        <a-input v-model="form.actionCode" :read-only="editing || !canManageStructure" :max-length="32" :placeholder="generatingCode ? '正在生成建议编码...' : '留空自动生成，或输入如 track'" @change="codeManuallyEdited = true" />
      </a-form-model-item>
      <a-form-model-item label="动作名称" prop="actionName">
        <a-input v-model="form.actionName" :disabled="!canManageStructure" placeholder="例如 提交审批" />
      </a-form-model-item>
      <a-form-model-item label="权限标识" prop="permissionCode">
        <a-input v-model="form.permissionCode" :disabled="!canManageStructure" placeholder="留空自动生成" />
      </a-form-model-item>
      <a-form-model-item v-if="handlerManaged" label="业务动作">
        <span>{{ businessActionName }}</span>
        <p class="action-flow-hint">由系统关联业务执行器，可直接配置审批流程。</p>
      </a-form-model-item>
      <a-form-model-item v-else label="业务执行方式">
        <a-select :value="executionMode" :disabled="!canManageStructure" @change="changeExecutionMode">
          <a-select-option value="none">仅权限控制（待接入）</a-select-option>
          <a-select-option value="api">通用业务接口</a-select-option>
          <a-select-option value="handler">专用业务执行器</a-select-option>
        </a-select>
      </a-form-model-item>
      <workflow-api-config v-if="!handlerManaged && executionMode === 'api'" ref="apiConfig" v-model="form.configJson" :module-id="moduleId" :disabled="!canManageStructure" />
      <a-form-model-item v-if="!handlerManaged && executionMode === 'handler'" label="业务执行器" prop="handlerKey">
        <a-auto-complete v-model="form.handlerKey" :disabled="!canManageStructure" :data-source="handlerKeys" placeholder="选择或输入已注册的执行器" class="tw-w-full" />
      </a-form-model-item>
      <a-form-model-item v-if="!handlerManaged && executionMode === 'handler'" label="执行器配置" prop="configJson">
        <a-textarea v-model="form.configJson" :disabled="!canManageStructure" :rows="6" placeholder='例如 {"operation":"invite"}' />
      </a-form-model-item>
      <a-form-model-item label="排序" prop="actionSort">
        <a-input-number v-model="form.actionSort" :disabled="!canManageStructure" :min="0" :precision="0" />
      </a-form-model-item>
      <a-form-model-item label="说明" prop="actionDescription">
        <a-textarea v-model="form.actionDescription" :disabled="!canManageStructure" :max-length="500" :rows="2" />
      </a-form-model-item>
      <a-form-model-item label="流程设计">
        <a-switch v-model="form.flowDesignEnabled" :disabled="!canManageWorkflow" checked-children="开启" un-checked-children="关闭" />
        <p class="action-flow-hint">开启仅开放流程设计入口，未配置或仅保存草稿不改变按钮行为。发布并启用审批后，新操作才进入审批；关闭保留流程与历史，已发起的审批继续处理。</p>
        <a-alert v-if="Number(moduleId) === 341201 && form.actionCode === 'submit'" type="warning" show-icon message="合同提交必须审批：关闭流程设计将阻止新提交，不会直接通过。" />
      </a-form-model-item>
      <a-form-model-item label="状态" prop="status">
        <a-radio-group v-model="form.status" :disabled="!canManageStructure">
          <a-radio-button value="0">启用</a-radio-button>
          <a-radio-button value="1">停用</a-radio-button>
        </a-radio-group>
      </a-form-model-item>
      <div class="module-action-form__footer">
        <a-space>
          <a-button type="primary" :loading="saving" :disabled="generatingCode" @click="submit()">保存</a-button>
          <a-button :disabled="saving" @click="cancel">取消</a-button>
        </a-space>
      </div>
    </a-form-model>
  </a-drawer>
</template>

<script>
import { createModuleAction, updateModuleAction, getNextModuleActionCode } from '/src/api/system/menu.js'
import { getWorkflowActionHandlers } from '/src/api/workflow/action.js'
import WorkflowApiConfig from './WorkflowApiConfig.vue'

export default {
  name: 'ModuleActionForm',
  components: { WorkflowApiConfig },
  props: {
    canManageStructure: { type: Boolean, default: false },
    canManageWorkflow: { type: Boolean, default: false }
  },
  data() {
    return {
      open: false,
      saving: false,
      generatingCode: false,
      codeManuallyEdited: false,
      editing: false,
      moduleId: undefined,
      moduleName: '',
      handlerKeys: [],
      handlerManaged: false,
      selectedExecutionMode: '',
      businessActionName: '',
      editorSequence: 0,
      form: this.emptyForm(),
      rules: {
        actionCode: [{ validator: this.validateActionCode, trigger: 'blur' }],
        actionName: [{ required: true, message: '请输入动作名称', trigger: 'blur' }],
        configJson: [{ validator: this.validateConfigJson, trigger: 'blur' }]
      }
    }
  },
  computed: {
    executionMode() { return this.selectedExecutionMode || (this.form.handlerKey === 'workflow.business-api' ? 'api' : this.form.handlerKey ? 'handler' : 'none') }
  },
  methods: {
    changeExecutionMode(mode) {
      this.selectedExecutionMode = mode
      this.form.handlerKey = mode === 'api' ? 'workflow.business-api' : ''
      this.form.configJson = '{}'
    },
    emptyForm() {
      return { actionCode: '', actionName: '', permissionCode: '', handlerKey: '', configJson: '{}', flowDesignEnabled: false, actionSort: 0, actionDescription: '', status: '0' }
    },
    handleAdd(page) {
      if (!this.canManageStructure) return
      this.editing = false
      this.selectedExecutionMode = ''
      this.codeManuallyEdited = false
      this.handlerManaged = false
      this.businessActionName = ''
      this.moduleId = page.moduleId
      this.moduleName = page.moduleName || '当前页面'
      this.form = this.emptyForm()
      this.show()
      this.loadNextActionCode(this.editorSequence)
    },
    handleUpdate(row) {
      if (!this.canManageStructure && !this.canManageWorkflow) return
      this.editing = true
      this.selectedExecutionMode = ''
      this.handlerManaged = row.handlerManaged === true
      this.businessActionName = row.businessActionName || row.actionName
      this.moduleId = row.moduleId
      this.moduleName = row.moduleName || '当前页面'
      this.form = {
        actionCode: row.actionCode,
        actionName: row.actionName,
        permissionCode: row.permissionCode || '',
        handlerKey: row.handlerKey || '',
        configJson: row.configJson || '{}',
        flowDesignEnabled: row.flowDesignEnabled === true || Number(row.flowDesignEnabled) === 1,
        actionSort: Number(row.actionSort || 0),
        actionDescription: row.actionDescription || '',
        status: String(row.status || '0')
      }
      this.show()
    },
    show() {
      this.editorSequence++
      this.generatingCode = false
      this.open = true
      this.$nextTick(() => this.$refs.actionForm && this.$refs.actionForm.clearValidate())
      this.loadHandlers()
    },
    async loadHandlers() {
      if (this.handlerKeys.length) return
      try {
        const response = await getWorkflowActionHandlers()
        this.handlerKeys = response.data || []
      } catch (error) {
        this.$message.warning('执行器选项加载失败，可稍后重新打开重试')
      }
    },
    async loadNextActionCode(sequence) {
      this.generatingCode = true
      try {
        const response = await getNextModuleActionCode(this.moduleId)
        if (sequence === this.editorSequence && !this.editing && !this.codeManuallyEdited) this.form.actionCode = response.data?.actionCode || ''
      } catch (error) {
        // An unavailable suggestion must not erase a code entered by the user.
      } finally {
        if (sequence === this.editorSequence) this.generatingCode = false
      }
    },
    submit() {
      if (this.saving || this.generatingCode) return
      this.$refs.actionForm.validate(async valid => {
        if (!valid) return
        if (this.executionMode === 'api') {
          try { this.$refs.apiConfig.validate() }
          catch (error) { this.$message.warning(error.message); return }
        }
        this.saving = true
        try {
          let actionCode = this.form.actionCode
          if (this.editing) {
            const body = this.canManageStructure ? { ...this.form } : { flowDesignEnabled: this.form.flowDesignEnabled }
            await updateModuleAction(this.moduleId, actionCode, body)
            this.$message.success('按钮修改成功')
          } else {
            const response = await createModuleAction(this.moduleId, { ...this.form })
            actionCode = response.data?.actionCode
            this.$message.success((response.data?.restored ? '按钮已恢复，请重新授权并检查审批配置。动作编码：' : '按钮新增成功，动作编码：') + actionCode)
          }
          this.open = false
          this.editorSequence++
          this.$emit('ok', { moduleId: this.moduleId, actionCode })
          await this.$store.dispatch('BootstrapSession').catch(() => this.$message.warning('按钮已保存，请刷新页面同步当前账号配置'))
        } catch (error) {
          this.$message.error(error.message || '按钮保存失败，请检查后重试')
        } finally {
          this.saving = false
        }
      })
    },
    cancel() {
      if (this.saving) return
      this.editorSequence++
      this.open = false
    },
    validateActionCode(rule, value, callback) {
      const code = String(value || '').trim()
      if (!code) return callback()
      if (code === 'page') return callback(new Error('page 为保留编码，请使用其他编码'))
      if (!/^[a-z][a-z0-9_]{0,31}$/.test(code)) return callback(new Error('以小写字母开头，仅支持小写字母、数字、下划线，最多32位'))
      callback()
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
.action-flow-hint {
  color: #8c8c8c;
  line-height: 1.6;
  margin: 8px 0;
}
.module-action-form__footer {
  position: absolute;
  left: 0;
  bottom: 0;
  width: 100%;
  padding: 12px 24px;
  border-top: 1px solid #e8e8e8;
  background: #fff;
  text-align: right;
}
</style>
