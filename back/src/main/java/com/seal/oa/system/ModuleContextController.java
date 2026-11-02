package com.seal.oa.system;

import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Logged-in business pages may resolve metadata; this never grants module permissions. */
@RestController
public class ModuleContextController {
    private final ModuleService moduleService;
    private final SystemService systemService;

    public ModuleContextController(ModuleService moduleService, SystemService systemService) {
        this.moduleService = moduleService;
        this.systemService = systemService;
    }

    @GetMapping("/system/module-context")
    public Map<String, Object> resolve(@RequestParam(defaultValue = "") String components) {
        var user = systemService.requireCurrentUser();
        List<String> selectors = Arrays.stream(components.split(",", -1)).map(String::trim).toList();
        if (selectors.size() > 32 || new HashSet<>(selectors).size() != selectors.size()
                || selectors.stream().anyMatch(value -> value.length() > 160
                || !value.matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*"))) {
            throw new ApiException(400, "模块组件标识无效、重复或数量超限");
        }
        return ApiResult.ok(moduleService.resolveContexts(user.tenantId(), selectors));
    }
}
