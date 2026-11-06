<!-- 表格列搜索 -->
<template>
  <div class="tw-p-2">
    <a-input
      ref="searchInput"
      :placeholder="`搜索${column.title}`"
      :value="selectedKeys[0]"
      class="tw-w-[188px] tw-mb-2 tw-block"
      @change="e => setSelectedKeys(e.target.value ? [e.target.value] : [])"
      @pressEnter="() => handleSearch(selectedKeys, confirm, column.dataIndex)"
    />
    <a-button type="primary" ghost size="small" class="tw-w-[90px] tw-mr-2" @click="() => handleSearch(selectedKeys, confirm, column.dataIndex)"> 搜索 </a-button>
    <a-button size="small" class="tw-w-[90px]" @click="() => handleReset(clearFilters)"> 重置 </a-button>
  </div>
</template>

<script>
export default {
  name: 'STableColumnSearch',
  props: {
    setSelectedKeys: {
      type: Function,
      default: () => {}
    },
    selectedKeys: {
      type: Array,
      default: () => []
    },
    confirm: {
      type: Function,
      default: () => {}
    },
    clearFilters: {
      type: Function,
      default: () => {}
    },
    column: {
      type: Object,
      default: () => {}
    }
  },
  methods: {
    /* 列搜索 */
    handleSearch(selectedKeys, confirm, dataIndex) {
      this.$emit('onSearch', dataIndex, selectedKeys[0])
      confirm()
    },
    /* 列重置 */
    handleReset(clearFilters) {
      clearFilters()
    }
  }
}
</script>
