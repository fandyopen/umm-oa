<template>
  <app-shell :title="title" back>
    <view v-if="error" class="error" @click="!busy && load()">{{ error }}</view>
    <view v-if="definition" class="card">
      <view class="h2">{{ title }}</view>
      <view v-if="action === 'track'" class="notice">按平台按钮流程配置提交；如需审批，通过后正式保存跟进。</view>
      <view v-if="key === 'applications' && action === 'add'" class="notice">候选人只允许「沟通中、可推荐」且未被招聘流程锁定的人才。</view>
      <view class="spacer" />
      <business-fields :fields="definition.fields" v-model="values" />
      <button class="btn" :disabled="busy" :loading="busy" @click="save">{{ action === 'track' ? '保存跟进' : '保存并提交' }}</button>
      <view class="notice" v-if="key === 'receipt' || key === 'invoice'">
        先保存业务草稿，再在详情中提交确认或审批。只有已发布并生效的按钮流程才会接入审批。
      </view>
    </view>
    <view v-else-if="!error" class="empty">正在加载表单…</view>
  </app-shell>
</template>
<script>
import BusinessFields from '../../components/Fields.vue'
import { catalog, positiveId } from '../../services/catalog'
import { requireModule, request, can } from '../../services/runtime'
import { formDefinition, loadOptions, saveBusiness, availableActions } from '../../services/business'
export default {
  components: { BusinessFields },
  data: () => ({ key: '', action: '', id: null, row: {}, definition: null, values: {}, options: {}, error: '', busy: false, source: '' }),
  computed: {
    title() {
      return (
        (this.action === 'track' ? '新增跟进' : this.action === 'add' ? '新增' : this.action === 'edit' ? '修改' : '业务办理') +
        ' · ' +
        ((catalog[this.key] && catalog[this.key].title) || '')
      )
    }
  },
  onLoad(q) {
    this.key = q.module
    this.action = q.action || 'add'
    this.id = q.id ? positiveId(q.id) : null
    this.source = q.source || ''
    this.load()
  },
  methods: {
    async load() {
      this.error = ''
      try {
        await requireModule(this.key)
        if (this.action !== 'add') {
          if (!this.id) throw new Error('记录编号无效')
          this.row = (await request(catalog[this.key].detail + this.id)).data
          const allowed = availableActions(this.key, this.row).find((a) => a.key === this.action)
          if (!allowed) throw new Error('当前状态或账号不允许此操作')
        } else if (!can(this.key, 'add')) throw new Error('没有新增权限')
        this.options = await loadOptions(this.key)
        this.definition = formDefinition(this.key, this.action, this.row, this.options)
        this.values = { ...this.definition.values }
        if (this.source) this.values.sourceKey = this.source
      } catch (e) {
        this.definition = null
        this.error = e.message
      }
    },
    async save() {
      if (this.busy) return
      for (const f of this.definition.fields) {
        const v = this.values[f.key]
        if (f.required && (v === undefined || v === null || String(v).trim() === '' || (Array.isArray(v) && !v.length)))
          return this.$toast('请填写' + f.label)
        if (
          f.type === 'number' &&
          v !== undefined &&
          v !== '' &&
          (!Number.isFinite(Number(v)) || (f.min !== undefined && Number(v) < f.min))
        )
          return this.$toast(f.label + '数值无效')
      }
      if (this.action === 'guarantee') {
        if (this.values.operation !== 'complete' && !this.values.exitDate) return this.$toast('请填写离职日期')
        if (this.values.operation === 'refund' && (!(Number(this.values.amount) > 0) || !this.values.attachmentId))
          return this.$toast('退费须填写正数金额并选择业务凭证')
      }
      this.busy = true
      this.error = ''
      try {
        await requireModule(
          this.key,
          this.action === 'add'
            ? 'add'
            : this.action === 'edit'
            ? 'edit'
            : (availableActions(this.key, this.row).find((a) => a.key === this.action) || {}).permission
        )
        const r = await saveBusiness(this.key, this.action, this.row, this.values, this.options, this.definition)
        uni.showModal({
          confirmText: '返回列表',
          title: r.data && r.data.status === 'pendingApproval' ? '已提交审批' : '保存成功',
          content:
            r.data && r.data.status === 'pendingApproval'
              ? '审批通过后正式生效，可在「我的发起」查看进度。'
              : ['receipt', 'invoice'].includes(this.key)
              ? '草稿已保存，请在列表打开详情后提交确认或审批。'
              : '业务已保存，返回列表可查看最新结果。',
          showCancel: false,
          success: () => uni.navigateBack()
        })
      } catch (e) {
        this.error = e.message
      } finally {
        this.busy = false
      }
    }
  }
}
</script>
