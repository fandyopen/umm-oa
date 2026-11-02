package com.seal.oa.compat;

import com.seal.oa.common.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CompatController {

    @RequestMapping("/**")
    public Map<String, Object> compatibility(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("X-UMM-OA-Compatible", "fallback");
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return ApiResult.error(404, "接口不存在或已停用，请确认前后端版本一致");
    }
}
