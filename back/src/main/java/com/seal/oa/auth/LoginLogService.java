package com.seal.oa.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LoginLogService {
    private static final Logger log = LoggerFactory.getLogger(LoginLogService.class);
    private final JdbcTemplate jdbc;

    public LoginLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String username, boolean success, String message, HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        try {
            jdbc.update("""
                    INSERT INTO sys_logininfor (user_name, ipaddr, browser, os, status, msg, login_time)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, limit(username, 64), limit(clientIp(request), 64), browser(agent), operatingSystem(agent),
                    success ? "0" : "1", limit(message, 255));
        } catch (DataAccessException exception) {
            // Audit storage failures must not change the authentication result. Never log credentials or tokens.
            log.error("Unable to persist login audit record", exception);
        }
    }

    private String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        // Only a local reverse proxy may supply the forwarded client address.
        if ("127.0.0.1".equals(remote) || "::1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null) {
                String first = forwarded.split(",", 2)[0].trim();
                if (!first.isEmpty() && first.matches("[0-9a-fA-F:.]+")) return first;
            }
        }
        return remote == null ? "未知" : remote;
    }

    private String browser(String agent) {
        if (agent == null || agent.isBlank()) return "未知";
        String[][] browsers = { { "Edg(?:e|A|iOS)?/([0-9.]+)", "Edge" },
                { "(?:OPR|Opera)/([0-9.]+)", "Opera" }, { "(?:Chrome|CriOS)/([0-9.]+)", "Chrome" },
                { "(?:Firefox|FxiOS)/([0-9.]+)", "Firefox" }, { "Version/([0-9.]+).*Safari/", "Safari" },
                { "(?:MSIE |rv:)([0-9.]+)", "Internet Explorer" } };
        for (String[] item : browsers) {
            Matcher match = Pattern.compile(item[0]).matcher(agent);
            if (match.find()) return limit(item[1] + " " + match.group(1), 100);
        }
        return "其他客户端";
    }

    private String operatingSystem(String agent) {
        if (agent == null || agent.isBlank()) return "未知";
        String value = agent.toLowerCase(Locale.ROOT);
        if (value.contains("android")) return "Android";
        if (value.contains("iphone") || value.contains("ipad") || value.contains("ipod")) return "iOS";
        if (value.contains("windows")) return "Windows";
        if (value.contains("mac os") || value.contains("macintosh")) return "macOS";
        if (value.contains("linux")) return "Linux";
        return "其他系统";
    }

    private String limit(String value, int length) {
        if (value == null) return "";
        return value.length() <= length ? value : value.substring(0, length);
    }
}
