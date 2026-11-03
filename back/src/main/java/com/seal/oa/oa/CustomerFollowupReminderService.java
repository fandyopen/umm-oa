package com.seal.oa.oa;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** 客户首次跟进提醒：当天一条，实际跟进后统一办结。 */
@Service
public class CustomerFollowupReminderService {
    public static final String BUSINESS_TYPE = "crmCustomerFollowup";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public CustomerFollowupReminderService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 8 * * ?", zone = "Asia/Shanghai")
    public void generateDailyReminders() {
        LocalDate today = LocalDate.now(ZONE);
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM oa_merchant WHERE status = '0' AND public_pool = '0'
                  AND last_followup_time IS NULL
                UNION SELECT business_id FROM oa_todo
                  WHERE business_type = ? AND status = 'todo'
                ORDER BY 1
                """, Long.class, BUSINESS_TYPE);
        ids.forEach(id -> synchronizeCustomer(id, today));
    }

    public void synchronizeCustomer(long customerId) {
        synchronizeCustomer(customerId, LocalDate.now(ZONE));
    }

    void synchronizeCustomer(long customerId, LocalDate day) {
        transactions.executeWithoutResult(ignored -> {
            // 对客户行加锁，保存、分配和定时任务并发时仍然每天只生成一条。
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM oa_merchant WHERE id = ? FOR UPDATE", customerId);
            if (rows.isEmpty()) {
                close(customerId, "cancelled");
                return;
            }
            Map<String, Object> customer = rows.get(0);
            boolean followed = customer.get("last_followup_time") != null || jdbc.queryForObject(
                    "SELECT COUNT(*) FROM oa_customer_followup WHERE merchant_id = ?", Long.class, customerId) > 0;
            if (followed) {
                close(customerId, "done");
                return;
            }
            Object owner = customer.get("owner_user_id");
            boolean eligible = "0".equals(customer.get("status"))
                    && "0".equals(customer.get("public_pool")) && owner != null
                    && jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id = ? AND status = '0' AND del_flag = '0'",
                            Long.class, owner) > 0;
            if (!eligible) {
                close(customerId, "cancelled");
                return;
            }
            jdbc.update("""
                    UPDATE oa_todo SET status = 'cancelled'
                    WHERE business_type = ? AND business_id = ? AND status = 'todo' AND user_id <> ?
                    """, BUSINESS_TYPE, customerId, owner);
            String taskId = "crm-customer-followup-" + customerId + "-" + owner + "-" + day;
            String title = "客户待跟进：" + customer.get("name");
            if (title.length() > 200) title = title.substring(0, 200);
            int existing = jdbc.update("""
                    UPDATE oa_todo SET title = ?, status = 'todo'
                    WHERE business_type = ? AND task_id = ?
                    """, title, BUSINESS_TYPE, taskId);
            if (existing == 0) {
                jdbc.update("""
                        INSERT INTO oa_todo (tenant_id, user_id, title, business_type, business_id, task_id, status)
                        VALUES (?, ?, ?, ?, ?, ?, 'todo')
                        """, customer.get("tenant_id"), owner, title, BUSINESS_TYPE, customerId, taskId);
            }
        });
    }

    private void close(long customerId, String status) {
        jdbc.update("UPDATE oa_todo SET status = ? WHERE business_type = ? AND business_id = ? AND status = 'todo'",
                status, BUSINESS_TYPE, customerId);
    }
}
