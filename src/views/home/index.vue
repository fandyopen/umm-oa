<template>
  <div class="home-dashboard">
    <a-row :gutter="[16, 16]">
      <a-col :xl="17" :lg="17" :md="24" :sm="24" :xs="24">
        <Todo />
      </a-col>
      <a-col :xl="7" :lg="7" :md="24" :sm="24" :xs="24">
        <Notice />
      </a-col>
    </a-row>
    <a-row :gutter="[16, 16]">
      <a-col :xl="12" :lg="12" :md="24" :sm="24" :xs="24">
        <BusinessOpportunity title="商机统计" />
      </a-col>
      <a-col :xl="12" :lg="12" :md="24" :sm="24" :xs="24">
        <AppCommon />
      </a-col>
    </a-row>
  </div>
</template>

<script>
import AppCommon from '/src/views/dashboard/components/app/Common.vue'
import BusinessOpportunity from '/src/views/dashboard/components/finance/BusinessOpportunity.vue'
import Notice from '/src/views/dashboard/components/notice/index.vue'
import Todo from '/src/views/dashboard/components/todo/index.vue'

export default {
  name: 'DashboardIndex',
  components: {
    AppCommon,
    BusinessOpportunity,
    Notice,
    Todo
  }
}
</script>

<style lang="less">
.home-dashboard {
  min-height: calc(100vh - 80px);
  padding: 16px;
  background: #f0f2f5;
}

.home-dashboard .ant-row {
  margin-bottom: 16px;
}

.home-dashboard .ant-row:last-child {
  margin-bottom: 0;
}

.home-dashboard .card-container {
  height: 304px;
  box-sizing: border-box;
  overflow: hidden;
}

.home-dashboard .card-container-height {
  height: 304px !important;
}

.home-dashboard .card-title {
  position: relative;
  box-sizing: border-box;
  margin-bottom: 0;
  padding: 0 18px;
  color: #333;
  font-weight: 400;
  font-size: 18px;
  font-family: "Microsoft YaHei", sans-serif;
}

.home-dashboard .card-title::before {
  position: absolute;
  top: 3px;
  left: 0;
  width: 8px;
  height: 20px;
  border-radius: 4px;
  background: #348ee4;
  content: "";
}
</style>
