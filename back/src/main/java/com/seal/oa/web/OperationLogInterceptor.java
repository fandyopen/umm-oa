package com.seal.oa.web;

import com.seal.oa.auth.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
public class OperationLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME = OperationLogInterceptor.class.getName() + ".startTime";
    private static final String OPERATOR = OperationLogInterceptor.class.getName() + ".operator";
    private static final Set<String> IGNORED_PATHS = Set.of(
            "/captchaImage",
            "/cce/captcha/get",
            "/captcha/check",
            "/login",
            "/logout",
            "/activiti/process/listHistory",
            "/error"
    );

    private final JdbcTemplate jdbc;

    public OperationLogInterceptor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME, System.currentTimeMillis());
        request.setAttribute(OPERATOR, AuthContext.username());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method)
                || IGNORED_PATHS.contains(request.getRequestURI())) {
            return;
        }
        try {
            long elapsed = System.currentTimeMillis()
                    - (Long) request.getAttribute(START_TIME);
            String path = request.getRequestURI();
            int status = exception == null && response.getStatus() < 400 ? 0 : 1;
            String error = exception == null ? "" : exception.getMessage();
            jdbc.update("""
                    INSERT INTO sys_oper_log (
                        title, business_type, method, request_method, oper_name,
                        oper_url, oper_ip, oper_param, json_result, status, error_msg, oper_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    title(path),
                    businessType(method),
                    truncate(handler.toString(), 200),
                    method,
                    request.getAttribute(OPERATOR),
                    path,
                    clientIp(request),
                    truncate(request.getQueryString(), 2000),
                    "durationMs=" + elapsed,
                    status,
                    truncate(error, 2000)
            );
        } catch (Exception ignored) {
            // Logging must never break the business request.
        }
    }

    private String title(String path) {
        if (path.startsWith("/system/post")) {
            return "岗位管理";
        }
        if (path.startsWith("/system/dept")) {
            return "组织架构";
        }
        if (path.startsWith("/system/user")) {
            return "成员管理";
        }
        if (path.startsWith("/system/dict")) {
            return "基础数据";
        }
        if (path.startsWith("/system/config")) {
            return "参数设置";
        }
        if (path.startsWith("/system/notice")) {
            return "通知公告";
        }
        return "系统操作";
    }

    private int businessType(String method) {
        return switch (method.toUpperCase()) {
            case "POST" -> 1;
            case "PUT", "PATCH" -> 2;
            case "DELETE" -> 3;
            default -> 0;
        };
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
