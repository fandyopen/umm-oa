<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb"
    ><a-card :bordered="false"
      ><div class="hr-heading">
        <div>
          <h2>人事异动与交接</h2>
          <p>入职、转正、调部门、离职与重新入职按审批配置办理，未来日期到期生效。</p>
        </div>
        <a-space
          ><hr-template-downloads /><a-button v-if="showModuleAction('changes', 'export')" icon="export" @click="exportRows">导出异动报表</a-button
          ><a-button type="primary" v-if="showModuleAction('changes', 'edit')" @click="open">新建异动</a-button><a-button icon="reload" @click="load">刷新</a-button></a-space
        >
      </div>
      <a-table :loading="loading" :data-source="rows" row-key="id" :scroll="{ x: 1000 }"
        ><a-table-column title="员工" data-index="nickName" /><a-table-column title="类型" data-index="eventType"
          ><template slot-scope="v">{{ eventNames[v] || v }}</template></a-table-column
        ><a-table-column title="生效日期" data-index="effectiveDate"
          ><template slot-scope="v">{{ dateText(v) }}</template></a-table-column
        ><a-table-column title="原因" data-index="reason" /><a-table-column title="状态" data-index="status"
          ><template slot-scope="v"
            ><a-tag>{{ statusNames[v] || v }}</a-tag></template
          ></a-table-column
        ><a-table-column title="操作" :width="260"
          ><template slot-scope="_, r"
            ><a-button v-if="['draft', 'rejected'].includes(r.status) && showModuleAction('changes', 'edit')" type="link" @click="edit(r)">编辑</a-button
            ><a-button v-if="['draft', 'rejected'].includes(r.status) && showModuleAction('changes', 'submit')" type="link" @click="submit(r)">提交审批</a-button
            ><a-button v-if="r.status === 'scheduled' && showModuleAction('changes', 'edit')" type="link" @click="cancel(r)">取消待生效</a-button
            ><a-button v-if="r.eventType === 'dismiss' && r.status === 'effective'" type="link" @click="showHandover(r)">办理交接</a-button></template
          ></a-table-column
        ></a-table
      >
      <a-modal v-model="visible" title="办理员工异动" :confirm-loading="busy" @ok="save"
        ><a-form-model layout="vertical"
          ><a-form-model-item label="员工"
            ><a-select v-model="form.userId" show-search option-filter-prop="children" class="tw-w-full"
              ><a-select-option v-for="e in employees" :key="e.userId" :value="e.userId">{{ e.employeeNo }} {{ e.nickName }}</a-select-option></a-select
            ></a-form-model-item
          ><a-form-model-item label="异动类型"
            ><a-select v-model="form.eventType"
              ><a-select-option v-for="t in ['entry', 'regular', 'transfer', 'dismiss', 'rehire']" :key="t">{{ eventNames[t] }}</a-select-option></a-select
            ></a-form-model-item
          ><a-form-model-item v-if="form.eventType === 'transfer'" label="目标部门"
            ><a-select v-model="form.deptId"
              ><a-select-option v-for="dept in options.departments" :key="dept.deptId" :value="dept.deptId">{{ dept.deptName }}</a-select-option></a-select
            ></a-form-model-item
          ><a-form-model-item label="生效日期"><a-date-picker v-model="form.effectiveDate" format="YYYY-MM-DD" value-format="YYYY-MM-DD" class="tw-w-full" /></a-form-model-item
          ><a-form-model-item label="办理原因"><a-textarea v-model="form.reason" :max-length="1000" /></a-form-model-item></a-form-model
        ><a-alert v-if="form.eventType === 'dismiss'" type="warning" message="离职生效时停用账号，保留档案和工资记录，并生成业务交接清单。"
      /></a-modal>
      <a-drawer :visible="!!handoverUser" title="离职业务交接" width="min(1000px,94vw)" @close="handoverUser = null"
        ><a-alert message="业务逐项移交给有效在职成员。历史工资和已完成业务记录保留。" type="info" class="tw-mb-4" /><a-table :data-source="handovers" row-key="id" :pagination="false"
          ><a-table-column title="业务" data-index="title" /><a-table-column title="状态" data-index="status"
            ><template slot-scope="v">{{ statusNames[v] }}</template></a-table-column
          ><a-table-column title="接收人"
            ><template slot-scope="_, r"
              ><a-select v-if="r.status === 'todo'" v-model="targets[r.id]" show-search option-filter-prop="children" class="tw-w-[180px]"
                ><a-select-option v-for="u in options.users.filter(user => user.delFlag === '0' && user.status === '0' && user.userId !== handoverUser)" :key="u.userId" :value="u.userId">{{
                  u.nickName
                }}</a-select-option></a-select
              ><span v-else>{{ r.targetUserId }}</span></template
            ></a-table-column
          ><a-table-column title="操作"
            ><template slot-scope="_, r"><a-button v-if="r.status === 'todo' && showModuleAction('changes', 'handover')" type="link" @click="handover(r)">确认交接</a-button></template></a-table-column
          ></a-table
        ></a-drawer
      >
    </a-card></page-header-wrapper
  >
