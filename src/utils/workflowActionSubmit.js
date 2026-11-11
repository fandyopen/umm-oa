// Reuses executeWorkflowAction; the server owns routing and approval decisions.
const moduleSubmitters = new WeakMap()

export function submitModuleWorkflowAction(component, execute, { primaryModule, module = primaryModule, actionCode, payload, label = '提交审批' }) {
  return component.checkModuleAction({ module, action: actionCode, label }).then(allowed => {
    if (!allowed) return null
    const moduleId = component.moduleContexts[module]?.moduleId
    if (!moduleId) throw new Error(label + '暂不可用：模块信息未就绪，请重试。')

    let submitters = moduleSubmitters.get(component)
    if (!submitters) {
      submitters = new Map()
      moduleSubmitters.set(component, submitters)
    }
    const key = module + ':' + actionCode
    let submitter = submitters.get(key)
    if (!submitter) {
      submitter = createWorkflowActionSubmitter(execute)
      submitters.set(key, submitter)
    }
    return submitter.submit({ moduleId, actionCode, payload })
  })
}

export function createWorkflowActionSubmitter(execute) {
  const submissions = new Map()
  return {
    submit(options) {
      const snapshot = JSON.parse(JSON.stringify(options))
      const fingerprint = JSON.stringify(snapshot)
      let submission = submissions.get(fingerprint)
      if (submission?.pending) return submission.pending
      if (!submission) {
        submission = { submissionKey: globalThis.crypto?.randomUUID?.() || 'action_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2), pending: null }
        submissions.set(fingerprint, submission)
      }
      submission.pending = Promise.resolve().then(() => execute({ ...snapshot, submissionKey: submission.submissionKey }))
        .then(response => {
          if (!['completed', 'pendingApproval'].includes(response?.data?.status)) throw new Error('业务提交结果异常，请核对后重试')
          submissions.delete(fingerprint)
          return response
        })
        .finally(() => { submission.pending = null })
      return submission.pending
    }
  }
}
