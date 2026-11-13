package com.seal.oa.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.auth.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipInputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.springframework.mock.web.MockHttpServletResponse;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:fixed_templates_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class FixedTemplateIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    String token;
    long role;

    @BeforeEach void setup() {
        String name="template_test_"+System.nanoTime();
        jdbc.update("INSERT INTO sys_user(user_name,nick_name,password,status,del_flag) VALUES(?,'模板测试','unused','0','0')",name);
        long user=jdbc.queryForObject("SELECT user_id FROM sys_user WHERE user_name=?",Long.class,name);
        jdbc.update("INSERT INTO sys_role(role_name,role_key) VALUES('模板测试',?)",name);
        role=jdbc.queryForObject("SELECT role_id FROM sys_role WHERE role_key=?",Long.class,name);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)",user,role);
        token=jwt.createToken(user,name);
    }
    @AfterEach void clear() { AuthContext.clear(); }
    void grant(long module) { jdbc.update("INSERT INTO sys_role_module(role_id,module_id) VALUES(?,?)",role,module); }
    MockHttpServletResponse request(String path) throws Exception {
        return mvc.perform(get("/system/templates/"+path).header("Authorization","Bearer "+token)).andReturn().getResponse();
    }
    int code(MockHttpServletResponse response) throws Exception { return mapper.readTree(response.getContentAsByteArray()).path("code").asInt(); }
    void workbook(MockHttpServletResponse response) throws Exception {
        assertEquals(200,response.getStatus());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").contains("attachment"));
        assertEquals("no-store",response.getHeader("Cache-Control"));
        boolean workbook=false,sheet=false;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                workbook|=entry.getName().equals("xl/workbook.xml");
                sheet|=entry.getName().equals("xl/worksheets/sheet1.xml");
            }
        }
        assertTrue(workbook && sheet,"Download must be a real Excel workbook");
    }
    @Test void everyCatalogTemplateAndPackDownloadWithOnlyModuleAccess() throws Exception {
        grant(341002);
        JsonNode response=mapper.readTree(request("hr/catalog").getContentAsByteArray());
        assertEquals(200,response.path("code").asInt());
        JsonNode schemas=response.path("data").path("templates");
        assertEquals(15,schemas.size());
        var names=schemas.fieldNames();
        while(names.hasNext()) workbook(request("hr/"+names.next()));
        workbook(request("hr/all"));
    }
    @Test void everyHrModuleCanReadCatalogIncludingSelfAndFinance() throws Exception {
        for(long module:new long[]{341003,341004,341005,341006,341409}) {
            jdbc.update("DELETE FROM sys_role_module WHERE role_id=?",role);
            grant(module);
            assertEquals(200,code(request("hr/catalog")));
            workbook(request("hr/salary"));
        }
    }
    @Test void membersOrOrganizationAccessCanDownloadAddressBook() throws Exception {
        grant(341001); workbook(request("members/address-book"));
        jdbc.update("DELETE FROM sys_role_module WHERE role_id=?",role);
        grant(340782); workbook(request("members/address-book"));
        assertEquals(403,code(request("hr/catalog")));
    }
    @Test void deniesAnonymousAndMissingModuleAndUnknownTemplate() throws Exception {
        var anonymous=mvc.perform(get("/system/templates/members/address-book")).andReturn().getResponse();
        assertEquals(401,code(anonymous));
        assertEquals(403,code(request("members/address-book")));
        assertEquals(403,code(request("hr/catalog")));
        grant(341002);
        assertEquals(404,code(request("hr/not-a-template")));
        assertEquals(404,code(request("hr/catalog.json")));
        assertEquals(403,code(request("members/address-book")));
    }
    @Test void customerJobAndTalentTemplatesRequireTheirOwnModule() throws Exception {
        String[] types={"customer","jobs","talents"};
        long[] modules={340352,341301,341302};
        for(int i=0;i<types.length;i++) {
            jdbc.update("DELETE FROM sys_role_module WHERE role_id=?",role);
            assertEquals(403,code(request("business/"+types[i])));
            grant(modules[i]);
            workbook(request("business/"+types[i]));
            var schema=mapper.readTree(request("business/"+types[i]+"/catalog").getContentAsByteArray());
            assertEquals(200,schema.path("code").asInt());
            assertTrue(schema.path("data").path("fields").size()>0);
            assertEquals(403,code(request("business/"+types[(i+1)%types.length])));
        }
        assertEquals(404,code(request("business/missing")));
    }
}
