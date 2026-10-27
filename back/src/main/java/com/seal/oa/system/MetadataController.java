package com.seal.oa.system;

import com.seal.oa.common.ApiResult;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
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
    @RequiresModuleAction(moduleId = 340314L)
    public Map<String, Object> dictionaryData(@RequestParam Map<String, String> query) {
        String where = "WHERE 1 = 1";
        List<Object> args = new java.util.ArrayList<>();
        if (StringUtils.hasText(query.get("dictType"))) {
            where += " AND dict_type = ?";
            args.add(query.get("dictType"));
        }
        if (StringUtils.hasText(query.get("dictLabel"))) {
            where += " AND dict_label LIKE ?";
            args.add("%" + query.get("dictLabel") + "%");
        }
        if (StringUtils.hasText(query.get("status"))) {
            where += " AND status = ?";
            args.add(query.get("status"));
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_data " + where,
                Long.class,
                args.toArray()
        );
        int pageNum = (int) Math.max(number(query.get("pageNum"), 1L), 1L);
        int pageSize = (int) Math.min(Math.max(number(query.get("pageSize"), 10L), 1L), 1000L);
        List<Object> pageArgs = new java.util.ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1L) * pageSize);
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM sys_dict_data " + where +
                        " ORDER BY dict_type, dict_sort LIMIT ? OFFSET ?",
                pageArgs.toArray()
        ));
        return ApiResult.page(rows, total == null ? 0 : total);
    }

    @GetMapping("/system/dict/data/{dictCode}")
    @RequiresModuleAction(moduleId = 340314L)
    public Map<String, Object> dictionaryDatum(@PathVariable Long dictCode) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM sys_dict_data WHERE dict_code = ?", dictCode));
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/system/dict/data")
    @RequiresModuleAction(moduleId = 340314L)
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
    @RequiresModuleAction(moduleId = 340314L)
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
    @RequiresModuleAction(moduleId = 340314L)
    public Map<String, Object> deleteDictionaryData(@PathVariable Long dictCode) {
        jdbc.update("DELETE FROM sys_dict_data WHERE dict_code = ?", dictCode);
        return ApiResult.ok();
    }

    @GetMapping("/system/dict/type/list")
    @RequiresModuleAction(moduleId = 340314L)
    public Map<String, Object> dictionaryTypes(@RequestParam Map<String, String> query) {
        String where = "WHERE 1 = 1";
        List<Object> args = new java.util.ArrayList<>();
        if (StringUtils.hasText(query.get("dictName"))) {
            where += " AND dict_name LIKE ?";
            args.add("%" + query.get("dictName") + "%");
        }
        if (StringUtils.hasText(query.get("dictType"))) {
            where += " AND dict_type LIKE ?";
            args.add("%" + query.get("dictType") + "%");
        }
        if (StringUtils.hasText(query.get("status"))) {
            where += " AND status = ?";
            args.add(query.get("status"));
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_type " + where,
                Long.class,
                args.toArray()
        );
        int pageNum = (int) Math.max(number(query.get("pageNum"), 1L), 1L);
        int pageSize = (int) Math.min(Math.max(number(query.get("pageSize"), 10L), 1L), 500L);
        List<Object> pageArgs = new java.util.ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1L) * pageSize);
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT dict_id, dict_name, dict_type, status, create_time, remark " +
                        "FROM sys_dict_type " + where + " ORDER BY dict_id LIMIT ? OFFSET ?",
                pageArgs.toArray()
        ));
        return ApiResult.page(rows, total == null ? 0 : total);
    }

    @GetMapping("/system/dict/type/optionselect")
    public Map<String, Object> dictionaryTypeOptions() {
        return ApiResult.ok(jdbc.queryForList("SELECT dict_id AS id, dict_name AS name, dict_type FROM sys_dict_type"));
    }

    @GetMapping("/system/dict/type/{dictId}")
    @RequiresModuleAction(moduleId = 340314L)
    public Map<String, Object> dictionaryType(@PathVariable Long dictId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM sys_dict_type WHERE dict_id = ?", dictId));
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/system/dict/type")
    @RequiresModuleAction(moduleId = 340314L)
    public Map<String, Object> addDictionaryType(@RequestBody Map<String, Object> body) {
        jdbc.update("INSERT INTO sys_dict_type (dict_name, dict_type, status, remark) VALUES (?, ?, ?, ?)",
                string(body.get("dictName")), string(body.get("dictType")),
                string(body.get("status")), string(body.get("remark")));
        return ApiResult.ok();
    }

    @PutMapping("/system/dict/type")
    @RequiresModuleAction(moduleId = 340314L)
    public Map<String, Object> updateDictionaryType(@RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE sys_dict_type SET dict_name = ?, dict_type = ?, status = ?, remark = ? WHERE dict_id = ?",
                string(body.get("dictName")), string(body.get("dictType")), string(body.get("status")),
                string(body.get("remark")),
                number(body.get("dictId"), 0L));
        return ApiResult.ok();
    }

    @DeleteMapping("/system/dict/type/{dictId}")
    @RequiresModuleAction(moduleId = 340314L)
    public Map<String, Object> deleteDictionaryType(@PathVariable Long dictId) {
        jdbc.update("DELETE FROM sys_dict_type WHERE dict_id = ?", dictId);
        return ApiResult.ok();
    }

    @GetMapping("/system/config/list")
    @RequiresModuleAction(moduleId = 340315L)
    public Map<String, Object> configs(@RequestParam Map<String, String> query) {
        String where = "WHERE 1 = 1";
        List<Object> args = new java.util.ArrayList<>();
        if (StringUtils.hasText(query.get("configName"))) {
            where += " AND config_name LIKE ?";
            args.add("%" + query.get("configName") + "%");
        }
        if (StringUtils.hasText(query.get("configKey"))) {
            where += " AND config_key LIKE ?";
            args.add("%" + query.get("configKey") + "%");
        }
        if (StringUtils.hasText(query.get("configType"))) {
            where += " AND config_type = ?";
            args.add(query.get("configType"));
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_config " + where,
                Long.class,
                args.toArray()
        );
        int pageNum = (int) Math.max(number(query.get("pageNum"), 1L), 1L);
        int pageSize = (int) Math.min(Math.max(number(query.get("pageSize"), 10L), 1L), 500L);
        List<Object> pageArgs = new java.util.ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1L) * pageSize);
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT config_id, config_name, config_key, config_value, config_type, " +
                        "create_time, remark FROM sys_config " + where +
                        " ORDER BY config_id LIMIT ? OFFSET ?",
                pageArgs.toArray()
        ));
        return ApiResult.page(rows, total == null ? 0 : total);
    }

    @GetMapping("/system/config/configKey/{configKey}")
    public Map<String, Object> configByKey(@PathVariable String configKey) {
        List<String> rows = jdbc.queryForList(
                "SELECT config_value FROM sys_config WHERE config_key = ?", String.class, configKey);
        return ApiResult.ok(rows.isEmpty() ? "" : rows.get(0));
    }

    @GetMapping("/system/config/{configId}")
    @RequiresModuleAction(moduleId = 340315L)
    public Map<String, Object> config(@PathVariable Long configId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM sys_config WHERE config_id = ?", configId));
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/system/config")
    @RequiresModuleAction(moduleId = 340315L)
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
    @RequiresModuleAction(moduleId = 340315L)
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
    @RequiresModuleAction(moduleId = 340315L)
    public Map<String, Object> deleteConfig(@PathVariable Long configId) {
        jdbc.update("DELETE FROM sys_config WHERE config_id = ?", configId);
        return ApiResult.ok();
    }

    @GetMapping("/system/notice/list")
    @RequiresModuleAction(moduleId = 340316L)
    public Map<String, Object> notices() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT notice_id, notice_title, notice_type, notice_content, status, create_by, create_time
                FROM sys_notice ORDER BY notice_id DESC
                """));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/system/notice/{noticeId}")
    @RequiresModuleAction(moduleId = 340316L)
    public Map<String, Object> notice(@PathVariable Long noticeId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM sys_notice WHERE notice_id = ?", noticeId));
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping({"/system/notice", "/system/message"})
    @RequiresModuleAction(moduleId = 340316L)
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
    @RequiresModuleAction(moduleId = 340316L)
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
    @RequiresModuleAction(moduleId = 340316L)
    public Map<String, Object> deleteNotice(@PathVariable Long noticeId) {
        jdbc.update("DELETE FROM sys_notice WHERE notice_id = ?", noticeId);
        return ApiResult.ok();
    }

    @GetMapping("/system/post/list")
    public Map<String, Object> posts() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT post_id, post_code, post_name, post_sort, status FROM sys_post ORDER BY post_sort
                """));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/system/post/list/all")
    public Map<String, Object> postsAll() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT post_id, post_code, post_name, post_sort, status
                FROM sys_post WHERE status = '0' ORDER BY post_sort
                """));
        return ApiResult.ok(rows);
    }

    @GetMapping("/system/post/{postId}")
    public Map<String, Object> post(@PathVariable Long postId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM sys_post WHERE post_id = ?", postId));
        return ApiResult.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/system/post")
    public Map<String, Object> addPost(@RequestBody Map<String, Object> body) {
        String postName = string(body.get("postName"));
        String postCode = StringUtils.hasText(string(body.get("postCode")))
                ? string(body.get("postCode"))
                : "POST_" + System.currentTimeMillis();
        Long nextSort = jdbc.queryForObject(
                "SELECT COALESCE(MAX(post_sort), -1) + 1 FROM sys_post",
                Long.class
        );
        jdbc.update("INSERT INTO sys_post (post_code, post_name, post_sort, status) VALUES (?, ?, ?, ?)",
                postCode,
                postName,
                number(body.get("postSort"), nextSort == null ? 0L : nextSort),
                StringUtils.hasText(string(body.get("status"))) ? string(body.get("status")) : "0");
        Long postId = jdbc.queryForObject("SELECT MAX(post_id) FROM sys_post", Long.class);
        return ApiResult.ok(postId);
    }

    @PutMapping("/system/post")
    public Map<String, Object> updatePost(@RequestBody Map<String, Object> body) {
        long postId = number(body.get("postId"), 0L);
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM sys_post WHERE post_id = ?",
                postId
        ));
        if (rows.isEmpty()) {
            throw new com.seal.oa.common.ApiException(404, "Post not found");
        }
        Map<String, Object> existing = rows.get(0);
        String postCode = body.containsKey("postCode")
                ? string(body.get("postCode"))
                : string(existing.get("postCode"));
        String postName = body.containsKey("postName")
                ? string(body.get("postName"))
                : string(existing.get("postName"));
        long postSort = body.containsKey("postSort")
                ? number(body.get("postSort"), 0L)
                : number(existing.get("postSort"), 0L);
        String status = body.containsKey("status")
                ? string(body.get("status"))
                : string(existing.get("status"));
        jdbc.update(
                "UPDATE sys_post SET post_code = ?, post_name = ?, post_sort = ?, status = ? WHERE post_id = ?",
                postCode,
                postName,
                postSort,
                StringUtils.hasText(status) ? status : "0",
                postId
        );
        return ApiResult.ok();
    }

    @DeleteMapping("/system/post/{postId}")
    public Map<String, Object> deletePost(@PathVariable Long postId) {
        jdbc.update("DELETE FROM sys_post WHERE post_id = ?", postId);
        return ApiResult.ok();
    }

    @PostMapping("/system/post/add_member")
    public Map<String, Object> addPostMembers(@RequestBody Map<String, Object> body) {
        long postId = number(body.get("postId"), 0L);
        for (Object rawId : values(body.get("userIds"))) {
            long userId = number(rawId, 0L);
            if (postId > 0L && userId > 0L) {
                jdbc.update("""
                        INSERT INTO sys_user_post (user_id, post_id)
                        SELECT ?, ? WHERE NOT EXISTS (
                            SELECT 1 FROM sys_user_post WHERE user_id = ? AND post_id = ?
                        )
                        """, userId, postId, userId, postId);
            }
        }
        return ApiResult.ok();
    }

    @DeleteMapping("/system/post/remove_member")
    public Map<String, Object> removePostMembers(@RequestBody Map<String, Object> body) {
        long postId = number(body.get("postId"), 0L);
        for (Object rawId : values(body.get("userIds"))) {
            jdbc.update("DELETE FROM sys_user_post WHERE user_id = ? AND post_id = ?",
                    number(rawId, 0L), postId);
        }
        return ApiResult.ok();
    }

    @GetMapping("/monitor/logininfor/list")
    @RequiresModuleAction(moduleId = 340319L)
    public Map<String, Object> loginLogs() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT info_id, user_name, ipaddr, browser, os, status, msg, login_time
                FROM sys_logininfor ORDER BY info_id DESC
                """));
        return ApiResult.page(rows, rows.size());
    }

    @DeleteMapping({"/monitor/logininfor/{infoId}", "/monitor/logininfor/clean"})
    @RequiresModuleAction(moduleId = 340319L)
    public Map<String, Object> deleteLoginLogs(@PathVariable(required = false) Long infoId) {
        if (infoId == null) {
            jdbc.update("DELETE FROM sys_logininfor");
        } else {
            jdbc.update("DELETE FROM sys_logininfor WHERE info_id = ?", infoId);
        }
        return ApiResult.ok();
    }

    @GetMapping("/monitor/operlog/list")
    @RequiresModuleAction(moduleId = 340318L)
    public Map<String, Object> operationLogs() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT oper_id, title, business_type, method, request_method, oper_name,
                       oper_url, oper_ip, status, error_msg, oper_time
                FROM sys_oper_log ORDER BY oper_id DESC
                """));
        return ApiResult.page(rows, rows.size());
    }

    @DeleteMapping({"/monitor/operlog/{operId}", "/monitor/operlog/clean"})
    @RequiresModuleAction(moduleId = 340318L)
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

    private List<?> values(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        if (value == null) {
            return List.of();
        }
        return java.util.Arrays.stream(String.valueOf(value).split(","))
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .toList();
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
