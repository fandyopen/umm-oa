package com.seal.oa.oa;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.common.JdbcMaps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class OaController {

    private final JdbcTemplate jdbc;

    public OaController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping({
            "/project/project/list",
            "/project/project/list/all",
            "/project/project/list/personal",
            "/project/project/list/in",
            "/project/project/list/manager"
    })
    public Map<String, Object> projects(@RequestParam Map<String, String> query) {
        String where = "WHERE 1 = 1";
        List<Object> args = new ArrayList<>();
        if (text(query.get("name"))) {
            where += " AND p.name LIKE ?";
            args.add("%" + query.get("name") + "%");
        }
        if (text(query.get("code"))) {
            where += " AND p.code LIKE ?";
            args.add("%" + query.get("code") + "%");
        }
        if (text(query.get("status"))) {
            where += " AND p.status = ?";
            args.add(query.get("status"));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM oa_project p " + where, Long.class, args.toArray());
        int pageSize = Math.min(Math.max(integer(query.get("pageSize"), 10), 1), 200);
        int pageNum = Math.max(integer(query.get("pageNum"), 1), 1);
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT p.id, p.tenant_id, p.code, p.name, p.nick_name, p.dept_id, d.dept_name,
                       p.type, p.owner_unit, p.cons_unit, p.sketch, p.status, p.process_status,
                       p.attachment, p.create_by, p.create_time, p.update_time
                FROM oa_project p
                LEFT JOIN sys_dept d ON d.dept_id = p.dept_id
                %s
                ORDER BY p.id DESC
                LIMIT ? OFFSET ?
                """.formatted(where), pageArgs.toArray()));
        rows.forEach(row -> row.put("tenantId_", row.get("tenantId")));
        return ApiResult.page(rows, total == null ? 0 : total);
    }

    @GetMapping("/project/project/{id}")
    public Map<String, Object> project(@PathVariable Long id) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT p.*, d.dept_name
                FROM oa_project p
                LEFT JOIN sys_dept d ON d.dept_id = p.dept_id
                WHERE p.id = ?
                """, id));
        if (rows.isEmpty()) {
            throw new ApiException(404, "项目不存在");
        }
        Map<String, Object> project = rows.get(0);
        project.put("tenantId_", project.get("tenantId"));
        project.put("members", JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_project_member WHERE project_id = ? ORDER BY id", id)));
        return ApiResult.ok(project);
    }

    @PostMapping("/project/project")
    public Map<String, Object> createProject(@RequestBody Map<String, Object> body) {
        String code = value(body.get("code"), "QS-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        jdbc.update("""
                INSERT INTO oa_project (
                    tenant_id, code, name, nick_name, dept_id, type, owner_unit, cons_unit,
                    sketch, status, process_status, attachment, create_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'draft', ?, ?)
                """,
                number(body.get("tenantId_"), number(body.get("tenantId"), 1L)),
                code,
                value(body.get("name"), "未命名项目"),
                value(body.get("nickName"), AuthContext.username()),
                number(body.get("deptId"), 101L),
                value(body.get("type"), ""),
                value(body.get("ownerUnit"), ""),
                value(body.get("consUnit"), ""),
                value(body.get("sketch"), ""),
                value(body.get("status"), "在建"),
                value(body.get("attachment"), ""),
                AuthContext.username()
        );
        Long id = jdbc.queryForObject("SELECT id FROM oa_project WHERE code = ?", Long.class, code);
        return ApiResult.ok(id);
    }

    @PutMapping("/project/project")
    public Map<String, Object> updateProject(@RequestBody Map<String, Object> body) {
        Long id = number(body.get("id"), 0L);
        if (id == 0L) {
            throw new ApiException(400, "项目 id 不能为空");
        }
        jdbc.update("""
                UPDATE oa_project SET code = ?, name = ?, nick_name = ?, dept_id = ?, type = ?,
                    owner_unit = ?, cons_unit = ?, sketch = ?, status = ?, attachment = ?, update_time = ?
                WHERE id = ?
                """,
                value(body.get("code"), ""),
                value(body.get("name"), ""),
                value(body.get("nickName"), ""),
                number(body.get("deptId"), 101L),
                value(body.get("type"), ""),
                value(body.get("ownerUnit"), ""),
                value(body.get("consUnit"), ""),
                value(body.get("sketch"), ""),
                value(body.get("status"), "在建"),
                value(body.get("attachment"), ""),
                Timestamp.valueOf(LocalDateTime.now()),
                id
        );
        return ApiResult.ok(id);
    }

    @DeleteMapping("/project/project/{id}")
    public Map<String, Object> deleteProject(@PathVariable Long id) {
        jdbc.update("DELETE FROM oa_project_member WHERE project_id = ?", id);
        jdbc.update("DELETE FROM oa_project WHERE id = ?", id);
        return ApiResult.ok();
    }

    @PostMapping({"/project/project/submitApply", "/project/project/reApply"})
    public Map<String, Object> submitProject(@RequestBody Map<String, Object> body) {
        Long dataId = number(body.get("dataId"), number(body.get("id"), 0L));
        jdbc.update("UPDATE oa_project SET process_status = 'running' WHERE id = ?", dataId);
        return ApiResult.ok(dataId);
    }

    @GetMapping({"/project/project/select/tree", "/project/project/query/children/project"})
    public Map<String, Object> projectTree() {
        return ApiResult.ok(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT id, name AS label, id AS value FROM oa_project ORDER BY id
                """)));
    }

    @GetMapping({
            "/project/contract/list",
            "/project/contract/list/personal",
            "/project/contract/list/apply",
            "/project/contract/list/only",
            "/project/contract/list/by/project",
            "/project/contract/list/qc",
            "/project/contract/list/in/personal"
    })
    public Map<String, Object> contracts(@RequestParam Map<String, String> query) {
        String where = "WHERE 1 = 1";
        List<Object> args = new ArrayList<>();
        if (text(query.get("name"))) {
            where += " AND c.name LIKE ?";
            args.add("%" + query.get("name") + "%");
        }
        if (text(query.get("code"))) {
            where += " AND c.code LIKE ?";
            args.add("%" + query.get("code") + "%");
        }
        if (text(query.get("projectId"))) {
            where += " AND c.project_id = ?";
            args.add(Long.parseLong(query.get("projectId")));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM oa_contract c " + where, Long.class, args.toArray());
        int pageSize = Math.min(Math.max(integer(query.get("pageSize"), 10), 1), 200);
        int pageNum = Math.max(integer(query.get("pageNum"), 1), 1);
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT c.*, p.name AS project_name
                FROM oa_contract c LEFT JOIN oa_project p ON p.id = c.project_id
                %s ORDER BY c.id DESC LIMIT ? OFFSET ?
                """.formatted(where), pageArgs.toArray()));
        rows.forEach(row -> row.put("tenantId_", row.get("tenantId")));
        return ApiResult.page(rows, total == null ? 0 : total);
    }

    @GetMapping("/project/contract/warnTotal")
    public Map<String, Object> contractWarnings() {
        Date deadline = java.sql.Date.valueOf(LocalDate.now().plusDays(30));
        Integer contract = jdbc.queryForObject("""
                SELECT COUNT(*) FROM oa_contract
                WHERE end_date BETWEEN CURRENT_DATE AND ?
                """, Integer.class, deadline);
        Integer planIn = jdbc.queryForObject("""
                SELECT COUNT(*) FROM oa_fund_plan
                WHERE plan_type = 'in' AND plan_date BETWEEN CURRENT_DATE AND ?
                """, Integer.class, deadline);
        Integer planOut = jdbc.queryForObject("""
                SELECT COUNT(*) FROM oa_fund_plan
                WHERE plan_type = 'out' AND plan_date BETWEEN CURRENT_DATE AND ?
                """, Integer.class, deadline);
        return ApiResult.ok(Map.of(
                "contract", contract == null ? 0 : contract,
                "earnest", 0,
                "planIn", planIn == null ? 0 : planIn,
                "planOut", planOut == null ? 0 : planOut
        ));
    }

    @GetMapping("/project/contract/warnContractStage")
    public Map<String, Object> contractStageWarnings() {
        return ApiResult.page(List.of(), 0);
    }

    @GetMapping("/project/contract/warnEarnest")
    public Map<String, Object> earnestWarnings() {
        return ApiResult.page(List.of(), 0);
    }

    @GetMapping("/project/contract/warnPaymentReal")
    public Map<String, Object> planInWarnings() {
        return ApiResult.page(List.of(), 0);
    }

    @GetMapping("/project/contract/warnPayment")
    public Map<String, Object> planOutWarnings() {
        return ApiResult.page(List.of(), 0);
    }

    @GetMapping("/project/contract/{id:\\d+}")
    public Map<String, Object> contract(@PathVariable Long id) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_contract WHERE id = ?", id));
        if (rows.isEmpty()) {
            throw new ApiException(404, "合同不存在");
        }
        Map<String, Object> contract = rows.get(0);
        contract.put("items", JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_contract_item WHERE contract_id = ? ORDER BY sn", id)));
        return ApiResult.ok(contract);
    }

    @PostMapping("/project/contract")
    public Map<String, Object> createContract(@RequestBody Map<String, Object> body) {
        String code = value(body.get("code"), "HT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        jdbc.update("""
                INSERT INTO oa_contract (
                    tenant_id, project_id, code, name, type, party_a, party_b, money,
                    tax_rate, start_date, end_date, efficacy, process_status, attachment,
                    remark, create_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'draft', ?, ?, ?)
                """,
                number(body.get("tenantId_"), 1L),
                nullableNumber(body.get("projectId")),
                code,
                value(body.get("name"), "未命名合同"),
                value(body.get("type"), ""),
                value(body.get("partyA"), ""),
                value(body.get("partyB"), ""),
                decimal(body.get("money"), 0),
                decimal(body.get("taxRate"), 0),
                null,
                null,
                value(body.get("efficacy"), "有效"),
                value(body.get("attachment"), ""),
                value(body.get("remark"), ""),
                AuthContext.username()
        );
        Long id = jdbc.queryForObject("SELECT id FROM oa_contract WHERE code = ?", Long.class, code);
        return ApiResult.ok(id);
    }

    @PutMapping("/project/contract")
    public Map<String, Object> updateContract(@RequestBody Map<String, Object> body) {
        Long id = number(body.get("id"), 0L);
        jdbc.update("""
                UPDATE oa_contract SET project_id = ?, code = ?, name = ?, type = ?, party_a = ?,
                    party_b = ?, money = ?, tax_rate = ?, efficacy = ?, attachment = ?, remark = ?,
                    update_time = ? WHERE id = ?
                """,
                nullableNumber(body.get("projectId")),
                value(body.get("code"), ""),
                value(body.get("name"), ""),
                value(body.get("type"), ""),
                value(body.get("partyA"), ""),
                value(body.get("partyB"), ""),
                decimal(body.get("money"), 0),
                decimal(body.get("taxRate"), 0),
                value(body.get("efficacy"), "有效"),
                value(body.get("attachment"), ""),
                value(body.get("remark"), ""),
                Timestamp.valueOf(LocalDateTime.now()),
                id
        );
        return ApiResult.ok(id);
    }

    @DeleteMapping("/project/contract/{id}")
    public Map<String, Object> deleteContract(@PathVariable Long id) {
        jdbc.update("DELETE FROM oa_contract_item WHERE contract_id = ?", id);
        jdbc.update("DELETE FROM oa_contract WHERE id = ?", id);
        return ApiResult.ok();
    }

    @PostMapping("/project/contract/submitApply")
    public Map<String, Object> submitContract(@RequestBody Map<String, Object> body) {
        Long dataId = number(body.get("dataId"), number(body.get("id"), 0L));
        jdbc.update("UPDATE oa_contract SET process_status = 'running' WHERE id = ?", dataId);
        return ApiResult.ok(dataId);
    }

    @GetMapping({"/project/expense/list", "/project/expense/list/personal"})
    public Map<String, Object> expenses(@RequestParam Map<String, String> query) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT e.*, p.name AS project_name
                FROM oa_expense e LEFT JOIN oa_project p ON p.id = e.project_id
                ORDER BY e.id DESC
                """));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/project/expense/{id}")
    public Map<String, Object> expense(@PathVariable Long id) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_expense WHERE id = ?", id));
        if (rows.isEmpty()) {
            throw new ApiException(404, "报销单不存在");
        }
        Map<String, Object> expense = rows.get(0);
        expense.put("details", JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_expense_item WHERE expense_id = ? ORDER BY id", id)));
        expense.put("invoices", JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_expense_invoice WHERE expense_id = ? ORDER BY id", id)));
        return ApiResult.ok(expense);
    }

    @PostMapping("/project/expense")
    public Map<String, Object> createExpense(@RequestBody Map<String, Object> body) {
        String code = value(body.get("code"), "BX-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        jdbc.update("""
                INSERT INTO oa_expense (
                    tenant_id, project_id, code, apply_user_id, apply_user_name, dept_id,
                    expense_type, total_amount, process_status, remark, attachment
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'draft', ?, ?)
                """,
                number(body.get("tenantId_"), 1L),
                nullableNumber(body.get("projectId")),
                code,
                AuthContext.userId(),
                AuthContext.username(),
                number(body.get("deptId"), 101L),
                value(body.get("expenseType"), ""),
                decimal(body.get("totalAmount"), 0),
                value(body.get("remark"), ""),
                value(body.get("attachment"), "")
        );
        Long id = jdbc.queryForObject("SELECT id FROM oa_expense WHERE code = ?", Long.class, code);
        saveExpenseDetails(id, body.get("details"));
        return ApiResult.ok(id);
    }

    @PutMapping("/project/expense")
    public Map<String, Object> updateExpense(@RequestBody Map<String, Object> body) {
        Long id = number(body.get("id"), 0L);
        jdbc.update("""
                UPDATE oa_expense SET project_id = ?, expense_type = ?, total_amount = ?,
                    remark = ?, attachment = ?, update_time = ? WHERE id = ?
                """,
                nullableNumber(body.get("projectId")),
                value(body.get("expenseType"), ""),
                decimal(body.get("totalAmount"), 0),
                value(body.get("remark"), ""),
                value(body.get("attachment"), ""),
                Timestamp.valueOf(LocalDateTime.now()),
                id
        );
        jdbc.update("DELETE FROM oa_expense_item WHERE expense_id = ?", id);
        jdbc.update("DELETE FROM oa_expense_invoice WHERE expense_id = ?", id);
        saveExpenseDetails(id, body.get("details"));
        return ApiResult.ok(id);
    }

    @DeleteMapping("/project/expense/{id}")
    public Map<String, Object> deleteExpense(@PathVariable Long id) {
        jdbc.update("DELETE FROM oa_expense_item WHERE expense_id = ?", id);
        jdbc.update("DELETE FROM oa_expense_invoice WHERE expense_id = ?", id);
        jdbc.update("DELETE FROM oa_expense WHERE id = ?", id);
        return ApiResult.ok();
    }

    @PostMapping("/project/expense/submitApply")
    public Map<String, Object> submitExpense(@RequestBody Map<String, Object> body) {
        Long dataId = number(body.get("dataId"), number(body.get("id"), 0L));
        jdbc.update("UPDATE oa_expense SET process_status = 'running' WHERE id = ?", dataId);
        return ApiResult.ok(dataId);
    }

    @GetMapping({"/project/apply/payment/list", "/project/apply/payment/list/all", "/project/apply/payment/list/notPay"})
    public Map<String, Object> purchaseApplies() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT a.*, p.name AS project_name
                FROM oa_purchase_apply a LEFT JOIN oa_project p ON p.id = a.project_id
                ORDER BY a.id DESC
                """));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/project/apply/payment/{id}")
    public Map<String, Object> purchaseApply(@PathVariable Long id) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_purchase_apply WHERE id = ?", id));
        if (rows.isEmpty()) {
            throw new ApiException(404, "采购申请不存在");
        }
        Map<String, Object> apply = rows.get(0);
        apply.put("items", JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_purchase_apply_item WHERE apply_id = ? ORDER BY id", id)));
        return ApiResult.ok(apply);
    }

    @PostMapping("/project/apply/payment")
    public Map<String, Object> createPurchaseApply(@RequestBody Map<String, Object> body) {
        String code = value(body.get("code"), "CG-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        jdbc.update("""
                INSERT INTO oa_purchase_apply (
                    tenant_id, project_id, code, apply_user_id, apply_user_name, dept_id,
                    purpose, total_amount, process_status, attachment
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'draft', ?)
                """,
                number(body.get("tenantId_"), 1L),
                nullableNumber(body.get("projectId")),
                code,
                AuthContext.userId(),
                AuthContext.username(),
                number(body.get("deptId"), 101L),
                value(body.get("purpose"), ""),
                decimal(body.get("totalAmount"), 0),
                value(body.get("attachment"), "")
        );
        Long id = jdbc.queryForObject("SELECT id FROM oa_purchase_apply WHERE code = ?", Long.class, code);
        savePurchaseItems(id, body.get("items"));
        return ApiResult.ok(id);
    }

    @PutMapping("/project/apply/payment")
    public Map<String, Object> updatePurchaseApply(@RequestBody Map<String, Object> body) {
        Long id = number(body.get("id"), 0L);
        jdbc.update("""
                UPDATE oa_purchase_apply SET project_id = ?, purpose = ?, total_amount = ?, attachment = ?
                WHERE id = ?
                """,
                nullableNumber(body.get("projectId")),
                value(body.get("purpose"), ""),
                decimal(body.get("totalAmount"), 0),
                value(body.get("attachment"), ""),
                id
        );
        jdbc.update("DELETE FROM oa_purchase_apply_item WHERE apply_id = ?", id);
        savePurchaseItems(id, body.get("items"));
        return ApiResult.ok(id);
    }

    @DeleteMapping("/project/apply/payment/{id}")
    public Map<String, Object> deletePurchaseApply(@PathVariable Long id) {
        jdbc.update("DELETE FROM oa_purchase_apply_item WHERE apply_id = ?", id);
        jdbc.update("DELETE FROM oa_purchase_apply WHERE id = ?", id);
        return ApiResult.ok();
    }

    @PostMapping("/project/apply/payment/submitApply")
    public Map<String, Object> submitPurchaseApply(@RequestBody Map<String, Object> body) {
        Long dataId = number(body.get("dataId"), number(body.get("id"), 0L));
        jdbc.update("UPDATE oa_purchase_apply SET process_status = 'running' WHERE id = ?", dataId);
        return ApiResult.ok(dataId);
    }

    @GetMapping("/project/budget/list")
    public Map<String, Object> budgets() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT b.*, p.name AS project_name
                FROM oa_budget b LEFT JOIN oa_project p ON p.id = b.project_id
                ORDER BY b.id DESC
                """));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/project/budget/{id}")
    public Map<String, Object> budget(@PathVariable Long id) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_budget WHERE id = ?", id));
        if (rows.isEmpty()) {
            throw new ApiException(404, "预算不存在");
        }
        Map<String, Object> budget = rows.get(0);
        budget.put("items", JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_budget_item WHERE budget_id = ? ORDER BY id", id)));
        return ApiResult.ok(budget);
    }

    @PostMapping("/project/budget")
    public Map<String, Object> createBudget(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                INSERT INTO oa_budget (tenant_id, project_id, code, name, budget_year, total_amount, status)
                VALUES (?, ?, ?, ?, ?, ?, 'draft')
                """,
                number(body.get("tenantId_"), 1L),
                nullableNumber(body.get("projectId")),
                value(body.get("code"), "YS-" + System.currentTimeMillis()),
                value(body.get("name"), "未命名预算"),
                number(body.get("budgetYear"), LocalDateTime.now().getYear()),
                decimal(body.get("totalAmount"), 0));
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM oa_budget", Long.class);
        return ApiResult.ok(id);
    }

    @GetMapping({"/project/warehouse/list", "/project/warehouse/statistic/byProject"})
    public Map<String, Object> warehouses() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_warehouse ORDER BY id"));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping({"/project/warehouse/in/list", "/project/warehouse/out/list"})
    public Map<String, Object> warehouseTransfers() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_warehouse_in ORDER BY id DESC"));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping({
            "/project/activity/taskWaitList",
            "/project/activity/taskDoneList",
            "/project/activity/taskCopyList",
            "/project/activity/started"
    })
    public Map<String, Object> todo(@RequestParam Map<String, String> query) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT id, tenant_id, user_id, title, business_type, business_id,
                       process_instance_id, task_id, status, create_time
                FROM oa_todo WHERE user_id = ? ORDER BY id DESC
                """, AuthContext.userId()));
        return ApiResult.page(rows, rows.size());
    }

    @PostMapping({"/project/activity/complete", "/project/activity/end/process", "/project/activity/back"})
    public Map<String, Object> completeTask(@RequestBody Map<String, Object> body) {
        Long todoId = number(body.get("todoId"), number(body.get("id"), 0L));
        if (todoId > 0) {
            jdbc.update("UPDATE oa_todo SET status = 'done' WHERE id = ?", todoId);
        }
        return ApiResult.ok();
    }

    @GetMapping("/project/regular/menu/list/build")
    public Map<String, Object> regularMenus() {
        return ApiResult.ok(List.of());
    }

    @PostMapping("/project/regular/menu")
    public Map<String, Object> addRegularMenu(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @DeleteMapping("/project/regular/menu/{menuId}")
    public Map<String, Object> deleteRegularMenu(@PathVariable Long menuId) {
        return ApiResult.ok();
    }

    private void saveExpenseDetails(Long expenseId, Object rawDetails) {
        if (!(rawDetails instanceof List<?> details)) {
            return;
        }
        for (Object item : details) {
            if (!(item instanceof Map<?, ?> detail)) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO oa_expense_item (expense_id, budget_id, detail, sum, replaced, remark)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    expenseId,
                    nullableNumber(detail.get("budgetId")),
                    value(detail.get("detail"), ""),
                    decimal(detail.get("sum"), 0),
                    value(detail.get("replaced"), "N"),
                    value(detail.get("remark"), ""));
        }
    }

    private void savePurchaseItems(Long applyId, Object rawItems) {
        if (!(rawItems instanceof List<?> items)) {
            return;
        }
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> detail)) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO oa_purchase_apply_item (
                        apply_id, name, spec, unit, amount, price, total_price, description
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    applyId,
                    value(detail.get("name"), ""),
                    value(detail.get("spec"), ""),
                    value(detail.get("unit"), ""),
                    decimal(detail.get("amount"), 0),
                    decimal(detail.get("price"), 0),
                    decimal(detail.get("totalPrice"), 0),
                    value(detail.get("description"), ""));
        }
    }

    private int integer(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private long number(Object value, long defaultValue) {
        try {
            return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private Long nullableNumber(Object value) {
        long result = number(value, Long.MIN_VALUE);
        return result == Long.MIN_VALUE ? null : result;
    }

    private double decimal(Object value, double defaultValue) {
        try {
            return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private String value(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}
