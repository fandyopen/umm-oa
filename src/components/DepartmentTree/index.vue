<template>
  <div class="department-tree">
    <a-tree v-if="treeData.length" :tree-data="treeData" :selected-keys="selectedKeys" :replace-fields="replaceFields" default-expand-all block-node @select="select">
      <template slot="title" slot-scope="department">
        <span class="department-tree-title" :class="{ 'is-editable': editable }">
          <span class="department-tree-label" :title="department.label">{{ department.label }}</span>
          <span v-if="editable" class="department-tree-actions">
            <a-icon type="edit" title="修改部门名称" @click.stop="$emit('edit', department)" />
            <a-icon type="delete" title="删除部门" @click.stop="$emit('delete', department)" />
          </span>
        </span>
      </template>
    </a-tree>
  </div>
</template>

<script>
export default {
  name: 'DepartmentTree',
  props: {
    treeData: { type: Array, default: () => [] },
    selectedKeys: { type: Array, default: () => [] },
    editable: { type: Boolean, default: false }
  },
  data: () => ({
    replaceFields: { children: 'children', title: 'label', key: 'id' }
  }),
  methods: {
    select(keys, info) {
      this.$emit('select', keys, info)
    }
  }
}
</script>

<style lang="less" scoped>
.department-tree {
  padding: 0 8px 16px;
  overflow-x: hidden;

  ::v-deep .ant-tree-node-content-wrapper {
    min-width: 0;
    height: 32px;
    line-height: 32px;
  }

  ::v-deep .ant-tree-title {
    display: block;
    width: 100%;
  }

  ::v-deep .ant-tree-switcher {
    height: 32px;
    line-height: 32px;
  }
}

.department-tree-title {
  position: relative;
  display: block;
  min-width: 0;
  height: 32px;
  line-height: 32px;

  &.is-editable {
    padding-right: 54px;
  }

  &:hover .department-tree-actions,
  &:focus-within .department-tree-actions {
    opacity: 1;
    visibility: visible;
    pointer-events: auto;
  }
}

.department-tree-label {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.department-tree-actions {
  position: absolute;
  top: 0;
  right: 2px;
  bottom: 0;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition: opacity 0.15s ease;

  i {
    width: 18px;
    line-height: 32px;
    text-align: center;
  }

  i:hover {
    color: #1890ff;
  }
}
</style>
