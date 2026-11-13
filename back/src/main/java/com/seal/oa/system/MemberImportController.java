package com.seal.oa.system;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/system/user/member-import")
public class MemberImportController {
    private final MemberImportService service;
    public MemberImportController(MemberImportService service) { this.service = service; }

    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestParam long moduleId, @RequestBody Map<String, Object> body) {
        if (AuthContext.get() == null) throw new ApiException(401, "请先登录");
        return ApiResult.ok(Map.of("rows", service.prepare(moduleId, body, AuthContext.username(), false)));
    }
}
