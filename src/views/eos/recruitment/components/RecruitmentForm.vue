<template>
  <a-form-model layout="vertical" class="recruit-form" @submit.prevent="submit">
    <a-alert v-if="hint" :message="hint" type="info" show-icon style="margin-bottom: 18px" />
    <a-form-model-item v-for="item in fields" :key="item.key" :required="item.required">
      <span slot="label">{{ item.label }}<small v-if="item.labelHint" class="field-label-hint">{{ item.labelHint }}</small></span>
      <a-select v-if="['select', 'multiple', 'contract'].includes(item.type)" v-model="form[item.key]" :mode="item.type === 'multiple' ? 'multiple' : 'default'" show-search allow-clear :filter-option="filterOption" :disabled="item.disabled" :placeholder="'请选择' + item.label" style="width: 100%">
        <a-select-option v-for="option in optionsFor(item)" :key="option.value" :value="option.value" :disabled="option.disabled" :title="option.disabledReason || option.label">{{ option.label }}</a-select-option>
      </a-select>
      <a-switch v-else-if="item.type === 'boolean'" v-model="form[item.key]" :disabled="item.disabled" checked-children="需要跟进" un-checked-children="无需跟进" />
      <a-input-number v-else-if="item.type === 'number'" v-model="form[item.key]" :min="item.min || 0" :max="item.max || 10000000000" :precision="item.precision === undefined ? 2 : item.precision" :disabled="item.disabled" style="width: 100%" />
      <a-textarea v-else-if="item.type === 'textarea'" v-model="form[item.key]" :rows="4" :max-length="item.max || 2000" :disabled="item.disabled" />
      <a-input v-else v-model="form[item.key]" :type="item.type === 'date' ? 'date' : item.type === 'datetime' ? 'datetime-local' : 'text'" :max-length="item.max || 200" :disabled="item.disabled" />
      <small v-if="item.hint" class="field-hint">{{ item.hint }}</small>
    </a-form-model-item>
    <div class="form-footer"><a-button @click="$emit('cancel')">取消</a-button><a-button type="primary" html-type="submit" :loading="saving">{{ submitText }}</a-button></div>
  </a-form-model>
</template>
<script>
export default {
  name: 'RecruitmentForm',
  props: { fields: { type: Array, default: () => [] }, initial: { type: Object, default: () => ({}) }, hint: { type: String, default: '' }, saving: Boolean, submitText: { type: String, default: '保存' } },
  data() {
    const form = { id: this.initial.id, revision: this.initial.revision }
    this.fields.forEach(item => { const value = this.initial[item.key]; form[item.key] = Array.isArray(value) ? [...value] : value })
    return { form }
  },
  watch: {
    'form.customerId'(value, old) { if (value !== old) this.$set(this.form, 'contractId', undefined) }
  },
  methods: {
    filterOption(input, option) { return String(option.componentOptions.children[0].text).toLowerCase().includes(input.toLowerCase()) },
    optionsFor(item) { return item.type === 'contract' ? item.options.filter(c => c.customerId === this.form.customerId).map(c => ({ value: c.id, label: c.name })) : item.options || [] },
    submit() {
      const missing = this.fields.find(item => item.required && (this.form[item.key] === undefined || this.form[item.key] === null || this.form[item.key] === ''))
      if (missing) return this.$message.warning('请填写' + missing.label)
      if (this.fields.some(item => item.key === 'phone') && !this.form.phone && !this.form.email) return this.$message.warning('手机号或邮箱至少填写一项')
      this.$emit('submit', { ...this.form })
    }
  }
}
</script>
<style scoped>
.recruit-form { padding-bottom: 64px; }
.field-hint { color: #8c8c8c; }
.field-label-hint { margin-left: 8px; color: #8c8c8c; font-size: 12px; font-weight: normal; }
.form-footer { display: flex; gap: 12px; justify-content: flex-end; padding: 16px 0; position: sticky; bottom: 0; background: white; border-top: 1px solid #eee; }
</style>
