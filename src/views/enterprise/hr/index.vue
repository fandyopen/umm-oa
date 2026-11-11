<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <div class="hr-member-layout">
      <aside class="hr-member-sidebar">
        <div class="hr-member-sidebar__title">人事管理</div>
        <a-menu :selected-keys="deptId ? [] : [memberTitle]" mode="inline" @click="handleMemberMenu">
          <a-menu-item key="全部成员">
            <a-icon type="team" />
            全部成员
          </a-menu-item>
          <a-menu-item key="离职成员">
            <a-icon type="user-delete" />
            离职成员
          </a-menu-item>
        </a-menu>
        <div class="hr-member-sidebar__section">公司</div>
        <a-spin :spinning="loadingDepartments">
          <department-tree :tree-data="departments" :selected-keys="deptId ? [deptId] : []" @select="handleDepartmentSelect" />
        </a-spin>
      </aside>
      <section class="hr-member-content">
        <MemberElement
          :configured-action-submitter="submitConfiguredAction"
          :module-action-guard="checkModuleAction"
          :module-action-visible="showModuleAction"
          :module-meta="moduleContexts.members"
          :key="deptId || memberTitle"
          :title="memberTitle"
          :dept-id="deptId"
          :show-role-info="false"
          read-only-departments
        />
      </section>
    </div>
  </page-header-wrapper>
</template>

<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import MemberElement from '../components/memberElement/index.vue'
import DepartmentTree from '/src/components/DepartmentTree/index.vue'
import { getTreeMenu } from '/src/api/enterprise/index.js'

export default {
  mixins: [withModuleContexts({ members: { component: 'enterprise/hr/index', label: '人事成员' } })],
  name: 'HrMembers',
  components: { MemberElement, DepartmentTree },
  data() {
    return {
      memberTitle: '全部成员',
      deptId: null,
      departments: [],
      loadingDepartments: false
    }
  },
  methods: {
    handleMemberMenu({ key }) {
      this.deptId = null
      this.memberTitle = key
    },
    handleDepartmentSelect(keys, { selected, node }) {
      if (!selected) return
      this.deptId = Number(keys[0])
      this.memberTitle = node.dataRef.label
    },
    async loadDepartments() {
      this.loadingDepartments = true
      try {
        const response = await getTreeMenu()
        this.departments = response.data || []
      } finally {
        this.loadingDepartments = false
      }
    }
  },
  created() {
    this.loadDepartments()
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

.hr-member-sidebar__section {
  padding: 18px 24px 8px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
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
