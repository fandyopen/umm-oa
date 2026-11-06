<!-- 操作列组件 -->
<template>
  <div :aria-busy="actionPending">
    <a-icon v-if="actionPending" type="loading" class="tw-mr-1.5" />
    <template v-if="actionFirst">
      <a-popconfirm
        v-if="actionFirst.popconfirm"
        :title="actionFirst.confirmTitle"
        :ok-text="actionFirst.confirmOkText"
        :cancel-text="actionFirst.confirmCancelText"
        @confirm="handleCustomAction(actionFirst.action)"
        @cancel="handleCustomAction(actionFirst.cancelAction)"
      >
        <a-icon v-if="actionFirst.confirmIcon" slot="icon" :type="actionFirst.confirmIcon" class="tw-text-[red]" />
        <a @click.stop>
          <template v-if="!isEmpty(actionFirst.icon)">
            <icon-font v-if="actionFirst.icon.indexOf('iconfont-') !== -1" class="s-mr-3" :type="actionFirst.icon" />
            <a-icon v-else class="s-mr-3" :type="actionFirst.icon" />
          </template>
          {{ actionFirst.label }}
        </a>
      </a-popconfirm>
      <a v-else @click.stop="handleCustomAction(actionFirst.action)">
        <template v-if="!isEmpty(actionFirst.icon)">
          <icon-font v-if="actionFirst.icon.indexOf('iconfont-') !== -1" class="s-mr-3" :type="actionFirst.icon" />
          <a-icon v-else class="s-mr-3" :type="actionFirst.icon" />
        </template>
        {{ actionFirst.label }}
      </a>
      <a-divider v-if="actionMore" type="vertical" />
    </template>
    <a-dropdown v-if="actionMore">
      <a-menu slot="overlay">
        <a-menu-item v-for="item in actionMore" :key="item.label">
          <a-popconfirm
            v-if="item.popconfirm"
            :title="item.confirmTitle"
            :ok-text="item.confirmOkText"
            :cancel-text="item.confirmCancelText"
            @confirm="handleCustomAction(item.action)"
            @cancel="handleCustomAction(item.cancelAction)"
          >
            <a @click.stop>
              <template v-if="!isEmpty(item.icon)">
                <icon-font v-if="item.icon.indexOf('iconfont-') !== -1" class="s-mr-3" :type="item.icon" />
                <a-icon v-else class="s-mr-3" :type="item.icon" />
              </template>
              {{ item.label }}
            </a>
          </a-popconfirm>
          <a v-else @click.stop="handleCustomAction(item.action)">
            <template v-if="!isEmpty(item.icon)">
              <icon-font v-if="item.icon.indexOf('iconfont-') !== -1" class="s-mr-3" :type="item.icon" />
              <a-icon v-else class="s-mr-3" :type="item.icon" />
            </template>
            {{ item.label }}</a
          >
        </a-menu-item>
      </a-menu>
      <a @click.stop> <icon-font class="s-mr-3" type="iconfont-gengduo" />更多 </a>
    </a-dropdown>
  </div>
</template>

<script>
import store from '/src/store/index.js'
import { hasModuleAction } from '/src/utils/modulePermission.js'
import { moduleActionName } from '/src/utils/moduleActionConfig.js'

const ACTION_BY_LABEL = {
  新增: 'add',
  修改: 'edit',
  删除: 'remove',
  导出: 'export',
  跟踪: 'track',
  转化项目: 'transfer',
  项目拆分: 'split',
  通知: 'notify',
  确认还款: 'confirm'
}

export default {
  name: 'SActionColumns',
  props: {
    // Explicit null means unresolved; only callers omitting this prop use the current route.
    moduleMeta: { type: Object, default: undefined },
    // Enable only when action callbacks perform their own async module checks.
    allowUnresolved: { type: Boolean, default: false },
    // 表格行数据
    record: {
      type: Object,
      default: () => {}
    },
    actions: {
      type: Array,
      default: () => []
    }
  },
  data: () => ({ actionPending: false }),
  computed: {
    actionModuleMeta() {
      return this.moduleMeta === undefined ? this.$route.meta : this.moduleMeta
    },
    visibleActions() {
      const permissions = store.getters.permissions || []
      return this.actions
        .filter(item => {
          const action = item.actionCode || ACTION_BY_LABEL[item.label]
          return !action || (!this.actionModuleMeta && this.allowUnresolved) || hasModuleAction(permissions, this.actionModuleMeta, action)
        })
        .map(item => {
          const action = item.actionCode || ACTION_BY_LABEL[item.label]
          return action && this.actionModuleMeta ? { ...item, label: moduleActionName(this.actionModuleMeta.moduleId, action, item.label) } : item
        })
    },
    // 操作列第一个按钮，是对象
    actionFirst() {
      return !this.isEmpty(this.visibleActions) ? this.visibleActions[0] : null
    },
    // 操作列更多按钮，是数组
    actionMore() {
      return !this.isEmpty(this.visibleActions) && this.visibleActions.length > 1 ? this.visibleActions.slice(1) : null
    }
  },
  methods: {
    /* 处理自定义action事件 */
    async handleCustomAction(action) {
      if (this.actionPending) return
      this.actionPending = true
      try {
        if (action instanceof Function) return await action(this.record)
        if (typeof action === 'string') this.$emit(action, this.record)
      } finally {
        this.actionPending = false
      }
    }
  }
}
</script>
