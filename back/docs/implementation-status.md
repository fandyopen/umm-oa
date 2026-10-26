# Implementation Status

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

