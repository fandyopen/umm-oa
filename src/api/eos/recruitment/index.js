import request from '/src/utils/axios/request.js'

const base = '/project/recruitment'
export const recruitmentRequest = (path, method = 'get', data) => request({ url: base + path, method, ...(method === 'get' ? { params: data } : { data }) })
export const recordPath = (kind, id) => {
  if (!['jobs', 'talents', 'applications'].includes(kind) || !Number.isSafeInteger(Number(id)) || Number(id) < 1) throw new Error('业务编号无效，请刷新列表')
  return '/' + kind + '/' + Number(id)
}
export const uploadRecruitmentFile = (path, file) => {
  const data = new FormData()
  data.append('file', file)
  return recruitmentRequest(path, 'post', data)
}
export const getRecruitmentFile = async (path) => {
  const blob = await request({ url: base + path, method: 'get', responseType: 'blob' })
  if (blob.type && blob.type.includes('application/json')) {
    const error = JSON.parse(await blob.text())
    throw new Error(error.msg || '无法读取文件')
  }
  return blob
}
