# Implementation Status

## 2026-10-26 Organization and System Settings Parity

- Added account-scoped reference waiting tasks. `13686621113` now receives the
  same two pending approvals as the reference site: payment by 昌远把 and
  invoice application by 奚哭芬.
- The `sys_admin_identity` list is now the source of truth for organization
  administrator privileges. Members receive the `admin` role and the full
  236-permission set without changing their business roles.
- Synchronized `isAdmin` and `isOwner` flags for all reference demo users.
- Checked all 34 local accounts; all three local users present in the reference
  system-administrator list can open team management and role permissions.
- Department rename now performs a partial update, preserving parent department,
  order, leader, phone, email and status.
- Department creation calculates ancestors and the next child order from the
  selected parent instead of using fixed demo values.
- Added independent department principal and leader relationship tables and
  working assign/query APIs.
- Department drag sorting now updates parent, ancestors and order through
  `/system/dept/sort`.
- Imported the reference system settings: 34 dictionary types, 130 dictionary
  values and 6 parameter records.
- Dictionary type/data and parameter list APIs now support filtering and
  pagination.
- Project type dictionary lookups now use `seal_project_type`, matching the
  reference frontend.

## 2026-10-26 Full Page Parity Audit

- Audited all 67 accessible desktop leaf routes with the same administrator
  account against the public reference site.
- Added explicit implementations for 27 project, finance and low-code list APIs
  that previously reached the compatibility fallback.
- Imported 121 representative first-page records from public reference API
  responses into `parity-data.sql`.
- Expanded the super-administrator permission set from 17 to the reference set
  of 236 permissions, restoring add, edit, delete, export and audit actions.
- Added the complete administrator organization data: the `企业管理` group,
  member relationships, detail endpoint and update operations.
- Replaced H2-incompatible mobile chart SQL with Java-side aggregation.
- Desktop audit result: 67/67 routes, 0 fallback responses, 0 page/API errors,
  0 missing buttons.
- Mobile runtime audit result: 67/67 paths, 0 fallback responses, 0 HTTP errors.

## 2026-10-26 Local Parity Update

- Dynamic routes now come from 85 records in `sys_module`, matching the public reference module tree.
- The system area now includes 组织机构、角色权限、基础数据、流程设置、参数设置、通知公告、操作日志、登录日志.
- `/system/module` adds list, tree, detail, create, update and delete operations without changing the RBAC `sys_menu` contract.
- All 68 leaf route components exist locally. The three previously missing pages reuse existing fund-plan and purchase-payment components.
- H5 inventory now covers 56 pages and 68 API paths. The main local business paths have explicit controllers; WeChat, DingTalk and slider-captcha integrations remain configuration-dependent.
- The demo account model now includes separate finance accountant, cashier, contract archive, production VP, purchasing, comprehensive manager, seal-confirmation and void-recovery roles.
- Merchant headers, contacts and bank accounts now have normalized local tables and CRUD APIs.

## Summary

| Area | Status | Notes |
| --- | --- | --- |
| Authentication | Implemented | Captcha, RSA-compatible password input, BCrypt, JWT |
| System management | Implemented | User, dept, role, menu, dict, config, notice, post, logs |
| Application center | Implemented | Apps, modules, tenants, basic admin groups |
| Project | Implemented | Project CRUD, members, tree, submit-status transition |
| Contract | Implemented | Contract CRUD, line items, submit-status transition |
| Purchase | Implemented | Purchase application CRUD and line items |
| Expense | Implemented | Expense CRUD, items, invoices |
| Budget | Implemented | Budget header/items and list/detail |
| Warehouse | Implemented | Warehouse and inbound list foundation |
| Todo/process | Partial | Todo list and task completion; no BPMN engine |
| Analysis/report | Compatibility | Empty compatible payloads |
| Invoice/fund/CRM | Partial | Schema and core list fields; full workflows pending |
| Third-party services | Not reconstructed | Baidu OCR, WPS, DingTalk, WebSocket need credentials/contracts |

## Endpoint Coverage

The frontend calls 722 endpoint operations across 84 API modules.

- Explicit backend mappings: 131 mapping annotations, including multiple URL aliases per method.
- Real domain implementation: authentication, system administration, application center, upload/download, project, contract, purchase, expense, budget, warehouse and todo foundations.
- Compatibility implementation: all unmatched routes are caught by `CompatController`. List-like paths return empty pagination, tree/select paths return empty arrays, details return an empty object, and writes return `code=200`.

Compatibility responses intentionally include:

```text
X-UMM-OA-Compatible: fallback
```

This header makes remaining stubs observable during migration.

## Database Scope

The SQL contains normalized system tables and core OA tables:

- Tenant, department, user, role, menu, post and their relations
- Dictionaries, system configuration, notices and logs
- Applications and application modules
- Project and project member
- Contract and contract line item
- Purchase application and line item
- Expense, expense item and invoice
- Loan, budget and budget item
- Fund plan, warehouse and warehouse movement
- Business opportunity, todo, process definition, instance and task
- Generic `biz_record` for modules not yet normalized

## Not Claimed

- No source code was recovered from the remote service.
- No undisclosed private business rules are asserted.
- No production data was copied into the seed SQL.
- Public demo credentials from the frontend README are intentionally not used as local production passwords.

