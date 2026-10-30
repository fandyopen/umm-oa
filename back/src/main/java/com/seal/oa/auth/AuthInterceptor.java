package com.seal.oa.auth;

import com.seal.oa.common.ApiException;
import com.seal.oa.system.SystemService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;
    private final SystemService systemService;

    public AuthInterceptor(JwtService jwtService, SystemService systemService) {
        this.jwtService = jwtService;
        this.systemService = systemService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(401, "认证失败，无法访问系统资源");
        }

        AuthContext.CurrentUser currentUser;
        try {
            currentUser = jwtService.parse(authorization.substring(7));
        } catch (Exception exception) {
            throw new ApiException(401, "登录状态已过期，请重新登录");
        }
        var account = systemService.findById(currentUser.userId());
        if (account.isEmpty() || !"0".equals(account.get().status())) {
            throw new ApiException(401, "账号已停用或删除，请重新登录");
        }
        AuthContext.set(currentUser);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        AuthContext.clear();
    }
}

