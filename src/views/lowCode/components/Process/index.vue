<template>
  <div class="process-designer">
    <div class="process-toolbar">
      <button type="button" @click="addNode('approval')">+ 审批节点</button>
      <button type="button" @click="addNode('condition')">+ 条件分支</button>
      <span class="toolbar-divider"></span>
      <button type="button" :disabled="!canMove(-1)" @click="moveNode(-1)">上移</button>
      <button type="button" :disabled="!canMove(1)" @click="moveNode(1)">下移</button>
      <button type="button" class="danger" :disabled="!canDelete" @click="removeNode">删除节点</button>
    </div>

    <div class="process-body">
      <div class="process-canvas">
        <div class="canvas-title">审批流程</div>
        <div class="flow-list">
          <template v-for="(node, index) in nodes">
            <div
              :key="node.id"
              class="flow-node"
              :class="[node.type, { selected: selectedId === node.id }]"
              @click="selectedId = node.id"
            >
              <div class="node-icon">{{ nodeIcon(node.type) }}</div>
              <div class="node-content">
                <strong>{{ node.name }}</strong>
                <span>{{ nodeTypeName(node.type) }}</span>
              </div>
            </div>
            <div v-if="index < nodes.length - 1" :key="`${node.id}-line`" class="flow-line">
              <span></span>
            </div>
          </template>
        </div>
      </div>

      <aside class="property-panel">
        <div class="panel-title">节点属性</div>
        <template v-if="selectedNode">
          <label>节点名称</label>
          <input v-model.trim="selectedNode.name" :disabled="selectedNode.type === 'start' || selectedNode.type === 'end'" />

          <label>节点类型</label>
          <input :value="nodeTypeName(selectedNode.type)" disabled />

          <template v-if="selectedNode.type === 'approval'">
            <label>审批人/角色</label>
            <input v-model.trim="selectedNode.approver" placeholder="例如：部门负责人" />
            <label>审批方式</label>
            <select v-model="selectedNode.approvalMode">
              <option value="any">或签</option>
              <option value="all">会签</option>
            </select>
          </template>

          <template v-if="selectedNode.type === 'condition'">
            <label>条件表达式</label>
            <textarea v-model="selectedNode.condition" rows="4" placeholder="例如：amount > 10000"></textarea>
          </template>
        </template>
        <div v-else class="empty-property">选择左侧节点后配置属性</div>
      </aside>
    </div>
  </div>
</template>

<script>
let sequence = 1

export default {
  name: 'ProcessDesigner',
  props: {
    tabName: {
      type: String,
      default: 'processDesign'
    },
    conf: {
      type: Object,
      default: () => ({})
    }
  },
  data() {
    return {
      selectedId: 'start',
      nodes: this.normalize(this.conf)
    }
  },
  computed: {
    selectedNode() {
      return this.nodes.find(node => node.id === this.selectedId) || null
    },
    canDelete() {
      return this.selectedNode && !['start', 'end'].includes(this.selectedNode.type)
    }
  },
  methods: {
    normalize(conf) {
      if (Array.isArray(conf?.nodes) && conf.nodes.length >= 2) {
        return conf.nodes.map(node => ({ ...node }))
      }
      return [
        { id: 'start', type: 'start', name: '发起人' },
        { id: 'approval_1', type: 'approval', name: '部门负责人', approver: '部门负责人', approvalMode: 'any' },
        { id: 'end', type: 'end', name: '流程结束' }
      ]
    },
    addNode(type) {
      const node = {
        id: `${type}_${Date.now()}_${sequence++}`,
        type,
        name: type === 'approval' ? '新审批节点' : '新条件分支',
        approver: type === 'approval' ? '' : undefined,
        approvalMode: type === 'approval' ? 'any' : undefined,
        condition: type === 'condition' ? '' : undefined
      }
      const endIndex = this.nodes.findIndex(item => item.type === 'end')
      this.nodes.splice(endIndex < 0 ? this.nodes.length : endIndex, 0, node)
      this.selectedId = node.id
    },
    removeNode() {
      if (!this.canDelete) return
      const index = this.nodes.findIndex(node => node.id === this.selectedId)
      this.nodes.splice(index, 1)
      this.selectedId = this.nodes[Math.max(0, index - 1)]?.id || 'start'
    },
    canMove(direction) {
      if (!this.selectedNode || ['start', 'end'].includes(this.selectedNode.type)) return false
      const index = this.nodes.findIndex(node => node.id === this.selectedId)
      const target = index + direction
      return target > 0 && target < this.nodes.length - 1
    },
    moveNode(direction) {
      if (!this.canMove(direction)) return
      const index = this.nodes.findIndex(node => node.id === this.selectedId)
      const [node] = this.nodes.splice(index, 1)
      this.nodes.splice(index + direction, 0, node)
    },
    nodeIcon(type) {
      return { start: '发', approval: '审', condition: '判', end: '结' }[type] || '流'
    },
    nodeTypeName(type) {
      return { start: '发起节点', approval: '审批节点', condition: '条件分支', end: '结束节点' }[type] || type
    },
    getData() {
      const nodes = this.nodes.map(node => ({ ...node }))
      const edges = nodes.slice(0, -1).map((node, index) => ({
        source: node.id,
        target: nodes[index + 1].id
      }))
      return Promise.resolve({ formData: { nodes, edges }, target: this.tabName })
    }
  }
}
</script>

