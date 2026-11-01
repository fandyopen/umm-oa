<!-- 流程表单设计 -->
<template>
  <div v-if="spinning" style="text-align: center">
    <a-spin :spinning="spinning"> </a-spin>
  </div>
  <div v-else class="page">
    <header class="page__header">
      <div class="step-tab">
        <div v-for="(item, index) in steps" :key="index" class="step" :class="[activeStep == item.key ? 'active' : '']" @click="changeSteps(item)">
          <span class="step-index">{{ index + 1 }}</span>
          {{ item.label }}
        </div>
      </div>
      <div class="publish-btn">
        <el-button size="small" :loading="saving" :disabled="!selectedAction || !!loadError" @click="saveDraft">保存草稿</el-button>
        <el-button type="primary" size="small" :loading="saving" :disabled="!selectedAction || !!loadError" @click="publish">发布</el-button>
      </div>
    </header>
    <div class="workflow-actions">
      <span class="workflow-actions__label">
        {{ $route.query.menuName || '当前页面' }} · 按钮审批流程
      </span>
      <a-select
        :value="actionCode"
        show-search
        option-filter-prop="children"
        style="width: 360px"
        @change="handleActionChange"
      >
        <a-select-option v-for="item in actionOptions" :key="item.code" :value="item.code">
          {{ item.name }}（{{ item.code }}）
        </a-select-option>
      </a-select>
      <span class="workflow-actions__label">当前动作编码：{{ actionCode }}</span>
      <div v-if="selectedAction" class="workflow-actions__switch">
        <span>启用审批</span>
        <a-switch v-model="approvalEnabled" size="small" />
      </div>
      <a-button size="small" icon="setting" @click="openActionManager">
        修改按钮配置
      </a-button>
    </div>
    <a-alert v-if="loadError" class="workflow-action-warning" type="warning" show-icon :message="loadError" description="请返回模块管理，在对应按钮的修改页开启流程设计后进入。" />
    <a-alert
      v-if="selectedAction && !selectedAction.handlerKey"
      class="workflow-action-warning"
      type="warning"
      show-icon
      message="该按钮尚未接入业务执行器，可先设计并保存草稿；正式发布启用审批前须完成业务接入。"
    />
    <a-alert v-if="mockData && mockData.draft" class="workflow-action-warning" type="info" show-icon message="当前为未发布草稿；保存草稿不会改变已发布流程，发布后新申请使用新配置，进行中的审批不受影响。" />
    <div v-if="mockData" class="page__content">
      <Process ref="processDesign" :conf="mockData.processData" tabName="processDesign" v-show="activeStep === 'processDesign'" />
    </div>
  </div>
</template>

<script>
import Process from '../components/Process/index.vue'
import { GET_MOCK_CONF } from '/src/api/processDesign/index.js'
import { setProcess, detailProcess } from '/src/api/processDesign/index.js'
import { getModuleActions } from '/src/api/system/menu.js'

