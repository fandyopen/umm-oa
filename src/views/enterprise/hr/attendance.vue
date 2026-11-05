<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb"
    ><a-card :bordered="false"><attendance-workspace /></a-card
  ></page-header-wrapper>
</template>
<script>
import AttendanceWorkspace from './AttendanceWorkspace.vue'
export default { name: 'HrAttendance', components: { AttendanceWorkspace } }
</script>
