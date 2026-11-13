package com.seal.oa.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.web.ModuleActionRequirement;
import com.seal.oa.web.RequiresAnyModuleAction;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Fixed, versioned templates live on the classpath, never in the mutable upload directory. */
@RestController
@RequestMapping("/system/templates")
public class FixedTemplateController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final JsonNode hrCatalog;
    private final JsonNode businessCatalog;
    private final JsonNode memberCatalog;
    private final SystemService system;

    public FixedTemplateController(ObjectMapper mapper, SystemService system) throws IOException {
        this.system = system;
        try (var input = new ClassPathResource("templates/hr/catalog.json").getInputStream()) {
            hrCatalog = mapper.readTree(input);
        }
        try (var input = new ClassPathResource("templates/business/catalog.json").getInputStream()) {
            businessCatalog = mapper.readTree(input);
        }
        try (var input = new ClassPathResource("templates/members/catalog.json").getInputStream()) {
            memberCatalog = mapper.readTree(input);
        }
    }

    @GetMapping("/hr/catalog")
    @RequiresAnyModuleAction({
        @ModuleActionRequirement(moduleId=341002), @ModuleActionRequirement(moduleId=341003),
        @ModuleActionRequirement(moduleId=341004), @ModuleActionRequirement(moduleId=341005),
        @ModuleActionRequirement(moduleId=341006), @ModuleActionRequirement(moduleId=341409)
    })
    public Map<String, Object> catalog() {
        authenticated();
        return ApiResult.ok(hrCatalog.deepCopy());
    }

    @GetMapping("/hr/{type}")
    @RequiresAnyModuleAction({
        @ModuleActionRequirement(moduleId=341002), @ModuleActionRequirement(moduleId=341003),
        @ModuleActionRequirement(moduleId=341004), @ModuleActionRequirement(moduleId=341005),
        @ModuleActionRequirement(moduleId=341006), @ModuleActionRequirement(moduleId=341409)
    })
    public ResponseEntity<byte[]> hr(@PathVariable String type) throws IOException {
        authenticated();
        if ("all".equals(type)) return download("hr/all.xlsx", "人事考勤与薪资模板大全.xlsx");
        JsonNode schema = hrCatalog.path("templates").get(type);
        if (schema == null) throw new ApiException(404, "模板不存在");
        return download("hr/" + type + ".xlsx", schema.path("name").asText() + ".xlsx");
    }

    @GetMapping("/members/address-book")
    @RequiresAnyModuleAction({
        @ModuleActionRequirement(moduleId=341001), @ModuleActionRequirement(moduleId=340782)
    })
    public ResponseEntity<byte[]> members() throws IOException {
        authenticated();
        return download("members/address-book.xlsx", "通讯录批量导入模板.xlsx");
    }

    @GetMapping("/members/catalog")
    @RequiresAnyModuleAction({
        @ModuleActionRequirement(moduleId=341001), @ModuleActionRequirement(moduleId=340782)
    })
    public Map<String, Object> memberCatalog() {
        authenticated();
        return ApiResult.ok(memberCatalog.deepCopy());
    }

    @GetMapping("/business/{type}/catalog")
    public Map<String, Object> businessCatalog(@PathVariable String type) {
        return ApiResult.ok(businessSchema(type).deepCopy());
    }

    @GetMapping("/business/{type}")
    public ResponseEntity<byte[]> business(@PathVariable String type) throws IOException {
        JsonNode schema = businessSchema(type);
        return download("business/" + type + ".xlsx", schema.path("name").asText() + ".xlsx");
    }

    private JsonNode businessSchema(String type) {
        authenticated();
        JsonNode schema = businessCatalog.path("templates").get(type);
        if (schema == null) throw new ApiException(404, "模板不存在");
        if (!system.hasModuleAction(AuthContext.userId(), schema.path("moduleId").asLong(), "access"))
            throw new ApiException(403, "没有该模块的访问权限");
        return schema;
    }

    private void authenticated() {
        if (AuthContext.get() == null) throw new ApiException(401, "请先登录后下载模板");
    }

    private ResponseEntity<byte[]> download(String resource, String filename) throws IOException {
        byte[] bytes;
        try (var input = new ClassPathResource("templates/" + resource).getInputStream()) {
            bytes = input.readAllBytes();
        }
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }
}
