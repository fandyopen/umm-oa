// Reuses executeWorkflowAction; the server owns routing and approval decisions.
export function createWorkflowActionSubmitter(execute) {
  let pending = null
  let lastFingerprint = ''
  let submissionKey = ''
  return {
    submit(options) {
      if (pending) return pending
      const snapshot = JSON.parse(JSON.stringify(options))
      const fingerprint = JSON.stringify(snapshot)
      if (fingerprint !== lastFingerprint || !submissionKey) {
        lastFingerprint = fingerprint
        submissionKey = globalThis.crypto?.randomUUID?.() || 'action_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2)
      }
      pending = Promise.resolve().then(() => execute({ ...snapshot, submissionKey }))
        .then(response => {
          if (!['completed', 'pendingApproval'].includes(response?.data?.status)) throw new Error('业务提交结果异常，请核对后重试')
          submissionKey = ''
          return response
        })
        .finally(() => { pending = null })
      return pending
    }
  }
}
