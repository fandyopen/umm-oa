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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class MerchantController {

    private final JdbcTemplate jdbc;

    public MerchantController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/project/merchant/list")
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        String where = "WHERE 1 = 1";
        List<Object> args = new ArrayList<>();
        if (text(query.get("name"))) {
            where += " AND name LIKE ?";
            args.add("%" + query.get("name") + "%");
        }
        if (text(query.get("type"))) {
            where += " AND type = ?";
            args.add(query.get("type"));
        }
        int pageSize = (int) Math.min(Math.max(number(query.get("pageSize"), 10L), 1L), 200L);
        int pageNum = (int) Math.max(number(query.get("pageNum"), 1L), 1L);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM oa_merchant " + where,
                Long.class,
                args.toArray()
        );
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT * FROM oa_merchant
                %s ORDER BY id DESC LIMIT ? OFFSET ?
                """.formatted(where), pageArgs.toArray()));
        rows.forEach(row -> row.put("tenantId_", row.get("tenantId")));
        return ApiResult.page(rows, total == null ? 0 : total);
    }

    @GetMapping("/project/merchant/{id:\\d+}")
    public Map<String, Object> detail(@PathVariable Long id) {
        Map<String, Object> merchant = requireMerchant(id);
        merchant.put("sealMerchantAccounts", JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_merchant_account WHERE merchant_id = ? ORDER BY id", id)));
        merchant.put("sealMerchantUsers", JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_merchant_contact WHERE merchant_id = ? ORDER BY id", id)));
        return ApiResult.ok(merchant);
    }

    @PostMapping("/project/merchant")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String serial = text(body.get("serial"))
                ? String.valueOf(body.get("serial"))
                : "KS-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        jdbc.update("""
                INSERT INTO oa_merchant (
                    tenant_id, serial, name, type, address, tax_number, taxpayer_type,
                    remark, attachment, create_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                number(body.get("tenantId_"), 1L),
                serial,
                value(body.get("name"), "Unnamed merchant"),
                value(body.get("type"), ""),
                value(body.get("address"), ""),
                value(body.get("taxNumber"), ""),
                value(body.get("taxpayerType"), ""),
                value(body.get("remark"), ""),
                value(body.get("attachment"), ""),
                AuthContext.username());
        Long id = jdbc.queryForObject("SELECT id FROM oa_merchant WHERE serial = ?", Long.class, serial);
        saveChildren(id, body);
        return ApiResult.ok(id);
    }

    @PutMapping("/project/merchant")
    public Map<String, Object> update(@RequestBody Map<String, Object> body) {
        long id = number(body.get("id"), 0L);
        requireMerchant(id);
        jdbc.update("""
                UPDATE oa_merchant SET serial = ?, name = ?, type = ?, address = ?, tax_number = ?,
                    taxpayer_type = ?, remark = ?, attachment = ?, update_time = ? WHERE id = ?
                """,
                value(body.get("serial"), ""),
                value(body.get("name"), ""),
                value(body.get("type"), ""),
                value(body.get("address"), ""),
                value(body.get("taxNumber"), ""),
                value(body.get("taxpayerType"), ""),
                value(body.get("remark"), ""),
                value(body.get("attachment"), ""),
                Timestamp.valueOf(LocalDateTime.now()),
                id);
        jdbc.update("DELETE FROM oa_merchant_account WHERE merchant_id = ?", id);
        jdbc.update("DELETE FROM oa_merchant_contact WHERE merchant_id = ?", id);
        saveChildren(id, body);
        return ApiResult.ok(id);
    }

    @DeleteMapping("/project/merchant/{id:\\d+}")
    public Map<String, Object> delete(@PathVariable Long id) {
        jdbc.update("DELETE FROM oa_merchant_account WHERE merchant_id = ?", id);
        jdbc.update("DELETE FROM oa_merchant_contact WHERE merchant_id = ?", id);
        jdbc.update("DELETE FROM oa_merchant WHERE id = ?", id);
        return ApiResult.ok();
    }

    @DeleteMapping("/project/merchant/user/{id}")
    public Map<String, Object> deleteContact(@PathVariable Long id) {
        jdbc.update("DELETE FROM oa_merchant_contact WHERE id = ?", id);
        return ApiResult.ok();
    }

    @DeleteMapping("/project/merchant/account/{id}")
    public Map<String, Object> deleteAccount(@PathVariable Long id) {
        jdbc.update("DELETE FROM oa_merchant_account WHERE id = ?", id);
        return ApiResult.ok();
    }

    @GetMapping("/project/merchant/account/query")
    public Map<String, Object> account(@RequestParam Map<String, String> query) {
        String where = "WHERE 1 = 1";
        List<Object> args = new ArrayList<>();
        if (text(query.get("account"))) {
            where += " AND account LIKE ?";
            args.add("%" + query.get("account") + "%");
        }
        if (text(query.get("merchantId"))) {
            where += " AND merchant_id = ?";
            args.add(number(query.get("merchantId"), 0L));
        }
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT * FROM oa_merchant_account %s ORDER BY id DESC
                """.formatted(where), args.toArray()));
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    private void saveChildren(Long merchantId, Map<String, Object> body) {
        if (body.get("sealMerchantUsers") instanceof List<?> contacts) {
            for (Object raw : contacts) {
                if (!(raw instanceof Map<?, ?> contact)) {
                    continue;
                }
                jdbc.update("""
                        INSERT INTO oa_merchant_contact (
                            merchant_id, name, phone, email, position, remark
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        merchantId,
                        value(contact.get("name"), ""),
                        value(contact.get("phone"), ""),
                        value(contact.get("email"), ""),
                        value(contact.get("position"), ""),
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

    private Map<String, Object> requireMerchant(Long id) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_merchant WHERE id = ?", id));
        if (rows.isEmpty()) {
            throw new ApiException(404, "Merchant not found");
        }
        return rows.get(0);
    }

    private long number(Object value, long defaultValue) {
        try {
            return value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private boolean text(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private String value(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}
