<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-card :bordered="false">
      <div class="hr-heading">
        <div>
          <h2>员工档案</h2>
          <p>统一维护任职资料，薪资按生效日期留存版本。</p>
        </div>
        <a-space
          ><hr-template-downloads /><a-button v-if="showModuleAction('employees', 'edit')" icon="import" @click="openImport('employee')">批量导入档案</a-button
          ><a-button v-if="showModuleAction('employees', 'salary_manage')" @click="openImport('salary')">导入薪资标准</a-button
          ></a-space
        >
      </div>
      <div class="hr-toolbar">
        <a-input-search v-model="query.keyword" placeholder="工号 / 姓名 / 手机" class="tw-w-[260px]" @search="load" /><a-select v-model="query.status" allow-clear placeholder="任职状态" class="tw-w-[140px]" @change="load"
          ><a-select-option value="active">在职</a-select-option><a-select-option value="leaving">待离职</a-select-option><a-select-option value="departed">已离职</a-select-option></a-select
        ><a-button icon="reload" @click="load">刷新</a-button>
        <a-button v-if="showModuleAction('employees', 'export') && (showModuleAction('employees', 'salary_view') || showModuleAction('employees', 'salary_manage'))" icon="export" @click="exportSalaries">导出薪资标准</a-button>
        <a-button v-if="showModuleAction('employees', 'export')" icon="export" @click="exportEmployees">导出档案</a-button>
      </div>
      <a-table :loading="loading" :data-source="rows" row-key="userId" :pagination="{ pageSize: 15 }" :scroll="{ x: 1100 }"
        ><a-table-column title="员工编号" data-index="employeeNo" /><a-table-column title="姓名" data-index="nickName" /><a-table-column title="部门" data-index="deptName" /><a-table-column
          title="手机"
          data-index="phonenumber"
        /><a-table-column title="入职日期" data-index="entryDate"
          ><template slot-scope="value">{{ dateText(value) }}</template></a-table-column
        ><a-table-column title="状态"
          ><template slot-scope="_, row"
            ><a-tag>{{ statusNames[row.employmentStatus] }}</a-tag></template
          ></a-table-column
        ><a-table-column title="操作" fixed="right" :width="110"
          ><template slot-scope="_, row"><a-button type="link" @click="open(row)">查看档案</a-button></template></a-table-column
        ></a-table
      >
    </a-card>
    <a-drawer :visible="!!employee" title="员工档案" width="min(1000px, 94vw)" @close="employee = null" destroy-on-close>
      <template v-if="employee"
        ><div class="employee-name">
          <a-avatar size="large" icon="user" />
          <div>
            <h2>{{ employee.nickName }}</h2>
            <span>{{ employee.employeeNo }} · {{ employee.deptName }}</span>
          </div>
          <a-tag>{{ statusNames[employee.employmentStatus] }}</a-tag>
        </div>
        <a-tabs v-model="tab"
          ><a-tab-pane key="profile" tab="基本与任职资料"
            ><a-alert type="info" show-icon message="历史成员的入职日期需补录，不使用账号创建日期代替。账号启停与任职状态分别管理。" class="tw-mb-4" /><a-form-model layout="vertical" :model="employee"
              ><div class="hr-form-grid">
                <a-form-model-item v-for="field in profileFields" :key="field.key" :label="field.label"
                  ><a-date-picker v-if="field.date" v-model="employee[field.key]" format="YYYY-MM-DD" value-format="YYYY-MM-DD" class="tw-w-full" />
                  <template v-else-if="field.key === 'employmentType'">
                    <a-select
                      v-model="employee.employmentType"
                      allow-clear
                      placeholder="请选择用工类型"
                      :loading="loadingTypes"
                      :disabled="!employmentTypes.length"
                      :get-popup-container="trigger => trigger.parentNode"
                      class="tw-w-full"
                    >
                      <a-select-option v-if="employmentTypeNeedsReview" :value="employee.employmentType" disabled>{{ employee.employmentType }}（历史值，待核对）</a-select-option>
                      <a-select-option v-for="option in employmentTypes" :key="option.value" :value="option.value">{{ option.label }}</a-select-option>
                    </a-select>
                    <div v-if="employmentTypeNeedsReview" class="employment-type-warning">历史用工类型尚未归类，请核对后重新选择；未修改时保留原值。</div>
                    <div v-if="employmentTypesError" class="employment-type-warning">{{ employmentTypesError }} <a @click="loadTypes">重试</a></div>
                  </template>
                  <a-input v-else v-model="employee[field.key]" :max-length="field.max || 100"
                /></a-form-model-item></div></a-form-model
            ><a-button v-if="showModuleAction('employees', 'edit')" type="primary" :loading="saving" @click="save">保存档案</a-button></a-tab-pane
          >
          <a-tab-pane v-if="employee.salaryVisible" key="salary" tab="薪资标准"
            ><a-alert type="info" show-icon message="定薪、调薪新增生效版本，工资按所属期间自动取值。已生效标准不可覆盖。" class="tw-mb-4" /><a-space class="tw-mb-4"
              ><a-button
                v-if="showModuleAction('employees', 'salary_manage')"
                type="primary"
                @click="
                  salaryForm = { effectiveDate: null, payType: 'monthly', allowance: 0 }
                  salaryVisible = true
                "
                >新增定薪 / 调薪</a-button
              ><a-button @click="downloadSalary">下载薪资标准模板</a-button></a-space
            ><a-table :data-source="employee.salaries" row-key="id" :pagination="false" size="small"
              ><a-table-column title="生效日期" data-index="effectiveDate"
                ><template slot-scope="value">{{ dateText(value) }}</template></a-table-column
              ><a-table-column title="方式" data-index="payType"
                ><template slot-scope="value">{{ value === 'daily' ? '日薪' : '月薪' }}</template></a-table-column
              ><a-table-column title="工资标准" data-index="baseAmount" /><a-table-column title="月固定补贴" data-index="allowance" /><a-table-column title="原因" data-index="reason" /><a-table-column
                title="状态"
                data-index="status"
                ><template slot-scope="value">{{ statusNames[value] }}</template></a-table-column
              ><a-table-column title="操作"
                ><template slot-scope="_, row"
                  ><a-button v-if="['draft', 'rejected'].includes(row.status) && showModuleAction('employees', 'salary_manage')" type="link" @click="editSalary(row)">编辑</a-button
                  ><a-button v-if="['draft', 'rejected'].includes(row.status) && showModuleAction('employees', 'salary_submit')" type="link" @click="submitSalary(row)">提交生效审批</a-button></template
                ></a-table-column
              ></a-table
            ></a-tab-pane
          >
          <a-tab-pane v-if="employee.salaryVisible" key="payAccount" tab="发薪账户"
            ><a-form-model layout="vertical"
              ><div class="hr-form-grid">
                <a-form-model-item label="开户人"><a-input v-model="employee.accountName" /></a-form-model-item><a-form-model-item label="开户银行"><a-input v-model="employee.bankName" /></a-form-model-item
                ><a-form-model-item label="银行卡号"><a-input v-model="employee.bankAccount" /></a-form-model-item></div></a-form-model
            ><a-button v-if="showModuleAction('employees', 'salary_manage')" type="primary" :loading="saving" @click="save">保存发薪账户</a-button></a-tab-pane
          >
          <a-tab-pane key="history" tab="任职历史"
            ><a-timeline
              ><a-timeline-item v-for="event in employee.events" :key="event.id"
                ><strong>{{ eventNames[event.eventType] || event.eventType }} · {{ dateText(event.effectiveDate) }}</strong>
                <p>{{ event.reason }} · {{ statusNames[event.status] || event.status }}</p></a-timeline-item
              ></a-timeline
            ><a-empty v-if="!employee.events.length" description="暂无异动记录"
          /></a-tab-pane>
          <a-tab-pane key="files" tab="档案附件"
            ><a-upload v-if="showModuleAction('employees', 'edit')" :before-upload="upload" :show-upload-list="false"><a-button icon="upload">上传私有附件</a-button></a-upload
            ><a-list :data-source="employee.attachments"
              ><a-list-item slot="renderItem" slot-scope="file"
                ><a @click="downloadFile(file.id)">{{ file.fileName }}</a
                ><span>{{ dateText(file.createTime) }}</span></a-list-item
              ></a-list
            ></a-tab-pane
          >
        </a-tabs>
      </template>
    </a-drawer>
    <a-modal v-model="salaryVisible" title="新增薪资生效版本" :confirm-loading="saving" @ok="saveSalary"
      ><a-form-model layout="vertical"
        ><a-form-model-item label="生效日期"><a-date-picker v-model="salaryForm.effectiveDate" format="YYYY-MM-DD" value-format="YYYY-MM-DD" class="tw-w-full" /></a-form-model-item
        ><a-form-model-item label="计薪方式"
          ><a-select v-model="salaryForm.payType"><a-select-option value="monthly">月薪</a-select-option><a-select-option value="daily">日薪</a-select-option></a-select></a-form-model-item
        ><a-form-model-item label="工资标准（元）"><a-input-number v-model="salaryForm.baseAmount" :min="0" :precision="2" class="tw-w-full" /></a-form-model-item
        ><a-form-model-item label="月固定补贴（元）"><a-input-number v-model="salaryForm.allowance" :min="0" :precision="2" class="tw-w-full" /></a-form-model-item
        ><a-form-model-item label="定薪调薪原因"><a-textarea v-model="salaryForm.reason" :max-length="1000" /></a-form-model-item></a-form-model
    ></a-modal>
    <hr-excel-import v-if="importType" :visible="!!importType" :type="importType" :commit-api="commitImport" @close="importType = ''" @done="load" />
  </page-header-wrapper>
