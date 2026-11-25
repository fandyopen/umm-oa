package com.seal.oa.system;

import com.seal.oa.common.ApiResult;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/system/junk-cleanup")
@RequiresModuleAction(moduleId = 341512L, action = "access")
public class JunkCleanupController {
    private final JunkCleanupService service;
    public JunkCleanupController(JunkCleanupService service) { this.service = service; }

    @GetMapping("/scan") public Map<String, Object> scan() { return ApiResult.ok(service.scan()); }
    @PostMapping("/clean") @RequiresModuleAction(moduleId = 341512L, action = "cleanup")
    public Map<String, Object> clean(@RequestBody Map<String, Object> body) { return ApiResult.ok(service.clean(body)); }
    @GetMapping("/history") public Map<String, Object> history() { return ApiResult.ok(service.history()); }
}
