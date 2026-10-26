package com.seal.oa.compat;

import com.seal.oa.common.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class CompatController {

    @RequestMapping("/**")
    public Map<String, Object> compatibility(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("X-UMM-OA-Compatible", "fallback");
        String method = request.getMethod();
        String path = request.getRequestURI();

        if ("GET".equalsIgnoreCase(method)) {
            if (path.contains("/export")) {
                Map<String, Object> result = ApiResult.message("导出兼容接口尚未实现");
                result.put("data", List.of());
                return result;
            }
            if (isListPath(path)) {
                return ApiResult.page(List.of(), 0);
            }
            if (path.contains("/tree") || path.contains("treeselect") || path.contains("/select")) {
                return ApiResult.ok(List.of());
            }
            return ApiResult.ok(Map.of());
        }
        return ApiResult.ok();
    }

    private boolean isListPath(String path) {
        return path.endsWith("/list")
                || path.contains("/list/")
                || path.contains("/page")
                || path.contains("/table");
    }
}
