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

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class RepaymentController {

    private final JdbcTemplate jdbc;

    public RepaymentController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping({
            "/project/loan/list",
            "/project/loan/list/all",
            "/project/loan/getAll",
            "/project/loan/getLoan"
    })
    public Map<String, Object> loans(@RequestParam Map<String, String> query) {
        String where = "WHERE 1 = 1";
        List<Object> args = new ArrayList<>();
        if (query.containsKey("personal") && "true".equals(query.get("personal"))) {
            where += " AND l.apply_user_id = ?";
            args.add(AuthContext.userId());
        }
        if (text(query.get("applyUserName"))) {
            where += " AND l.apply_user_name LIKE ?";
            args.add("%" + query.get("applyUserName") + "%");
        }
        if (text(query.get("processStatus"))) {
            where += " AND l.process_status = ?";
            args.add(query.get("processStatus"));
        }

        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT l.*, p.name AS project_name
                FROM oa_loan l
                LEFT JOIN oa_project p ON p.id = l.project_id
                %s
                ORDER BY l.id DESC
                """.formatted(where), args.toArray()));
        rows.forEach(row -> {
            row.put("nickname", row.get("applyUserName"));
            row.put("sysUser", Map.of(
                    "userId", row.getOrDefault("applyUserId", AuthContext.userId()),
                    "nickName", row.getOrDefault("applyUserName", AuthContext.username())
            ));
        });
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/project/loan/{id:\\d+}")
    public Map<String, Object> loan(@PathVariable Long id) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT l.*, p.name AS project_name
                FROM oa_loan l
                LEFT JOIN oa_project p ON p.id = l.project_id
                WHERE l.id = ?
                """, id));
        if (rows.isEmpty()) {
            throw new ApiException(404, "借款申请不存在");
        }
        Map<String, Object> loan = rows.get(0);
        loan.put("sealLoanDetails", JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT id, loan_id, loan_name, sum, remark
                FROM oa_loan_detail WHERE loan_id = ? ORDER BY id
                """, id)));
        loan.put("nickname", loan.get("applyUserName"));
        return ApiResult.ok(loan);
    }

    @PostMapping("/project/loan")
    public Map<String, Object> createLoan(@RequestBody Map<String, Object> body) {
        String code = value(body.get("code"), "JKSQ" + System.currentTimeMillis());
        BigDecimal amount = decimal(body.get("amount"), 0);
        if (amount.signum() == 0) {
            amount = detailsAmount(body.get("sealLoanDetails"));
        }
        jdbc.update("""
                INSERT INTO oa_loan (
                    tenant_id, code, apply_user_id, apply_user_name, project_id, loan_type,
                    amount, loan_date, expected_repay_date, actual_repay_amount,
                    process_status, remark, attachment
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'draft', ?, ?)
                """,
                number(body.get("tenantId_"), 1L),
                code,
                AuthContext.userId(),
                AuthContext.username(),
                nullableNumber(body.get("projectId")),
                value(body.get("type"), ""),
                amount,
                date(body.get("loanDate"), 0),
                date(body.get("expectedRepayDate"), 30),
                value(body.get("remark"), ""),
                value(body.get("attachment"), "")
        );
        Long id = jdbc.queryForObject("SELECT id FROM oa_loan WHERE code = ?", Long.class, code);
        saveLoanDetails(id, body.get("sealLoanDetails"));
        return ApiResult.ok(id);
    }

    @PutMapping("/project/loan")
    public Map<String, Object> updateLoan(@RequestBody Map<String, Object> body) {
        Long id = number(body.get("id"), 0L);
        if (id == 0L) {
            throw new ApiException(400, "借款 id 不能为空");
        }
        BigDecimal amount = decimal(body.get("amount"), detailsAmount(body.get("sealLoanDetails")));
        jdbc.update("""
                UPDATE oa_loan SET project_id = ?, loan_type = ?, amount = ?, loan_date = ?,
                    expected_repay_date = ?, remark = ?, attachment = ?
                WHERE id = ?
                """,
                nullableNumber(body.get("projectId")),
                value(body.get("type"), ""),
                amount,
                date(body.get("loanDate"), 0),
                date(body.get("expectedRepayDate"), 30),
                value(body.get("remark"), ""),
                value(body.get("attachment"), ""),
                id
        );
        jdbc.update("DELETE FROM oa_loan_detail WHERE loan_id = ?", id);
        saveLoanDetails(id, body.get("sealLoanDetails"));
        return ApiResult.ok(id);
    }

    @DeleteMapping("/project/loan/{id:\\d+}")
    public Map<String, Object> deleteLoan(@PathVariable Long id) {
        jdbc.update("DELETE FROM oa_loan_detail WHERE loan_id = ?", id);
        jdbc.update("DELETE FROM oa_repayment WHERE loan_id = ?", id);
        jdbc.update("DELETE FROM oa_loan WHERE id = ?", id);
        return ApiResult.ok();
    }

    @DeleteMapping("/project/loan/detail/{detailId}")
    public Map<String, Object> deleteLoanDetail(@PathVariable Long detailId) {
        jdbc.update("DELETE FROM oa_loan_detail WHERE id = ?", detailId);
        return ApiResult.ok();
    }

    @PostMapping({"/project/loan/submitApply", "/project/loan/reApply"})
    public Map<String, Object> submitLoan(@RequestParam Map<String, String> query) {
        Long id = longValue(query.get("dataId"), longValue(query.get("id"), 0L));
        jdbc.update("UPDATE oa_loan SET process_status = 'running' WHERE id = ?", id);
        return ApiResult.ok(id);
    }

    @GetMapping("/project/loan/getProject")
    public Map<String, Object> loanProjects() {
        return ApiResult.ok(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT id AS projectId, name AS projectName FROM oa_project ORDER BY id DESC
                """)));
    }

    @GetMapping("/project/loan/total/loanAnalyst")
    public Map<String, Object> loanAnalysis() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT COALESCE(loan_type, '其他') AS name, COALESCE(SUM(amount),0) AS money
                FROM oa_loan
                GROUP BY COALESCE(loan_type, '其他')
                ORDER BY money DESC
                """));
        return ApiResult.ok(rows);
    }

    @GetMapping("/project/repay/list")
    public Map<String, Object> repayments() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT r.*, l.code AS loan_code, l.apply_user_name
                FROM oa_repayment r
                LEFT JOIN oa_loan l ON l.id = r.loan_id
                ORDER BY r.id DESC
                """));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/project/repay/unconfirm")
    public Map<String, Object> unconfirmedRepayments() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT r.*, l.code AS loan_code, l.apply_user_name
                FROM oa_repayment r
                LEFT JOIN oa_loan l ON l.id = r.loan_id
                WHERE r.status = 'pending'
                ORDER BY r.id DESC
                """));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/project/repay/{id:\\d+}")
    public Map<String, Object> repayment(@PathVariable Long id) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_repayment WHERE id = ?", id));
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/project/repay")
    public Map<String, Object> createRepayment(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                INSERT INTO oa_repayment (loan_id, user_id, amount, repay_date, status, remark)
                VALUES (?, ?, ?, ?, 'pending', ?)
                """,
                number(body.get("loanId"), 0L),
                AuthContext.userId(),
                decimal(body.get("amount"), 0),
                date(body.get("repayDate"), 0),
                value(body.get("remark"), ""));
        return ApiResult.ok(jdbc.queryForObject("SELECT MAX(id) FROM oa_repayment", Long.class));
    }

    @PutMapping("/project/repay")
    public Map<String, Object> updateRepayment(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                UPDATE oa_repayment SET amount = ?, repay_date = ?, remark = ? WHERE id = ?
                """,
                decimal(body.get("amount"), 0),
                date(body.get("repayDate"), 0),
                value(body.get("remark"), ""),
                number(body.get("id"), 0L));
        return ApiResult.ok();
    }

    @DeleteMapping("/project/repay/{id:\\d+}")
    public Map<String, Object> deleteRepayment(@PathVariable Long id) {
        jdbc.update("DELETE FROM oa_repayment WHERE id = ?", id);
        return ApiResult.ok();
    }

    @GetMapping("/project/repay/confirmRepay/{id}")
    public Map<String, Object> confirmRepayment(@PathVariable Long id) {
        Map<String, Object> repayment = jdbc.queryForMap(
                "SELECT loan_id, amount, status FROM oa_repayment WHERE id = ?", id);
        if (!"confirmed".equals(repayment.get("status"))) {
            jdbc.update("UPDATE oa_repayment SET status = 'confirmed' WHERE id = ?", id);
            jdbc.update("""
                    UPDATE oa_loan SET actual_repay_amount = actual_repay_amount + ?
                    WHERE id = ?
                    """, decimal(repayment.get("amount"), 0), ((Number) repayment.get("loan_id")).longValue());
        }
        return ApiResult.ok();
    }

    @GetMapping("/project/repay/getUserMoney")
    public Map<String, Object> userAvailableLoanAmount() {
        BigDecimal total = decimal(jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount - actual_repay_amount),0)
                FROM oa_loan WHERE apply_user_id = ?
                """, BigDecimal.class, AuthContext.userId()), 0);
        return ApiResult.ok(total);
    }

    @GetMapping({"/project/repay/getRepay/", "/project/repay/getRepay"})
    public Map<String, Object> repaymentSearch() {
        return repayments();
    }

    private void saveLoanDetails(Long loanId, Object rawDetails) {
        if (!(rawDetails instanceof List<?> details)) {
            return;
        }
        for (Object item : details) {
            if (!(item instanceof Map<?, ?> detail)) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO oa_loan_detail (loan_id, loan_name, sum, remark)
                    VALUES (?, ?, ?, ?)
                    """,
                    loanId,
                    value(detail.get("loanName"), ""),
                    decimal(detail.get("sum"), 0),
                    value(detail.get("remark"), ""));
        }
    }

    private BigDecimal detailsAmount(Object rawDetails) {
        if (!(rawDetails instanceof List<?> details)) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = BigDecimal.ZERO;
        for (Object item : details) {
            if (item instanceof Map<?, ?> detail) {
                amount = amount.add(decimal(detail.get("sum"), 0));
            }
        }
        return amount;
    }

    private Date date(Object value, int plusDays) {
        LocalDate date = LocalDate.now().plusDays(plusDays);
        if (value != null && !String.valueOf(value).isBlank()) {
            try {
                date = LocalDate.parse(String.valueOf(value).substring(0, 10));
            } catch (Exception ignored) {
                // Keep the default date when the form sends a non-ISO value.
            }
        }
        return Date.valueOf(date);
    }

    private BigDecimal decimal(Object value, BigDecimal defaultValue) {
        try {
            return value == null ? defaultValue : new BigDecimal(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private BigDecimal decimal(Object value, double defaultValue) {
        return decimal(value, BigDecimal.valueOf(defaultValue));
    }

    private long number(Object value, long defaultValue) {
        try {
            return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private Long nullableNumber(Object value) {
        long number = number(value, Long.MIN_VALUE);
        return number == Long.MIN_VALUE ? null : number;
    }

    private long longValue(String value, long defaultValue) {
        try {
            return Long.parseLong(value);
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
