package com.seal.oa.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.auth.JwtService;
import com.seal.oa.common.ApiException;
import com.seal.oa.workflow.WorkflowActionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:member_import_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class MemberImportIntegrationTest {
    @Autowired MemberImportService service;
    @Autowired WorkflowActionService actions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    String operator;
    long dept;

    @BeforeEach void setup() {
        operator=jdbc.queryForObject("SELECT user_name FROM sys_user WHERE user_id=1",String.class);
        AuthContext.set(new AuthContext.CurrentUser(1L,operator));
        dept=department(1,"导入部门"+System.nanoTime(),"0","0");
    }
    @AfterEach void clear() { AuthContext.clear(); }
    long department(long tenant,String name,String status,String deleted) {
        jdbc.update("INSERT INTO sys_dept(tenant_id,dept_name,status,del_flag) VALUES(?,?,?,?)",tenant,name,status,deleted);
        return jdbc.queryForObject("SELECT MAX(dept_id) FROM sys_dept",Long.class);
    }
    Map<String,Object> row(String phone,Object department) {
        return new LinkedHashMap<>(Map.of("rowNumber",7,"nickName","待导入成员","phonenumber",phone,"department",department));
    }
    Map<String,Object> body(Map<String,Object>... rows) { return new LinkedHashMap<>(Map.of("rows",List.of(rows))); }
    int count(String phone) { return jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE phonenumber=?",Integer.class,phone); }
    ApiException invalid(Map<String,Object> payload) { return assertThrows(ApiException.class,()->service.prepare(341001,payload,operator,false)); }

    @Test void acceptsIdOrExactNameAndPreviewDoesNotCreateUsers() {
        String name=jdbc.queryForObject("SELECT dept_name FROM sys_dept WHERE dept_id=?",String.class,dept);
        var rows=service.prepare(341001,body(row("13981100001",String.valueOf(dept)),row("13981100002"," "+name+" ")),operator,false);
        assertEquals(2,rows.size());
        for(var r:rows) { assertEquals(dept,((Number)r.get("deptId")).longValue()); assertEquals(name,r.get("deptName")); }
        assertEquals(0,count("13981100001"));
    }
    @Test void rejectsUnknownNamesBlankDepartmentsAndInvalidIdsWithPhysicalRow() {
        for(Object value:List.of("部门打错了","","999999999999999999999999","-100")) {
            String message=invalid(body(row("13981100003",value))).getMessage();
            assertTrue(message.contains("第7行"));
            assertTrue(message.contains("整批未导入"));
        }
    }
    @Test void refusesAmbiguousNamesButAcceptsTheirIds() {
        long a=department(1,"重名部门","0","0"),b=department(1,"重名部门","0","0");
        assertTrue(invalid(body(row("13981100004","重名部门"))).getMessage().contains("重名"));
        assertEquals(2,service.prepare(341001,body(row("13981100004",a),row("13981100005",b)),operator,false).size());
    }
    @Test void rejectsDisabledDeletedAndForeignDepartmentsByNameAndId() {
        for(var state:List.of(new String[]{"1","1","0"},new String[]{"1","0","2"},new String[]{"2","0","0"})) {
            String name="不可导入部门"+System.nanoTime();
            long id=department(Long.parseLong(state[0]),name,state[1],state[2]);
            invalid(body(row("13981100006",id))); invalid(body(row("13981100006",name)));
        }
    }
    @Test void validatesEntireBatchBeforeAnyInsertionAndRejectsDuplicatePhones() {
        assertThrows(ApiException.class,()->service.execute(341001,body(row("13981100007",dept),row("13981100008","错误部门")),operator,false));
        assertEquals(0,count("13981100007")); assertEquals(0,count("13981100008"));
        assertTrue(invalid(body(row("13981100007",dept),row("13981100007",dept))).getMessage().contains("重复"));
        var existing=row(jdbc.queryForObject("SELECT phonenumber FROM sys_user WHERE user_id=1",String.class),dept);
        assertTrue(invalid(body(existing)).getMessage().contains("已存在"));
    }
    @Test void createsAccountsAndProfilesWithoutAcceptingCallerPrivilegesOrTenant() {
        var r=row("13981100009",dept);
        r.putAll(Map.of("tenantId_",2,"password","caller-password","roleIds",List.of(1),"status","1","sex","女"));
        assertEquals(1,service.execute(341001,body(r),operator,false));
        var account=jdbc.queryForMap("SELECT * FROM sys_user WHERE user_name='13981100009'");
        assertEquals(1,((Number)account.get("tenant_id")).intValue());
        assertEquals("0",account.get("status")); assertEquals("1",account.get("sex"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE user_id=?",Integer.class,account.get("user_id")));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_employee WHERE user_id=?",Integer.class,account.get("user_id")));
    }
    @Test void revalidatesFrozenDepartmentAndExistingPhonesAtExecution() {
        var prepared=service.prepare(341001,body(row("13981100010",dept)),operator,false);
        jdbc.update("UPDATE sys_dept SET dept_name='审批期间改名' WHERE dept_id=?",dept);
        var frozen=new LinkedHashMap<String,Object>(Map.of("rows",prepared));
        assertThrows(ApiException.class,()->service.execute(341001,frozen,operator,true));
        assertEquals(0,count("13981100010"));
    }
    @Test void directWorkflowActionUsesSameValidationAndRejectsWrongDepartments() {
        jdbc.update("UPDATE sys_module_action SET flow_design_enabled=0 WHERE module_id=341001 AND action_code='invite'");
        var result=actions.execute(Map.of("moduleId",341001,"actionCode","invite","payload",body(row("13981100011",dept))));
        assertEquals("completed",result.get("status")); assertEquals(1,count("13981100011"));
        assertThrows(ApiException.class,()->actions.execute(Map.of("moduleId",341001,"actionCode","invite","payload",body(row("13981100012","错别字")))));
        assertEquals(0,count("13981100012"));
    }
    @Test void apiRequiresAuthenticationAndTheSelectedModulesInvitationPermission() throws Exception {
        var payload=json.writeValueAsString(body(row("13981100013",dept)));
        var anonymous=mvc.perform(post("/system/user/member-import/preview?moduleId=341001").contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();
        assertEquals(401,json.readTree(anonymous.getResponse().getContentAsByteArray()).path("code").asInt());
        String token=jwt.createToken(1L,operator);
        var valid=mvc.perform(post("/system/user/member-import/preview?moduleId=341001").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();
        assertEquals(200,json.readTree(valid.getResponse().getContentAsByteArray()).path("code").asInt());
        var wrongModule=mvc.perform(post("/system/user/member-import/preview?moduleId=341002").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();
        assertEquals(403,json.readTree(wrongModule.getResponse().getContentAsByteArray()).path("code").asInt());
        assertEquals(0,count("13981100013"));
    }
}
