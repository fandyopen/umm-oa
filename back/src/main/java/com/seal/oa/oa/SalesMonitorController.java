package com.seal.oa.oa;

import com.seal.oa.common.ApiResult;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/project/crm/sales-performance/monitor")
public class SalesMonitorController {
    private final SalesMonitorService monitor;
    public SalesMonitorController(SalesMonitorService monitor) { this.monitor = monitor; }

    @GetMapping
    @RequiresModuleAction(moduleId = 341112, action = "access")
    public Map<String,Object> overview(@RequestParam Map<String,String> query) { return ApiResult.ok(monitor.overview(query)); }

    @GetMapping("/details")
    @RequiresModuleAction(moduleId = 341112, action = "access")
    public Map<String,Object> details(@RequestParam Map<String,String> query) { return monitor.details(query); }
}
