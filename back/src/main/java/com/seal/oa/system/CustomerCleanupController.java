package com.seal.oa.system;

import com.seal.oa.common.ApiResult;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/system/customer-cleanup")
@RequiresModuleAction(moduleId=341510L,action="access")
public class CustomerCleanupController {
    private final CustomerCleanupService service;
    public CustomerCleanupController(CustomerCleanupService service) { this.service=service; }
    @GetMapping("/customers") public Map<String,Object> search(@RequestParam(defaultValue="") String keyword) { return ApiResult.ok(service.search(keyword)); }
    @GetMapping("/customers/{id}/preview") public Map<String,Object> preview(@PathVariable long id) { return ApiResult.ok(service.preview(id)); }
    @PostMapping("/customers/{id}/cleanup") @RequiresModuleAction(moduleId=341510L,action="cleanup")
    public Map<String,Object> cleanup(@PathVariable long id,@RequestBody Map<String,Object> body) {
        body.put("operation","cleanup");return ApiResult.ok(service.execute(id,body));
    }
    @DeleteMapping("/customers/{id}") @RequiresModuleAction(moduleId=341510L,action="remove")
    public Map<String,Object> delete(@PathVariable long id,@RequestBody Map<String,Object> body) {
        body.put("operation","delete");return ApiResult.ok(service.execute(id,body));
    }
    @GetMapping("/history") public Map<String,Object> history() { return ApiResult.ok(service.history()); }
    @GetMapping("/history/{id}/backup") public ResponseEntity<byte[]> backup(@PathVariable String id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=customer-cleanup-backup.zip").body(service.backup(id));
    }
}
