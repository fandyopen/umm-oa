<template>
  <view>
    <view v-for="group in groups" :key="group.key" :class="group.depth ? 'card' : ''">
      <view v-if="group.title" class="h3">{{ group.title }}</view>
      <view class="kv" v-for="field in group.rows" :key="field.key">
        <text class="kvlabel">{{ field.title }}</text>
        <text class="kvvalue">{{ field.value }}</text>
      </view>
      <view class="kv" v-for="(field, index) in group.configured" :key="index">
        <text class="kvlabel">{{ field.label }}</text>
        <text class="kvvalue">{{ field.text }}</text>
      </view>
      <business-files :value="group.attachment" />
    </view>
  </view>
</template>
<script>
import { approvalGroups } from '../services/business'
import BusinessFiles from './Files.vue'
export default {
  components: { BusinessFiles },
  props: { value: { type: Object, default: () => ({}) }, schema: { type: Array, default: () => [] } },
  computed: {
    groups() {
      return approvalGroups(this.value, this.schema)
    }
  }
}
</script>
