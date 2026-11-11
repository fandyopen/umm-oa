package com.seal.oa.oa;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/** 客户首次跟进提醒：保存时派发一次，实际跟进后办结。 */
@Service
public class CustomerFollowupReminderService {
    public static final String BUSINESS_TYPE = "crmCustomerFollowup";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final OwnerFollowupTodoService todos;

    public CustomerFollowupReminderService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                           OwnerFollowupTodoService todos) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.todos = todos;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcilePendingReminders() {
        jdbc.queryForList("SELECT DISTINCT business_id FROM oa_todo WHERE business_type = ? AND status = 'todo'",
                Long.class, BUSINESS_TYPE).forEach(this::synchronizeCustomer);
    }

    public void synchronizeCustomer(long customerId) {
        transactions.executeWithoutResult(ignored -> {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM oa_merchant WHERE id = ? FOR UPDATE", customerId);
            if (rows.isEmpty()) {
                todos.close(BUSINESS_TYPE, customerId, "cancelled");
                return;
            }
            Map<String, Object> customer = rows.get(0);
            boolean followed = customer.get("last_followup_time") != null || jdbc.queryForObject(
                    "SELECT COUNT(*) FROM oa_customer_followup WHERE merchant_id = ?", Long.class, customerId) > 0;
            boolean eligible = "0".equals(customer.get("status")) && "0".equals(customer.get("public_pool"));
            todos.synchronize(BUSINESS_TYPE, customerId, customer.get("tenant_id"), customer.get("owner_user_id"),
                    "客户待跟进：" + customer.get("name"), eligible, followed);
        });
    }
}
