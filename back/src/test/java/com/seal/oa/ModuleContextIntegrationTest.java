package com.seal.oa;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = """
        spring.datasource.url=jdbc:h2:mem:umm_oa_module_context;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1
        """)
@AutoConfigureMockMvc
@Transactional
class ModuleContextIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/system/module-context").param("components", "enterprise/hr/index"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void ordinaryUserCanResolveHiddenBusinessModulesWithoutManagementPermission() throws Exception {
        String token = login("15609825919");
        mvc.perform(get("/system/module-context").header("Authorization", token)
                        .param("components", "enterprise/hr/index,eos/crm/contract/versions,eos/crm/customer/index"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data['enterprise/hr/index'].moduleId").value(341001))
                .andExpect(jsonPath("$.data['eos/crm/contract/versions'].moduleId").value(341204))
                .andExpect(jsonPath("$.data['eos/crm/contract/versions'].perms").value("crm:version:list"))
                .andExpect(jsonPath("$.data['eos/crm/customer/index'].component").value("eos/crm/customer/index"));
        // Resolving metadata must not grant management access.
        mvc.perform(get("/system/module/list").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void resolvesDatabaseIdsInsteadOfAssumingSeedValues() throws Exception {
        insertModule(990001, 1, "test/business/contract", "0");
        mvc.perform(get("/system/module-context").header("Authorization", login("13610316814"))
                        .param("components", "test/business/contract"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data['test/business/contract'].moduleId").value(990001));
    }

    @Test
    void excludesOtherTenantsAndDisabledOrUnknownModules() throws Exception {
        insertModule(990002, 2, "test/business/foreign", "0");
        insertModule(990003, 1, "test/business/disabled", "1");
        mvc.perform(get("/system/module-context").header("Authorization", login("13610316814"))
                        .param("components", "test/business/foreign,test/business/disabled,test/business/unknown"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void rejectsAmbiguousActiveComponentWithinTenant() throws Exception {
        insertModule(990004, 1, "test/business/duplicate", "0");
        insertModule(990005, 1, "test/business/duplicate", "0");
        mvc.perform(get("/system/module-context").header("Authorization", login("13610316814"))
                        .param("components", "test/business/duplicate"))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void sameComponentInAnotherTenantDoesNotCreateAmbiguity() throws Exception {
        insertModule(990006, 1, "test/business/shared", "0");
        insertModule(990007, 2, "test/business/shared", "0");
        insertModule(990008, 1, "test/business/shared", "1");
        mvc.perform(get("/system/module-context").header("Authorization", login("13610316814"))
                        .param("components", "test/business/shared"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data['test/business/shared'].moduleId").value(990006));
    }

    @Test
    void rejectsEmptyDuplicateMalformedAndExcessiveSelectors() throws Exception {
        String token = login("13610316814");
        mvc.perform(get("/system/module-context").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(400));
        for (String components : new String[]{"", "a/b,", "a/b,a/b", "../secret", "a/' OR 1=1", "x".repeat(161),
                String.join(",", java.util.stream.IntStream.range(0, 33).mapToObj(i -> "module/" + i).toList())}) {
            mvc.perform(get("/system/module-context").header("Authorization", token).param("components", components))
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    private void insertModule(long id, long tenant, String component, String status) {
        jdbc.update("""
                INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name,
                    path, component, perms, status, hidden)
                VALUES (?, ?, 0, 'Context test', ?, ?, ?, 'test:context:list', ?, 1)
                """, id, tenant, "Context" + id, "/context/" + id, component, status);
    }

    private String login(String username) throws Exception {
        var result = mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("username", username, "password", "admin123123"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty()).andReturn();
        return "Bearer " + mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}

