package com.seal.oa.oa;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiResult;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.system.DataScopeService;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/project/crm")
public class CrmController {

    private static final long ANALYSIS_MODULE_ID = 340644L;
    private static final long RISK_MODULE_ID = 341101L;
    private static final String BUSINESS_MODULE = "/project/business/list";

    private final JdbcTemplate jdbc;
    private final DataScopeService dataScopeService;
    private final ParityRecordService parityRecords;

    public CrmController(
            JdbcTemplate jdbc,
            DataScopeService dataScopeService,
            ParityRecordService parityRecords
    ) {
        this.jdbc = jdbc;
        this.dataScopeService = dataScopeService;
        this.parityRecords = parityRecords;
    }

    @GetMapping("/analysis/overview")
    @RequiresModuleAction(moduleId = ANALYSIS_MODULE_ID, action = "access")
    public Map<String, Object> analysis() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> customers = customerRows(true);
        result.put("summary", List.of(
                metric("客户总数", customers.size(), ""),
                metric("合作客户", countStatus(customers, "合作中"), "家"),
                metric("潜在客户", countStatus(customers, "潜在客户"), "家"),
                metric("合作职位数", sumLong(customers, "cooperationPositions"), "个"),
                metric("推荐人数", sumLong(customers, "referralCount"), "人"),
                metric("入职人数", sumLong(customers, "hireCount"), "人"),
                metric("合同金额", sumDecimal(customers, "contractAmount"), "元"),
                metric("回款金额", sumDecimal(customers, "receivedAmount"), "元")
        ));
        result.put("statusDistribution", distribution(customers, "cooperationStatus"));
        result.put("sourceDistribution", distribution(customers, "customerSource"));
        result.put("industryDistribution", distribution(customers, "industry"));
        result.put("topCustomers", customers.stream()
                .sorted((left, right) -> contribution(right).compareTo(contribution(left)))
                .limit(10)
                .map(customer -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", customer.get("id"));
                    row.put("name", customer.get("name"));
                    row.put("industry", customer.get("industry"));
                    row.put("cooperationStatus", customer.get("cooperationStatus"));
                    row.put("contractAmount", decimal(customer.get("contractAmount")));
                    row.put("receivedAmount", decimal(customer.get("receivedAmount")));
                    row.put("cooperationPositions", number(customer.get("cooperationPositions"), 0L));
                    row.put("referralCount", number(customer.get("referralCount"), 0L));
                    row.put("hireCount", number(customer.get("hireCount"), 0L));
                    row.put("activityScore", activityScore(customer));
                    return row;
                })
                .toList());
        result.put("opportunityCount", parityRecords.rows(BUSINESS_MODULE).stream()
                .filter(this::canAccessOpportunity)
                .count());
        result.put("riskCount", risks(true).size());
        return ApiResult.ok(result);
    }

    @GetMapping("/risk/list")
    @RequiresModuleAction(moduleId = RISK_MODULE_ID, action = "access")
    public Map<String, Object> riskList() {
        List<Map<String, Object>> risks = risks(true);
        synchronizeRiskTodos(risks, true);
        return ApiResult.page(risks, risks.size());
    }

    @PostMapping("/risk/sync")
    @RequiresModuleAction(moduleId = RISK_MODULE_ID, action = "access")
    public Map<String, Object> synchronizeRisks() {
        List<Map<String, Object>> risks = risks(true);
        int created = synchronizeRiskTodos(risks, true);
        return ApiResult.ok(Map.of(
                "riskCount", risks.size(),
                "createdTodos", created
        ));
    }

    @Scheduled(cron = "0 0 8 * * ?")
    public void generateDailyRiskTodos() {
        synchronizeRiskTodos(risks(false), false);
    }

    private List<Map<String, Object>> customerRows(boolean scoped) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT m.*, u.dept_id AS owner_dept_id
                FROM oa_merchant m
                LEFT JOIN sys_user u ON u.user_id = m.owner_user_id
                WHERE m.status = '0'
                """));
        if (!scoped) {
            return rows;
        }
        return dataScopeService.filterRows(
                AuthContext.userId(),
                rows,
                List.of("ownerDeptId"),
                List.of("ownerUserId", "ownerUserName", "createBy")
        );
    }

    private List<Map<String, Object>> risks(boolean scoped) {
        List<Map<String, Object>> customers = customerRows(scoped);
        List<Map<String, Object>> risks = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Map<String, Object> customer : customers) {
            long id = number(customer.get("id"), 0L);
            long ownerUserId = number(customer.get("ownerUserId"), 0L);
            if (ownerUserId <= 0L || "1".equals(String.valueOf(customer.get("publicPool")))) {
                continue;
            }
            LocalDate lastFollowup = date(customer.get("lastFollowupTime"));
            if (lastFollowup == null) {
                lastFollowup = date(customer.get("assignedAt"));
            }
            if (lastFollowup == null) {
                lastFollowup = date(customer.get("createTime"));
            }
            long noFollowupDays = lastFollowup == null ? 999L : ChronoUnit.DAYS.between(lastFollowup, today);
            if (noFollowupDays >= 30L) {
                risks.add(risk(
                        id,
                        customer,
                        "no_followup",
                        "长期未跟进",
                        noFollowupDays >= 60L ? "high" : "medium",
                        "距离上次跟进已 " + noFollowupDays + " 天"
                ));
            }

            LocalDate expiry = date(customer.get("contractExpireDate"));
            if (expiry != null) {
                long expiryDays = ChronoUnit.DAYS.between(today, expiry);
                if (expiryDays >= 0L && expiryDays <= 30L) {
                    risks.add(risk(
                            id,
                            customer,
                            "contract_expiry",
                            "合同即将到期",
                            expiryDays <= 7L ? "high" : "medium",
                            "合同将在 " + expiryDays + " 天后到期"
                    ));
                }
            }

            BigDecimal contractAmount = decimal(customer.get("contractAmount"));
            BigDecimal receivedAmount = decimal(customer.get("receivedAmount"));
            LocalDate lastPayment = date(customer.get("lastPaymentDate"));
            long noPaymentDays = lastPayment == null ? 999L : ChronoUnit.DAYS.between(lastPayment, today);
            if (contractAmount.compareTo(BigDecimal.ZERO) > 0
                    && receivedAmount.compareTo(contractAmount) < 0
                    && noPaymentDays >= 60L) {
                risks.add(risk(
                        id,
                        customer,
                        "no_payment",
                        "长期未回款",
                        noPaymentDays >= 90L ? "high" : "medium",
                        "应收 " + contractAmount.subtract(receivedAmount) + " 元，已 " + noPaymentDays + " 天未回款"
                ));
            }
        }
        return risks;
    }

    private Map<String, Object> risk(
            long customerId,
            Map<String, Object> customer,
            String type,
            String title,
            String level,
            String reason
    ) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("taskId", "crm-risk-" + customerId + "-" + type);
        item.put("customerId", customerId);
        item.put("customerName", customer.get("name"));
        item.put("ownerUserId", customer.get("ownerUserId"));
        item.put("ownerUserName", customer.get("ownerUserName"));
        item.put("riskType", type);
        item.put("title", title);
        item.put("level", level);
        item.put("reason", reason);
        item.put("status", "todo");
        return item;
    }

    private int synchronizeRiskTodos(List<Map<String, Object>> risks, boolean scoped) {
        Set<String> activeTaskIds = risks.stream()
                .map(risk -> String.valueOf(risk.get("taskId")))
                .collect(Collectors.toSet());
        Set<Long> scopedCustomerIds = scoped
                ? customerRows(true).stream()
                    .map(customer -> number(customer.get("id"), 0L))
                    .filter(id -> id > 0L)
                    .collect(Collectors.toSet())
                : Set.of();
        if (!scoped || !scopedCustomerIds.isEmpty()) {
            List<Object> doneArgs = new ArrayList<>(activeTaskIds);
            StringBuilder closeSql = new StringBuilder("""
                    UPDATE oa_todo SET status = 'done'
                    WHERE business_type = 'crmRisk' AND status = 'todo'
                    """);
            if (!activeTaskIds.isEmpty()) {
                String placeholders = String.join(
                        ",",
                        java.util.Collections.nCopies(activeTaskIds.size(), "?")
                );
                closeSql.append(" AND task_id NOT IN (").append(placeholders).append(")");
            }
            if (scoped) {
                String customerPlaceholders = String.join(
                        ",",
                        java.util.Collections.nCopies(scopedCustomerIds.size(), "?")
                );
                closeSql.append(" AND business_id IN (").append(customerPlaceholders).append(")");
                doneArgs.addAll(scopedCustomerIds);
            }
            jdbc.update(closeSql.toString(), doneArgs.toArray());
        }

        int created = 0;
        for (Map<String, Object> risk : risks) {
            String taskId = String.valueOf(risk.get("taskId"));
            Long existing = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM oa_todo
                    WHERE business_type = 'crmRisk' AND task_id = ?
                    """, Long.class, taskId);
            if (existing != null && existing > 0L) {
                jdbc.update("""
                        UPDATE oa_todo
                        SET user_id = ?, title = ?, status = 'todo'
                        WHERE business_type = 'crmRisk' AND task_id = ?
                        """,
                        number(risk.get("ownerUserId"), 0L),
                        "客户风险：" + risk.get("title") + " - " + risk.get("customerName"),
                        taskId);
                continue;
            }
            created += jdbc.update("""
                    INSERT INTO oa_todo (
                        tenant_id, user_id, title, business_type, business_id,
                        process_instance_id, task_id, status, create_time
                    ) VALUES (1, ?, ?, 'crmRisk', ?, NULL, ?, 'todo', CURRENT_TIMESTAMP)
                    """,
                    number(risk.get("ownerUserId"), 0L),
                    "客户风险：" + risk.get("title") + " - " + risk.get("customerName"),
                    number(risk.get("customerId"), 0L),
                    taskId);
        }
        return created;
    }

    private boolean canAccessOpportunity(Map<String, Object> opportunity) {
        Object deptId = opportunity.containsKey("deptId")
                ? opportunity.get("deptId")
                : opportunity.get("dept_id");
        return dataScopeService.canAccess(
                AuthContext.userId(),
                deptId,
                opportunity.get("ownerUserId"),
                opportunity.get("ownerUserName"),
                opportunity.get("createBy")
        );
    }

    private Map<String, Object> metric(String name, Number value, String unit) {
        return Map.of("name", name, "value", value, "unit", unit);
    }

    private List<Map<String, Object>> distribution(List<Map<String, Object>> rows, String key) {
        Map<String, Long> counts = rows.stream().collect(Collectors.groupingBy(
                row -> {
                    String value = String.valueOf(row.getOrDefault(key, ""));
                    return value.isBlank() ? "未设置" : value;
                },
                LinkedHashMap::new,
                Collectors.counting()
        ));
        return counts.entrySet().stream()
                .map(entry -> Map.<String, Object>of("name", entry.getKey(), "value", entry.getValue()))
                .toList();
    }

    private long countStatus(List<Map<String, Object>> rows, String status) {
        return rows.stream()
                .filter(row -> status.equals(String.valueOf(row.get("cooperationStatus"))))
                .count();
    }

    private long sumLong(List<Map<String, Object>> rows, String key) {
        return rows.stream().mapToLong(row -> number(row.get(key), 0L)).sum();
    }

    private BigDecimal sumDecimal(List<Map<String, Object>> rows, String key) {
        return rows.stream()
                .map(row -> decimal(row.get(key)))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal contribution(Map<String, Object> customer) {
        return decimal(customer.get("contractAmount"))
                .add(decimal(customer.get("receivedAmount")));
    }

    private int activityScore(Map<String, Object> customer) {
        int score = 0;
        LocalDate last = date(customer.get("lastFollowupTime"));
        if (last != null) {
            long days = ChronoUnit.DAYS.between(last, LocalDate.now());
            score += days <= 7L ? 40 : days <= 30L ? 25 : 10;
        }
        if ("合作中".equals(String.valueOf(customer.get("cooperationStatus")))) {
            score += 30;
        }
        if (decimal(customer.get("receivedAmount")).compareTo(BigDecimal.ZERO) > 0) {
            score += 30;
        }
        return Math.min(score, 100);
    }

    private LocalDate date(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        try {
            String text = String.valueOf(value);
            return LocalDate.parse(text.substring(0, 10));
        } catch (Exception ignored) {
            return null;
        }
    }

    private long number(Object value, long defaultValue) {
        try {
            if (value instanceof Number number) {
                return number.longValue();
            }
            String text = value == null ? "" : String.valueOf(value);
            return text.isBlank() ? defaultValue : Long.parseLong(text);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private BigDecimal decimal(Object value) {
        try {
            return value == null
                    ? BigDecimal.ZERO
                    : new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }
}
