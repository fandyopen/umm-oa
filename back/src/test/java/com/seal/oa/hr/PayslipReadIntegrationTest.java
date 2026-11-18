package com.seal.oa.hr;

import static org.junit.jupiter.api.Assertions.*;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:payslip_read_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@Transactional
class PayslipReadIntegrationTest {
    @Autowired HrData d;
    @Autowired HrPayrollService payroll;
    @Autowired HrController controller;
    long user, other, batch, item;

    @BeforeEach
    void prepare() {
        user = member("payslip-reader");
        other = member("payslip-other");
        d.jdbc.update("INSERT INTO sys_role(role_id,role_name,role_key,data_scope) VALUES(99581,'工资条查看测试','payslip_test','5')");
        d.jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,99581)", user);
        d.jdbc.update("INSERT INTO sys_role_module(role_id,module_id) VALUES(99581,341006)");
        batch = d.insert("INSERT INTO oa_hr_payroll(period,batch_type,rules_json,status,published,create_by) VALUES('2026-11','regular','{}','approved',1,1)");
        item = d.insert("INSERT INTO oa_hr_payroll_item(payroll_id,user_id,snapshot_json,base_amount,allowance,gross_amount,net_amount) VALUES(?,?,'{}',3174.29,0,3174.29,3174.29)", batch, user);
        notice(user, "hrPayslip", "todo");
        AuthContext.set(new AuthContext.CurrentUser(user, "payslip-reader"));
    }

    private long member(String name) {
        d.jdbc.update("INSERT INTO sys_user(user_name,nick_name,password,dept_id) VALUES(?,?,'x',326)", name, name);
        return d.jdbc.queryForObject("SELECT user_id FROM sys_user WHERE user_name=?", Long.class, name);
    }

    private long notice(long owner, String type, String status) {
        return d.insert("INSERT INTO oa_todo(user_id,title,business_type,business_id,task_id,status) VALUES(?,'工资条',?,?,?,?)",
                owner, type, item, "hr-payslip-" + item, status);
    }

    @AfterEach
    void clear() { AuthContext.clear(); }

    @Test
    void markReadReturnsPersistedFirstViewWithoutChangingPayOrOtherNotices() {
        long foreignNotice = notice(other, "hrPayslip", "todo");
        long unrelatedNotice = notice(user, "otherBusiness", "todo");
        var before = payroll.self().get(0);
        assertEquals(0, before.get("viewed"));
        assertNull(before.get("viewedAt"));
        var response = controller.read(item);
        assertEquals(200, response.get("code"));
        var state = (Map<?, ?>) response.get("data");
        assertEquals(1, state.get("viewed"));
        assertNotNull(state.get("viewedAt"));
        var second = payroll.acknowledge(item);
        assertEquals(state, second);
        var after = payroll.self().get(0);
        assertEquals(state.get("viewedAt"), after.get("viewedAt"));
        assertEquals(1, after.get("viewed"));
        for (String field : new String[]{"baseAmount", "grossAmount", "netAmount", "paidAmount"})
            assertEquals(before.get(field), after.get(field), field);
        assertEquals(new BigDecimal("3174.29"), after.get("netAmount"));
        assertEquals(1L, d.jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_history WHERE entity_type='payslip' AND entity_id=?", Long.class, item));
        assertEquals("done", d.jdbc.queryForObject("SELECT status FROM oa_todo WHERE business_type='hrPayslip' AND user_id=?", String.class, user));
        for (long id : new long[]{foreignNotice, unrelatedNotice})
            assertEquals("todo", d.jdbc.queryForObject("SELECT status FROM oa_todo WHERE id=?", String.class, id));
        d.jdbc.update("DELETE FROM oa_todo WHERE user_id=?", user);
        assertEquals(state.get("viewedAt"), payroll.self().get(0).get("viewedAt"));
    }

    @Test
    void oldCompletedPayslipNoticeKeepsReadStateWithoutInventingViewTime() {
        d.jdbc.update("UPDATE oa_todo SET status='done' WHERE user_id=?", user);
        var row = payroll.self().get(0);
        assertEquals(1, row.get("viewed"));
        assertNull(row.get("viewedAt"));
        var state = payroll.acknowledge(item);
        assertEquals(1, state.get("viewed"));
        assertNull(state.get("viewedAt"));
        assertEquals(0L, d.jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_history WHERE entity_type='payslip' AND entity_id=?", Long.class, item));
    }

    @Test
    void completedForeignOrUnrelatedNoticesDoNotMarkOwnPayslipRead() {
        notice(other, "hrPayslip", "done");
        notice(user, "otherBusiness", "done");
        assertEquals(0, payroll.self().get(0).get("viewed"));
        assertNotNull(payroll.acknowledge(item).get("viewedAt"));
    }

    @Test
    void cannotReadOthersUnpublishedOrUnapprovedPayslips() {
        d.jdbc.update("UPDATE oa_hr_payroll_item SET user_id=? WHERE id=?", other, item);
        assertThrows(ApiException.class, () -> payroll.acknowledge(item));
        assertTrue(payroll.self().isEmpty());
        d.jdbc.update("UPDATE oa_hr_payroll_item SET user_id=? WHERE id=?", user, item);
        d.jdbc.update("UPDATE oa_hr_payroll SET published=0 WHERE id=?", batch);
        assertThrows(ApiException.class, () -> payroll.acknowledge(item));
        assertTrue(payroll.self().isEmpty());
        d.jdbc.update("UPDATE oa_hr_payroll SET published=1,status='draft' WHERE id=?", batch);
        assertThrows(ApiException.class, () -> payroll.acknowledge(item));
        assertTrue(payroll.self().isEmpty());
        assertEquals(0L, d.jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_history WHERE entity_type='payslip' AND entity_id=?", Long.class, item));
        assertEquals("todo", d.jdbc.queryForObject("SELECT status FROM oa_todo WHERE user_id=?", String.class, user));
    }

    @Test
    void moduleAccessIsRequiredForListingAndMarking() {
        d.jdbc.update("DELETE FROM sys_role_module WHERE role_id=99581");
        assertThrows(ApiException.class, () -> payroll.self());
        assertThrows(ApiException.class, () -> payroll.acknowledge(item));
    }
}