const beforeUnload = function (e) {
  var confirmationMessage = '离开网站可能会丢失您编辑得内容'
  ;(e || window.event).returnValue = confirmationMessage // Gecko and Trident
  return confirmationMessage // Gecko and WebKit
}
export default {
  name: 'FormDesign',
  components: { Process },
  data() {
    return {
      // 加载字段
      spinning: true,
      saving: false,
      // 表单字段
      formItems: [],
      activeStep: 'processDesign', // 激活的步骤面板
      steps: [{ label: '流程设计', key: 'processDesign' }],
      mockData: null, // 可选择诸如 $route.param，Ajax获取数据等方式自行注入
      actionCode: '',
      loadError: '',
      loadSequence: 0,
      approvalEnabled: true,
      moduleActions: []
    }
  },
  computed: {
    actionOptions() {
      return this.moduleActions.map(item => ({
          code: item.actionCode,
          name: item.actionName,
          handlerKey: item.handlerKey
      }))
    },
    selectedAction() {
      return this.moduleActions.find(item => item.actionCode === this.actionCode)
    }
  },
  methods: {
    changeSteps(item) {
      this.activeStep = item.key
    },
    publish() { return this.saveProcess(false) },
    saveDraft() { return this.saveProcess(true) },
    async saveProcess(draft) {
      if (this.saving || !this.selectedAction || this.loadError || !this.$refs.processDesign) return
      this.saving = true
      const getCmpData = name => this.$refs[name].getData()
      const p3 = getCmpData('processDesign')
      return Promise.all([p3])
        .then(res => {
          const param = {
            processData: res[0].formData,
            actionCode: this.actionCode,
            approvalEnabled: this.approvalEnabled,
            draft
          }
          return this.sendToServer(param)
        })
        .catch(err => {
          err.target && (this.activeStep = err.target)
          err.msg && this.$message.warning(err.msg)
          if (!err.msg) this.$message.error(err.message || '流程保存失败')
        })
        .finally(() => { this.saving = false })
    },
    sendToServer(param) {
      const { menuId, menuName, tenantAppId } = this.$route.query
      const menuIdInt = parseInt(menuId)
      const tenantAppIdInt = parseInt(tenantAppId)
      // 为空新增
      if (this._.isNil(this.mockData._id)) {
        const data = {
          tenantAppId: tenantAppIdInt,
          menuId: menuIdInt,
          menuName,
          processData: param.processData,
          actionCode: param.actionCode,
            approvalEnabled: param.approvalEnabled,
            draft: param.draft
        }
        return setProcess(data).then(res => {
          if (res.code === 200) {
            this.$message.success(param.draft ? '草稿已保存，已发布流程不受影响' : '发布成功')
            if (param.draft) this.mockData.draft = true
            else this.$router.go(-1)
          }
        })
        // 否则修改
      } else {
        const data = {
          tenantAppId: tenantAppIdInt,
          menuId: menuIdInt,
          menuName,
          processData: param.processData,
          actionCode: param.actionCode,
          approvalEnabled: param.approvalEnabled,
          draft: param.draft,
          _id: this.mockData._id
        }
        return setProcess(data).then(res => {
          if (res.code === 200) {
            this.$message.success(param.draft ? '草稿已保存，已发布流程不受影响' : '发布成功')
            if (param.draft) this.mockData.draft = true
            else this.$router.go(-1)
          }
        })
      }
    },
    handleFormItem() {
      const { formItems: formItemsQuery, conditions } = this.$route.query
      this.formItems = []
      // 表单配置项
      if (formItemsQuery) {
        formItemsQuery.map(item => {
          item.map(i => {
            if (i.type === 'select') {
              i.formId = this.uuidv4()
              // 将其设置为el的组件
              i.tag = 'el-select'
              i.vModel = i.prop
              this.formItems.push(i)
            } else if (i.type === 'inputNumber') {
              i.formId = this.uuidv4()
              // 将其设置为el的组件
              i.tag = 'el-input-number'
              i.vModel = i.prop
              this.formItems.push(i)
            } else if (i.type === 'radio') {
              i.formId = this.uuidv4()
              // 将其设置为el的组件
              i.tag = 'el-radio-group'
              i.vModel = i.prop
              this.formItems.push(i)
            }
          })
        })
      }
      if (conditions) {
        try {
          this.formItems = JSON.parse(conditions)
        } catch (error) {
          console.log('err', error)
          this.formItems = []
        }
      }
      this.$store.commit('clearPCondition')
      this.$store.commit('initPConditions', this.formItems)
    },
    handleActionChange(actionCode) {
      this.$router.replace({
        query: {
          ...this.$route.query,
          actionCode
        }
      })
    },
    openActionManager() {
      this.$router.push({
        path: '/system/moduleActions',
        query: {
          moduleId: this.$route.query.menuId,
          actionCode: this.actionCode
        }
      })
    },
    async initFlowData() {
      const sequence = ++this.loadSequence
      this.handleFormItem()
      const { menuId, tenantAppId, actionCode } = this.$route.query
      this.spinning = true
      this.loadError = ''
      this.mockData = null
      this.moduleActions = []
      this.actionCode = actionCode || ''
      try {
        const moduleId = Number(menuId)
        if (!Number.isSafeInteger(moduleId) || moduleId <= 0 || !actionCode || actionCode === 'page') {
          throw new Error('流程设计需要指定按钮，不再使用页面默认审批')
        }
        const actionsRes = await getModuleActions(moduleId, { includeDisabled: true })
        if (sequence !== this.loadSequence) return
        this.moduleActions = (actionsRes.data || []).filter(item => item.flowDesignEnabled && String(item.status) === '0')
        if (!this.selectedAction) throw new Error('该按钮不存在、已停用或尚未开启流程设计')
        const res = await detailProcess({ tenantAppId: Number(tenantAppId) || 1, menuId: moduleId, actionCode })
        if (sequence !== this.loadSequence) return
        const data = res && (res._id || res.draft) ? res : await GET_MOCK_CONF()
        if (sequence !== this.loadSequence) return
        this.mockData = data
        this.approvalEnabled = res && (res._id || res.draft) ? res.approvalEnabled !== false : true
      } catch (error) {
        if (sequence === this.loadSequence) this.loadError = error.message || String(error || '流程加载失败')
      } finally {
        if (sequence === this.loadSequence) this.spinning = false
      }
    }
  },
  beforeRouteEnter(to, from, next) {
    window.addEventListener('beforeunload', beforeUnload)
    next()
  },
  beforeRouteLeave(to, from, next) {
    window.removeEventListener('beforeunload', beforeUnload)
    next()
  },
  watch: {
    '$route.query': {
      handler(newVal, oldVal) {
        this.mockData = null
        this.initFlowData()
      },
      immediate: true,
      deep: true
    }
  }
}
</script>

