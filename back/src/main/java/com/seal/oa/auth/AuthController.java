package com.seal.oa.auth;

import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.system.ModuleService;
import com.seal.oa.system.OrganizationPermissionService;
import com.seal.oa.system.SystemService;
import com.seal.oa.system.SystemService.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
public class AuthController {

    private final CaptchaService captchaService;
    private final PasswordCipher passwordCipher;
    private final JwtService jwtService;
    private final ModuleService moduleService;
    private final SystemService systemService;
    private final OrganizationPermissionService organizationPermissionService;
    private final boolean validateCaptcha;

    public AuthController(
            CaptchaService captchaService,
            PasswordCipher passwordCipher,
            JwtService jwtService,
            ModuleService moduleService,
            SystemService systemService,
            OrganizationPermissionService organizationPermissionService,
            @Value("${app.captcha.validate:false}") boolean validateCaptcha
    ) {
        this.captchaService = captchaService;
        this.passwordCipher = passwordCipher;
        this.jwtService = jwtService;
        this.moduleService = moduleService;
        this.systemService = systemService;
        this.organizationPermissionService = organizationPermissionService;
        this.validateCaptcha = validateCaptcha;
    }

    @GetMapping("/captchaImage")
    public Map<String, Object> captcha() {
        return captchaService.create();
    }

    @PostMapping({"/captcha/get", "/captcha/check"})
    public Map<String, Object> mobileCaptcha() {
        return ApiResult.message("Slider captcha is disabled; use /captchaImage");
    }

    @PostMapping({"/login", "/login/pwd"})
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String username = text(body.get("username"));
        String password = passwordCipher.decryptIfPossible(text(body.get("password")));
        if (validateCaptcha && !captchaService.validate(text(body.get("uuid")), text(body.get("code")))) {
            throw new ApiException(500, "Captcha code is incorrect");
        }

        UserAccount user = systemService.findLoginUser(username)
                .orElseThrow(() -> new ApiException(500, "Invalid username or password"));
        if (!"0".equals(user.status())) {
            throw new ApiException(500, "User account is disabled");
        }
        if (!systemService.passwordMatches(user, password)) {
            throw new ApiException(500, "Invalid username or password");
        }

        Map<String, Object> result = ApiResult.message("Login successful");
        result.put("token", jwtService.createToken(user.userId(), user.username()));
        return result;
    }

    @GetMapping("/getInfo")
    public Map<String, Object> getInfo() {
        UserAccount user = systemService.requireCurrentUser();
        Map<String, Object> result = ApiResult.ok();
        result.put("user", systemService.userDetails(user));
        result.put("roles", systemService.roleKeys(user.userId()));
        result.put("permissions", systemService.permissions(user.userId()));
        result.put("posts", systemService.posts(user.userId()));
        return result;
    }

    @DeleteMapping("/logout")
    public Map<String, Object> logout() {
        return ApiResult.ok();
    }

    @GetMapping({"/getRouters", "/getAppRouters"})
    public Map<String, Object> routers() {
        UserAccount user = systemService.requireCurrentUser();
        boolean admin = systemService.isUnrestricted(user.userId());
        return ApiResult.ok(moduleService.routers(
                user.tenantId(),
                admin,
                Set.copyOf(systemService.permissions(user.userId())),
                moduleService.roleModuleIds(user.userId())
        ));
    }

    @GetMapping("/crm/user/get_user_info")
    public Map<String, Object> currentUserInfo() {
        UserAccount user = systemService.requireCurrentUser();
        Map<String, Object> result = ApiResult.ok();
        result.put("userInfo", systemService.userDetails(user));
        result.put("perms", organizationPermissionService.evaluate(user.userId()));
        return result;
    }

    /**
     * 组织权限判定诊断：返回模块授权命中情况、数据范围和最终结果。
     */
    @GetMapping("/system/permission/organization")
    public Map<String, Object> organizationPermissionDiagnosis() {
        UserAccount user = systemService.requireCurrentUser();
        return ApiResult.ok(organizationPermissionService.evaluate(user.userId()));
    }

    @PostMapping("/crm/user/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        throw new ApiException(501, "Registration is not enabled for this deployment");
    }

    @PostMapping({"/WX/authLogin", "/WX/wxlogin"})
    public Map<String, Object> wechatLogin(@RequestBody(required = false) Map<String, Object> body) {
        throw new ApiException(501, "WeChat login requires an AppId, Secret, and callback domain");
    }

    @GetMapping("/dingDing/auth/getConfig")
    public Map<String, Object> dingTalkConfig() {
        throw new ApiException(501, "DingTalk login requires enterprise application credentials");
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