</template>
<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { hr } from '/src/api/enterprise/hr.js'
import HrTemplateDownloads from '/src/components/HrTemplateDownloads/index.vue'
import { employeeRecords, salaryStandardRecords } from './reports.js'
import HrExcelImport from '/src/components/HrExcelImport/index.vue'
import { exportTemplate } from './templates.js'
import { statusNames, eventNames, dateText, fileBody } from './hrShared.js'
import { loadEmploymentTypes } from './employmentTypes.js'
import { saveAs } from 'file-saver'
export default {
  name: 'HrEmployees',
  components: { HrExcelImport, HrTemplateDownloads },
  mixins: [withModuleContexts({ employees: { component: 'enterprise/hr/employees', label: '员工档案' } })],
  data: () => ({
    rows: [],
    query: {},
    loading: false,
    saving: false,
    employee: null,
    employmentTypes: [],
    loadingTypes: false,
    employmentTypesError: '',
    tab: 'profile',
    salaryVisible: false,
    salaryForm: {},
    importType: '',
    statusNames,
    eventNames,
    profileFields: [
      { key: 'employeeNo', label: '员工编号', max: 64 },
      { key: 'dingtalkUserId', label: '钉钉人员ID', max: 128 },
      { key: 'entryDate', label: '入职日期', date: true },
      { key: 'regularDate', label: '转正日期', date: true },
      { key: 'employmentType', label: '用工类型' },
      { key: 'emergencyContact', label: '紧急联系人' },
      { key: 'emergencyPhone', label: '紧急联系电话', max: 32 },
      { key: 'contractStart', label: '劳动合同开始', date: true },
      { key: 'contractEnd', label: '劳动合同结束', date: true }
    ]
  }),
  computed: {
    employmentTypeNeedsReview() {
      return !!this.employee?.employmentType && !this.employmentTypes.some(option => option.value === this.employee.employmentType) && !this.loadingTypes && !this.employmentTypesError
    }
  },
  mounted() {
    this.loadTypes()
    this.load()
    if (this.$route.query.userId) this.open({ userId: this.$route.query.userId })
  },
  methods: {
    dateText,
    async loadTypes() {
      this.loadingTypes = true
      this.employmentTypesError = ''
      try {
        this.employmentTypes = await loadEmploymentTypes()
      } catch (error) {
        this.employmentTypesError = error.message || '用工类型加载失败'
      } finally {
        this.loadingTypes = false
      }
    },
    async load() {
      this.loading = true
      try {
        this.rows = (await hr('/employees', 'get', this.query)).data || []
      } finally {
        this.loading = false
      }
    },
    setEmployee(employee) {
      const form = { ...employee }
      // Define optional fields before Vue observes the object so selecting an empty date updates immediately.
      for (const field of this.profileFields) {
        const value = employee[field.key]
        form[field.key] = field.date ? (value ? String(value).slice(0, 10) : null) : value ?? ''
      }
      form.employmentType = employee.employmentType || undefined
      this.employee = form
    },
    async open(row) {
      this.setEmployee((await hr('/employees/' + row.userId)).data)
      this.tab = 'profile'
    },
    async save() {
      const accountOnly = this.tab === 'payAccount'
      if (!(await this.checkModuleAction({ module: 'employees', action: accountOnly ? 'salary_manage' : 'edit', label: accountOnly ? '保存发薪账户' : '保存档案' }))) return
      this.saving = true
      try {
        const payload = accountOnly ? this.employee : { ...this.employee, employmentType: this.employee.employmentType || null }
        await hr('/employees/' + this.employee.userId + (accountOnly ? '/accounts' : ''), 'put', payload)
        this.$message.success(accountOnly ? '发薪账户已保存' : '档案已保存')
        await this.open(this.employee)
        this.load()
      } finally {
        this.saving = false
      }
    },
    editSalary(row) {
      this.salaryForm = { ...row, effectiveDate: row.effectiveDate ? String(row.effectiveDate).slice(0, 10) : null }
      this.salaryVisible = true
    },
    async saveSalary() {
      if (!(await this.checkModuleAction({ module: 'employees', action: 'salary_manage', label: '维护薪资' }))) return
      this.saving = true
      try {
        await hr('/employees/' + this.employee.userId + '/salary', 'post', this.salaryForm)
        this.salaryVisible = false
        this.$message.success('已保存草稿，请提交生效审批')
        this.setEmployee((await hr('/employees/' + this.employee.userId)).data)
      } finally {
        this.saving = false
      }
    },
    async submitSalary(row) {
      const response = await this.submitConfiguredAction('salary_submit', { salaryId: row.id, revision: row.revision, title: '员工定薪 / 调薪审批' }, '提交薪资生效')
      if (!response) return
      this.setEmployee((await hr('/employees/' + this.employee.userId)).data)
      this.$message.success('已按审批配置提交或生效')
    },
    async openImport(type) {
      if (!(await this.checkModuleAction({ module: 'employees', action: type === 'salary' ? 'salary_manage' : 'edit', label: type === 'salary' ? '导入薪资标准' : '批量导入档案' }))) return
      this.importType = type
    },
    commitImport(payload) {
      return hr(this.importType === 'salary' ? '/salaries/import' : '/employees/import', 'post', payload)
    },
    downloadSalary() {
      exportTemplate('salary')
    },
    async exportEmployees() {
      if (!(await this.checkModuleAction({ module: 'employees', action: 'export', label: '导出档案' }))) return
      const response = await hr('/reports/employees', 'get', this.query)
      await exportTemplate('employeeReport', employeeRecords(response.data || []))
    },
    async exportSalaries() {
      if (!(await this.checkModuleAction({ module: 'employees', action: 'export', label: '导出薪资标准' }))) return
      const response = await hr('/reports/salaries', 'get', this.query)
      await exportTemplate('salaryReport', salaryStandardRecords(response.data || []))
    },
    async upload(file) {
      if (file.size > 10 * 1024 * 1024) {
        this.$message.warning('附件不能超过10MB')
        return false
      }
      await hr('/employees/' + this.employee.userId + '/attachments', 'post', await fileBody(file))
      this.setEmployee((await hr('/employees/' + this.employee.userId)).data)
      return false
    },
    async downloadFile(id) {
      const file = (await hr('/files/' + id)).data
      saveAs(new Blob([Uint8Array.from(atob(file.fileContent), c => c.charCodeAt(0))]), file.fileName)
    }
  }
}
</script>
<style scoped src="./hr.css"></style>
<style scoped>
.employment-type-warning {
  margin-top: 4px;
  color: #ad6800;
  font-size: 12px;
  line-height: 1.6;
}
</style>
