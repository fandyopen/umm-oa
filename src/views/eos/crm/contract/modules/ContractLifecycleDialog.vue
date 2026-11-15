<template>
  <a-modal :visible="!!action" :title="title" :confirm-loading="saving" :mask-closable="false" :closable="!saving" :keyboard="!saving" @cancel="cancel" @ok="save">
    <p>合同：{{ record.name }}（{{ record.code }}）</p>
    <a-alert type="warning" show-icon :message="hint" />
    <a-form layout="vertical" class="tw-mt-4" v-if="action !== 'remove'">
      <a-form-item label="操作原因（必填）"><a-textarea v-model="reason" :disabled="saving" :rows="4" :max-length="2000" /></a-form-item>
    </a-form>
  </a-modal>
</template>
<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { crmModules } from '/src/views/eos/crm/moduleContexts.js'
import { contractAction, deleteContract } from '/src/api/eos/crm/contracts.js'
export default {
  mixins: [withModuleContexts({ contract: crmModules.contract }, { save: function () { return { module: 'contract', action: this.action, label: this.title } } })],
  props: { action: { type: String, default: '' }, record: { type: Object, required: true } },
  data: () => ({ reason: '', saving: false }),
  computed: {
    title() { return { void: '作废合同', terminate: '终止合同', withdraw: '撤回审批', remove: '删除合同' }[this.action] || '' },
    hint() { return { void: '仅从未生效的合同可作废；已发布作废审批时，通过后才生效。', terminate: '请先关闭或完成关联职位。终止后停止新增履约，未结账款及保证期仍需处理，处理完后办理结案。', withdraw: '撤回当前审批并停止未办理待办，保留原流程记录；合同内容审批撤回后回到草稿。', remove: '没有历史和业务引用的草稿会彻底删除；其余可删除合同从默认列表隐藏，保留版本、回款及业务历史。' }[this.action] || '' }
  },
  watch: { action() { this.reason = '' } },
  methods: {
    cancel() { if (!this.saving) this.$emit('close') },
    async save() {
      if (this.saving || !this.action) return
      if (this.action !== 'remove' && !this.reason.trim()) return this.$message.warning('请填写操作原因')
      this.saving = true
      try {
        const payload = { contractId: this.record.id, revision: this.record.revision, reason: this.reason.trim(), title: this.title + '：' + this.record.name }
        let result
        if (['void', 'terminate'].includes(this.action)) result = await this.submitConfiguredAction(this.action, payload, this.title)
        else if (this.action === 'remove') result = await deleteContract(this.record.id, { revision: this.record.revision })
        else result = await contractAction(this.record.id, this.action, payload)
        if (!result) return
        this.$message.success(result.data?.status === 'pendingApproval' ? '申请已提交，审批通过后生效' : this.action === 'remove' ? '合同已删除' : '操作成功')
        this.$emit('success', { deleted: this.action === 'remove' })
        this.$emit('close')
      } finally { this.saving = false }
    }
  }
}
</script>
