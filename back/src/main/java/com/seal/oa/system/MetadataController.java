package com.seal.oa.system;

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

import java.util.List;
import java.util.Map;

@RestController
public class MetadataController {

    private final JdbcTemplate jdbc;

    public MetadataController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/system/dict/data/type/{dictType}")
    public Map<String, Object> dictionaryValues(@PathVariable String dictType) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT dict_code, dict_sort, dict_label, dict_value, dict_type,
                       css_class, list_class, is_default, status
                FROM sys_dict_data
                WHERE dict_type = ? AND status = '0'
                ORDER BY dict_sort
                """, dictType));
        return ApiResult.ok(rows);
    }

    @GetMapping("/system/dict/data/list")
    public Map<String, Object> dictionaryData(@RequestParam Map<String, String> query) {
        String dictType = query.get("dictType");
        List<Map<String, Object>> rows = dictType == null || dictType.isBlank()
                ? jdbc.queryForList("SELECT * FROM sys_dict_data ORDER BY dict_type, dict_sort")
                : jdbc.queryForList(
                        "SELECT * FROM sys_dict_data WHERE dict_type = ? ORDER BY dict_sort",
                        dictType);
        return ApiResult.page(JdbcMaps.camelCase(rows), rows.size());
    }

    @GetMapping("/system/dict/data/{dictCode}")
    public Map<String, Object> dictionaryDatum(@PathVariable Long dictCode) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM sys_dict_data WHERE dict_code = ?", dictCode));
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/system/dict/data")
    public Map<String, Object> addDictionaryData(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                INSERT INTO sys_dict_data (
                    dict_sort, dict_label, dict_value, dict_type, css_class,
                    list_class, is_default, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                number(body.get("dictSort"), 0L),
                string(body.get("dictLabel")),
                string(body.get("dictValue")),
                string(body.get("dictType")),
                string(body.get("cssClass")),
                string(body.get("listClass")),
                string(body.get("isDefault")),
                string(body.get("status"))
        );
        return ApiResult.ok();
    }

    @PutMapping("/system/dict/data")
    public Map<String, Object> updateDictionaryData(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                UPDATE sys_dict_data SET dict_sort = ?, dict_label = ?, dict_value = ?,
                    dict_type = ?, css_class = ?, list_class = ?, is_default = ?, status = ?
                WHERE dict_code = ?
                """,
                number(body.get("dictSort"), 0L),
                string(body.get("dictLabel")),
                string(body.get("dictValue")),
                string(body.get("dictType")),
                string(body.get("cssClass")),
                string(body.get("listClass")),
                string(body.get("isDefault")),
                string(body.get("status")),
                number(body.get("dictCode"), 0L)
        );
        return ApiResult.ok();
    }

    @DeleteMapping("/system/dict/data/{dictCode}")
    public Map<String, Object> deleteDictionaryData(@PathVariable Long dictCode) {
        jdbc.update("DELETE FROM sys_dict_data WHERE dict_code = ?", dictCode);
        return ApiResult.ok();
    }

    @GetMapping("/system/dict/type/list")
    public Map<String, Object> dictionaryTypes(@RequestParam Map<String, String> query) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT dict_id, dict_name, dict_type, status, create_time
                FROM sys_dict_type ORDER BY dict_id
                """));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/system/dict/type/optionselect")
    public Map<String, Object> dictionaryTypeOptions() {
        return ApiResult.ok(jdbc.queryForList("SELECT dict_id AS id, dict_name AS name, dict_type FROM sys_dict_type"));
    }

    @GetMapping("/system/dict/type/{dictId}")
    public Map<String, Object> dictionaryType(@PathVariable Long dictId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM sys_dict_type WHERE dict_id = ?", dictId));
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/system/dict/type")
    public Map<String, Object> addDictionaryType(@RequestBody Map<String, Object> body) {
        jdbc.update("INSERT INTO sys_dict_type (dict_name, dict_type, status) VALUES (?, ?, ?)",
                string(body.get("dictName")), string(body.get("dictType")), string(body.get("status")));
        return ApiResult.ok();
    }

    @PutMapping("/system/dict/type")
    public Map<String, Object> updateDictionaryType(@RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE sys_dict_type SET dict_name = ?, dict_type = ?, status = ? WHERE dict_id = ?",
                string(body.get("dictName")), string(body.get("dictType")), string(body.get("status")),
                number(body.get("dictId"), 0L));
        return ApiResult.ok();
    }

    @DeleteMapping("/system/dict/type/{dictId}")
    public Map<String, Object> deleteDictionaryType(@PathVariable Long dictId) {
        jdbc.update("DELETE FROM sys_dict_type WHERE dict_id = ?", dictId);
        return ApiResult.ok();
    }

    @GetMapping("/system/config/list")
    public Map<String, Object> configs() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT config_id, config_name, config_key, config_value, config_type, create_time, remark
                FROM sys_config ORDER BY config_id
                """));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/system/config/configKey/{configKey}")
    public Map<String, Object> configByKey(@PathVariable String configKey) {
        List<String> rows = jdbc.queryForList(
                "SELECT config_value FROM sys_config WHERE config_key = ?", String.class, configKey);
        return ApiResult.ok(rows.isEmpty() ? "" : rows.get(0));
    }

    @GetMapping("/system/config/{configId}")
    public Map<String, Object> config(@PathVariable Long configId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM sys_config WHERE config_id = ?", configId));
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/system/config")
    public Map<String, Object> addConfig(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                INSERT INTO sys_config (config_name, config_key, config_value, config_type, remark)
                VALUES (?, ?, ?, ?, ?)
                """,
                string(body.get("configName")), string(body.get("configKey")),
                string(body.get("configValue")), string(body.get("configType")),
                string(body.get("remark")));
        return ApiResult.ok();
    }

    @PutMapping("/system/config")
    public Map<String, Object> updateConfig(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                UPDATE sys_config SET config_name = ?, config_key = ?, config_value = ?,
                    config_type = ?, remark = ? WHERE config_id = ?
                """,
                string(body.get("configName")), string(body.get("configKey")),
                string(body.get("configValue")), string(body.get("configType")),
                string(body.get("remark")), number(body.get("configId"), 0L));
        return ApiResult.ok();
    }

    @DeleteMapping("/system/config/{configId}")
    public Map<String, Object> deleteConfig(@PathVariable Long configId) {
        jdbc.update("DELETE FROM sys_config WHERE config_id = ?", configId);
        return ApiResult.ok();
    }

    @GetMapping("/system/notice/list")
    public Map<String, Object> notices() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT notice_id, notice_title, notice_type, notice_content, status, create_by, create_time
                FROM sys_notice ORDER BY notice_id DESC
                """));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/system/notice/{noticeId}")
    public Map<String, Object> notice(@PathVariable Long noticeId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM sys_notice WHERE notice_id = ?", noticeId));
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping({"/system/notice", "/system/message"})
    public Map<String, Object> addNotice(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                INSERT INTO sys_notice (notice_title, notice_type, notice_content, status, create_by)
                VALUES (?, ?, ?, ?, ?)
                """,
                string(body.get("noticeTitle")), string(body.get("noticeType")),
                string(body.get("noticeContent")), string(body.get("status")), "admin");
        return ApiResult.ok();
    }

    @PutMapping("/system/notice")
    public Map<String, Object> updateNotice(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                UPDATE sys_notice SET notice_title = ?, notice_type = ?, notice_content = ?, status = ?
                WHERE notice_id = ?
                """,
                string(body.get("noticeTitle")), string(body.get("noticeType")),
                string(body.get("noticeContent")), string(body.get("status")),
                number(body.get("noticeId"), 0L));
        return ApiResult.ok();
    }

    @DeleteMapping("/system/notice/{noticeId}")
    public Map<String, Object> deleteNotice(@PathVariable Long noticeId) {
        jdbc.update("DELETE FROM sys_notice WHERE notice_id = ?", noticeId);
        return ApiResult.ok();
    }

    @GetMapping({"/system/post/list", "/system/post/list/all"})
    public Map<String, Object> posts() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT post_id, post_code, post_name, post_sort, status FROM sys_post ORDER BY post_sort
                """));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/system/post/{postId}")
    public Map<String, Object> post(@PathVariable Long postId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM sys_post WHERE post_id = ?", postId));
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/system/post")
    public Map<String, Object> addPost(@RequestBody Map<String, Object> body) {
        jdbc.update("INSERT INTO sys_post (post_code, post_name, post_sort, status) VALUES (?, ?, ?, ?)",
                string(body.get("postCode")), string(body.get("postName")),
                number(body.get("postSort"), 0L), string(body.get("status")));
        return ApiResult.ok();
    }

    @PutMapping("/system/post")
    public Map<String, Object> updatePost(@RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE sys_post SET post_code = ?, post_name = ?, post_sort = ?, status = ? WHERE post_id = ?",
                string(body.get("postCode")), string(body.get("postName")),
                number(body.get("postSort"), 0L), string(body.get("status")),
                number(body.get("postId"), 0L));
        return ApiResult.ok();
    }

    @DeleteMapping("/system/post/{postId}")
    public Map<String, Object> deletePost(@PathVariable Long postId) {
        jdbc.update("DELETE FROM sys_post WHERE post_id = ?", postId);
        return ApiResult.ok();
    }

    @GetMapping("/monitor/logininfor/list")
    public Map<String, Object> loginLogs() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT info_id, user_name, ipaddr, browser, os, status, msg, login_time
                FROM sys_logininfor ORDER BY info_id DESC
                """));
        return ApiResult.page(rows, rows.size());
    }

    @DeleteMapping({"/monitor/logininfor/{infoId}", "/monitor/logininfor/clean"})
    public Map<String, Object> deleteLoginLogs(@PathVariable(required = false) Long infoId) {
        if (infoId == null) {
            jdbc.update("DELETE FROM sys_logininfor");
        } else {
            jdbc.update("DELETE FROM sys_logininfor WHERE info_id = ?", infoId);
        }
        return ApiResult.ok();
    }

    @GetMapping("/monitor/operlog/list")
    public Map<String, Object> operationLogs() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT oper_id, title, business_type, method, request_method, oper_name,
                       oper_url, oper_ip, status, error_msg, oper_time
                FROM sys_oper_log ORDER BY oper_id DESC
                """));
        return ApiResult.page(rows, rows.size());
    }

    @DeleteMapping({"/monitor/operlog/{operId}", "/monitor/operlog/clean"})
    public Map<String, Object> deleteOperationLogs(@PathVariable(required = false) Long operId) {
        if (operId == null) {
            jdbc.update("DELETE FROM sys_oper_log");
        } else {
            jdbc.update("DELETE FROM sys_oper_log WHERE oper_id = ?", operId);
        }
        return ApiResult.ok();
    }

    private long number(Object value, long defaultValue) {
        try {
            return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
