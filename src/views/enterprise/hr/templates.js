import * as XLSX from 'xlsx'
import { saveAs } from 'file-saver'
import { loadEmploymentTypes } from './employmentTypes.js'

const field = (key, label, type = 'text', required = true) => ({ key, label, type, required })
const identity = [field('employeeNo', '员工编号'), field('employeeName', '员工姓名', 'text', false), field('dingtalkUserId', '钉钉人员ID', 'text', false)]
const monthNumbers = [
  ['requiredDays', '应出勤天数'],
  ['actualDays', '实际出勤天数（不含请假）'],
  ['personalLeaveHours', '事假小时'],
  ['sickLeaveHours', '病假小时'],
  ['paidLeaveHours', '带薪假小时'],
  ['absentDays', '缺勤天数'],
  ['overtimeHours', '加班小时'],
  ['lateCount', '迟到次数'],
  ['earlyCount', '早退次数'],
  ['missingCount', '缺卡次数']
]
const dayNumbers = [
  ['scheduledHours', '排班工时'],
  ['actualHours', '实际出勤小时'],
  ['personalLeaveHours', '事假小时'],
  ['sickLeaveHours', '病假小时'],
  ['paidLeaveHours', '带薪假小时'],
  ['absentHours', '缺勤小时'],
  ['overtimeHours', '加班小时'],
  ['lateCount', '迟到次数'],
  ['earlyCount', '早退次数'],
  ['missingCount', '缺卡次数']
]
export const templates = {
  employee: {
    name: '员工档案批量导入模板',
    title: '批量导入档案',
    note: '仅更新已有员工，不新增成员。先“导出档案”获取员工编号和档案版本，再填写本模板；版本必填，档案变更后需重新导出。空白字段保留原值，单人修改或清空字段请在“查看档案”中操作。用工类型填写“填写说明”列出的名称或编码；入职日期须为真实日期。',
    fields: [
      ...identity,
      field('revision', '档案版本', 'number'),
      field('entryDate', '入职日期', 'date', false),
      field('regularDate', '转正日期', 'date', false),
      field('employmentType', '用工类型', 'text', false),
      field('emergencyContact', '紧急联系人', 'text', false),
      field('emergencyPhone', '紧急联系电话', 'text', false),
      field('contractStart', '合同开始日期', 'date', false),
      field('contractEnd', '合同结束日期', 'date', false)
    ]
  },
  salary: {
    name: '员工薪资标准模板',
    note: '定薪与调薪新增生效版本，导入后需提交审批；monthly为月薪，daily为日薪。固定补贴按月维护，生成工资时按计薪比例折算。禁止用本期工资调整覆盖长期标准。',
    fields: [
      ...identity,
      field('effectiveDate', '薪资生效日期', 'date'),
      field('payType', '计薪方式（monthly或daily）'),
      field('baseAmount', '工资标准', 'number'),
      field('allowance', '月固定补贴', 'number'),
      field('reason', '定薪调薪原因')
    ]
  },
  monthly: {
    name: '钉钉月度考勤导入模板',
    note: '一人一月一行。实际出勤不含请假：实际出勤×每日工时＋各类请假小时＋缺勤天数×每日工时应与应出勤×每日工时相等。缺失不是零；钉钉未启用的字段可在映射时明确选择按0导入。每日与请假明细仅供追溯，不重复累计到月汇总。',
    fields: [...identity, field('period', '考勤月份（YYYY-MM）', 'text', false), ...monthNumbers.map(([key, label]) => field(key, label, 'number'))]
  },
  daily: {
    name: '钉钉每日考勤导入模板',
    note: '一人一天一行，需补齐全月排班工时。用于考勤日历及月中调薪、入离职分段核算；跨夜班次按钉钉归属考勤日期导入，工时使用已核准结果。',
    fields: [
      ...identity,
      field('workDate', '考勤日期', 'date'),
      field('checkIn', '首次打卡', 'text', false),
      field('checkOut', '末次打卡', 'text', false),
      field('result', '考勤结果', 'text', false),
      ...dayNumbers.map(([key, label]) => field(key, label, 'number'))
    ]
  },
  leave: {
    name: '钉钉请假记录导入模板',
    note: '一人一审批实例一行。请假申请和审批在钉钉完成，本表留存结果，不重复审批、不叠加扣款。跨月请假可分别归属月份留存。',
    fields: [
      ...identity,
      field('leaveId', '钉钉审批实例ID'),
      field('leaveType', '请假类型'),
      field('startDate', '开始日期', 'date'),
      field('endDate', '结束日期', 'date'),
      field('leaveHours', '请假小时', 'number'),
      field('approvalStatus', '钉钉审批状态')
    ]
  },
  payroll: {
    name: '工资表导出回传模板',
    note: '请从具体工资草稿导出。批次、月份、版本、员工编号及基础核算项不能修改。可调整奖金、提成、本期调整、扣款、个税和个人承担项目；正负差额用本期调整。核对说明必填。缺行不会删除原记录，回传前展示差异。',
    fields: [
      field('batchId', '工资批次', 'number'),
      field('period', '工资月份'),
      field('revision', '工资版本', 'number'),
      ...identity,
      ...[
        ['baseAmount', '核算基本工资'],
        ['allowance', '核算固定补贴'],
        ['overtime', '核算加班工资'],
        ['bonus', '奖金'],
        ['commission', '提成'],
        ['adjustment', '本期调整'],
        ['deduction', '其他扣款'],
        ['tax', '个人所得税'],
        ['contribution', '个人承担项目']
      ].map(([key, label]) => field(key, label, 'number')),
      field('grossAmount', '应发合计（系统重算）', 'number', false),
      field('netAmount', '实发合计（系统重算）', 'number', false),
      field('reason', '核对调整说明')
    ]
  },
  supplement: { name: '工资补发扣回模板', note: '补发填正数，扣回填负数；按员工编号匹配，需填写批次说明并审批后登记实际付款或扣回凭证。', fields: [...identity, field('adjustment', '补发扣回金额', 'number')] },
  payment: {
    name: '工资发放回执报表',
    note: '用于对账与留存，实际发放在系统逐笔登记流水及私有凭证，不能仅凭导入表标记成功。',
    fields: [
      field('period', '工资月份'),
      ...identity,
      field('bankName', '银行'),
      field('bankAccount', '银行卡号'),
      field('netAmount', '实发金额', 'number'),
      field('paidAmount', '已支付金额', 'number'),
      field('remainingAmount', '未付金额', 'number'),
      field('referenceNo', '银行流水'),
      field('paymentDate', '实际付款日期'),
      field('status', '付款结果')
    ]
  }
}
const aliases = {
  employeeNo: ['工号', '员工工号', '人员编号'],
  employeeName: ['姓名', '员工'],
  dingtalkUserId: ['用户ID', 'userid', '钉钉UserId'],
  workDate: ['日期', '考勤日期'],
  requiredDays: ['应出勤天数'],
  actualDays: ['出勤天数', '实际出勤天数'],
  checkIn: ['上班打卡时间', '上班1打卡时间'],
  checkOut: ['下班打卡时间', '下班1打卡时间'],
  lateCount: ['迟到次数'],
  earlyCount: ['早退次数'],
  missingCount: ['缺卡次数'],
  personalLeaveHours: ['事假(小时)', '事假（小时）'],
  sickLeaveHours: ['病假(小时)', '病假（小时）'],
  overtimeHours: ['加班时长', '加班小时'],
  leaveId: ['审批编号', '审批实例ID']
}
const reportFields = items => items.map(([key, label, type]) => field(key, label, type || 'text', false))
const amounts = reportFields([
  ['baseAmount', '基本工资', 'money'],
  ['allowance', '固定补贴', 'money'],
  ['overtime', '加班工资', 'money'],
  ['bonus', '奖金', 'money'],
  ['commission', '提成', 'money'],
  ['adjustment', '本期调整', 'money'],
  ['grossAmount', '应发合计', 'money'],
  ['deduction', '其他扣款', 'money'],
  ['tax', '个人所得税', 'money'],
  ['contribution', '个人承担项目', 'money'],
  ['netAmount', '实发金额', 'money'],
  ['paidAmount', '实际已付', 'money'],
  ['remainingAmount', '未付或待扣回', 'money']
])
Object.assign(templates, {
  employeeReport: {
    name: '员工花名册',
    note: '按当前筛选和数据范围导出。任职状态与账号启停分开管理。此报表不包含薪资和银行卡信息；批量更新请使用档案导入模板，并保留本表的员工编号和档案版本。',
    fields: reportFields([
      ['employeeNo', '员工编号'],
      ['revision', '档案版本', 'number'],
      ['employeeName', '姓名'],
      ['deptName', '部门'],
      ['phonenumber', '手机'],
      ['email', '邮箱'],
      ['employmentStatus', '任职状态'],
      ['entryDate', '入职日期', 'date'],
      ['regularDate', '转正日期', 'date'],
      ['exitDate', '离职日期', 'date'],
      ['employmentType', '用工类型'],
      ['contractStart', '劳动合同开始', 'date'],
      ['contractEnd', '劳动合同结束', 'date']
    ])
  },
  salaryReport: {
    name: '员工薪资标准报表',
    note: '按当前员工筛选、组织数据范围和薪资查看权限导出。导出的必填列与薪资导入模板一致，可修改生效日期后直接导入为新版本；已生效标准不可覆盖。',
    fields: [...templates.salary.fields, field('deptName', '部门', 'text', false), field('status', '状态', 'text', false)]
  },
  changeReport: {
    name: '员工状态与异动历史',
    note: '每次异动单独留存，包括草稿、审批中、待生效、生效、退回和取消记录。审批通过不代表未来日期已生效。',
    fields: reportFields([
      ['id', '异动单号', 'number'],
      ['employeeNo', '员工编号'],
      ['nickName', '姓名'],
      ['eventType', '异动类型'],
      ['effectiveDate', '生效日期', 'date'],
      ['status', '处理状态'],
      ['reason', '办理原因'],
      ['operatorName', '办理人'],
      ['createTime', '创建时间']
    ])
  },
  payrollSummary: {
    name: '月度工资汇总报表',
    note: '汇总工资批次的审批、工资条发布和实际付款情况。补发与扣回为独立批次；付款以实际登记凭证为准，不以审批通过或发布工资条代替到账。',
    fields: reportFields([
      ['id', '工资批次', 'number'],
      ['period', '工资月份'],
      ['batchType', '批次类型'],
      ['employeeCount', '人数', 'number'],
      ['status', '审批状态'],
      ['published', '工资条发布'],
      ['netAmount', '实发合计', 'money'],
      ['paidAmount', '实际已付', 'money'],
      ['remainingAmount', '未付或待扣回', 'money'],
      ['reason', '补发扣回说明']
    ]),
    totals: ['employeeCount', 'netAmount', 'paidAmount', 'remainingAmount']
  },
  payslip: {
    name: '员工工资条报表',
    note: '个人工资条仅展示本人已发布记录。实际已付单独列示，查看工资条不代表已到账。工资调整回传请使用具体草稿导出的工资表。',
    fields: [field('period', '工资月份'), ...identity, field('deptName', '部门', 'text', false), ...amounts, field('reason', '核对说明', 'text', false)],
    totals: ['grossAmount', 'deduction', 'tax', 'contribution', 'netAmount', 'paidAmount', 'remainingAmount']
  },
  departmentPayroll: {
    name: '部门工资汇总报表',
    note: '按工资冻结时的部门归集基本工资、补贴、奖金、扣款和实发；不包含未维护的单位承担社保、公积金等费用，不能直接视为完整用工成本。',
    fields: [field('period', '工资月份'), field('deptName', '部门'), field('employeeCount', '人数', 'number'), ...amounts],
    totals: ['employeeCount', ...amounts.map(f => f.key)]
  },
  paymentDetail: {
    name: '工资发放流水明细',
    note: '每次付款、失败及重试分别列示。失败金额不计入已付，负数为实际扣回。凭证仅供有权限的人员在系统私有下载。',
    fields: reportFields([
      ['batchId', '工资批次', 'number'],
      ['period', '工资月份'],
      ['employeeNo', '员工编号'],
      ['employeeName', '姓名'],
      ['deptName', '部门'],
      ['paymentId', '发放记录', 'number'],
      ['amount', '本次金额', 'money'],
      ['paymentDate', '实际付款日期', 'date'],
      ['status', '付款结果'],
      ['referenceNo', '银行流水或失败单号'],
      ['reason', '失败或备注'],
      ['operatorName', '登记人'],
      ['createTime', '登记时间']
    ])
  }
})
export const templateGroups = [
  { label: '人员资料', types: ['employee', 'salary', 'employeeReport', 'salaryReport', 'changeReport'] },
  { label: '钉钉考勤', types: ['monthly', 'daily', 'leave'] },
  { label: '工资与发放', types: ['payroll', 'supplement', 'payment', 'payrollSummary', 'payslip', 'departmentPayroll', 'paymentDetail'] }
]
export function suggestColumn(headers, f) {
  return headers.find(h => h === f.key || h === f.label || (aliases[f.key] || []).includes(h)) || ''
}
export function convertCell(value, f) {
  if (value === undefined || value === null || value === '') return ''
  if (f.type === 'number') {
    const number = Number(String(value).replace(/,/g, '').trim())
    if (!Number.isFinite(number)) throw new Error(f.label + '必须是数字')
    return number
  }
  if (f.type === 'date') {
    if (typeof value === 'number') {
      const date = XLSX.SSF.parse_date_code(value)
      if (!date) throw new Error(f.label + '无效')
      return date.y + '-' + String(date.m).padStart(2, '0') + '-' + String(date.d).padStart(2, '0')
    }
    const match = String(value).match(/([0-9]{4})[-/.年]([0-9]{1,2})[-/.月]([0-9]{1,2})/)
    if (!match) throw new Error(f.label + '应为YYYY-MM-DD')
    return match[1] + '-' + match[2].padStart(2, '0') + '-' + match[3].padStart(2, '0')
  }
  return String(value).trim()
}
export function buildWorkbook(entries, employmentTypes = []) {
  const book = XLSX.utils.book_new()
  const instructions = [
    ['模板 / 报表', '说明', '字段', '要求'],
    ['通用说明', '空模板不含示例人员；前500行的编号、银行卡、日期等字段已设为文本。超过500行请复制已有空白行格式。工号或已绑定钉钉人员ID用于匹配，不能仅凭姓名。缺失数据不能默认当零；工资调整回传必须使用实际批次导出文件。']
  ]
  entries.forEach(({ type, records = [], sheetName }) => {
    const schema = templates[type]
    if (!schema) throw new Error('未知报表类型：' + type)
    const sheet = XLSX.utils.aoa_to_sheet([schema.fields.map(f => f.label), ...records.map(row => schema.fields.map(f => (row[f.key] === null || row[f.key] === undefined ? '' : row[f.key])))])
    if (!records.length) {
      for (let r = 1; r <= 500; r++) {
        schema.fields.forEach((f, c) => {
          if (f.type === 'text' || f.type === 'date') sheet[XLSX.utils.encode_cell({ r, c })] = { t: 's', v: '', z: '@' }
        })
      }
      sheet['!ref'] = XLSX.utils.encode_range({ s: { r: 0, c: 0 }, e: { r: 500, c: schema.fields.length - 1 } })
    }
    sheet['!cols'] = schema.fields.map(f => ({ wch: Math.min(34, Math.max(14, f.label.length * 2)) }))
    sheet['!autofilter'] = { ref: XLSX.utils.encode_range({ s: { r: 0, c: 0 }, e: { r: records.length, c: schema.fields.length - 1 } }) }
    records.forEach((row, r) =>
      schema.fields.forEach((f, c) => {
        const cell = sheet[XLSX.utils.encode_cell({ r: r + 1, c })]
        if (!cell) return
        if (f.type === 'text' || f.type === 'date') {
          cell.t = 's'
          cell.v = String(cell.v)
          cell.z = '@'
        } else cell.z = f.type === 'money' ? '#,##0.00;[Red](#,##0.00)' : '#,##0.######'
      })
    )
    if (schema.totals && records.length) {
      const totalRow = records.length + 1
      XLSX.utils.sheet_add_aoa(sheet, [['合计']], { origin: { r: totalRow, c: 0 } })
      schema.fields.forEach((f, c) => {
        if (schema.totals.includes(f.key)) {
          const col = XLSX.utils.encode_col(c)
          const sum = records.reduce((total, row) => total + Number(row[f.key] || 0), 0)
          sheet[XLSX.utils.encode_cell({ r: totalRow, c })] = { t: 'n', f: 'SUM(' + col + '2:' + col + (records.length + 1) + ')', v: sum, z: f.type === 'money' ? '#,##0.00;[Red](#,##0.00)' : '0' }
        }
      })
    }
    XLSX.utils.book_append_sheet(book, sheet, (sheetName || (entries.length === 1 ? '数据' : schema.name)).slice(0, 31))
    instructions.push([schema.name, schema.note])
    schema.fields.forEach(f => {
      const requirement =
        f.key === 'employmentType' && employmentTypes.length
          ? '用工关系；' + employmentTypes.map(option => option.label + '（' + option.value + '）').join('、')
          : { number: '数字', money: '金额（元），两位小数', date: 'YYYY-MM-DD', text: '文本，编号请保持文本格式' }[f.type]
      instructions.push(['', '', f.label, (f.required ? '必填' : '选填') + '；' + requirement])
    })
  })
  const guide = XLSX.utils.aoa_to_sheet(instructions)
  guide['!cols'] = [{ wch: 28 }, { wch: 100 }, { wch: 28 }, { wch: 45 }]
  XLSX.utils.book_append_sheet(book, guide, '填写说明')
  return book
}
export async function exportWorkbook(entries, filename) {
  const employmentTypes = entries.some(entry => templates[entry.type].fields.some(f => f.key === 'employmentType')) ? await loadEmploymentTypes() : []
  const book = buildWorkbook(entries, employmentTypes)
  saveAs(new Blob([XLSX.write(book, { bookType: 'xlsx', type: 'array', compression: true })], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), filename + '.xlsx')
}
export function exportTemplate(type, records = [], filename) {
  return exportWorkbook([{ type, records }], filename || templates[type].name)
}
export function downloadTemplatePack() {
  return exportWorkbook(
    templateGroups.flatMap(group => group.types.map(type => ({ type }))),
    '人事考勤与薪资模板大全'
  )
}
