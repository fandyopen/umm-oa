package com.seal.oa.oa;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.system.DataScopeService;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
public class MerchantController {

    private static final long CUSTOMER_MODULE_ID = 340352L;

    private final JdbcTemplate jdbc;
    private final DataScopeService dataScopeService;

    public MerchantController(JdbcTemplate jdbc, DataScopeService dataScopeService) {
        this.jdbc = jdbc;
        this.dataScopeService = dataScopeService;
    }

    @GetMapping("/project/merchant/list")
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "access")
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        List<Map<String, Object>> rows = filteredCustomers(query);
        int pageSize = Math.min(Math.max(integer(query.get("pageSize"), 10), 1), 200);
        int pageNum = Math.max(integer(query.get("pageNum"), 1), 1);
        int from = Math.min((pageNum - 1) * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        return ApiResult.page(rows.subList(from, to), rows.size());
    }

    @GetMapping("/project/merchant/export")
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "export")
    public Map<String, Object> export(@RequestParam Map<String, String> query) {
        return ApiResult.ok(filteredCustomers(query));
    }

    @PostMapping("/project/merchant/import")
    @Transactional
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "add")
    public Map<String, Object> importCustomers(@RequestBody Map<String, Object> body) {
        if (!(body.get("rows") instanceof List<?> rows) || rows.isEmpty()) {
            throw new ApiException(400, "导入文件没有可用数据");
        }
        if (rows.size() > 500) {
            throw new ApiException(400, "单次最多导入 500 条客户档案");
        }

        int imported = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Object raw = rows.get(index);
            Map<String, Object> row = new LinkedHashMap<>();
            if (raw instanceof Map<?, ?> values) {
                values.forEach((key, value) -> {
                    if (key instanceof String name) {
                        row.put(name, value);
                    }
                });
            }
            String name = value(row.get("name"), "").trim();
            String industry = value(row.get("industry"), "").trim();
            String reason = name.isBlank() ? "公司名称不能为空"
                    : industry.isBlank() ? "行业不能为空" : "";
            if (!reason.isBlank()) {
                errors.add(importError(index + 2, name, reason));
                continue;
            }
            try {
                create(row);
                imported++;
            } catch (ApiException exception) {
                errors.add(importError(index + 2, name, exception.getMessage()));
            }
        }
        return ApiResult.ok(Map.of(
                "imported", imported,
                "skipped", errors.size(),
                "errors", errors
        ));
    }

    private Map<String, Object> importError(int row, String name, String reason) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("row", row);
        error.put("name", name);
        error.put("reason", reason);
        return error;
    }

    private List<Map<String, Object>> filteredCustomers(Map<String, String> query) {
        StringBuilder where = new StringBuilder("WHERE m.status = '0'");
        List<Object> args = new ArrayList<>();
        appendContains(where, args, query, "name", "m.name LIKE ?");
        appendEquals(where, args, query, "industry", "m.industry = ?");
        appendEquals(where, args, query, "companyScale", "m.company_scale = ?");
        appendEquals(where, args, query, "region", "m.region = ?");
        appendEquals(where, args, query, "enterpriseNature", "m.enterprise_nature = ?");
        appendEquals(where, args, query, "customerSource", "m.customer_source = ?");
        appendEquals(where, args, query, "cooperationStatus", "m.cooperation_status = ?");
        appendEquals(where, args, query, "ownerUserId", "m.owner_user_id = ?");
        appendEquals(where, args, query, "publicPool", "m.public_pool = ?");

        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT m.*, d.dept_name AS owner_dept_name, u.dept_id AS owner_dept_id,
                       (SELECT COUNT(*) FROM oa_merchant_contact c WHERE c.merchant_id = m.id) AS contact_count,
                       (SELECT COUNT(*) FROM oa_merchant_collaborator co WHERE co.merchant_id = m.id) AS collaborator_count
                FROM oa_merchant m
                LEFT JOIN sys_user u ON u.user_id = m.owner_user_id
                LEFT JOIN sys_dept d ON d.dept_id = u.dept_id
                %s
                ORDER BY m.create_time DESC, m.id DESC
                """.formatted(where), args.toArray()));
        if (!"1".equals(query.get("publicPool"))) {
            rows = dataScopeService.filterRows(
                    AuthContext.userId(),
                    rows,
                    List.of("ownerDeptId"),
                    List.of("ownerUserId", "ownerUserName", "createBy")
            );
        }
        rows.forEach(this::decorateCustomer);
        return rows;
    }

    @GetMapping("/project/merchant/options")
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "access")
    public Map<String, Object> options(@RequestParam Map<String, String> query) {
        StringBuilder where = new StringBuilder("WHERE m.status = '0' AND m.public_pool = '0'");
        List<Object> args = new ArrayList<>();
        appendContains(where, args, query, "name", "m.name LIKE ?");
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT m.id, m.name, m.serial, m.industry, m.cooperation_status,
                       m.owner_user_id, m.owner_user_name, m.create_by,
                       u.dept_id AS owner_dept_id
                FROM oa_merchant m
                LEFT JOIN sys_user u ON u.user_id = m.owner_user_id
                %s ORDER BY m.name
                """.formatted(where), args.toArray()));
        return ApiResult.ok(dataScopeService.filterRows(
                AuthContext.userId(),
                rows,
                List.of("ownerDeptId"),
                List.of("ownerUserId", "ownerUserName", "createBy")
        ));
    }

    @GetMapping("/project/merchant/owner/options")
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "access")
    public Map<String, Object> ownerOptions(@RequestParam Map<String, String> query) {
        StringBuilder where = new StringBuilder("WHERE u.del_flag = '0' AND u.status = '0'");
        List<Object> args = new ArrayList<>();
        if (text(query.get("keyword"))) {
            where.append(" AND (u.nick_name LIKE ? OR u.user_name LIKE ?)");
            args.add("%" + query.get("keyword") + "%");
            args.add("%" + query.get("keyword") + "%");
        }
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT u.user_id, u.user_name, u.nick_name, u.dept_id, d.dept_name
                FROM sys_user u LEFT JOIN sys_dept d ON d.dept_id = u.dept_id
                %s ORDER BY d.order_num, u.nick_name
                """.formatted(where), args.toArray()));
        return ApiResult.ok(dataScopeService.filterRows(
                AuthContext.userId(),
                rows,
                List.of("deptId"),
                List.of("userId", "userName", "nickName")
        ));
    }

    @GetMapping("/project/merchant/{id:\\d+}")
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "access")
    public Map<String, Object> detail(@PathVariable Long id) {
        Map<String, Object> merchant = requireAccessibleMerchant(id);
        decorateCustomer(merchant);
        merchant.put("sealMerchantAccounts", JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_merchant_account WHERE merchant_id = ? ORDER BY id", id)));
        merchant.put("sealMerchantUsers", JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT id, merchant_id, name, phone, wechat, email, position,
                       decision_role, primary_contact, remark
                FROM oa_merchant_contact WHERE merchant_id = ? ORDER BY primary_contact DESC, id
                """, id)));
        merchant.put("collaborators", JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT c.user_id, c.user_name, u.nick_name
                FROM oa_merchant_collaborator c
                LEFT JOIN sys_user u ON u.user_id = c.user_id
                WHERE c.merchant_id = ? ORDER BY c.id
                """, id)));
        merchant.put("followups", followups(id));
        return ApiResult.ok(merchant);
    }

    @PostMapping("/project/merchant")
    @Transactional
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "add")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        validateDuplicate(body, 0L);
        String serial = defaultString(body.get("serial"), "KH-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" +
                UUID.randomUUID().toString().substring(0, 8));
        long ownerUserId = ownerId(body);
        requireOwnerAccess(ownerUserId);
        jdbc.update("""
                INSERT INTO oa_merchant (
                    tenant_id, serial, name, type, address, tax_number, taxpayer_type,
                    industry, company_scale, region, enterprise_nature, customer_source,
                    cooperation_status, owner_user_id, owner_user_name, public_pool, assigned_at,
                    last_followup_time, contract_expire_date, last_payment_date,
                    cooperation_positions, referral_count, hire_count, contract_amount,
                    received_amount, remark, attachment, create_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                1L,
                serial,
                value(body.get("name"), "未命名客户"),
                value(body.get("type"), "客户"),
                value(body.get("address"), ""),
                value(body.get("taxNumber"), ""),
                value(body.get("taxpayerType"), ""),
                value(body.get("industry"), ""),
                value(body.get("companyScale"), ""),
                value(body.get("region"), ""),
                value(body.get("enterpriseNature"), ""),
                value(body.get("customerSource"), ""),
                defaultString(body.get("cooperationStatus"), "潜在客户"),
                ownerUserId,
                nickName(ownerUserId),
                defaultString(body.get("publicPool"), "0"),
                Timestamp.valueOf(LocalDateTime.now()),
                dateTime(body.get("lastFollowupTime")),
                sqlDate(body.get("contractExpireDate")),
                sqlDate(body.get("lastPaymentDate")),
                number(body.get("cooperationPositions"), 0L),
                number(body.get("referralCount"), 0L),
                number(body.get("hireCount"), 0L),
                decimal(body.get("contractAmount")),
                decimal(body.get("receivedAmount")),
                value(body.get("remark"), ""),
                value(body.get("attachment"), ""),
                AuthContext.username());
        Long id = jdbc.queryForObject("SELECT id FROM oa_merchant WHERE serial = ?", Long.class, serial);
        saveChildren(id, body);
        saveCollaborators(id, body);
        return ApiResult.ok(id);
    }

    @PutMapping("/project/merchant")
    @Transactional
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "edit")
    public Map<String, Object> update(@RequestBody Map<String, Object> body) {
        long id = number(body.get("id"), 0L);
        requireAccessibleMerchant(id);
        validateDuplicate(body, id);
        long ownerUserId = ownerId(body);
        requireOwnerAccess(ownerUserId);
        jdbc.update("""
                UPDATE oa_merchant SET serial = ?, name = ?, type = ?, address = ?, tax_number = ?,
                    taxpayer_type = ?, industry = ?, company_scale = ?, region = ?,
                    enterprise_nature = ?, customer_source = ?, cooperation_status = ?,
                    owner_user_id = ?, owner_user_name = ?, public_pool = ?,
                    last_followup_time = ?, contract_expire_date = ?, last_payment_date = ?,
                    cooperation_positions = ?, referral_count = ?, hire_count = ?,
                    contract_amount = ?, received_amount = ?, remark = ?, attachment = ?,
                    update_time = ? WHERE id = ?
                """,
                value(body.get("serial"), ""),
                value(body.get("name"), ""),
                value(body.get("type"), "客户"),
                value(body.get("address"), ""),
                value(body.get("taxNumber"), ""),
                value(body.get("taxpayerType"), ""),
                value(body.get("industry"), ""),
                value(body.get("companyScale"), ""),
                value(body.get("region"), ""),
                value(body.get("enterpriseNature"), ""),
                value(body.get("customerSource"), ""),
                defaultString(body.get("cooperationStatus"), "潜在客户"),
                ownerUserId,
                nickName(ownerUserId),
                defaultString(body.get("publicPool"), "0"),
                dateTime(body.get("lastFollowupTime")),
                sqlDate(body.get("contractExpireDate")),
                sqlDate(body.get("lastPaymentDate")),
                number(body.get("cooperationPositions"), 0L),
                number(body.get("referralCount"), 0L),
                number(body.get("hireCount"), 0L),
                decimal(body.get("contractAmount")),
                decimal(body.get("receivedAmount")),
                value(body.get("remark"), ""),
                value(body.get("attachment"), ""),
                Timestamp.valueOf(LocalDateTime.now()),
                id);
        jdbc.update("DELETE FROM oa_merchant_account WHERE merchant_id = ?", id);
        jdbc.update("DELETE FROM oa_merchant_contact WHERE merchant_id = ?", id);
        jdbc.update("DELETE FROM oa_merchant_collaborator WHERE merchant_id = ?", id);
        saveChildren(id, body);
        saveCollaborators(id, body);
        return ApiResult.ok(id);
    }

    @DeleteMapping("/project/merchant/{id:\\d+}")
    @Transactional
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "remove")
    public Map<String, Object> delete(@PathVariable Long id) {
        Map<String, Object> customer = requireAccessibleMerchant(id);
        Long followupCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM oa_customer_followup WHERE merchant_id = ?",
                Long.class,
                id);
        Long opportunityCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz_record WHERE payload_json LIKE ?",
                Long.class,
                "%\"customerId\":" + id + "%");
        boolean draft = Set.of("草稿", "潜在客户")
                .contains(String.valueOf(customer.get("cooperationStatus")));
        if (!draft || (followupCount != null && followupCount > 0L)
                || (opportunityCount != null && opportunityCount > 0L)) {
            jdbc.update("UPDATE oa_merchant SET status = '1', update_time = CURRENT_TIMESTAMP WHERE id = ?", id);
            return ApiResult.ok(Map.of("deleted", false, "disabled", true));
        }
        jdbc.update("DELETE FROM oa_merchant_account WHERE merchant_id = ?", id);
        jdbc.update("DELETE FROM oa_merchant_contact WHERE merchant_id = ?", id);
        jdbc.update("DELETE FROM oa_merchant_collaborator WHERE merchant_id = ?", id);
        jdbc.update("DELETE FROM oa_merchant WHERE id = ?", id);
        return ApiResult.ok(Map.of("deleted", true, "disabled", false));
    }

    @GetMapping("/project/merchant/{id:\\d+}/followups")
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "access")
    public Map<String, Object> followupList(@PathVariable Long id) {
        requireAccessibleMerchant(id);
        return ApiResult.ok(followups(id));
    }

    @PostMapping("/project/merchant/{id:\\d+}/followups")
    @Transactional
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "track")
    public Map<String, Object> addFollowup(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireAccessibleMerchant(id);
        Timestamp followupTime = dateTime(body.get("followupTime"));
        if (followupTime == null) {
            followupTime = Timestamp.valueOf(LocalDateTime.now());
        }
        jdbc.update("""
                INSERT INTO oa_customer_followup (
                    tenant_id, merchant_id, followup_time, method, content, next_plan,
                    next_followup_time, owner_user_id, owner_user_name, create_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                1L,
                id,
                followupTime,
                value(body.get("method"), "电话"),
                value(body.get("content"), ""),
                value(body.get("nextPlan"), ""),
                dateTime(body.get("nextFollowupTime")),
                AuthContext.userId(),
                nickName(AuthContext.userId()),
                AuthContext.username());
        jdbc.update("UPDATE oa_merchant SET last_followup_time = ? WHERE id = ?", followupTime, id);
        return ApiResult.ok();
    }

    @DeleteMapping("/project/merchant/followup/{id}")
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "track")
    public Map<String, Object> deleteFollowup(@PathVariable Long id) {
        List<Long> merchantIds = jdbc.queryForList("""
                SELECT merchant_id FROM oa_customer_followup WHERE id = ?
                """, Long.class, id);
        if (merchantIds.isEmpty()) {
            throw new ApiException(404, "跟进记录不存在");
        }
        requireAccessibleMerchant(merchantIds.get(0));
        jdbc.update("DELETE FROM oa_customer_followup WHERE id = ?", id);
        return ApiResult.ok();
    }

    @PostMapping("/project/merchant/{id:\\d+}/assign")
    @Transactional
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "edit")
    public Map<String, Object> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireAccessibleMerchant(id);
        long ownerUserId = ownerId(body);
        requireOwnerAccess(ownerUserId);
        jdbc.update("""
                UPDATE oa_merchant SET owner_user_id = ?, owner_user_name = ?,
                    public_pool = '0', assigned_at = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, ownerUserId, nickName(ownerUserId), id);
        jdbc.update("DELETE FROM oa_merchant_collaborator WHERE merchant_id = ?", id);
        saveCollaborators(id, body);
        return ApiResult.ok();
    }

    @PostMapping("/project/merchant/{id:\\d+}/release")
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "edit")
    public Map<String, Object> release(@PathVariable Long id) {
        requireAccessibleMerchant(id);
        jdbc.update("""
                UPDATE oa_merchant SET owner_user_id = NULL, owner_user_name = '',
                    public_pool = '1', assigned_at = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, id);
        return ApiResult.ok();
    }

    @PostMapping("/project/merchant/{id:\\d+}/claim")
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "access")
    public Map<String, Object> claim(@PathVariable Long id) {
        Map<String, Object> customer = requireAccessibleMerchant(id);
        if (!"1".equals(String.valueOf(customer.get("publicPool")))) {
            throw new ApiException(400, "该客户不在公海");
        }
        jdbc.update("""
                UPDATE oa_merchant SET owner_user_id = ?, owner_user_name = ?,
                    public_pool = '0', assigned_at = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, AuthContext.userId(), nickName(AuthContext.userId()), id);
        return ApiResult.ok();
    }

    @PostMapping("/project/merchant/handover")
    @Transactional
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "edit")
    public Map<String, Object> handover(@RequestBody Map<String, Object> body) {
        long fromUserId = number(body.get("fromUserId"), 0L);
        long toUserId = number(body.get("toUserId"), 0L);
        if (fromUserId <= 0L || toUserId <= 0L || fromUserId == toUserId) {
            throw new ApiException(400, "请选择不同的原负责人和新负责人");
        }
        requireOwnerAccess(fromUserId);
        requireOwnerAccess(toUserId);
        int count = jdbc.update("""
                UPDATE oa_merchant SET owner_user_id = ?, owner_user_name = ?,
                    assigned_at = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
                WHERE owner_user_id = ?
                """, toUserId, nickName(toUserId), fromUserId);
        return ApiResult.ok(count);
    }

    @DeleteMapping("/project/merchant/user/{id}")
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "edit")
    public Map<String, Object> deleteContact(@PathVariable Long id) {
        List<Long> merchantIds = jdbc.queryForList("""
                SELECT merchant_id FROM oa_merchant_contact WHERE id = ?
                """, Long.class, id);
        if (merchantIds.isEmpty()) {
            throw new ApiException(404, "联系人不存在");
        }
        requireAccessibleMerchant(merchantIds.get(0));
        jdbc.update("DELETE FROM oa_merchant_contact WHERE id = ?", id);
        return ApiResult.ok();
    }

    @DeleteMapping("/project/merchant/account/{id}")
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "edit")
    public Map<String, Object> deleteAccount(@PathVariable Long id) {
        List<Long> merchantIds = jdbc.queryForList("""
                SELECT merchant_id FROM oa_merchant_account WHERE id = ?
                """, Long.class, id);
        if (merchantIds.isEmpty()) {
            throw new ApiException(404, "银行账户不存在");
        }
        requireAccessibleMerchant(merchantIds.get(0));
        jdbc.update("DELETE FROM oa_merchant_account WHERE id = ?", id);
        return ApiResult.ok();
    }

    @GetMapping("/project/merchant/account/query")
    @RequiresModuleAction(moduleId = CUSTOMER_MODULE_ID, action = "access")
    public Map<String, Object> account(@RequestParam Map<String, String> query) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        appendContains(where, args, query, "account", "account LIKE ?");
        appendEquals(where, args, query, "merchantId", "merchant_id = ?");
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT a.*, m.owner_user_id, m.owner_user_name, m.create_by,
                       u.dept_id AS owner_dept_id
                FROM oa_merchant_account a
                JOIN oa_merchant m ON m.id = a.merchant_id
                LEFT JOIN sys_user u ON u.user_id = m.owner_user_id
                WHERE 1 = 1
                %s ORDER BY a.id DESC
                """.formatted(where), args.toArray()));
        rows = dataScopeService.filterRows(
                AuthContext.userId(),
                rows,
                List.of("ownerDeptId"),
                List.of("ownerUserId", "ownerUserName", "createBy")
        );
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    private List<Map<String, Object>> followups(Long merchantId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT * FROM oa_customer_followup
                WHERE merchant_id = ? ORDER BY followup_time DESC, id DESC
                """, merchantId));
        rows.forEach(row -> {
            row.put("followupTime", formatted(row.get("followupTime")));
            row.put("nextFollowupTime", formatted(row.get("nextFollowupTime")));
            row.put("createTime", formatted(row.get("createTime")));
        });
        return rows;
    }

    private void saveChildren(Long merchantId, Map<String, Object> body) {
        if (body.get("sealMerchantUsers") instanceof List<?> contacts) {
            for (Object raw : contacts) {
                if (!(raw instanceof Map<?, ?> contact)) {
                    continue;
                }
                jdbc.update("""
                        INSERT INTO oa_merchant_contact (
                            merchant_id, name, phone, wechat, email, position,
                            decision_role, primary_contact, remark
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        merchantId,
                        value(contact.get("name"), ""),
                        value(contact.get("phone"), ""),
                        value(contact.get("wechat"), ""),
                        value(contact.get("email"), ""),
                        value(contact.get("position"), ""),
                        value(contact.get("decisionRole"), ""),
                        defaultString(contact.get("primaryContact"), "0"),
                        value(contact.get("remark"), ""));
            }
        }
        if (body.get("sealMerchantAccounts") instanceof List<?> accounts) {
            for (Object raw : accounts) {
                if (!(raw instanceof Map<?, ?> account)) {
                    continue;
                }
                jdbc.update("""
                        INSERT INTO oa_merchant_account (
                            merchant_id, account, account_name, bank, remark
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                        merchantId,
                        value(account.get("account"), ""),
                        value(account.get("accountName"), ""),
                        value(account.get("bank"), ""),
                        value(account.get("remark"), ""));
            }
        }
    }

    private void saveCollaborators(Long merchantId, Map<String, Object> body) {
        if (!(body.get("collaboratorIds") instanceof List<?> values)) {
            return;
        }
        for (Object raw : values) {
            long userId = number(raw, 0L);
            if (userId > 0L) {
                jdbc.update("""
                        INSERT INTO oa_merchant_collaborator (merchant_id, user_id, user_name)
                        VALUES (?, ?, ?)
                        """, merchantId, userId, nickName(userId));
            }
        }
    }

    private void validateDuplicate(Map<String, Object> body, long currentId) {
        String name = value(body.get("name"), "").trim();
        String taxNumber = value(body.get("taxNumber"), "").trim();
        List<Object> args = new ArrayList<>();
        args.add(currentId);
        args.add(name);
        StringBuilder where = new StringBuilder(
                "WHERE id <> ? AND LOWER(TRIM(name)) = LOWER(?)");
        if (text(taxNumber)) {
            where.append(" OR (tax_number IS NOT NULL AND tax_number <> '' AND tax_number = ?)");
            args.add(taxNumber);
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM oa_merchant " + where,
                Long.class,
                args.toArray()
        );
        if (count != null && count > 0L) {
            throw new ApiException(409, "客户名称或税号已存在，请勿重复建档");
        }
    }

    private void decorateCustomer(Map<String, Object> row) {
        row.put("publicPool", "1".equals(String.valueOf(row.get("publicPool"))) ? "1" : "0");
        row.put("createTime", formatted(row.get("createTime")));
        row.put("assignedAt", formatted(row.get("assignedAt")));
        row.put("lastFollowupTime", formatted(row.get("lastFollowupTime")));
        row.put("contractExpireDate", dateString(row.get("contractExpireDate")));
        row.put("lastPaymentDate", dateString(row.get("lastPaymentDate")));
        row.put("tenantId_", row.get("tenantId"));
        Map<String, Object> dept = new LinkedHashMap<>();
        dept.put("deptId", row.get("ownerDeptId"));
        dept.put("deptName", row.get("ownerDeptName"));
        row.put("ownerDept", dept);
        row.put("contactCount", number(row.get("contactCount"), 0L));
        row.put("collaboratorCount", number(row.get("collaboratorCount"), 0L));
    }

    private Map<String, Object> requireMerchant(Long id) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT m.*, d.dept_name AS owner_dept_name, u.dept_id AS owner_dept_id
                FROM oa_merchant m
                LEFT JOIN sys_user u ON u.user_id = m.owner_user_id
                LEFT JOIN sys_dept d ON d.dept_id = u.dept_id
                WHERE m.id = ?
                """, id));
        if (rows.isEmpty()) {
            throw new ApiException(404, "客户不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> requireAccessibleMerchant(Long id) {
        Map<String, Object> merchant = requireMerchant(id);
        if (!"1".equals(String.valueOf(merchant.get("publicPool")))
                && !dataScopeService.canAccess(
                        AuthContext.userId(),
                        merchant.get("ownerDeptId"),
                        merchant.get("ownerUserId"),
                        merchant.get("ownerUserName"),
                        merchant.get("createBy")
                )) {
            throw new ApiException(403, "无权访问该客户");
        }
        return merchant;
    }

    private void requireOwnerAccess(Long userId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT user_id, user_name, nick_name, dept_id
                FROM sys_user
                WHERE user_id = ? AND del_flag = '0' AND status = '0'
                """, userId));
        if (rows.isEmpty()) {
            throw new ApiException(400, "客户负责人不存在或已停用");
        }
        Map<String, Object> owner = rows.get(0);
        if (!dataScopeService.canAccess(
                AuthContext.userId(),
                owner.get("deptId"),
                owner.get("userId"),
                owner.get("userName"),
                owner.get("nickName")
        )) {
            throw new ApiException(403, "无权将客户分配给该负责人");
        }
    }

    private long ownerId(Map<String, Object> body) {
        long ownerId = number(body.get("ownerUserId"), 0L);
        return ownerId > 0L ? ownerId : AuthContext.userId();
    }

    private String nickName(Long userId) {
        if (userId == null || userId <= 0L) {
            return "";
        }
        List<String> names = jdbc.queryForList(
                "SELECT nick_name FROM sys_user WHERE user_id = ?",
                String.class,
                userId
        );
        return names.isEmpty() ? "" : names.get(0);
    }

    private void appendContains(
            StringBuilder where,
            List<Object> args,
            Map<String, String> query,
            String key,
            String condition
    ) {
        if (text(query.get(key))) {
            where.append(" AND ").append(condition);
            args.add("%" + query.get(key) + "%");
        }
    }

    private void appendEquals(
            StringBuilder where,
            List<Object> args,
            Map<String, String> query,
            String key,
            String condition
    ) {
        if (text(query.get(key))) {
            where.append(" AND ").append(condition);
            args.add(query.get(key));
        }
    }

    private long number(Object value, long defaultValue) {
        try {
            if (value instanceof Number number) {
                return number.longValue();
            }
            String text = value == null ? "" : String.valueOf(value).trim();
            return text.isEmpty() ? defaultValue : Long.parseLong(text);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private BigDecimal decimal(Object value) {
        try {
            String text = value == null ? "" : String.valueOf(value).trim();
            return text.isEmpty() ? BigDecimal.ZERO : new BigDecimal(text);
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private Timestamp dateTime(Object value) {
        if (value == null || !text(value)) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp;
        }
        String text = String.valueOf(value).replace('T', ' ');
        try {
            return Timestamp.valueOf(text.length() > 19 ? text.substring(0, 19) : text);
        } catch (Exception ignored) {
            try {
                return Timestamp.valueOf(LocalDate.parse(text.substring(0, 10)).atStartOfDay());
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private Date sqlDate(Object value) {
        if (value == null || !text(value)) {
            return null;
        }
        try {
            return Date.valueOf(String.valueOf(value).substring(0, 10));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String formatted(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return value == null ? "" : String.valueOf(value);
    }

    private String dateString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean text(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private String value(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private String defaultString(Object value, String defaultValue) {
        return text(value) ? String.valueOf(value) : defaultValue;
    }

    private int integer(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}
