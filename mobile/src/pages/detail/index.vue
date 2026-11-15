<template>
  <app-shell :title="info.title || '业务详情'" back>
    <view v-if="error" class="error" @click="load">{{ error }} · 点击重试</view>
    <view v-if="row">
      <view class="card">
        <view class="row">
          <text class="h2">{{ row.name || row.title || row.talentName || row.period || info.title }}</text>
          <text class="tag" v-if="row.status || row.stage">{{ label(row.stage || row.status) }}</text>
        </view>
        <view v-if="key === 'applications'" class="muted">{{ substate(row) }}</view>
        <view class="divider" />
        <read-only :value="summary" />
        <view class="actions">
          <button v-for="action in actions" :key="action.key" class="secondary" @click="act(action)">{{ action.label }}</button>
        </view>
      </view>
      <view v-for="section in sections" :key="section.key">
        <view class="section">
          <text class="h2">{{ section.title }}</text>
          <button
            v-if="section.key === 'attachments' && key === 'applications' && canUpload"
            class="secondary"
            :loading="uploading"
            @click="upload"
          >
            上传证明
          </button>
        </view>
        <view v-for="(item, index) in section.rows" :key="index" class="card">
          <read-only :value="item" />
          <text
            v-if="item.originalName && ['attachments', 'resumes'].includes(section.key)"
            class="link"
            @click="openAttachment(section.key, item)"
          >
            查看附件 ↗
          </text>
        </view>
        <view v-if="!section.rows.length" class="card empty">暂无记录</view>
      </view>
      <view v-if="key === 'customers'" class="actions">
        <button v-if="canContracts" class="secondary" @click="$go('list', { module: 'contracts', customerId: id })">查看合同</button>
      </view>
    </view>
    <view v-else-if="!error" class="empty">正在加载…</view>
  </app-shell>
</template>
<script>
import { catalog, positiveId, pageRows } from '../../services/catalog'
import { requireModule, request, can, visible, chooseUpload, openFile } from '../../services/runtime'
import { displayRows, availableActions, label, applicationSubstate, saveBusiness } from '../../services/business'
import ReadOnly from '../../components/ReadOnly.vue'
export default {
  components: { ReadOnly },
  data: () => ({ key: '', id: null, info: {}, row: null, error: '', uploading: false }),
  computed: {
    summary() {
      const value = { ...this.row }
      if (this.key === 'contracts') delete value.feeTerms
      return value
    },
    details() {
      return displayRows(this.row)
    },
    actions() {
      return availableActions(this.key, this.row || {})
    },
    canUpload() {
      return can('applications', 'offer') || can('applications', 'entry')
    },
    canContracts() {
      return visible('contracts')
    },
    sections() {
      const r = this.row || {}
      const definitions =
        this.key === 'customers'
          ? [
              ['followups', '跟进记录'],
              ['sealMerchantUsers', '联系人']
            ]
          : this.key === 'contracts'
          ? [
              ['feeTerms', '收费条款'],
              ['versions', '合同版本'],
              ['reminders', '风险提醒']
            ]
          : this.key === 'applications'
          ? [
              ['interviews', '面试记录'],
              ['attachments', '业务附件'],
              ['exceptions', '跟进事项'],
              ['events', '操作记录']
            ]
          : this.key === 'talents'
          ? [
              ['events', '沟通与操作记录'],
              ['resumes', '简历版本']
            ]
          : []
      return definitions.map(([key, title]) => ({ key, title, rows: Array.isArray(r[key]) ? r[key] : [] }))
    }
  },
  onLoad(q) {
    this.key = q.module
    this.info = catalog[this.key] || {}
    this.id = positiveId(q.id)
  },
  onShow() {
    this.load()
  },
  methods: {
    openAttachment(type, item) {
      openFile('/project/recruitment/' + type + '/' + item.id + '/file', item.originalName)
    },
    label,
    substate: applicationSubstate,
    display: displayRows,
    async load() {
      this.error = ''
      this.row = null
      try {
        await requireModule(this.key)
        if (!this.id) throw new Error('记录编号无效，请返回列表重新打开')
        if (this.info.self) {
          const result = pageRows(await request(this.info.list))
          this.row = result.rows.find((r) => String(r.id) === String(this.id))
          if (!this.row) throw new Error('记录不存在或没有访问权限')
          if (this.key === 'attendance') {
            this.row = (await request('/project/hr/self/attendance/' + this.id)).data
          }
          if (this.key === 'payslips') await request('/project/hr/self/payslips/' + this.id + '/read', 'POST')
        } else this.row = (await request(this.info.detail + this.id)).data
      } catch (e) {
        this.error = e.message
      }
    },
    async act(a) {
      if (a.key === 'submit') {
        try {
          await saveBusiness(this.key, 'submit', this.row)
          this.$toast('已提交')
          await this.load()
        } catch (e) {
          this.$toast(e.message)
        }
      } else this.$go('form', { module: this.key, id: this.id, action: a.key })
    },
    async upload() {
      if (this.uploading) return
      this.uploading = true
      try {
        await chooseUpload('/project/recruitment/applications/' + this.id + '/attachments')
        this.$toast('证明已上传')
        await this.load()
      } catch (e) {
        this.$toast(e.message)
      } finally {
        this.uploading = false
      }
    }
  }
}
</script>
