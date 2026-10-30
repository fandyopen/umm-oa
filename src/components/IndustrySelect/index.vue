<template>
  <a-select
    :value="value"
    :allow-clear="allowClear"
    :disabled="disabled"
    :placeholder="placeholder"
    :size="size"
    show-search
    option-filter-prop="children"
    style="width: 100%"
    @change="handleChange"
  >
    <a-select-option v-for="industry in industries" :key="industry" :value="industry">
      {{ industry }}
    </a-select-option>
  </a-select>
</template>

<script>
// 客户行业属于稳定的业务枚举，统一在组件内维护，避免各页面重复定义后产生差异。
const industries = [
  '互联网',
  '软件与信息服务',
  '人工智能',
  '电子商务',
  '通信与电子',
  '制造业',
  '汽车与零部件',
  '建筑工程',
  '房地产',
  '金融',
  '保险',
  '专业服务',
  '企业服务',
  '批发零售',
  '物流运输',
  '能源化工',
  '农业',
  '教育',
  '医疗健康',
  '文化传媒',
  '旅游与酒店',
  '政府及公共事业',
  '其他'
]

export default {
  name: 'IndustrySelect',
  props: {
    value: { type: String, default: undefined },
    placeholder: { type: String, default: '请选择行业' },
    allowClear: { type: Boolean, default: true },
    disabled: { type: Boolean, default: false },
    size: { type: String, default: 'default' }
  },
  data() {
    return { industries }
  },
  methods: {
    handleChange(value) {
      this.$emit('input', value)
      this.$emit('change', value)
    }
  }
}
</script>
