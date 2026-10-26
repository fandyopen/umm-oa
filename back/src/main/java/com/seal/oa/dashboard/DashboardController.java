package com.seal.oa.dashboard;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiResult;
import com.seal.oa.common.JdbcMaps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class DashboardController {

    private final JdbcTemplate jdbc;

    public DashboardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/project/analysis/first/page")
    public Map<String, Object> executiveStatistics() {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime nextMonth = monthStart.plusMonths(1);

        BigDecimal revenueContract = sum("SELECT COALESCE(SUM(money),0) FROM oa_contract");
        BigDecimal expenseContract = sum("SELECT COALESCE(SUM(total_amount),0) FROM oa_purchase_apply");
        BigDecimal collection = sum("SELECT COALESCE(SUM(amount),0) FROM oa_fund_plan WHERE plan_type='in'");
        BigDecimal payment = sum("SELECT COALESCE(SUM(total_amount),0) FROM oa_expense");
        BigDecimal profit = collection.subtract(payment);

        BigDecimal revenueMonth = sumBetween(
                "SELECT COALESCE(SUM(money),0) FROM oa_contract WHERE 1=1", monthStart, nextMonth);
        BigDecimal expenseMonth = sumBetween(
                "SELECT COALESCE(SUM(total_amount),0) FROM oa_purchase_apply WHERE 1=1", monthStart, nextMonth);
        BigDecimal collectionMonth = sumBetween(
                "SELECT COALESCE(SUM(amount),0) FROM oa_fund_plan WHERE plan_type='in'", monthStart, nextMonth);
        BigDecimal paymentMonth = sumBetween(
                "SELECT COALESCE(SUM(total_amount),0) FROM oa_expense WHERE 1=1", monthStart, nextMonth);
        BigDecimal profitMonth = collectionMonth.subtract(paymentMonth);

        return ApiResult.ok(List.of(
                moneyItem("利润", profit, profitMonth),
                moneyItem("收入合同总金额", revenueContract, revenueMonth),
                moneyItem("支出合同总金额", expenseContract, expenseMonth),
                moneyItem("收款总金额", collection, collectionMonth),
                moneyItem("支出总金额", payment, paymentMonth)
        ));
    }

    @GetMapping("/project/analysis/project/top/staff")
    public Map<String, Object> employeeStatistics() {
        Long userId = AuthContext.userId();
        Integer projectCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM oa_project_member WHERE user_id = ?
                """, Integer.class, userId);
        BigDecimal loanTotal = sumWithArgs("""
                SELECT COALESCE(SUM(amount),0) FROM oa_loan WHERE apply_user_id = ?
                """, userId);
        BigDecimal repayTotal = sumWithArgs("""
                SELECT COALESCE(SUM(amount - actual_repay_amount),0) FROM oa_loan WHERE apply_user_id = ?
                """, userId);

        Map<String, Object> projects = new LinkedHashMap<>();
        projects.put("name", "参与项目");
        projects.put("num", projectCount == null ? 0 : projectCount);
        return ApiResult.ok(List.of(
                projects,
                numberItem("借款总额", loanTotal),
                numberItem("待还总额", repayTotal)
        ));
    }

    @GetMapping("/project/analysis/top/sale")
    public Map<String, Object> salesStatistics() {
        Map<String, Object> contracts = aggregateItem(
                "收入合同",
                "SELECT COUNT(*) AS num, COALESCE(SUM(money),0) AS money FROM oa_contract"
        );
        Map<String, Object> collections = aggregateItem(
                "收款总额",
                "SELECT COUNT(*) AS num, COALESCE(SUM(amount),0) AS money FROM oa_fund_plan WHERE plan_type='in'"
        );
        Integer opportunities = jdbc.queryForObject("""
                SELECT COUNT(*) FROM oa_business_opportunity WHERE status IN ('running','跟进中')
                """, Integer.class);
        Integer biddingProjects = jdbc.queryForObject("""
                SELECT COUNT(*) FROM oa_project WHERE status = '在建'
                """, Integer.class);

        Map<String, Object> opportunity = new LinkedHashMap<>();
        opportunity.put("name", "正在跟进商机");
        opportunity.put("num", opportunities == null ? 0 : opportunities);
        Map<String, Object> bidding = new LinkedHashMap<>();
        bidding.put("name", "正在招投标项目");
        bidding.put("num", biddingProjects == null ? 0 : biddingProjects);
        return ApiResult.ok(List.of(contracts, collections, opportunity, bidding));
    }

    @GetMapping("/project/analysis/project/purchase/top")
    public Map<String, Object> projectManagerStatistics() {
        Map<String, Object> building = projectStatusItem("在建项目", "在建");
        Map<String, Object> finished = projectStatusItem("完工项目", "已完成");
        Map<String, Object> contracts = aggregateItem(
                "支出合同",
                "SELECT COUNT(*) AS num, COALESCE(SUM(money),0) AS money FROM oa_contract"
        );
        Map<String, Object> purchases = aggregateItem(
                "材料采购",
                "SELECT COUNT(*) AS num, COALESCE(SUM(total_amount),0) AS money FROM oa_purchase_apply"
        );
        return ApiResult.ok(List.of(building, finished, contracts, purchases));
    }

    @GetMapping("/project/analysis/project/staff/project/type")
    public Map<String, Object> employeeProjectTypes() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT COALESCE(d.dict_label, p.type, '未分类') AS name, COUNT(*) AS num
                FROM oa_project p
                LEFT JOIN sys_dict_data d
                  ON d.dict_type = 'project_type' AND d.dict_value = p.type
                GROUP BY COALESCE(d.dict_label, p.type, '未分类')
                ORDER BY num DESC
                """));
        return ApiResult.ok(rows);
    }

    @GetMapping("/project/analysis/project/staff/project/process")
    public Map<String, Object> employeeProjects(@RequestParam Map<String, String> query) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT p.id AS project_id, p.name AS project_name,
                       COALESCE(SUM(c.money),0) AS in_contact_total,
                       0 AS invoicesum,
                       COALESCE((
                         SELECT SUM(fp.amount) FROM oa_fund_plan fp
                         WHERE fp.project_id = p.id AND fp.plan_type = 'in'
                       ),0) AS in_total,
                       COALESCE(SUM(c.money),0) - COALESCE((
                         SELECT SUM(fp.amount) FROM oa_fund_plan fp
                         WHERE fp.project_id = p.id AND fp.plan_type = 'in'
                       ),0) AS un_in_total,
                       p.status AS payment
                FROM oa_project p
                JOIN oa_project_member m ON m.project_id = p.id AND m.user_id = ?
                LEFT JOIN oa_contract c ON c.project_id = p.id
                GROUP BY p.id, p.name, p.status
                ORDER BY p.id DESC
                """, AuthContext.userId()));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/project/analysis/project/purchase")
    public Map<String, Object> projectStatusChart() {
        return ApiResult.ok(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT status AS name, COUNT(*) AS num
                FROM oa_project
                GROUP BY status
                ORDER BY num DESC
                """)));
    }

    @GetMapping("/project/analysis/project/purchase/type")
    public Map<String, Object> projectExpenseChart() {
        return ApiResult.ok(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT p.name AS type, '合同支出' AS name, COALESCE(SUM(c.money),0) AS money
                FROM oa_project p
                LEFT JOIN oa_contract c ON c.project_id = p.id
                GROUP BY p.id, p.name
                ORDER BY money DESC
                """)));
    }

    @GetMapping("/project/analysis/project/purchase/table")
    public Map<String, Object> projectExpenseTable(@RequestParam Map<String, String> query) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT c.name AS contract_name, p.name AS project_name,
                       c.party_b AS supplier, c.money AS contract_sum,
                       COALESCE((
                         SELECT SUM(pay.pay_amount) FROM oa_purchase_payment pay
                         WHERE pay.contract_id = c.id
                       ),0) AS contract_pay,
                       0 AS settlement,
                       c.money - COALESCE((
                         SELECT SUM(pay.pay_amount) FROM oa_purchase_payment pay
                         WHERE pay.contract_id = c.id
                       ),0) AS un_pay,
                       CASE WHEN c.money = 0 THEN 0 ELSE
                         COALESCE((
                           SELECT SUM(pay.pay_amount) FROM oa_purchase_payment pay
                           WHERE pay.contract_id = c.id
                         ),0) / c.money
                       END AS ratio
                FROM oa_contract c
                LEFT JOIN oa_project p ON p.id = c.project_id
                ORDER BY c.id DESC
                """));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/project/analysis/business/sale")
    public Map<String, Object> businessOpportunityChart() {
        return ApiResult.ok(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT COALESCE(stage, '未分类') AS name, COUNT(*) AS num
                FROM oa_business_opportunity
                GROUP BY COALESCE(stage, '未分类')
                ORDER BY num DESC
                """)));
    }

    @GetMapping("/project/analysis/project/sale")
    public Map<String, Object> salesProjectChart() {
        return projectStatusChart();
    }

    @GetMapping("/project/analysis/project/sale/invoice")
    public Map<String, Object> salesFunnel() {
        BigDecimal contract = sum("SELECT COALESCE(SUM(money),0) FROM oa_contract");
        BigDecimal invoice = sum("SELECT COALESCE(SUM(amount),0) FROM oa_invoice");
        BigDecimal collection = sum("SELECT COALESCE(SUM(amount),0) FROM oa_fund_plan WHERE plan_type='in'");
        return ApiResult.ok(List.of(
                Map.of("name", "收入合同", "year", contract),
                Map.of("name", "开票金额", "year", invoice),
                Map.of("name", "收款金额", "year", collection)
        ));
    }

    @GetMapping("/project/analysis/project/sale/total")
    public Map<String, Object> salesProjectTable(@RequestParam Map<String, String> query) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT p.id AS project_id, p.name AS project_name,
                       COALESCE(SUM(c.money),0) AS incontact_total,
                       COALESCE((
                         SELECT SUM(i.amount) FROM oa_invoice i WHERE i.project_id = p.id
                       ),0) AS invoicesum,
                       COALESCE((
                         SELECT SUM(fp.amount) FROM oa_fund_plan fp
                         WHERE fp.project_id = p.id AND fp.plan_type = 'in'
                       ),0) AS in_total,
                       COALESCE(SUM(c.money),0) - COALESCE((
                         SELECT SUM(fp.amount) FROM oa_fund_plan fp
                         WHERE fp.project_id = p.id AND fp.plan_type = 'in'
                       ),0) AS un_in_total
                FROM oa_project p
                LEFT JOIN oa_contract c ON c.project_id = p.id
                GROUP BY p.id, p.name
                ORDER BY p.id DESC
                """));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/project/analysis/first/in/out")
    public Map<String, Object> monthlyIncomeOutcome() {
        int year = LocalDate.now().getYear();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            LocalDateTime start = LocalDate.of(year, month, 1).atStartOfDay();
            LocalDateTime end = start.plusMonths(1);
            BigDecimal in = sumBetween(
                    "SELECT COALESCE(SUM(amount),0) FROM oa_fund_plan WHERE plan_type='in'", start, end);
            BigDecimal out = sumBetween(
                    "SELECT COALESCE(SUM(amount),0) FROM oa_fund_plan WHERE plan_type='out'", start, end);
            Map<String, Object> income = new LinkedHashMap<>();
            income.put("name", String.format("%02d", month));
            income.put("in", in);
            income.put("out", null);
            rows.add(income);
            Map<String, Object> outcome = new LinkedHashMap<>();
            outcome.put("name", String.format("%02d", month));
            outcome.put("in", null);
            outcome.put("out", out);
            rows.add(outcome);
        }
        return ApiResult.ok(rows);
    }

    @GetMapping("/project/analysis/manager/cost")
    public Map<String, Object> managerCost() {
        BigDecimal plannedIn = sum("SELECT COALESCE(SUM(amount),0) FROM oa_fund_plan WHERE plan_type='in'");
        BigDecimal plannedOut = sum("SELECT COALESCE(SUM(amount),0) FROM oa_fund_plan WHERE plan_type='out'");
        BigDecimal expense = sum("SELECT COALESCE(SUM(total_amount),0) FROM oa_expense");
        BigDecimal purchase = sum("SELECT COALESCE(SUM(total_amount),0) FROM oa_purchase_apply");
        return ApiResult.ok(List.of(
                Map.of("name", "计划收入", "money", plannedIn),
                Map.of("name", "计划支出", "money", plannedOut),
                Map.of("name", "报销支出", "money", expense),
                Map.of("name", "采购支出", "money", purchase)
        ));
    }

    private Map<String, Object> moneyItem(String name, BigDecimal year, BigDecimal month) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("year", year);
        item.put("month", month);
        return item;
    }

    private Map<String, Object> numberItem(String name, BigDecimal money) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("money", money);
        return item;
    }

    private Map<String, Object> aggregateItem(String name, String sql) {
        Map<String, Object> row = jdbc.queryForMap(sql);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("num", ((Number) row.getOrDefault("num", 0)).intValue());
        item.put("money", decimal(row.get("money")));
        return item;
    }

    private Map<String, Object> projectStatusItem(String name, String status) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT COUNT(DISTINCT p.id) AS num, COALESCE(SUM(b.total_amount),0) AS money
                FROM oa_project p
                LEFT JOIN oa_budget b ON b.project_id = p.id
                WHERE p.status = ?
                """, status);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("num", ((Number) row.getOrDefault("num", 0)).intValue());
        item.put("money", decimal(row.get("money")));
        return item;
    }

    private BigDecimal sum(String sql) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumWithArgs(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumBetween(String baseSql, LocalDateTime start, LocalDateTime end) {
        String sql = baseSql + " AND create_time >= ? AND create_time < ?";
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, start, end);
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal decimal
                ? decimal.setScale(2, RoundingMode.HALF_UP)
                : new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
