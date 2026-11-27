package com.seal.oa.system;

import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class NoticeController {
    private final NoticeService service;
    private final SystemService system;
    public NoticeController(NoticeService service, SystemService system) { this.service = service; this.system = system; }

    // Reading published company notices is available to every authenticated employee.
    @GetMapping("/notices") public Map<String, Object> published(@RequestParam Map<String, String> query) { return service.list(query, true); }
    @GetMapping("/notices/{id}") public Map<String, Object> publishedDetail(@PathVariable long id) { return ApiResult.ok(service.detail(id, true)); }

    @GetMapping("/system/notice/list") @RequiresModuleAction(moduleId=340316L)
    public Map<String, Object> list(@RequestParam Map<String, String> query) { return service.list(query, false); }
    @GetMapping("/system/notice/{id}") @RequiresModuleAction(moduleId=340316L)
    public Map<String, Object> detail(@PathVariable long id) { return ApiResult.ok(service.detail(id, false)); }
    @PostMapping("/system/notice") @RequiresModuleAction(moduleId=340316L, action="add")
    public Map<String, Object> add(@RequestBody Map<String, Object> body) {
        if (Boolean.TRUE.equals(body.get("publish")) && !system.hasModuleAction(system.requireCurrentUser().userId(), 340316L, "edit"))
            throw new ApiException(403, "没有发布通知的权限，请配置通知公告的修改权限");
        return ApiResult.ok(Map.of("noticeId", Boolean.TRUE.equals(body.get("publish")) ? service.saveAndPublish(body, null) : service.save(body, null)));
    }
    @PutMapping("/system/notice") @RequiresModuleAction(moduleId=340316L, action="edit")
    public Map<String, Object> edit(@RequestBody Map<String, Object> body) {
        long id;
        try { id = Long.parseLong(String.valueOf(body.get("noticeId"))); } catch (Exception ex) { throw new ApiException(400, "公告编号无效"); }
        return ApiResult.ok(Map.of("noticeId", Boolean.TRUE.equals(body.get("publish")) ? service.saveAndPublish(body, id) : service.save(body, id)));
    }
    @PutMapping("/system/notice/{id}/publish") @RequiresModuleAction(moduleId=340316L, action="edit")
    public Map<String, Object> publish(@PathVariable long id) { service.publish(id); return ApiResult.ok(); }
    @PutMapping("/system/notice/{id}/withdraw") @RequiresModuleAction(moduleId=340316L, action="edit")
    public Map<String, Object> withdraw(@PathVariable long id) { service.withdraw(id); return ApiResult.ok(); }
    @DeleteMapping("/system/notice/{ids}") @RequiresModuleAction(moduleId=340316L, action="remove")
    public Map<String, Object> delete(@PathVariable String ids) { service.delete(ids); return ApiResult.ok(); }
    @PostMapping("/system/message") @RequiresModuleAction(moduleId=340316L, action="edit")
    public Map<String, Object> legacyPublish(@RequestBody Map<String, Object> body) {
        long id;
        try { id = Long.parseLong(String.valueOf(body.get("noticeId"))); } catch (Exception ex) { throw new ApiException(400, "请刷新页面后从通知公告列表发布，旧发布请求未执行"); }
        service.publish(id); return ApiResult.ok();
    }
}
