import { hr } from '/src/api/enterprise/hr.js'

export async function loadEmploymentTypes() {
  const response = await hr('/options')
  const options = response.data?.employmentTypes
  if (!Array.isArray(options) || !options.length) throw new Error('用工类型选项加载失败，请更新后端并重试')
  return options
}

export function normalizeEmploymentType(value, options) {
  const text = String(value ?? '').trim()
  if (!text) return ''
  const option = options.find(option => option.value === text || option.label === text)
  if (!option) throw new Error('用工类型“' + text + '”不在系统选项中，请核对后填写：' + options.map(option => option.label).join('、'))
  return option.value
}
