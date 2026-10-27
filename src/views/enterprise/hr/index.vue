<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <div class="hr-member-layout">
      <aside class="hr-member-sidebar">
        <div class="hr-member-sidebar__title">人事管理</div>
        <a-menu :selected-keys="[memberTitle]" mode="inline" @click="handleMemberMenu">
          <a-menu-item key="全部成员">
            <a-icon type="team" />
            全部成员
          </a-menu-item>
          <a-menu-item key="离职成员">
            <a-icon type="user-delete" />
            离职成员
          </a-menu-item>
        </a-menu>
      </aside>
      <section class="hr-member-content">
        <MemberElement :key="memberTitle" :title="memberTitle" :show-role-info="false" />
      </section>
    </div>
  </page-header-wrapper>
</template>

<script>
import MemberElement from '../components/memberElement/index.vue'

export default {
  name: 'HrMembers',
  components: { MemberElement },
  data() {
    return {
      memberTitle: '全部成员'
    }
  },
  methods: {
    handleMemberMenu({ key }) {
      this.memberTitle = key
    }
  }
}
</script>

<style lang="less" scoped>
.hr-member-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  min-height: calc(100vh - 180px);
  overflow: hidden;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}

.hr-member-sidebar {
  border-right: 1px solid #f0f0f0;
  background: #fafafa;
}

.hr-member-sidebar__title {
  padding: 20px 24px 12px;
  color: rgba(0, 0, 0, 0.85);
  font-weight: 600;
  font-size: 16px;
}

.hr-member-content {
  min-width: 0;
  padding: 20px;
}

@media (max-width: 768px) {
  .hr-member-layout {
    grid-template-columns: 1fr;
  }

  .hr-member-sidebar {
    border-right: 0;
    border-bottom: 1px solid #f0f0f0;
  }
}
</style>
