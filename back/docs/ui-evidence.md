# UI Evidence Map

The screenshots in `../img` were used together with Vue forms, table columns, dictionaries and API paths. The screenshots establish business vocabulary and workflow grouping; the source establishes exact property names.

| Screenshot | Business area | Data inferred |
| --- | --- | --- |
| `首页.png`, `首页-高管.png`, `首页-财务出纳.png` | Dashboards | Project status, contracts, receivables, payables, cost and todo summaries |
| `待办事项.png`, `流程审批.png` | Todo and approval | Task, process instance, approval state, initiator, assignee |
| `流程设计器.png`, `流程表单设计器.png` | Low-code workflow | Process definition JSON, form schema, module binding |
| `项目管理.png`, `项目综合查询.png` | Project | Code, name, department, type, owner unit, status, members, budget |
| `合同拟定.png` | Contract | Contract header, parties, amount, tax, stage/payment lines |
| `采购审批.png` | Purchase | Applicant, project, items, quantity, price, total and approval state |
| `个人报销.png` | Expense | Applicant, project, expense type, detail rows, invoices and total |
| `备用金借还.png` | Loan/repayment | Loan applicant, amount, expected/actual repayment, status |
| `移动端-首页.png`, `移动端-全部应用.png` | Mobile home/apps | App and module catalog fields |
| `移动端-合同查询.png`, `移动端-采购查询.png`, `移动端-项目查询.png` | Mobile queries | Search/filter fields and list-card fields |
| `移动端-我的审批.png`, `移动端-审批进度.png`, `移动端-我的.png` | Mobile approval/profile | Todo state, approval history, user profile fields |
| `移动端1.png`, `移动端2.png` | Mobile screenshots | Navigation and responsive field priority |
| `技术支持.png` | Support/contact | Tenant and contact information |

## Key Status Values

The frontend and dictionaries expose these recurring states:

- Process: `draft`, `running`, `approved`, `rejected`
- Project display status: `在建`, `已完成`, `已暂停`
- Contract efficacy: `有效`
- Todo: `todo`, `done`

## Source of Truth Order

1. Explicit property names in Vue/JSX forms and API client files
2. JSON keys returned by the public API
3. UI screenshot labels and workflow grouping
4. Route/menu names for module boundaries
5. Conservative naming only when the first four sources are absent