<style lang="stylus" scoped>
$header-height = 54px;

.page {
  width: 100%;
  height: 100%;
  box-sizing: border-box;

  .page__header {
    width: 100%;
    height: $header-height;
    flex-center();
    justify-content: center;
    box-sizing: border-box;
    color: white;
    background: #3296fa;
    font-size: 14px;
    position: sticky;
    top: 0;
    z-index: 10;

    .page-actions {
      text-align: center;
      line-height: $header-height;
      position: absolute;
      left: 0;

      > div {
        height: 100%;
        padding-left: 20px;
        padding-right: 20px;
        display: inline-block;
      }

      i {
        font-size: 22px;
        vertical-align: middle;
      }
    }

    .step-tab {
      display: flex;
      justify-content: center;
      height: 100%;
      position: relative;

      >.step {
        width: 140px;
        line-height: $header-height;
        padding-left: 30px;
        padding-right: 30px;
        cursor: pointer;
        position: relative;

        &.active >.step-index {
          background: white;
          color: #4483f2;
        }

        >.step-index {
          display: inline-block;
          width: 18px;
          height: 18px;
          border: 1px solid #fff;
          border-radius: 8px;
          line-height: 18px;
          text-align: center;
          box-sizing: border-box;
        }
      }
    }
  }

  .page__content {
    width: 100%;
    height: calc(100% - 108px);
    overflow: hidden;
    box-sizing: border-box;
    background: #f5f5f7;
  }
}

.workflow-actions {
  min-height: 54px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  box-sizing: border-box;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);

  &__label {
    color: #333;
    font-size: 14px;
    font-weight: 600;
  }

  &__switch {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 8px;
    color: #595959;
    font-size: 13px;
  }
}

.workflow-action-warning {
  margin: 12px 18px 0;
}

.publish-btn {
  color: #3296fa;
  padding: 7px 20px;
  border-radius: 4px;
  font-size: 14px;
  position: absolute;
  top: 50%;
  right: 10px;
  transform: translateY(-50%);
}

.github {
  position: fixed;
  bottom: 10px;
  left: 20px;
}
</style>
