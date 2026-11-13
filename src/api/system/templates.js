import request from '/src/utils/axios/request.js'
import { saveAs } from 'file-saver'

export const getHrTemplateCatalog = () => request({ url: '/system/templates/hr/catalog', method: 'get' })
export const getMemberTemplateSchema = async () => (await request({ url: '/system/templates/members/catalog', method: 'get' })).data
export const previewMemberImport = (moduleId, rows) => request({ url: '/system/user/member-import/preview', method: 'post', params: { moduleId }, data: { rows }, silentError: true })
export const getBusinessTemplateSchema = async type => (await request({ url: '/system/templates/business/' + encodeURIComponent(type) + '/catalog', method: 'get' })).data

export async function fetchFixedTemplate(path) {
  try {
    const blob = await request({ url: '/system/templates/' + path, method: 'get', responseType: 'blob', silentError: true })
    if (!(blob instanceof Blob)) throw new Error('模板下载失败，请重试')
    if (blob.type.includes('json') || blob.type.includes('text')) {
      const text = await blob.text()
      let error
      try {
        error = JSON.parse(text)
      } catch (_) {
        throw new Error('模板下载失败，请重试')
      }
      throw new Error(error.msg || '模板下载失败，请重试')
    }
    const signature = new Uint8Array(await blob.slice(0, 2).arrayBuffer())
    if (signature[0] !== 80 || signature[1] !== 75) throw new Error('模板文件无效，请联系管理员')
    return blob
  } catch (error) {
    const response = error.response?.data
    if (response instanceof Blob && response.type.includes('json')) {
      const data = JSON.parse(await response.text())
      throw new Error(data.msg || '模板下载失败')
    }
    throw error
  }
}

export async function downloadFixedTemplate(path, filename) {
  saveAs(await fetchFixedTemplate(path), filename + '.xlsx')
}
