<template>
  <a-spin :spinning="loading">
    <div class="business-stage-form">
      <div class="stage-progress">
        <a-steps class="stage-steps" :current="selectedIndex" label-placement="vertical" size="small" @change="handleStepChange">
          <a-step v-for="(stage, index) in stages" :key="stage" :title="stage" :status="stepStatus(index)" :disabled="!canSelectStep(index)" />
        </a-steps>
      </div>
      <a-alert v-if="loadError" type="error" show-icon message="商机信息加载失败">
        <template slot="description"><a @click="loadBusiness">重新加载</a></template>
      </a-alert>
      <a-alert v-else-if="loaded && currentIndex < 0" type="error" show-icon message="无法识别当前商机阶段，请核对商机信息后重试。" />
      <a-alert v-else-if="isFinished" :type="currentStage === '赢单' ? 'success' : 'info'" show-icon :message="'商机已' + currentStage + '，阶段推进已结束。'" />
      <div v-if="loaded && currentIndex >= 0" class="stage-summary">
        <span
          >当前阶段：<strong>{{ currentStage }}</strong></span
        >
        <span v-if="!isFinished"
          >本次推进至：<strong class="stage-target">{{ formData.state }}</strong></span
        >
        <span v-if="viewingHistory"
          >查看节点：<strong>{{ viewingStage }}</strong></span
        >
      </div>
      <section v-if="viewingHistory" class="stage-history">
        <div class="history-heading">
          <strong>{{ viewingStage }} · 活动记录</strong><a-tag>只读</a-tag>
        </div>
        <a-empty v-if="!viewedRecords.length" description="该节点暂无活动记录" />
        <a-descriptions v-for="(record, index) in viewedRecords" :key="index" bordered :column="1" size="small" class="history-record">
          <a-descriptions-item label="活动时间">{{ formatRecordTime(record.recordTime) }}</a-descriptions-item>
          <a-descriptions-item label="阶段附件">
            <template v-if="record.files.length">
              <a v-for="(file, fileIndex) in record.files" :key="fileIndex" class="history-attachment" @click="handlePreview(file)"><a-icon type="paper-clip" /> {{ file.name }}</a>
            </template>
            <span v-else>无附件</span>
          </a-descriptions-item>
          <a-descriptions-item label="活动事件"
            ><div class="history-remark">{{ record.remark || '未填写活动事件' }}</div></a-descriptions-item
          >
        </a-descriptions>
      </section>
      <a-form-model v-if="loaded && currentIndex >= 0 && !isFinished" v-show="!viewingHistory" ref="form" :model="formData" :rules="rules" :label-col="{ span: 4 }" :wrapper-col="{ span: 20 }">
        <a-form-model-item label="活动时间" prop="recordTime">
          <a-date-picker v-model="formData.recordTime" :disabled="saving" style="width: 100%" />
        </a-form-model-item>
        <a-form-model-item label="阶段附件">
          <a-upload :action="uploadUrl" :headers="uploadHeaders" :file-list="fileList" :disabled="saving" @change="handleUploadChange" @preview="handlePreview">
            <a-button :disabled="saving" icon="upload">点击上传</a-button>
          </a-upload>
        </a-form-model-item>
        <a-form-model-item label="活动事件" prop="remark">
          <a-textarea v-model="formData.remark" :disabled="saving" :auto-size="{ minRows: 5, maxRows: 10 }" placeholder="记录本次阶段推进的活动与结果" />
        </a-form-model-item>
      </a-form-model>
      <div class="form-actions">
        <a-button :disabled="saving" @click="handleCancel">{{ isFinished ? '关闭' : '取消' }}</a-button>
        <a-button v-if="viewingHistory && !isFinished" type="primary" @click="returnToNextStage">返回下一步：{{ formData.state }}</a-button>
        <a-button v-else-if="!isFinished" type="primary" :loading="saving" :disabled="!loaded || currentIndex < 0 || uploading" @click="handleSubmit">确定</a-button>
      </div>
    </div>
  </a-spin>
