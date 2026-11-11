<template>
  <div>
    <a-card
      class="card-container dashboard-todo"
      :bordered="false"
      :headStyle="{ border: 'none' }"
      :bodyStyle="{ padding: '8px 16px 12px', minWidth: 0 }"
      :tab-list="tabListNoTitle"
      :active-tab-key="noTitleKey"
      @tabChange="key => onTabChange(key, 'noTitleKey')"
    >
      <h1 slot="title" class="my-todo-title">我的待办</h1>
      <div v-if="noTitleKey === 'todo'">
        <todo ref="todo" :search="false" :reload="false" simple />
      </div>
      <div v-else-if="noTitleKey === 'done'">
        <done ref="done" :search="false" :reload="false" simple />
      </div>
      <div v-else-if="noTitleKey === 'myProcess'">
        <process-started simple ref="processStart" />
      </div>
      <div v-else-if="noTitleKey === 'myCopy'">
        <copy simple ref="copy" />
      </div>
      <div slot="tabBarExtraContent" class="todo-toolbar">
        <a-button type="link" size="small" title="刷新待办" aria-label="刷新待办" @click="handleTabTodoRefresh"><a-icon type="reload" /></a-button>
        <a @click="handleTabTodoMore">更多</a>
      </div>
    </a-card>
  </div>
</template>

<script>
import Todo from '/src/views/eos/todoItem/todo/todo.vue'
import Done from '/src/views/eos/todoItem/done/done.vue'
import ProcessStarted from '/src/views/eos/todoItem/todo/processStarted.vue'
import Copy from '/src/views/eos/todoItem/copy/copy.vue'

export default {
  components: {
    Todo,
    Done,
    ProcessStarted,
    Copy
  },
  data() {
    return {
      tabListNoTitle: [
        {
          key: 'todo',
          tab: '待办'
        },
        {
          key: 'done',
          tab: '已办'
        },
        {
          key: 'myProcess',
          tab: '我的发起'
        },
        {
          key: 'myCopy',
          tab: '抄送我的'
        }
      ],
      noTitleKey: 'todo'
    }
  },
  methods: {
    onTabChange(key, type) {
      this[type] = key
    },
    handleTabTodoRefresh() {
      if (this.noTitleKey === 'todo') {
        this.$refs.todo.refresh()
      }
      if (this.noTitleKey === 'done') {
        this.$refs.done.$refs.table.refresh(true)
      }
      if (this.noTitleKey === 'myProcess') {
        this.$refs.processStart.$refs.table.refresh(true)
      }
      if (this.noTitleKey === 'myCopy') {
        this.$refs.copy.$refs.table.refresh(true)
      }
    },
    handleTabTodoMore() {
      if (this.noTitleKey === 'todo') {
        this.$router.push('/todoItem/todo')
      }
      if (this.noTitleKey === 'done') {
        this.$router.push('/todoItem/done')
      }
      if (this.noTitleKey === 'myProcess') {
        this.$router.push('/todoItem/my-process-start')
      }
      if (this.noTitleKey === 'myCopy') {
        this.$router.push('/todoItem/copy')
      }
    }
  }
}
</script>

<style lang="less">
.dashboard-todo {
  min-width: 0;
  min-height: 304px;
  height: auto !important;

  .ant-card-head {
    padding: 0 20px;
  }

  .ant-card-head-title {
    padding: 16px 0 8px;
  }

  .my-todo-title {
    position: relative;
    padding-left: 14px;
    margin: 0;
    font-size: 16px;
    line-height: 24px;
    font-weight: 500;
    color: #1d2129;

    &:before {
      content: '';
      position: absolute;
      left: 0;
      top: 4px;
      width: 4px;
      height: 16px;
      background: #348ee4;
      border-radius: 2px;
    }
  }

  .ant-tabs-bar {
    border: none;
    margin: 0;
  }

  .ant-card-head .ant-tabs {
    margin-bottom: 0;
  }

  .ant-tabs-nav-container {
    font-size: 13px;
    color: #4e5969;
  }

  .ant-tabs.ant-tabs-large .ant-tabs-tab {
    margin-right: 24px;
    padding: 8px 0;
    font-size: 13px;
    line-height: 20px;
  }

  .ant-tabs.ant-tabs-large .ant-tabs-extra-content {
    line-height: 36px;
  }

  .todo-toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    height: 36px;
    font-size: 12px;

    .ant-btn {
      padding: 0 4px;
      color: #86909c;
    }
  }

  .s-table .ant-table {
    font-size: 12px;

    .ant-table-thead > tr > th,
    .ant-table-tbody > tr > td {
      padding: 9px 8px !important;
      vertical-align: middle;
      line-height: 20px;
      font-size: 12px;
      border-bottom: 1px solid #f2f3f5;
    }

    .ant-table-thead > tr > th {
      background: #f7f8fa !important;
      color: #86909c;
      font-weight: 400;
      white-space: nowrap;
    }

    .ant-table-tbody > tr > td {
      color: #4e5969;
    }

    .info-unread {
      color: #1d2129;
      font-weight: 500;
    }

    .todo-subject {
      display: -webkit-box;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: 2;
      white-space: normal;
      overflow-wrap: anywhere;
    }

    .ant-table-body {
      overflow-x: auto !important;
    }

    .ant-pagination {
      display: none;
    }
  }

  @media (max-width: 768px) {
    .ant-card-head {
      padding: 0 12px;
    }

    .ant-tabs.ant-tabs-large .ant-tabs-tab {
      margin-right: 16px;
    }
  }
}
</style>
