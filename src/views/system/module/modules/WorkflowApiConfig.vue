<template>
  <div>
    <a-form-model-item label="业务接口" required>
      <a-select :value="config.endpoint || undefined" show-search :filter-option="filterEndpoint" :loading="loading" :disabled="disabled || loading" placeholder="搜索并选择当前模块业务接口" @change="selectEndpoint">
        <a-select-option v-for="api in apis" :key="api.id" :value="api.id">{{ api.name }} · {{ api.method }} {{ api.path }}</a-select-option>
      </a-select>
      <p v-if="loadError" class="api-config-error">接口目录加载失败，<a @click="loadApis">点击重试</a></p>
      <p v-else-if="!loading && !apis.length" class="api-config-hint">当前模块没有符合通用接入条件的接口，请使用专用执行器。</p>
      <p v-if="selected" class="api-config-hint">接口原权限：{{ selected.requiredAction }}。申请人仍须具备该权限。启用审批后，相同原权限的直接写接口也会受到保护；不同操作请绑定各自按钮。</p>
      <p v-if="config.endpoint && !selected && !loading" class="api-config-error">原接口不在可用目录中，请重新选择。</p>
    </a-form-model-item>
    <a-form-model-item v-for="name in selected ? selected.pathParameters : []" :key="name" :label="'路径参数 ' + name" required>
      <a-input :value="config.pathMapping[name]" :disabled="disabled" placeholder="提交数据中的字段，例如 customerId 或 record.id" @input="setPath(name, $event.target.value)" />
    </a-form-model-item>
    <a-form-model-item v-if="selected && selected.hasBody" label="请求字段">
      <p class="api-config-hint">不添加映射时传递全部业务字段；添加后仅传递映射字段。支持 record.name 这样的字段路径。</p>
      <div v-for="(row, index) in bodyRows" :key="index" class="api-config-row">
        <a-input v-model="row.target" :disabled="disabled" placeholder="接口字段" @input="saveBody" />
        <span>←</span>
        <a-input v-model="row.source" :disabled="disabled" placeholder="提交字段" @input="saveBody" />
        <a-button :disabled="disabled" icon="minus" @click="removeBody(index)" />
      </div>
      <a-button size="small" :disabled="disabled" icon="plus" @click="bodyRows.push({ target: '', source: '' })">添加请求字段</a-button>
    </a-form-model-item>
    <a-form-model-item label="申请标题">
      <a-input v-model="config.titleTemplate" :disabled="disabled" :max-length="200" placeholder="例如 跟进审批：{customerName}" @input="emitConfig" />
    </a-form-model-item>
    <a-form-model-item label="审批展示">
      <div v-for="(row, index) in config.displayFields" :key="index" class="api-config-row">
        <a-input v-model="row.label" :disabled="disabled" placeholder="展示名称" @input="emitConfig" />
        <a-input v-model="row.field" :disabled="disabled" placeholder="提交字段" @input="emitConfig" />
        <a-button :disabled="disabled" icon="minus" @click="removeDisplay(index)" />
      </div>
      <a-button size="small" :disabled="disabled" icon="plus" @click="config.displayFields.push({ label: '', field: '' })">添加展示字段</a-button>
      <p class="api-config-hint">申请时冻结表单和执行配置。未选择展示字段时按通用详情显示提交内容。</p>
    </a-form-model-item>
  </div>
</template>

<script>
import { getWorkflowActionApis } from '/src/api/workflow/action.js'

export default {
  name: 'WorkflowApiConfig',
  props: { moduleId: { type: [Number, String], required: true }, value: { type: String, default: '{}' }, disabled: { type: Boolean, default: false } },
  data: () => ({ apis: [], loading: false, loadError: false, config: { endpoint: '', pathMapping: {}, bodyMapping: {}, displayFields: [], titleTemplate: '' }, bodyRows: [], requestSequence: 0 }),
  computed: { selected() { return this.apis.find(api => api.id === this.config.endpoint) } },
  watch: {
    value: { immediate: true, handler(value) {
      let config
      try { config = JSON.parse(value || '{}') } catch (error) { config = {} }
      if (JSON.stringify(this.config) === JSON.stringify(config)) return
      this.config = { ...config, pathMapping: config.pathMapping || {}, bodyMapping: config.bodyMapping || {}, displayFields: config.displayFields || [], titleTemplate: config.titleTemplate || '' }
      this.bodyRows = Object.entries(this.config.bodyMapping).map(([target, source]) => ({ target, source }))
    } },
    moduleId: { immediate: true, handler() { this.loadApis() } }
  },
  beforeDestroy() { this.requestSequence++ },
  methods: {
    async loadApis() {
      const sequence = ++this.requestSequence
      this.loading = true
      this.loadError = false
      this.apis = []
      try {
        const response = await getWorkflowActionApis(this.moduleId)
        if (sequence === this.requestSequence) this.apis = response.data || []
      } catch (error) { if (sequence === this.requestSequence) this.loadError = true }
      finally { if (sequence === this.requestSequence) this.loading = false }
    },
    filterEndpoint(input, option) { return option.componentOptions.children.map(child => child.text || '').join('').toLowerCase().includes(input.toLowerCase()) },
    selectEndpoint(id) {
      this.config.endpoint = id
      this.config.pathMapping = Object.fromEntries((this.selected?.pathParameters || []).map(name => [name, name]))
      this.config.bodyMapping = {}
      this.bodyRows = []
      this.emitConfig()
    },
    setPath(name, value) { this.$set(this.config.pathMapping, name, value); this.emitConfig() },
    saveBody() { this.config.bodyMapping = Object.fromEntries(this.bodyRows.filter(row => row.target).map(row => [row.target, row.source])); this.emitConfig() },
    removeBody(index) { this.bodyRows.splice(index, 1); this.saveBody() },
    removeDisplay(index) { this.config.displayFields.splice(index, 1); this.emitConfig() },
    emitConfig() { this.$emit('input', JSON.stringify(this.config)) },
    validate() {
      if (this.loading || this.loadError) throw new Error('请先加载业务接口目录')
      if (!this.selected) throw new Error('请选择业务接口')
      if (this.selected.pathParameters.some(name => !String(this.config.pathMapping[name] || '').trim())) throw new Error('请完整填写路径参数映射')
      if (this.bodyRows.some(row => !row.target.trim() || !row.source.trim())) throw new Error('请完整填写请求字段映射')
      if (new Set(this.bodyRows.map(row => row.target)).size !== this.bodyRows.length) throw new Error('请求字段不能重复')
      if (this.config.displayFields.some(row => !row.label.trim() || !row.field.trim())) throw new Error('请完整填写审批展示字段')
      this.saveBody()
    }
  }
}
</script>

<style scoped>
.api-config-row { display: flex; gap: 6px; margin-bottom: 8px; align-items: center; }
.api-config-hint { color: #8c8c8c; line-height: 1.6; margin: 8px 0; }
.api-config-error { color: #c00000; }
</style>
