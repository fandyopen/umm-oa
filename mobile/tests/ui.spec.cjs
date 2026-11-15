const { test, expect } = require('@playwright/test')
const key = 'a'.repeat(64)
test.beforeEach(async ({ page }) => {
  page.mobileErrors = []
  page.on('pageerror', (e) => page.mobileErrors.push(e.message))
})
test.afterEach(async ({ page }) => {
  expect(page.mobileErrors).toEqual([])
})
async function fixture(page) {
  const mutations = []
  const meta = { moduleId: 12, perms: 'crm:customer:list', component: 'eos/crm/customer/index', moduleName: '客户档案' }
  const actions = [{ moduleId: 12, actionCode: 'track', status: '0', permissionCode: 'crm:customer:track' }]
  const session = {
    code: 200,
    user: { userName: 'test', nickName: '移动测试', dept: { deptName: '客户拓展部' } },
    roles: ['member'],
    permissions: ['crm:customer:list', 'crm:customer:track'],
    moduleActions: actions,
    currentCompany: '测试企业'
  }
  const task = {
    mobileKey: key,
    mobileKind: 'todo',
    title: '跟进审批',
    taskId: 'task-1',
    instanceId: 'instance-1',
    businessType: 'workflowAction',
    taskName: '负责人审核',
    form: { title: '跟进审批', customerName: '测试客户', content: '提交时的跟进内容' },
    fields: []
  }
  await page.route('**/api/**', async (route) => {
    const req = route.request(),
      url = new URL(req.url()),
      path = url.pathname.replace(/^\/api/, '')
    let body = { code: 200, data: [] }
    if (req.method() !== 'GET' && path !== '/login') mutations.push({ path, data: req.postDataJSON() })
    if (path === '/captchaImage') body = { code: 200, captchaEnabled: false }
    else if (path === '/login') body = { code: 200, token: 'fixture-token' }
    else if (path === '/auth/session') body = session
    else if (path === '/system/module-context') body = { code: 200, data: { 'eos/crm/customer/index': meta } }
    else if (path === '/mobile/tasks/todo') body = { code: 200, rows: [task], total: 1 }
    else if (path === '/mobile/tasks/todo/' + key) body = { code: 200, data: task }
    else if (path === '/project/merchant/list')
      body = { code: 200, rows: [{ id: 1, name: '测试客户', industry: '软件', ownerUserName: '移动测试' }], total: 1 }
    else if (path === '/project/merchant/1')
      body = { code: 200, data: { id: 1, name: '测试客户', industry: '软件', ownerUserId: 2, ownerUserName: '移动测试', followups: [] } }
    else if (path === '/project/merchant/owner/options') body = { code: 200, data: [{ userId: 2, nickName: '移动测试' }] }
    else if (path === '/workflow/action/execute') body = { code: 200, data: { status: 'pendingApproval', requestId: 1 } }
    else if (path === '/activiti/process/listHistory')
      body = {
        code: 200,
        rows: [{ activityName: '发起申请', assigneeName: '申请人', comment: '提交申请', endTime: '2026-11-13 09:00:00' }],
        total: 1
      }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
  await page.goto('./')
  await page.waitForURL('**/pages/login/index')
  await page.locator('input').nth(0).fill('test')
  await page.locator('input').nth(1).fill('test-password')
  await page
    .locator('uni-button')
    .filter({ hasText: /^登录$/ })
    .click()
  await expect(page.getByText('让每一次沟通，都有下一步。', { exact: true })).toBeVisible()
  return mutations
}
test('phone layout and platform permissions restrict app visibility and deep links', async ({ page }) => {
  await fixture(page)
  await expect(page.getByText('上午好，移动测试').or(page.getByText('下午好，移动测试'))).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.locator('.tab').filter({ hasText: '应用' }).click()
  await expect(page.locator('.appitem').filter({ hasText: '客户档案' })).toBeVisible()
  await expect(page.locator('.appitem').filter({ hasText: '回款登记' })).toHaveCount(0)
  await page.goto('./#/pages/list/index?module=receipt')
  await expect(page.locator('.error')).toContainText('没有该模块或操作的权限')
  await expect(page.locator('.listcard')).toHaveCount(0)
})
test('followup sends the configured workflow action once without an early business write', async ({ page }) => {
  const mutations = await fixture(page)
  await page.goto('./#/pages/detail/index?module=customers&id=1')
  await page.getByText('新增跟进', { exact: true }).click()
  await page.locator('textarea').nth(0).fill('客户希望下周继续沟通')
  await page
    .locator('uni-button')
    .filter({ hasText: /^保存跟进$/ })
    .click()
  await expect(page.getByText('已提交审批', { exact: true })).toBeVisible()
  const submission = mutations.filter((r) => r.path === '/workflow/action/execute')
  expect(submission).toHaveLength(1)
  expect(submission[0].data.actionCode).toBe('track')
  expect(submission[0].data.payload.customerId).toBe(1)
  expect(submission[0].data.payload.content).toBe('客户希望下周继续沟通')
  expect(mutations.some((r) => r.path.includes('/followups'))).toBe(false)
})
test('approval shows the frozen content and submits the task identifiers and opinion', async ({ page }) => {
  const mutations = await fixture(page)
  await page.goto('./#/pages/task/index?kind=todo&key=' + key)
  await expect(page.getByText('提交时的跟进内容', { exact: true })).toBeVisible()
  await expect(page.getByText('发起申请', { exact: true })).toBeVisible()
  await page.locator('textarea').fill('资料完整，同意')
  await page
    .locator('uni-button')
    .filter({ hasText: /^同意$/ })
    .click()
  await page.getByText('确定', { exact: true }).click()
  await expect.poll(() => mutations.filter((r) => r.path === '/activiti/process/complete').length).toBe(1)
  const completion = mutations.find((r) => r.path === '/activiti/process/complete')
  expect(completion.data).toEqual({ taskId: 'task-1', instanceId: 'instance-1', variables: { pass: true, comment: '资料完整，同意' } })
})
