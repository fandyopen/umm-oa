import * as XLSX from 'xlsx'

export function parseMemberImport(bytes, schema) {
  const book = XLSX.read(bytes, { type: 'array', raw: true })
  const sheet = book.Sheets[book.SheetNames[0]]
  if (!sheet) throw new Error('文件没有数据工作表')
  const matrix = XLSX.utils.sheet_to_json(sheet, { header: 1, defval: '', raw: true, blankrows: true })
  const headers = (matrix[0] || []).map(value => String(value).trim())
  const fields = schema.fields.map(field => {
    const names = [field.label, ...(field.aliases || [])]
    const matches = headers.flatMap((header, index) => (names.includes(header) ? [index] : []))
    if (matches.length > 1) throw new Error(field.label + '对应了多列表头，请只保留一列')
    if (!matches.length && field.required) throw new Error('缺少表头：' + field.label)
    return { ...field, column: matches[0] }
  })
  const rows = []
  matrix.slice(1).forEach((values, index) => {
    if (!values.some(value => value !== '' && value !== null && value !== undefined)) return
    const row = { rowNumber: index + 2 }
    fields.forEach(field => {
      if (field.column === undefined) return
      const cell = sheet[XLSX.utils.encode_cell({ r: index + 1, c: field.column })]
      if (cell?.f) throw new Error('第' + row.rowNumber + '行：' + field.label + '请填写实际值，不能使用公式')
      const value = values[field.column]
      if (typeof value === 'number' && !Number.isSafeInteger(value)) throw new Error('第' + row.rowNumber + '行：' + field.label + '请使用文本格式填写')
      row[field.key] = value === null || value === undefined ? '' : String(value).trim()
    })
    rows.push(row)
  })
  if (!rows.length || rows.length > 200) throw new Error('每次导入1至200名成员')
  return rows
}
