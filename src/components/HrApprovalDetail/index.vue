<template>
  <div>
    <a-alert v-if="error" type="error" show-icon :message="error" />
    <a-spin :spinning="loading"
      ><template v-if="detail">
        <a-alert type="info" show-icon message="审批期间草稿已锁定。审批通过不等于实际发薪；未来日期的人事异动到期生效。" style="margin-bottom: 16px" />
        <a-descriptions bordered :column="2"
          ><a-descriptions-item v-for="field in fields" :key="field.key" :label="field.label">{{ display(field.key) }}</a-descriptions-item></a-descriptions
        >
        <a-table v-if="kind === 'payroll'" style="margin-top: 16px" :data-source="rows" row-key="id" :columns="columns" :scroll="{ x: 1600 }" size="small" />
        <a-descriptions v-if="kind === 'change'" bordered :column="1" style="margin-top: 16px"
          ><a-descriptions-item label="原部门">{{ before.deptName || before.deptId || '—' }}</a-descriptions-item
          ><a-descriptions-item v-if="after.deptId" label="目标部门编号">{{ after.deptId }}</a-descriptions-item></a-descriptions
        >
      </template></a-spin
    >
  </div>
</template>
<script>
import { hr } from '/src/api/enterprise/hr.js'
import { statusNames, eventNames, money, parseObject } from '/src/views/enterprise/hr/hrShared.js'
export default {
  name: 'HrApprovalDetail',
  props: { row: { type: Object, required: true } },
  data: () => ({ detail: null, loading: false, error: '' }),
  computed: {
    kind() {
      return this.row.payrollId ? 'payroll' : this.row.salaryId ? 'salary' : 'change'
    },
    fields() {
      return this.kind === 'payroll'
        ? [
            { key: 'period', label: '工资月份' },
            { key: 'status', label: '状态' },
            { key: 'netAmount', label: '本批实发合计' },
            { key: 'revision', label: '当前版本' }
          ]
        : [
            { key: 'nickName', label: '员工' },
            { key: 'effectiveDate', label: '生效日期' },
            { key: 'status', label: '状态' },
            ...(this.kind === 'salary'
              ? [
                  { key: 'payType', label: '计薪方式' },
                  { key: 'baseAmount', label: '工资标准（元）' },
                  { key: 'allowance', label: '固定补贴（元）' }
                ]
              : [{ key: 'eventType', label: '异动类型' }]),
            { key: 'reason', label: '办理原因' }
          ]
    },
    rows() {
      return (this.detail?.rows || []).map(r => ({ ...r, employeeName: r.snapshot.nickName, deptName: r.snapshot.deptName }))
    },
    columns() {
      return [
        { title: '员工', dataIndex: 'employeeName', width: 120 },
        { title: '部门', dataIndex: 'deptName', width: 130 },
        ...[
          ['baseAmount', '基本工资'],
          ['allowance', '补贴'],
          ['overtime', '加班工资'],
          ['bonus', '奖金'],
          ['commission', '提成'],
          ['adjustment', '本期调整'],
          ['deduction', '扣款'],
          ['tax', '个税'],
          ['contribution', '个人承担'],
          ['netAmount', '实发']
        ].map(([dataIndex, title]) => ({ title, dataIndex, width: 105, customRender: money })),
        { title: '核对说明', dataIndex: 'reason', width: 180 }
      ]
    },
    before() {
      return parseObject(this.detail?.beforeJson || '{}')
    },
    after() {
      return parseObject(this.detail?.afterJson || '{}')
    }
  },
  watch: {
    row: {
      immediate: true,
      handler() {
        this.load()
      }
    }
  },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        const path = this.kind === 'payroll' ? '/payroll/' + this.row.payrollId : this.kind === 'salary' ? '/salaries/' + this.row.salaryId : '/changes/' + this.row.eventId
        this.detail = (await hr(path)).data
      } catch (error) {
        this.error = '无法读取审批业务详情，请核对人事页面权限和员工数据范围。'
      } finally {
        this.loading = false
      }
    },
    display(key) {
      const value = this.detail[key]
      if (key === 'status') return statusNames[value] || value
      if (key === 'eventType') return eventNames[value] || value
      if (key === 'payType') return value === 'daily' ? '日薪' : '月薪'
      if (key === 'effectiveDate') return String(value).slice(0, 10)
      if (['netAmount', 'baseAmount', 'allowance'].includes(key)) return money(value)
      return value === null || value === undefined ? '—' : value
    }
  }
}
</script>