<style lang="less" scoped>
.process-designer {
  height: 100%;
  background: #f4f6f8;
  color: #26323d;
}

.process-toolbar {
  height: 52px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 18px;
  border-bottom: 1px solid #dde3e8;
  background: #fff;
}

.process-toolbar button {
  height: 32px;
  padding: 0 14px;
  border: 1px solid #c8d2dc;
  border-radius: 4px;
  background: #fff;
  color: #2b3b4a;
  cursor: pointer;
}

.process-toolbar button:hover:not(:disabled) {
  border-color: #348ee4;
  color: #348ee4;
}

.process-toolbar button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.process-toolbar .danger {
  color: #d4380d;
}

.toolbar-divider {
  width: 1px;
  height: 24px;
  margin: 0 6px;
  background: #e2e7ec;
}

.process-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  height: calc(100% - 52px);
}

.process-canvas {
  overflow: auto;
  padding: 28px;
}

.canvas-title {
  margin-bottom: 24px;
  font-size: 18px;
  font-weight: 600;
}

.flow-list {
  width: 340px;
  margin: 0 auto;
}

.flow-node {
  display: flex;
  align-items: center;
  min-height: 64px;
  padding: 10px 14px;
  border: 1px solid #cfd8e1;
  border-left: 4px solid #348ee4;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(31, 45, 61, 0.06);
  cursor: pointer;
}

.flow-node.selected {
  border-color: #348ee4;
  box-shadow: 0 0 0 3px rgba(52, 142, 228, 0.12);
}

.flow-node.start,
.flow-node.end {
  border-left-color: #52c41a;
}

.flow-node.condition {
  border-left-color: #faad14;
}

.node-icon {
  width: 34px;
  height: 34px;
  margin-right: 12px;
  border-radius: 50%;
  background: #edf5ff;
  color: #348ee4;
  font-weight: 700;
  line-height: 34px;
  text-align: center;
}

.node-content {
  display: flex;
  flex-direction: column;
}

.node-content span {
  margin-top: 4px;
  color: #8795a3;
  font-size: 12px;
}

.flow-line {
  display: flex;
  justify-content: center;
  height: 32px;
}

.flow-line span {
  width: 2px;
  height: 100%;
  background: #bfcbd7;
}

.property-panel {
  padding: 20px;
  border-left: 1px solid #dde3e8;
  background: #fff;
}

.property-panel label {
  display: block;
  margin: 18px 0 8px;
  color: #536270;
  font-size: 13px;
}

.property-panel input,
.property-panel select,
.property-panel textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 10px;
  border: 1px solid #cfd8e1;
  border-radius: 4px;
  outline: none;
}

.property-panel input:focus,
.property-panel select:focus,
.property-panel textarea:focus {
  border-color: #348ee4;
}

.panel-title {
  padding-bottom: 14px;
  border-bottom: 1px solid #edf0f3;
  font-size: 16px;
  font-weight: 600;
}

.empty-property {
  margin-top: 48px;
  color: #8b98a5;
  text-align: center;
}
</style>