</template>
<script>
import moment from 'moment'
import storage from 'store'
import { ACCESS_TOKEN } from '/src/store/mutation-types.js'
import { getBusiness, addBusinessStage } from '/src/api/eos/business/business.js'
import { previewDoc } from '/src/components/Office/office.js'

const stages = ['初步接洽', '需求确认', '方案报价', '商务谈判', '赢单', '输单']
const isTerminal = stage => stage === '赢单' || stage === '输单'
const newActivity = state => ({ state, recordTime: moment(), remark: '', attachment: '' })

export default {
  name: 'BusinessStageForm',
  props: {
    formId: { type: Number, required: true }
  },
  data() {
    return {
      stages,
      loading: false,
      loaded: false,
      loadError: false,
      saving: false,
      currentStage: '',
      stageRecords: [],
      viewingStage: '',
      formData: newActivity(''),
      fileList: [],
      uploadUrl: import.meta.env.VITE_APP_BASE_API + '/common/upload',
      rules: {
        recordTime: [{ required: true, message: '请选择活动时间', trigger: 'change' }],
        remark: [{ required: true, whitespace: true, message: '请填写活动事件', trigger: 'blur' }]
      }
    }
  },
  computed: {
    currentIndex() {
      return stages.indexOf(this.currentStage)
    },
    selectedIndex() {
      return stages.indexOf(this.viewingStage)
    },
    historyStates() {
      return this.stageRecords.map(record => record.state)
    },
    viewingHistory() {
      return this.loaded && this.currentIndex >= 0 && this.completedStages.has(this.viewingStage)
    },
    viewedRecords() {
      return this.stageRecords.filter(record => record.state === this.viewingStage).map(record => ({ ...record, files: this.parseAttachments(record.attachment) }))
    },
    isFinished() {
      return isTerminal(this.currentStage)
    },
    uploading() {
      return this.fileList.some(file => file.status === 'uploading')
    },
    uploadHeaders() {
      return { Authorization: 'Bearer ' + storage.get(ACCESS_TOKEN) }
    },
    completedStages() {
      // Win and loss are alternative outcomes. Losing never implies that the opportunity was won.
      if (this.currentStage === '输单') {
        const lastActive = Math.max(0, ...this.historyStates.map(stage => stages.indexOf(stage)).filter(index => index >= 0 && index < 4))
        return new Set([...stages.slice(0, lastActive + 1), '输单'])
      }
      return new Set(stages.slice(0, this.currentIndex + 1))
    }
  },
  mounted() {
    this.loadBusiness()
  },
  methods: {
    async loadBusiness() {
      if (this.loading || this.saving) return
      this.loading = true
      this.loaded = false
      this.loadError = false
      try {
        const { data } = await getBusiness(this.formId)
        this.stageRecords = data.sealBusinessStages || []
        this.currentStage = data.stage || data.businessState || this.historyStates[this.historyStates.length - 1] || stages[0]
        this.resetActivity()
        this.loaded = true
      } catch (_) {
        this.loadError = true
      } finally {
        this.loading = false
      }
    },
    nextStage() {
      return this.currentIndex >= 0 && !this.isFinished ? stages[this.currentIndex + 1] : ''
    },
    resetActivity() {
      this.formData = newActivity(this.nextStage())
      this.viewingStage = this.formData.state || this.currentStage
      this.fileList = []
      this.$nextTick(() => {
        if (this.$refs.form) this.$refs.form.clearValidate()
      })
    },
    canSelectStep(index) {
      if (!this.loaded || this.saving || this.currentIndex < 0) return false
      return this.completedStages.has(stages[index]) || this.canAdvanceTo(stages[index])
    },
    canAdvanceTo(stage) {
      if (this.currentIndex < 0 || this.isFinished) return false
      return stage === this.nextStage() || (this.currentStage === '商务谈判' && stage === '输单')
    },
    stepStatus(index) {
      if (this.completedStages.has(stages[index])) return 'finish'
      return stages[index] === this.formData.state ? 'process' : 'wait'
    },
    handleStepChange(index) {
      if (!this.canSelectStep(index)) return
      this.viewingStage = stages[index]
      if (!this.completedStages.has(this.viewingStage)) this.formData.state = this.viewingStage
    },
    returnToNextStage() {
      this.viewingStage = this.formData.state
    },
    parseAttachments(attachment) {
      try {
        const files = typeof attachment === 'string' ? JSON.parse(attachment || '[]') : attachment
        return Array.isArray(files) ? files.filter(file => file && file.name && file.url) : []
      } catch (_) {
        return []
      }
    },
    formatRecordTime(value) {
      return value && moment(value).isValid() ? moment(value).format('YYYY-MM-DD HH:mm:ss') : value || '未记录'
    },
    handleUploadChange({ fileList }) {
      this.fileList = fileList.map(file => (file.status === 'done' && file.response?.code !== 200 ? { ...file, status: 'error' } : file))
      this.formData.attachment = JSON.stringify(this.fileList.filter(file => file.status === 'done' && file.response?.code === 200).map(file => ({ name: file.name, url: file.response.fileName })))
    },
    handlePreview(file) {
      previewDoc(file.name, file.response?.fileName || file.url)
    },
    handleCancel() {
      if (!this.saving) this.$emit('close')
    },
    async handleSubmit() {
      if (!this.loaded || this.loading || this.saving || this.viewingHistory || !this.canAdvanceTo(this.formData.state)) return false
      if (this.uploading) return false
      if (this.fileList.some(file => file.status === 'error')) return this.$message.warning('请移除上传失败的附件或重新上传')
      this.saving = true
      try {
        const valid = await new Promise(resolve => this.$refs.form.validate(resolve))
        if (!valid) return false
        const state = this.formData.state
        const record = {
          ...this.formData,
          businessId: this.formId,
          recordTime: moment(this.formData.recordTime).format('YYYY-MM-DD HH:mm:ss')
        }
        await addBusinessStage(record)
        this.currentStage = state
        this.stageRecords.push(record)
        this.resetActivity()
        this.$message.success('已推进至“' + state + '”')
        this.$emit('saveSuccess', { stage: state, nextStage: this.formData.state })
        return true
      } finally {
        this.saving = false
      }
    }
  }
}
</script>
<style lang="less" scoped>
.stage-progress {
  overflow-x: auto;
  padding: 8px 0 18px;
}
.stage-steps {
  min-width: 590px;
  ::v-deep .ant-steps-item-title {
    white-space: nowrap;
  }
  ::v-deep .ant-steps-item-process > .ant-steps-item-container > .ant-steps-item-content > .ant-steps-item-title {
    color: #1890ff;
  }
  ::v-deep .ant-steps-item-finish .ant-steps-item-icon {
    background: #fff1f0;
    border-color: #f5222d !important;
  }
  // 历史节点可点击查看，悬停时仍保持已完成状态色。
  ::v-deep .ant-steps-item-finish .ant-steps-icon,
  ::v-deep .ant-steps-item-finish .ant-steps-item-title {
    color: #f5222d !important;
  }
  ::v-deep .ant-steps-item-finish .ant-steps-item-tail::after {
    background-color: #ffa39e;
  }
  ::v-deep .ant-steps-item-wait .ant-steps-item-icon {
    background: #fafafa;
    border-color: #d9d9d9;
  }
  ::v-deep .ant-steps-item-wait .ant-steps-icon,
  ::v-deep .ant-steps-item-wait .ant-steps-item-title {
    color: #bfbfbf !important;
  }
}
.stage-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  margin: 0 0 20px;
  padding: 12px 16px;
  background: #f7f9fc;
  border-radius: 4px;
  color: #667085;
}
.stage-target {
  color: #1890ff;
}
.history-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.history-record {
  margin-bottom: 16px;
}
.history-attachment {
  display: block;
  overflow-wrap: anywhere;
}
.history-remark {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #edf0f3;
}
.business-stage-form > .ant-alert {
  margin-bottom: 20px;
}
</style>
