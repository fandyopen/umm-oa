-- Back up the database before applying. Removes only the 20 retired domain tables.
-- Current CRM contracts, finance, recruitment, HR, commission and workflow tables are preserved.
DROP TABLE IF EXISTS oa_project_member;
DROP TABLE IF EXISTS oa_project;
DROP TABLE IF EXISTS oa_contract_item;
DROP TABLE IF EXISTS oa_contract;
DROP TABLE IF EXISTS oa_purchase_apply_item;
DROP TABLE IF EXISTS oa_purchase_apply;
DROP TABLE IF EXISTS oa_purchase_payment;
DROP TABLE IF EXISTS oa_expense_item;
DROP TABLE IF EXISTS oa_expense_invoice;
DROP TABLE IF EXISTS oa_expense;
DROP TABLE IF EXISTS oa_loan_detail;
DROP TABLE IF EXISTS oa_loan;
DROP TABLE IF EXISTS oa_repayment;
DROP TABLE IF EXISTS oa_budget_item;
DROP TABLE IF EXISTS oa_budget;
DROP TABLE IF EXISTS oa_fund_plan;
DROP TABLE IF EXISTS oa_warehouse_in;
DROP TABLE IF EXISTS oa_warehouse_out;
DROP TABLE IF EXISTS oa_warehouse;
DROP TABLE IF EXISTS oa_invoice;
