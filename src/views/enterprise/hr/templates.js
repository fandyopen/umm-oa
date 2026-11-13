import * as XLSX from 'xlsx'
import { saveAs } from 'file-saver'
import { loadEmploymentTypes } from './employmentTypes.js'

import Vue from 'vue'
import { getHrTemplateCatalog, downloadFixedTemplate } from '/src/api/system/templates.js'

export const templateState = Vue.observable({ templates: {}, groups: [], aliases: {}, ready: false, loading: false, error: '' })
let catalogRequest
export function loadTemplateCatalog() {
  if (templateState.ready) return Promise.resolve(templateState)
  if (catalogRequest) return catalogRequest
  templateState.loading = true
  templateState.error = ''
  catalogRequest = getHrTemplateCatalog()
    .then(response => {
      const catalog = response.data
      if (!catalog?.templates || !Array.isArray(catalog.groups)) throw new Error('模板目录格式无效')
      templateState.templates = catalog.templates
      templateState.groups = catalog.groups
      templateState.aliases = catalog.aliases || {}
      templateState.ready = true
      return templateState
    })
    .catch(error => {
      templateState.error = error.message || '模板目录加载失败，请重试'
      throw error
    })
    .finally(() => {
      templateState.loading = false
      catalogRequest = null
    })
  return catalogRequest
}
export function getTemplateSchema(type) {
  return templateState.templates[type] || { name: '模板加载中', note: '', fields: [] }
}
export function suggestColumn(headers, f) {
  return headers.find(h => h === f.key || h === f.label || (templateState.aliases[f.key] || []).includes(h)) || ''
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
    const schema = templateState.templates[type]
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
  await loadTemplateCatalog()
  const employmentTypes = entries.some(entry => getTemplateSchema(entry.type).fields.some(f => f.key === 'employmentType')) ? await loadEmploymentTypes() : []
  const book = buildWorkbook(entries, employmentTypes)
  saveAs(new Blob([XLSX.write(book, { bookType: 'xlsx', type: 'array', compression: true })], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), filename + '.xlsx')
}
export async function exportTemplate(type, records = [], filename) {
  await loadTemplateCatalog()
  const schema = templateState.templates[type]
  if (!schema) throw new Error('未知报表类型：' + type)
  if (!records.length) return downloadFixedTemplate('hr/' + encodeURIComponent(type), filename || schema.name)
  return exportWorkbook([{ type, records }], filename || schema.name)
}
export function downloadTemplatePack() {
  return downloadFixedTemplate('hr/all', '人事考勤与薪资模板大全')
}
