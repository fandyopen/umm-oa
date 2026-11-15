<template>
  <a-drawer :visible="visible" title="公司计薪规则" width="min(820px,94vw)" :closable="!saving" :mask-closable="!saving" @close="close">
    <a-alert type="info" show-icon message="公司规则设置后按工资月份自动沿用。每次保存新增版本，已生成工资保留原规则；如需改用新规则，请在工资草稿中明确重新核算。" class="tw-mb-4" />
    <div class="hr-toolbar">
      <a-month-picker v-model="month" :allow-clear="false" format="YYYY-MM" value-format="YYYY-MM" @change="load" />
      <a-button :loading="loading" icon="reload" @click="load">刷新</a-button>
      <a-button v-if="canManage" type="primary" :disabled="loading || saving || failed" @click="create()">新增规则版本</a-button>
    </div>
    <a-alert v-if="failed" type="error" show-icon message="计薪规则加载失败，请刷新重试" class="tw-mb-4" />
    <template v-else-if="!loading">
      <h3>{{ month }} 适用规则</h3>
      <template v-if="applicable">
        <p>{{ applicable.name }} · V{{ applicable.versionNo }} · {{ applicable.effectiveMonth }} 起生效</p>
        <a-descriptions bordered :column="2" size="small">
          <a-descriptions-item v-for="f in fields" :key="f.key" :label="f.label">{{ applicable.rules[f.key] }}</a-descriptions-item>
        </a-descriptions>
      </template>
      <a-alert v-else type="warning" show-icon message="本月尚无适用计薪规则，生成工资前请先设置。可以设置更早的生效月份，之后各月会自动沿用。" />
    </template>
    <a-card v-if="editing" title="保存新的规则版本" class="tw-mt-4">
      <a-alert type="warning" show-icon message="同一生效月份取最新保存的版本；后续月份有单独规则时优先取该月规则。已有工资金额不会自动变化。" class="tw-mb-4" />
      <a-form-model layout="vertical">
        <div class="hr-form-grid">
          <a-form-model-item label="规则名称" required><a-input v-model="form.name" :max-length="100" :disabled="saving" placeholder="例如：公司计薪规则" /></a-form-model-item>
          <a-form-model-item label="生效月份" required><a-month-picker v-model="form.effectiveMonth" :allow-clear="false" format="YYYY-MM" value-format="YYYY-MM" :disabled="saving" class="tw-w-full" /></a-form-model-item>
          <a-form-model-item v-for="f in fields" :key="f.key" :label="f.label" required>
            <a-input-number v-model="form.rules[f.key]" :min="f.min || 0" :max="f.max || 9999999999" :precision="4" :disabled="saving" class="tw-w-full" />
          </a-form-model-item>
        </div>
        <a-form-model-item label="设置 / 变更说明" required><a-textarea v-model="form.reason" :max-length="1000" :rows="3" :disabled="saving" placeholder="记录公司核准依据或本次修改原因" /></a-form-model-item>
        <p class="hr-note">月薪按本月应出勤折算，固定补贴同比例折算。系统不预设计薪工时、请假比例、加班倍率、个税或社保规则。</p>
        <a-space><a-button type="primary" :loading="saving" @click="save">保存规则版本</a-button><a-button :disabled="saving" @click="editing = false">取消</a-button></a-space>
      </a-form-model>
    </a-card>
    <h3 class="hr-section-title">规则版本记录</h3>
    <a-table :loading="loading" :data-source="versions" row-key="id" :pagination="{ pageSize: 6 }" size="small">
      <a-table-column title="版本" data-index="versionNo"><template slot-scope="v">V{{ v }}</template></a-table-column>
      <a-table-column title="规则名称" data-index="name" />
      <a-table-column title="生效月份" data-index="effectiveMonth" />
      <a-table-column title="设置人" data-index="creatorName" />
      <a-table-column title="设置时间" data-index="createTime" />
      <a-table-column v-if="canManage" title="操作"><template slot-scope="_, r"><a-button type="link" :disabled="saving" @click="create(r)">以此新建</a-button></template></a-table-column>
      <template slot="expandedRowRender" slot-scope="r">
        <a-descriptions :column="2" size="small"><a-descriptions-item v-for="f in fields" :key="f.key" :label="f.label">{{ r.rules[f.key] }}</a-descriptions-item><a-descriptions-item label="变更说明" :span="2">{{ r.reason }}</a-descriptions-item></a-descriptions>
      </template>
    </a-table>
  </a-drawer>
</template>

<script>
import { hr } from '/src/api/enterprise/hr.js'
import { payrollRuleFields } from './payrollRules.js'

export default {
  name: 'PayrollRules',
  props: { canManage: Boolean, period: { type: String, default: '' } },
  data: () => ({ visible: false, month: '', loading: false, saving: false, failed: false, editing: false, versions: [], applicable: null, latestVersion: 0, form: { rules: {} }, fields: payrollRuleFields, requestVersion: 0 }),
  methods: {
    async open() {
      this.month = this.period || new Date().toLocaleDateString('sv-SE').slice(0, 7)
      this.editing = false
      this.visible = true
      await this.load()
    },
    close() {
      if (!this.saving) this.visible = false
    },
    async load() {
      const request = ++this.requestVersion
      this.loading = true
      this.failed = false
      try {
        const { data } = await hr('/payroll/rules', 'get', { period: this.month })
        if (request !== this.requestVersion) return
        this.versions = data.versions || []
        this.applicable = data.applicableRule || null
        this.latestVersion = data.latestVersion
      } catch (e) {
        if (request === this.requestVersion) this.failed = true
      } finally {
        if (request === this.requestVersion) this.loading = false
      }
    },
    create(source) {
      if (!this.canManage || this.failed || this.loading || this.saving) return
      const base = source || this.applicable
      this.form = { name: base?.name || '公司计薪规则', effectiveMonth: this.month, rules: { ...(base?.rules || {}) }, reason: '', baseVersion: this.latestVersion }
      this.editing = true
    },
    async save() {
      if (this.saving || !this.canManage) return
      if (!this.form.name?.trim() || !this.form.effectiveMonth || !this.form.reason?.trim()) return this.$message.warning('请填写规则名称、生效月份和变更说明')
      if (this.fields.some(f => this.form.rules[f.key] === null || this.form.rules[f.key] === undefined)) return this.$message.warning('请明确填写全部公司计薪参数')
      this.saving = true
      try {
        await hr('/payroll/rules', 'post', this.form)
        this.editing = false
        this.$message.success('计薪规则已保存，新生成工资将按月份自动引用')
        await this.load()
        this.$emit('saved')
      } finally {
        this.saving = false
      }
    }
  }
}
</script>
<style src="./hr.css"></style>
