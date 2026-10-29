<!-- 跟踪表单 -->
<template>
  <div>
    <a-steps :current="current" @change="handleStepChange">
      <a-step title="初步接洽" />
      <a-step title="需求确认" />
      <a-step title="方案报价" />
      <a-step title="商务谈判" />
      <a-step title="赢单" />
      <a-step title="输单" />
    </a-steps>
    <s-form style="margin-top: 20px" single ok :formItems="formItems" :formData="formData" :rules="formRules" @submit="handleSubmit" @cancel="handleCancel" />
  </div>
</template>

<script>
import moment from 'moment'
import { SForm } from '/src/components/index.js'
import { getBusiness, addBusinessStage } from '/src/api/eos/business/business.js'

export default {
  components: {
    SForm
  },
  props: {
    businessCode: {
      type: String,
      required: true
    },
    formId: {
      type: Number,
      required: true
    }
  },
  data() {
    return {
      current: 0,
      currentStateMap: {
        初步接洽: 0,
        需求确认: 1,
        方案报价: 2,
        商务谈判: 3,
        赢单: 4,
        输单: 5,
        '': 0
      },
      currentStateMapNum: {
        0: '初步接洽',
        1: '需求确认',
        2: '方案报价',
        3: '商务谈判',
        4: '赢单',
        5: '输单'
      },
      formData: {
        recordTime: moment()
      },
      formRules: {
        recordTime: { required: true, message: '请选择', trigger: 'blur' },
        remark: { required: true, message: '请输入', trigger: 'blur' }
      },
      formItems: [
        [
          {
            type: 'datePicker',
            label: '活动时间',
            prop: 'recordTime',
            formItemProps: {
              props: {
                labelCol: { lg: 3, md: 8, sm: 10, xs: 24 },
                wrapperCol: { lg: 21, md: 16, sm: 14, xs: 24 }
              }
            }
          }
        ],
        [
          {
            type: 'upload',
            label: '阶段附件',
            prop: 'attachment',
            formItemProps: {
              props: {
                labelCol: { lg: 3, md: 8, sm: 10, xs: 24 },
                wrapperCol: { lg: 21, md: 16, sm: 14, xs: 24 }
              }
            }
          }
        ],
        [
          {
            type: 'inputTextArea',
            label: '活动事件',
            prop: 'remark',
            compProps: {
              props: {
                autoSize: { minRows: 10 }
              }
            },
            formItemProps: {
              props: {
                labelCol: { lg: 3, md: 8, sm: 10, xs: 24 },
                wrapperCol: { lg: 21, md: 16, sm: 14, xs: 24 }
              }
            }
          }
        ]
      ]
    }
  },
  mounted() {
    // 查询明细
    getBusiness(this.formId).then(res => {
      const stageList = res.data.sealBusinessStages
      if (!this.isEmpty(stageList)) {
        const currentState = stageList[stageList.length - 1].state
        this.current = this.currentStateMap[currentState]
        this.formData.state = currentState
      } else {
        this.formData.state = '初步接洽'
      }
    })
  },
  methods: {
    handleStepChange(current) {
      this.formData.state = this.currentStateMapNum[current]
      this.current = current
    },
    handleCancel() {
      this.$emit('close')
    },
    handleSubmit() {
      // 新增商机跟踪商机状态
      addBusinessStage({
        ...this.formData,
        businessCode: this.businessCode,
        recordTime: moment(this.formData.recordTime).format('YYYY-MM-DD HH:mm:ss')
      }).then(res => {
        this.$message.success('保存成功')
        this.$emit('saveSuccess')
        this.handleCancel()
      })
    }
  }
}
</script>
