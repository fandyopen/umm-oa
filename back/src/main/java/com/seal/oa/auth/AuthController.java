package com.seal.oa.auth;

import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.system.SystemService;
import com.seal.oa.system.SystemService.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class AuthController {

    private final CaptchaService captchaService;
    private final PasswordCipher passwordCipher;
    private final JwtService jwtService;
    private final SystemService systemService;
    private final boolean validateCaptcha;

    public AuthController(
            CaptchaService captchaService,
            PasswordCipher passwordCipher,
            JwtService jwtService,
            SystemService systemService,
            @Value("${app.captcha.validate:false}") boolean validateCaptcha
    ) {
        this.captchaService = captchaService;
        this.passwordCipher = passwordCipher;
        this.jwtService = jwtService;
        this.systemService = systemService;
        this.validateCaptcha = validateCaptcha;
    }

    @GetMapping("/captchaImage")
    public Map<String, Object> captcha() {
        return captchaService.create();
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String username = text(body.get("username"));
        String password = passwordCipher.decryptIfPossible(text(body.get("password")));
        if (validateCaptcha && !captchaService.validate(text(body.get("uuid")), text(body.get("code")))) {
            throw new ApiException(500, "验证码错误");
        }

        UserAccount user = systemService.findLoginUser(username)
                .orElseThrow(() -> new ApiException(500, "用户不存在/密码错误"));
        if (!"0".equals(user.status())) {
            throw new ApiException(500, "用户已停用");
        }
        if (!systemService.passwordMatches(user, password)) {
            throw new ApiException(500, "用户不存在/密码错误");
        }

        Map<String, Object> result = ApiResult.message("登录成功");
        result.put("token", jwtService.createToken(user.userId(), user.username()));
        return result;
    }

    @GetMapping("/getInfo")
    public Map<String, Object> getInfo() {
        UserAccount user = systemService.requireCurrentUser();
        Map<String, Object> result = ApiResult.message("操作成功");
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

    @GetMapping("/getRouters")
    public Map<String, Object> routers() {
        Map<String, Object> result = ApiResult.ok();
        result.put("data", List.of(
                menu("工作台", "/todo", "eos/todoItem/todo/index", "iconfont-shouye"),
                menu("项目管理", "/project", "eos/project/index", "iconfont-xiangmu"),
                menu("合同管理", "/contract", "eos/contract/index", "iconfont-hetong"),
                menu("采购管理", "/purchase", "eos/purchase/apply/index", "iconfont-caigou"),
                menu("费用报销", "/expense", "eos/expense/index", "iconfont-baoxiao"),
                menu("预算管理", "/budget", "eos/budget/index", "iconfont-yusuan"),
                menu("资金计划", "/fund-plan", "eos/fundPlan/index", "iconfont-zijin"),
                menu("发票管理", "/invoice", "eos/invoice/make/index", "iconfont-fapiao"),
                menu("仓库管理", "/warehouse", "eos/warehouse/warehouse/index", "iconfont-cangku"),
                menu("系统管理", "/system", "system/user/index", "iconfont-shezhi"),
                menu("应用中心", "/app/appCenter", "appCenter/dashboardApp/index", "iconfont-yingyong")
        ));
        return result;
    }

    @GetMapping("/crm/user/get_user_info")
    public Map<String, Object> currentUserInfo() {
        UserAccount user = systemService.requireCurrentUser();
        Map<String, Object> result = ApiResult.ok();
        result.put("userInfo", systemService.userDetails(user));
        result.put("perms", systemService.permissions(user.userId()));
        return result;
    }

    @PostMapping("/crm/user/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        throw new ApiException(501, "注册流程尚未接入，可由 sys_user/sys_tenant 表继续扩展");
    }

    private Map<String, Object> menu(String title, String path, String component, String icon) {
        Map<String, Object> menu = new LinkedHashMap<>();
        menu.put("name", title);
        menu.put("path", path);
        menu.put("component", component);
        menu.put("hidden", false);
        menu.put("meta", Map.of(
                "title", title,
                "icon", icon,
                "noCache", false
        ));
        return menu;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
