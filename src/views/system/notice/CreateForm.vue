<template>
  <page-header-wrapper @back="back">
    <a-card :bordered="false">
      <h2>{{ noticeId ? '修改通知' : '新增通知' }}</h2>
      <a-alert v-if="error" type="error" :message="error" show-icon />
      <a-alert type="info" message="点击发布通知后员工即可查看，无需审批。也可以先保存草稿；将已发布通知保存为草稿后会暂时停止展示。" class="tip" />
      <a-spin :spinning="loading">
        <a-form-model ref="form" :model="form" :rules="rules" layout="vertical">
          <a-form-model-item label="公告标题" prop="noticeTitle"><a-input v-model="form.noticeTitle" :max-length="200" placeholder="请输入标题" /></a-form-model-item>
          <a-form-model-item label="公告类型" prop="noticeType"><a-select v-model="form.noticeType"><a-select-option value="1">通知</a-select-option><a-select-option value="2">公告</a-select-option></a-select></a-form-model-item>
          <a-form-model-item label="正文"><tinymce-editor v-if="!loading && !error" ref="editor" :value="form.noticeContent" /></a-form-model-item>
          <a-space><a-button v-if="showModuleAction('notices', 'edit')" type="primary" :loading="saving" :disabled="loading || !!error" @click="save(true)">发布通知</a-button><a-button :loading="saving" :disabled="loading || !!error" @click="save(false)">保存草稿</a-button><a-button @click="back">返回列表</a-button></a-space>
        </a-form-model>
      </a-spin>
    </a-card>
  </page-header-wrapper>
</template>
<script>
import { getNotice, addNotice, updateNotice } from '/src/api/system/notice.js'
import { withModuleContexts } from '/src/utils/moduleContext.js'
import TinymceEditor from './TinymceEditor.vue'
export default {
  components: { TinymceEditor },
  mixins: [withModuleContexts({ notices: { component: 'system/notice/index', label: '通知公告' } })],
  data: () => ({ loading: true, saving: false, error: '', form: { noticeTitle: '', noticeType: '1', noticeContent: '' },
    rules: { noticeTitle: [{ required: true, message: '请填写标题', trigger: 'blur' }], noticeType: [{ required: true, message: '请选择类型', trigger: 'change' }] } }),
  computed: { noticeId() { return this.$route.query.id || this.$route.params.id || null } },
  watch: { noticeId: { immediate: true, handler() { this.load() } } },
  methods: {
    async load() {
      this.loading = true; this.error = ''; this.form = { noticeTitle: '', noticeType: '1', noticeContent: '' }
      try { if (this.noticeId) this.form = (await getNotice(this.noticeId)).data }
      catch (error) { this.error = error.message || '加载失败' }
      finally { this.loading = false }
    },
    async save(publish) {
      if (this.saving) return
      const valid = await new Promise(resolve => this.$refs.form.validate(resolve))
      if (!valid || !(await this.checkModuleAction({ module: 'notices', action: this.noticeId ? 'edit' : 'add', label: '保存通知' }))) return
      if (publish && !(await this.checkModuleAction({ module: 'notices', action: 'edit', label: '发布通知' }))) return
      this.saving = true
      try {
        const payload = { ...this.form, publish, noticeContent: this.$refs.editor.tinymceHtml }
        if (this.noticeId) await updateNotice({ ...payload, noticeId: this.noticeId })
        else await addNotice(payload)
        this.$message.success(publish ? '通知已发布' : '已保存草稿'); this.back()
      } finally { this.saving = false }
    },
    back() { this.$router.push('/system/notice') }
  }
}
</script>
<style scoped>.tip { margin: 16px 0; }</style>
