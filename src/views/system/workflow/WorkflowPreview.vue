<template>
  <div v-if="nodes.length" class="workflow-preview">
    <template v-for="(node, index) in nodes">
      <div :key="'node-' + index" class="preview-node" :class="'preview-node--' + node.type">
        <a-icon :type="icons[node.type] || 'deployment-unit'" class="preview-node__icon" />
        <div class="preview-node__content">
          <strong>{{ node.name || types[node.type] || '流程节点' }}</strong>
          <template v-if="node.type === 'approval'">
            <span>{{ node.approverLabel || node.approver || '未设置审批人' }}</span>
            <span class="preview-node__note">审批方式：{{ node.approvalMode === 'all' ? '会签' : '或签' }}</span>
          </template>
          <span v-else-if="node.type === 'condition'">{{ node.condition || '未设置条件表达式' }}</span>
          <span v-else class="preview-node__note">{{ types[node.type] || node.type }}</span>
        </div>
      </div>
      <a-icon v-if="index < nodes.length - 1" :key="'arrow-' + index" type="arrow-down" class="preview-arrow" />
    </template>
  </div>
  <a-empty v-else description="尚未配置流程节点" :image="simpleImage" />
</template>

<script>
import { Empty } from 'ant-design-vue'

export default {
  name: 'WorkflowPreview',
  props: { nodes: { type: Array, default: () => [] } },
  data: () => ({
    simpleImage: Empty.PRESENTED_IMAGE_SIMPLE,
    types: { start: '发起人', approval: '审批节点', condition: '条件节点', end: '流程结束' },
    icons: { start: 'user', approval: 'audit', condition: 'branches', end: 'check-circle' }
  })
}
</script>

<style lang="less" scoped>
.workflow-preview { display: flex; flex-direction: column; align-items: center; padding: 24px 12px; background: #f7f9fc; border-radius: 8px; }
.preview-node { display: flex; gap: 14px; width: 100%; max-width: 420px; padding: 16px 20px; background: #fff; border: 1px solid #e6ebf2; border-radius: 8px; }
.preview-node__icon { margin-top: 4px; font-size: 20px; color: #3296fa; }
.preview-node__content { display: flex; flex-direction: column; gap: 6px; min-width: 0; overflow-wrap: anywhere; }
.preview-node__content strong { color: #263445; font-size: 14px; }
.preview-node__note { font-size: 12px; color: #8a94a2; }
.preview-node--start, .preview-node--end { max-width: 280px; }
.preview-node--end .preview-node__icon { color: #52a77c; }
.preview-node--condition .preview-node__icon { color: #d99a28; }
.preview-arrow { padding: 12px; color: #aab6c5; }
</style>
