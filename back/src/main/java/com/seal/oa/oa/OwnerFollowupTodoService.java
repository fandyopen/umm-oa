package com.seal.oa.oa;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 调用方在业务事务内锁定源记录；客户与商机共用首次跟进待办规则。 */
@Service
public class OwnerFollowupTodoService {
    private final JdbcTemplate jdbc;

    public OwnerFollowupTodoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void synchronize(String type, long businessId, Object tenantId, Object owner,
                            String title, boolean eligible, boolean followed) {
        boolean activeOwner = owner != null && jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE user_id = ? AND status = '0' AND del_flag = '0'",
                Long.class, owner) > 0;
        if (!eligible || !activeOwner) {
            close(type, businessId, "cancelled");
            return;
        }
        if (followed) {
            close(type, businessId, "done");
            return;
        }
        jdbc.update("""
                UPDATE oa_todo SET status = 'cancelled'
                WHERE business_type = ? AND business_id = ? AND status = 'todo' AND (user_id <> ? OR user_id IS NULL)
                """, type, businessId, owner);
        List<Long> pending = jdbc.queryForList("""
                SELECT id FROM oa_todo
                WHERE business_type = ? AND business_id = ? AND user_id = ? AND status = 'todo'
                ORDER BY create_time, id
                """, Long.class, type, businessId, owner);
        String shortTitle = title.length() > 200 ? title.substring(0, 200) : title;
        if (!pending.isEmpty()) {
            long retained = pending.get(0);
            jdbc.update("UPDATE oa_todo SET title = ? WHERE id = ?", shortTitle, retained);
            // 合并旧版每日产生的重复待办，保留首次推送时间及已办历史。
            jdbc.update("""
                    UPDATE oa_todo SET status = 'cancelled'
                    WHERE business_type = ? AND business_id = ? AND status = 'todo' AND id <> ?
                    """, type, businessId, retained);
            return;
        }
        jdbc.update("""
                INSERT INTO oa_todo (tenant_id, user_id, title, business_type, business_id, task_id, status)
                VALUES (?, ?, ?, ?, ?, ?, 'todo')
                """, tenantId, owner, shortTitle, type, businessId,
                type + "-" + businessId + "-" + UUID.randomUUID());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void close(String type, long businessId, String status) {
        jdbc.update("UPDATE oa_todo SET status = ? WHERE business_type = ? AND business_id = ? AND status = 'todo'",
                status, type, businessId);
    }
}
