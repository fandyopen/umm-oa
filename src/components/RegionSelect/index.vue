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
    <a-select-opt-group label="常用大区">
      <a-select-option v-for="region in areaRegions" :key="region" :value="region">
        {{ region }}
      </a-select-option>
    </a-select-opt-group>
    <a-select-opt-group label="省级地区">
      <a-select-option v-for="region in provinceRegions" :key="region" :value="region">
        {{ region }}
      </a-select-option>
    </a-select-opt-group>
  </a-select>
</template>

<script>
// 同时保留原系统的大区口径和省级地区，兼容已有客户数据与精细化录入。
const areaRegions = ['华北', '华东', '华南', '华中', '西南', '西北', '东北', '港澳台', '海外']

const provinceRegions = [
  '北京',
  '天津',
  '河北',
  '山西',
  '内蒙古',
  '辽宁',
  '吉林',
  '黑龙江',
  '上海',
  '江苏',
  '浙江',
  '安徽',
  '福建',
  '江西',
  '山东',
  '河南',
  '湖北',
  '湖南',
  '广东',
  '广西',
  '海南',
  '重庆',
  '四川',
  '贵州',
  '云南',
  '西藏',
  '陕西',
  '甘肃',
  '青海',
  '宁夏',
  '新疆',
  '香港',
  '澳门',
  '台湾'
]

export default {
  name: 'RegionSelect',
  props: {
    value: { type: String, default: undefined },
    placeholder: { type: String, default: '请选择地区' },
    allowClear: { type: Boolean, default: true },
    disabled: { type: Boolean, default: false },
    size: { type: String, default: 'default' }
  },
  data() {
    return { areaRegions, provinceRegions }
  },
  methods: {
    handleChange(value) {
      this.$emit('input', value)
      this.$emit('change', value)
    }
  }
}
</script>