</template>
<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { executeWorkflowAction } from '/src/api/workflow/action.js'
import { hr } from '/src/api/enterprise/hr.js'
import { statusNames, eventNames, dateText } from './hrShared.js'
import HrTemplateDownloads from '/src/components/HrTemplateDownloads/index.vue'
import { exportTemplate } from './templates.js'
import { changeRecords } from './reports.js'
export default {
  components: { HrTemplateDownloads },
  name: 'HrChanges',
  mixins: [withModuleContexts({ changes: { component: 'enterprise/hr/changes', label: '人事异动' } })],
  data: () => ({ rows: [], employees: [], options: { departments: [], users: [] }, loading: false, busy: false, visible: false, form: {}, handoverUser: null, handovers: [], targets: {}, statusNames, eventNames }),
  mounted() {
    this.load()
    if (this.$route.query.handoverUserId) this.showHandover({ userId: Number(this.$route.query.handoverUserId) })
    hr('/options').then(r => {
      this.options = r.data
    })
  },
  methods: {
    dateText,
    async exportRows() {
      if (!(await this.checkModuleAction({ module: 'changes', action: 'export', label: '导出人事异动' }))) return
      exportTemplate('changeReport', changeRecords((await hr('/reports/changes')).data || []))
    },
    async load() {
      this.loading = true
      try {
        this.rows = (await hr('/changes')).data || []
      } finally {
        this.loading = false
      }
    },
    async open() {
      if (!(await this.checkModuleAction({ module: 'changes', action: 'edit', label: '新建异动' }))) return
      this.employees = this.options.users
      this.form = { eventType: 'transfer', effectiveDate: null }
      this.visible = true
    },
    async edit(row) {
      await this.open()
      let after = {}
      try {
        after = JSON.parse(row.afterJson || '{}')
      } catch (error) {}
      this.form = { ...after, userId: row.userId, eventId: row.id, eventRevision: row.revision, eventType: row.eventType, effectiveDate: row.effectiveDate ? String(row.effectiveDate).slice(0, 10) : null, reason: row.reason }
    },
    async save() {
      this.busy = true
      try {
        if (!this.form.userId) return this.$message.warning('请选择员工')
        const options = (await hr('/options')).data
        const employee = options.users.find(e => e.userId === this.form.userId)
        if (!employee) return this.$message.warning('员工不在可操作范围')
        await hr('/employees/' + this.form.userId + '/changes', 'post', { ...this.form, revision: employee.revision })
        this.visible = false
        await this.load()
        this.$message.success('已保存异动草稿，请提交审批')
      } finally {
        this.busy = false
      }
    },
    async submit(r) {
      if (!(await this.checkModuleAction({ module: 'changes', action: 'submit', label: '提交异动' }))) return
      await executeWorkflowAction({ moduleId: this.moduleContexts.changes.moduleId, actionCode: 'submit', payload: { eventId: r.id, revision: r.revision, title: '人事异动：' + r.nickName } })
      this.$message.success('已按审批配置办理')
      this.load()
    },
    cancel(r) {
      this.$confirm({
        title: '取消这项待生效异动？',
        content: '取消操作会留存历史。',
        onOk: async () => {
          await hr('/changes/' + r.id + '/cancel', 'post', { reason: '人事取消待生效异动' })
          this.load()
        }
      })
    },
    async showHandover(r) {
      this.handoverUser = r.userId
      this.handovers = (await hr('/employees/' + r.userId + '/handover')).data
      this.targets = {}
    },
    async handover(r) {
      if (!this.targets[r.id]) return this.$message.warning('请选择接收人')
      await hr('/handover/' + r.id, 'post', { targetUserId: this.targets[r.id] })
      this.handovers = (await hr('/employees/' + this.handoverUser + '/handover')).data
      this.$message.success('交接完成')
    }
  }
}
</script>
<style scoped src="./hr.css"></style>
