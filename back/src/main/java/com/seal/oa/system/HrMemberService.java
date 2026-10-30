package com.seal.oa.system;

import com.seal.oa.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
public class HrMemberService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public HrMemberService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    public void validateInvite(Map<String, Object> body) {
        String username = username(body);
        if (!StringUtils.hasText(username)) {
            throw new ApiException(400, "手机号不能为空");
        }
        String phoneNumber = string(body.get("phonenumber"));
        Long duplicate = StringUtils.hasText(phoneNumber)
                ? jdbc.queryForObject("""
                        SELECT COUNT(*) FROM sys_user
                        WHERE user_name = ? OR phonenumber = ?
                        """, Long.class, username, phoneNumber)
                : jdbc.queryForObject("""
                        SELECT COUNT(*) FROM sys_user WHERE user_name = ?
                        """, Long.class, username);
        if (duplicate != null && duplicate > 0L) {
            throw new ApiException(400, "该手机号已存在或属于离职成员");
        }
    }

    @Transactional
    public long invite(Map<String, Object> body, String operator) {
        validateInvite(body);
        String username = username(body);
        String rawPassword = StringUtils.hasText(string(body.get("password")))
                ? string(body.get("password"))
                : "admin123123";
        jdbc.update("""
                INSERT INTO sys_user (
                    tenant_id, dept_id, user_name, nick_name, password, email, phonenumber,
                    sex, avatar, status, create_by, remark
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                longValue(body.get("tenantId_"), 1L),
                longValue(body.get("deptId"), 101L),
                username,
                defaultString(body.get("nickName"), username),
                passwordEncoder.encode(rawPassword),
                defaultString(body.get("email"), ""),
                defaultString(body.get("phonenumber"), username),
                defaultString(body.get("sex"), "0"),
                defaultString(body.get("avatar"), ""),
                defaultString(body.get("status"), "0"),
                operator,
                defaultString(body.get("remark"), ""));
        Long id = jdbc.queryForObject(
                "SELECT user_id FROM sys_user WHERE user_name = ?",
                Long.class,
                username
        );
        bind(body, "roleIds", id, "sys_user_role", "user_id", "role_id");
        bind(body, "postIds", id, "sys_user_post", "user_id", "post_id");
        return id == null ? 0L : id;
    }

    public void validateTransfer(long deptId, List<Long> userIds) {
        if (deptId <= 0L) {
            throw new ApiException(400, "目标部门不能为空");
        }
        if (userIds.isEmpty()) {
            throw new ApiException(400, "请选择需要调整部门的成员");
        }
        Long deptCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_dept WHERE dept_id = ? AND status = '0'
                """, Long.class, deptId);
        if (deptCount == null || deptCount == 0L) {
            throw new ApiException(404, "目标部门不存在");
        }
    }

    @Transactional
    public int transfer(long deptId, List<Long> userIds) {
        validateTransfer(deptId, userIds);
        int count = 0;
        for (Long userId : userIds) {
            count += jdbc.update("""
                    UPDATE sys_user SET dept_id = ?, update_time = CURRENT_TIMESTAMP
                    WHERE user_id = ? AND del_flag = '0'
                    """, deptId, userId);
        }
        return count;
    }

    public void validateDismiss(List<Long> userIds) {
        if (userIds.isEmpty()) {
            throw new ApiException(400, "请选择需要转为离职的成员");
        }
    }

    @Transactional
    public int dismiss(List<Long> userIds) {
        validateDismiss(userIds);
        int count = 0;
        for (Long userId : userIds) {
            count += jdbc.update("""
                    UPDATE sys_user SET del_flag = '2', update_time = CURRENT_TIMESTAMP
                    WHERE user_id = ? AND del_flag = '0'
                    """, userId);
        }
        return count;
    }

    private void bind(
            Map<String, Object> body,
            String sourceKey,
            Long userId,
            String table,
            String userColumn,
            String valueColumn
    ) {
        Object raw = body.get(sourceKey);
        if (!(raw instanceof List<?> values)) {
            return;
        }
        for (Object value : values) {
            long id = longValue(value, 0L);
            if (id > 0L) {
                if ("sys_user_role".equals(table) && id == 1L) {
                    throw new ApiException(403, "超级管理员角色不能分配给新账号");
                }
                jdbc.update(
                        "INSERT INTO " + table + " (" + userColumn + ", " + valueColumn + ") VALUES (?, ?)",
                        userId,
                        id
                );
            }
        }
    }

    private String username(Map<String, Object> body) {
        return StringUtils.hasText(string(body.get("userName")))
                ? string(body.get("userName"))
                : string(body.get("phonenumber"));
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String defaultString(Object value, String defaultValue) {
        String text = string(value);
        return text.isEmpty() ? defaultValue : text;
    }

    private long longValue(Object value, long defaultValue) {
        try {
            return value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}
