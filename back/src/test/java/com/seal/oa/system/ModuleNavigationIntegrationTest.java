package com.seal.oa.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:module_navigation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class ModuleNavigationIntegrationTest {
    @Autowired ModuleService modules;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    final long id = 341112L;
    final String component = "eos/crm/salesPerformance/index";

    String authorization() { return "Bearer " + jwt.createToken(1L, "13610316814"); }

    @Test void renameWithStaleLegacyAliasUpdatesDatabaseDetailAndNavigationWithoutChangingPageComponent() throws Exception {
        var body = new LinkedHashMap<>(modules.module(id));
        body.put("menuType", "C"); body.put("component", component);
        body.put("moduleName", "部门看板"); body.put("menuName", "旧菜单名称");
        mvc.perform(put("/system/module").header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/system/module/" + id).header("Authorization", authorization())).andExpect(jsonPath("$.data.moduleName").value("部门看板")).andExpect(jsonPath("$.data.menuName").value("部门看板")).andExpect(jsonPath("$.data.component").value(component)).andExpect(jsonPath("$.data.menuType").value("C"));
        String response = mvc.perform(get("/getRouters").header("Authorization", authorization())).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var nav = find(json.readTree(response).path("data"), id);
        assertNotNull(nav); assertEquals("部门看板", nav.path("meta").path("title").asText()); assertEquals(component, nav.path("component").asText());
    }

    @Test void legacyMenuNameOnlyClientsCanStillRename() throws Exception {
        var body = new LinkedHashMap<>(modules.module(id)); body.remove("moduleName"); body.put("menuName", "兼容旧客户端");
        mvc.perform(put("/system/module").header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(jsonPath("$.code").value(200));
        assertEquals("兼容旧客户端", modules.module(id).get("moduleName"));
    }

    @Test void createUsesCanonicalNameAndPageCannotBeSavedWithDirectoryComponent() throws Exception {
        var body = new LinkedHashMap<>(modules.module(id));
        body.put("moduleId", 99871L); body.put("menuId", 99871L); body.put("moduleName", "新页面名称"); body.put("menuName", "旧别名"); body.put("routeName", "NavigationTest"); body.put("path", "navigation-test"); body.put("menuType", "C"); body.put("component", component);
        mvc.perform(post("/system/module").header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(jsonPath("$.code").value(200));
        assertEquals("新页面名称", modules.module(99871L).get("moduleName"));
        body.put("moduleId", id); body.put("menuId", id); body.put("component", "ParentView");
        var original = modules.module(id);
        mvc.perform(put("/system/module").header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(jsonPath("$.code").value(400));
        assertEquals(original.get("component"), modules.module(id).get("component"));
        assertEquals(original.get("moduleName"), modules.module(id).get("moduleName"));
    }

    private com.fasterxml.jackson.databind.JsonNode find(com.fasterxml.jackson.databind.JsonNode nodes, long moduleId) {
        for (var n : nodes) {
            if (n.path("moduleId").asLong() == moduleId) return n;
            var nested = find(n.path("children"), moduleId); if (nested != null) return nested;
        }
        return null;
    }
}
