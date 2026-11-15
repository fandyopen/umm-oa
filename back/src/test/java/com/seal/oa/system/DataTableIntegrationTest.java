package com.seal.oa.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:data_tables_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class DataTableIntegrationTest {
    @Autowired DataTableService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    long customer,foreignCustomer;
    long limitedAdmin;
    @BeforeEach void setup() {
        jdbc.update("INSERT INTO sys_user(tenant_id,dept_id,user_name,nick_name,password,status,del_flag) SELECT 1,dept_id,?,'其他管理员',password,'0','0' FROM sys_user WHERE user_id=1", "DTADMIN"+System.nanoTime());
        limitedAdmin=jdbc.queryForObject("SELECT MAX(user_id) FROM sys_user",Long.class);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,role_id FROM sys_role WHERE role_key='admin'",limitedAdmin);
        AuthContext.set(new AuthContext.CurrentUser(limitedAdmin,"other-admin"));
        customer=insert("INSERT INTO oa_merchant(tenant_id,name,status,public_pool) VALUES(1,?,'0','0')","DT-"+System.nanoTime());
        foreignCustomer=insert("INSERT INTO oa_merchant(tenant_id,name,status,public_pool) VALUES(2,?,'0','0')","FOREIGN-DT-"+System.nanoTime());
    }
    @AfterEach void clear() { AuthContext.clear(); }
    long insert(String sql,Object... args) { jdbc.update(sql,args); return jdbc.queryForObject("SELECT MAX(id) FROM oa_merchant",Long.class); }
    @SuppressWarnings("unchecked") List<Map<String,Object>> rows(String table,Map<String,String> q) { return (List<Map<String,Object>>)service.rows(table,q).get("rows"); }
    @SuppressWarnings("unchecked") Map<String,Object> values(Map<String,Object> row) { return (Map<String,Object>)row.get("values"); }
    Map<String,Object> body(Map<String,Object> row,Map<String,Object> values) { var b=new HashMap<String,Object>(); b.put("values",values); b.put("reason","独立测试库维护验证"); if(row!=null) { b.put("key",row.get("key")); b.put("version",row.get("version")); } return b; }
    String filter(String field,Object value) throws Exception { return json.writeValueAsString(List.of(Map.of("field",field,"op","eq","value",value))); }
    void full() { AuthContext.set(new AuthContext.CurrentUser(1L,"13610316814")); }

    @Test void discoversRealTablesAndCoreLabelsAndProtectsSystemFields() {
        var tables=service.list();
        assertTrue(tables.stream().anyMatch(t->t.get("name").equals("sys_user") && t.get("label").equals("成员账号") && Boolean.TRUE.equals(t.get("core"))));
        assertFalse((boolean)service.detail("sys_user").get("editAllowed"));
        assertFalse((boolean)service.detail("oa_finance_record").get("deleteAllowed"));
        assertTrue((boolean)service.detail("oa_merchant_contact").get("createAllowed"));
        assertFalse(tables.stream().anyMatch(t->t.get("name").equals("tables")));
    }
    @Test void authenticationAndSuperAdminAreBothRequiredEvenWithGrantedModule() {
        AuthContext.clear(); assertEquals(401,assertThrows(ApiException.class,service::list).code());
        long user=jdbc.queryForObject("SELECT MIN(user_id) FROM sys_user WHERE user_id<>1 AND user_id NOT IN (SELECT ur.user_id FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE r.role_key='admin')",Long.class);
        AuthContext.set(new AuthContext.CurrentUser(user,"restricted"));
        assertEquals(403,assertThrows(ApiException.class,service::list).code());
        assertThrows(ApiException.class,()->service.detail("sys_dict_type"));
        assertThrows(ApiException.class,()->service.history("sys_user",1,20));
        assertThrows(ApiException.class,()->service.save("sys_dict_type",body(null,Map.of("dict_name","x","dict_type","x")),true));
    }
    @Test void httpEndpointRejectsUnauthenticatedRequests() throws Exception {
        AuthContext.clear(); var result=mvc.perform(get("/system/data-tables")).andReturn();
        assertEquals(401,json.readTree(result.getResponse().getContentAsString()).get("code").asInt());
    }
    @Test void childDataIsTenantScopedAndCannotBeReassigned() throws Exception {
        jdbc.update("INSERT INTO oa_merchant_contact(merchant_id,name) VALUES(?,'跨企业联系人')",foreignCustomer);
        assertTrue(rows("oa_merchant_contact",Map.of("filters",filter("merchant_id",foreignCustomer))).isEmpty());
        assertThrows(ApiException.class,()->service.save("oa_merchant_contact",body(null,Map.of("merchant_id",foreignCustomer,"name","越权")),true));
        var row=service.save("oa_merchant_contact",body(null,Map.of("merchant_id",customer,"name","联系人")),true);
        assertThrows(ApiException.class,()->service.save("oa_merchant_contact",body(row,Map.of("merchant_id",foreignCustomer)),false));
        assertThrows(ApiException.class,()->service.save("oa_merchant_contact",body(row,Map.of("tenant_id",2)),false));
        assertTrue(rows("oa_merchant",Map.of("filters",filter("id",foreignCustomer))).isEmpty());
    }
    @Test void crudWritesAuditedBeforeAfterAndRejectsStaleVersions() throws Exception {
        var created=service.save("oa_merchant_contact",body(null,Map.of("merchant_id",customer,"name","原姓名","primary_contact","0")),true);
        var updated=service.save("oa_merchant_contact",body(created,Map.of("name","新姓名")),false);
        assertEquals("新姓名",values(updated).get("name"));
        assertEquals(409,assertThrows(ApiException.class,()->service.save("oa_merchant_contact",body(created,Map.of("name","旧请求")),false)).code());
        assertEquals(409,assertThrows(ApiException.class,()->service.delete("oa_merchant_contact",body(created,Map.of()))).code());
        service.delete("oa_merchant_contact",body(updated,Map.of()));
        var history=service.history("oa_merchant_contact",1,20); var text=json.writeValueAsString(history);
        assertTrue(text.contains("原姓名")); assertTrue(text.contains("新姓名")); assertTrue(text.contains("独立测试库维护验证"));
        assertEquals(3L,history.get("total"));
    }
    @Test void dictionaryReferencesAreCheckedWithoutDatabaseForeignKeys() {
        String type="dt_"+System.nanoTime();
        var parent=service.save("sys_dict_type",body(null,Map.of("dict_name","测试字典","dict_type",type)),true);
        var option=service.save("sys_dict_data",body(null,Map.of("dict_type",type,"dict_label","甲","dict_value","a")),true);
        assertFalse(service.impact("sys_dict_type",body(parent,Map.of())).isEmpty());
        assertEquals(409,assertThrows(ApiException.class,()->service.delete("sys_dict_type",body(parent,Map.of()))).code());
        assertThrows(ApiException.class,()->service.save("sys_dict_data",body(null,Map.of("dict_type",type,"dict_label","重复","dict_value","a")),true));
        service.delete("sys_dict_data",body(option,Map.of())); service.delete("sys_dict_type",body(parent,Map.of()));
    }
    @Test void sensitiveFieldsAndNestedJsonCredentialsDoNotLeaveService() throws Exception {
        String text=json.writeValueAsString(service.rows("sys_user",Map.of()));
        assertFalse(text.contains("\"password\"")); assertFalse(text.contains("$2a$"));
        assertFalse(json.writeValueAsString(service.rows("sys_config",Map.of())).contains("config_value"));
        jdbc.update("INSERT INTO oa_form_submission(tenant_id,form_key,data_json) VALUES(1,'dt_mask',?)","{\"nested\":{\"password\":\"LEAK-ME\",\"apiKey\":\"KEY-LEAK\"},\"ok\":\"visible\"}");
        text=json.writeValueAsString(service.rows("oa_form_submission",Map.of("filters",filter("form_key","dt_mask"))));
        assertFalse(text.contains("LEAK-ME")); assertFalse(text.contains("KEY-LEAK")); assertTrue(text.contains("visible"));
        assertThrows(ApiException.class,()->service.rows("sys_user",Map.of("sort","password")));
    }
    @Test void rejectsSqlIdentifiersOperatorsAndInvalidPagination() {
        for(String name:List.of("sys_user;drop table sys_user","sys_user WHERE 1=1","information_schema.tables","missing_table")) assertThrows(ApiException.class,()->service.detail(name));
        assertThrows(ApiException.class,()->service.rows("sys_user",Map.of("sort","user_id desc;--")));
        assertThrows(ApiException.class,()->service.rows("sys_user",Map.of("direction","desc;--")));
        assertThrows(ApiException.class,()->service.rows("sys_user",Map.of("size","999999")));
        assertThrows(ApiException.class,()->service.rows("sys_user",Map.of("filters","[{\"field\":\"user_id\",\"op\":\"eq OR 1=1\",\"value\":1}]")));
    }
    @Test void inputTypesNullsAndProtectedStatesAreValidated() throws Exception {
        assertThrows(ApiException.class,()->service.save("oa_merchant_contact",body(null,Map.of("merchant_id","1 OR 1=1")),true));
        assertThrows(ApiException.class,()->service.save("sys_dict_type",body(null,Map.of("dict_type","dt")),true));
        assertThrows(ApiException.class,()->service.save("sys_dict_type",Map.of("values",Map.of("dict_name","字典","dict_type","dt")),true));
        var row=rows("oa_merchant",Map.of("filters",filter("id",customer))).get(0);
        assertThrows(ApiException.class,()->service.save("oa_merchant",body(row,Map.of("status","1")),false));
        assertThrows(ApiException.class,()->service.save("oa_merchant",body(row,Map.of("id",999)),false));
        assertThrows(ApiException.class,()->service.delete("oa_merchant",body(row,Map.of())));
        assertThrows(ApiException.class,()->service.save("oa_merchant_contact",body(null,Map.of("merchant_id",customer,"phone","x".repeat(100))),true));
    }
    @Test void bigIntegerIdentifiersKeepTheirExactValues() {
        long id=9007199254740993L; jdbc.update("INSERT INTO oa_merchant(id,tenant_id,name,status,public_pool) VALUES(?,1,'精确ID','0','0')",id);
        var row=service.save("oa_merchant_contact",body(null,Map.of("merchant_id",Long.toString(id),"name","精确关联")),true);
        assertEquals("9007199254740993",values(row).get("merchant_id"));
    }
    @Test void invalidChangesDoNotWriteHistoryAndOtherTenantHistoryIsHidden() {
        long n=jdbc.queryForObject("SELECT COUNT(*) FROM sys_data_table_history",Long.class);
        assertThrows(ApiException.class,()->service.save("oa_merchant_contact",body(null,Map.of("merchant_id",foreignCustomer,"name","bad")),true));
        assertEquals(n,jdbc.queryForObject("SELECT COUNT(*) FROM sys_data_table_history",Long.class));
        jdbc.update("INSERT INTO sys_data_table_history(tenant_id,table_name,action,record_key,reason,operator_id,operator_name) VALUES(2,'oa_merchant','修改','{}','OTHER-TENANT',1,'other')");
        assertEquals(0L,service.history("oa_merchant",1,20).get("total"));
    }
    @Test void primaryContactMustBeUniqueAndClientCannotSupplyIncompleteKeys() {
        var row=service.save("oa_merchant_contact",body(null,Map.of("merchant_id",customer,"name","主要联系人","primary_contact","1")),true);
        assertThrows(ApiException.class,()->service.save("oa_merchant_contact",body(null,Map.of("merchant_id",customer,"primary_contact","1")),true));
        var b=body(row,Map.of("name","bad")); b.put("key",Map.of()); assertThrows(ApiException.class,()->service.save("oa_merchant_contact",b,false));
    }
    @Test void designatedAccountGetsFullPermissionsOnCoreAndWorkflowTables() {
        full();
        for (String table : List.of("sys_module","sys_user","oa_merchant","oa_hr_salary","oa_process_instance","oa_finance_record","sys_data_table_history")) {
            var meta=service.detail(table);
            assertEquals(true,meta.get("fullMaintenance"),table);
            assertEquals(true,meta.get("editAllowed"),table);
            assertEquals(true,meta.get("createAllowed"),table);
            assertEquals(true,meta.get("deleteAllowed"),table);
        }
        assertEquals(false,service.detail("sys_user_role").get("editAllowed"));
        assertEquals(true,service.detail("sys_user_role").get("createAllowed"));
        assertEquals(true,service.detail("sys_user_role").get("deleteAllowed"));
    }
    @Test void fullGrantUsesDatabaseIdentityAndCannotBeSpoofedWithContextUsername() {
        AuthContext.set(new AuthContext.CurrentUser(limitedAdmin,"13610316814"));
        assertEquals(false,service.detail("sys_module").get("fullMaintenance"));
        assertEquals(false,service.detail("sys_module").get("editAllowed"));
        full(); assertEquals(true,service.detail("sys_module").get("editAllowed"));
    }
    @Test void fullAccountCanCreateManualPrimaryKeyModifyModuleAndDeleteIt() throws Exception {
        full();
        String id="9007199254741001";
        var created=service.save("sys_module",body(null,Map.of("module_id",id,"module_name","完整权限测试","parent_id",340309,"path","dt_full")),true);
        assertEquals(id,values(created).get("module_id"));
        var updated=service.save("sys_module",body(created,Map.of("module_name","完整权限已修改","has_flow",0,"status","1")),false);
        assertEquals("完整权限已修改",values(updated).get("module_name"));
        service.delete("sys_module",body(updated,Map.of()));
        assertTrue(rows("sys_module",Map.of("filters",filter("module_id",id))).isEmpty());
        assertEquals(3L,service.history("sys_module",1,20).get("total"));
    }
    @Test void fullAccountSupportsCompositeKeysAndRelationChecks() {
        full();
        var module=service.save("sys_module",body(null,Map.of("module_id","9000001","module_name","复合键测试")),true);
        var action=service.save("sys_module_action",body(null,Map.of("module_id","9000001","action_code","test","action_name","测试动作")),true);
        assertEquals(2,((Map<?,?>)action.get("key")).size());
        var updated=service.save("sys_module_action",body(action,Map.of("action_name","更新动作","status","1")),false);
        assertEquals("更新动作",values(updated).get("action_name"));
        assertEquals(409,assertThrows(ApiException.class,()->service.delete("sys_module",body(module,Map.of()))).code());
        service.delete("sys_module_action",body(updated,Map.of()));
        service.delete("sys_module",body(module,Map.of()));
    }
    @Test void fullAccountCanMaintainFieldsPreviouslyBlockedByBusinessWhitelist() throws Exception {
        full(); var original=rows("oa_merchant",Map.of("filters",filter("id",customer))).get(0);
        var updated=service.save("oa_merchant",body(original,Map.of("status","1","cooperation_status","active")),false);
        assertEquals("1",values(updated).get("status"));
        assertEquals("active",values(updated).get("cooperation_status"));
        assertThrows(ApiException.class,()->service.save("oa_merchant",body(updated,Map.of("tenant_id",2)),false));
        assertThrows(ApiException.class,()->service.save("oa_merchant",body(updated,Map.of("id",1)),false));
        service.delete("oa_merchant",body(updated,Map.of()));
    }
    @Test void fullAccountCanCreateAndDeleteFinanceRecordsWithoutOldReadOnlyPolicy() {
        full();
        var row=service.save("oa_finance_receivable",body(null,Map.of("customer_id",customer,"title","数据维护应收","amount","10.50","due_date","2026-11-30")),true);
        var updated=service.save("oa_finance_receivable",body(row,Map.of("status","confirmed","amount","12.50")),false);
        assertEquals("confirmed",values(updated).get("status"));
        service.delete("oa_finance_receivable",body(updated,Map.of()));
    }
    @Test void sensitiveFieldsAreWritableWithoutExposingPasswordsOrHashes() throws Exception {
        full(); String login="dt_member_"+System.nanoTime(); String raw="Test-only-password!7";
        var row=service.save("sys_user",body(null,Map.of("user_name",login,"nick_name","测试成员","password",raw)),true);
        String hash=jdbc.queryForObject("SELECT password FROM sys_user WHERE user_name=?",String.class,login);
        assertTrue(passwords.matches(raw,hash)); assertNotEquals(raw,hash);
        String output=json.writeValueAsString(row); assertFalse(output.contains(raw)); assertFalse(output.contains(hash));
        var changed=service.save("sys_user",body(row,Map.of("password","Changed-test-only!8")),false);
        assertTrue(passwords.matches("Changed-test-only!8",jdbc.queryForObject("SELECT password FROM sys_user WHERE user_name=?",String.class,login)));
        String history=json.writeValueAsString(service.history("sys_user",1,20));
        assertFalse(history.contains(raw)); assertFalse(history.contains(hash)); assertFalse(history.contains("Changed-test-only!8"));
        service.delete("sys_user",body(changed,Map.of()));
    }
    @Test void hiddenValuesParticipateInVersionChecksAndDoNotLeak() throws Exception {
        full();
        var created=service.save("sys_config",body(null,Map.of("config_name","测试参数","config_key","dt_secret_"+System.nanoTime(),"config_value","first-secret")),true);
        jdbc.update("UPDATE sys_config SET config_value='second-secret' WHERE config_id=?",((Map<?,?>)created.get("key")).get("config_id"));
        assertEquals(409,assertThrows(ApiException.class,()->service.save("sys_config",body(created,Map.of("config_value","third-secret")),false)).code());
        assertFalse(json.writeValueAsString(service.rows("sys_config",Map.of())).contains("second-secret"));
    }
    @Test void moduleFieldsHaveChineseLabelsAndUnknownFieldsKeepOneFallbackName() {
        assertEquals("模块名称",DataTableRegistry.fieldLabel("module_name",null));
        assertEquals("上级ID",DataTableRegistry.fieldLabel("parent_id",null));
        assertEquals("路由名称",DataTableRegistry.fieldLabel("route_name",null));
        assertEquals("future_unknown_field",DataTableRegistry.fieldLabel("future_unknown_field",null));
    }

    long finance(String type, long owner, String status) {
        String token=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO oa_finance_record(record_type,customer_id,amount,status,create_token) VALUES(?,?,10,?,?)",type,owner,status,token);
        return jdbc.queryForObject("SELECT id FROM oa_finance_record WHERE create_token=?",Long.class,token);
    }
    Map<String,Object> financeRow(long id) throws Exception { return rows("oa_finance_record",Map.of("filters",filter("id",id))).get(0); }
    @SuppressWarnings("unchecked") List<Map<String,Object>> relatedRows(Map<String,Object> group) { return (List<Map<String,Object>>)group.get("rows"); }

    @Test void pageProfilesUseStableComponentsAndRetainDefaultTitleAliases() throws Exception {
        jdbc.update("UPDATE sys_module SET module_name='实际到账登记' WHERE component='eos/crm/finance/receipt' AND tenant_id=1");
        var p=service.pages().stream().filter(x->x.get("key").equals("finance-receipt")).findFirst().orElseThrow();
        assertTrue(p.get("title").toString().contains("回款登记"));
        assertTrue(p.get("currentTitle").toString().contains("实际到账登记"));
        assertEquals("oa_finance_record",p.get("table")); assertEquals("/finance/receipt",p.get("route"));
        assertTrue(service.pages().stream().anyMatch(x->x.get("title").toString().contains("员工考勤表")));
        for (var profile : service.pages()) {
            // Every registered profile must execute against this project's actual schema.
            service.rows(profile.get("table").toString(),Map.of("pageKey",profile.get("key").toString(),"size","1"));
        }
        String catalog=json.writeValueAsString(service.pages());
        assertTrue(catalog.contains("收费节点")); assertTrue(catalog.contains("payload_json"));
    }
    @Test void pageScopesAreServerEnforcedAndCannotMixOtherBusinessesOrTenants() throws Exception {
        long receipt=finance("receipt",customer,"draft"); finance("invoice",customer,"draft"); finance("receipt",foreignCustomer,"draft");
        var all=rows("oa_finance_record",Map.of("pageKey","finance-receipt"));
        assertTrue(all.stream().anyMatch(x->values(x).get("id").equals(""+receipt)));
        assertTrue(all.stream().allMatch(x->values(x).get("record_type").equals("receipt")));
        assertFalse(all.stream().anyMatch(x->values(x).get("customer_id").equals(""+foreignCustomer)));
        assertTrue(rows("oa_finance_record",Map.of("pageKey","finance-receipt","filters",filter("record_type","invoice"))).isEmpty());
        assertThrows(ApiException.class,()->service.rows("sys_user",Map.of("pageKey","finance-receipt")));
        assertThrows(ApiException.class,()->service.rows("oa_finance_record",Map.of("pageKey","finance-receipt OR 1=1")));
        finance("writeoff",customer,"draft"); finance("reversal",customer,"draft");
        assertTrue(rows("oa_finance_record",Map.of("pageKey","finance-adjustment")).stream().allMatch(x->Set.of("writeoff","reversal").contains(values(x).get("record_type"))));
    }
    @Test void opportunityProfileUsesCurrentPayloadTableAndContractsExcludeLogicalDeletes() throws Exception {
        jdbc.update("INSERT INTO biz_record(tenant_id,module,business_key,payload_json) VALUES(1,'/project/business/list','dt-opportunity','{}')");
        jdbc.update("INSERT INTO biz_record(tenant_id,module,business_key,payload_json) VALUES(1,'/other/list','dt-other','{}')");
        assertTrue(rows("biz_record",Map.of("pageKey","opportunities")).stream().allMatch(x->values(x).get("module").equals("/project/business/list")));
        assertEquals("biz_record",service.pages().stream().filter(x->x.get("key").equals("opportunities")).findFirst().orElseThrow().get("table"));
        jdbc.update("INSERT INTO oa_crm_contract(tenant_id,customer_id,code,name,service_type,start_date,end_date,terms_json,deleted) VALUES(1,?,'DT-DELETED','已删除测试合同','recruit','2026-01-01','2026-12-31','[]',1)",customer);
        long id=jdbc.queryForObject("SELECT id FROM oa_crm_contract WHERE code='DT-DELETED'",Long.class);
        assertFalse(rows("oa_crm_contract",Map.of("pageKey","contracts")).stream().anyMatch(x->values(x).get("id").equals(""+id)));
        assertEquals(1,rows("oa_crm_contract",Map.of("filters",filter("id",id))).size());
    }
    @Test void exactReceiptAssociationUsesReceiptIdAndHistoryIncludesDiscriminator() throws Exception {
        long id=finance("receipt",customer,"draft");
        long contract=90000123, receipt=90000456;
        jdbc.update("INSERT INTO oa_crm_contract(id,tenant_id,customer_id,code,name,service_type,start_date,end_date,terms_json) VALUES(?,1,?,'DT-RELATION','测试合同','recruit','2026-01-01','2026-12-31','[]')",contract,customer);
        for(long receiptId : List.of(receipt,id)) jdbc.update("INSERT INTO oa_crm_contract_receipt(id,contract_id,term_key,amount,receipt_date) VALUES(?,?,'term',10,'2026-11-01')",receiptId,contract);
        jdbc.update("UPDATE oa_finance_record SET contract_id=?,receipt_id=?,status='confirmed' WHERE id=?",contract,receipt,id);
        jdbc.update("INSERT INTO oa_finance_history(entity_type,entity_id,action,content) VALUES('record',?,'save','正确历史')",id);
        jdbc.update("INSERT INTO oa_finance_history(entity_type,entity_id,action,content) VALUES('receivable',?,'save','同ID不同业务')",id);
        jdbc.update("INSERT INTO oa_finance_history(entity_type,entity_id,action,content) VALUES('record',?,'save','其他记录')",id+1);
        var row=financeRow(id); var groups=service.related("oa_finance_record",body(row,Map.of()));
        var receipts=groups.stream().filter(g->g.get("table").equals("oa_crm_contract_receipt") && g.get("note").toString().startsWith("receipt_id")).findFirst().orElseThrow();
        assertEquals(""+receipt,values(relatedRows(receipts).get(0)).get("id")); assertEquals(1L,receipts.get("total"));
        full(); var actual=relatedRows(receipts).get(0);
        assertEquals(409,assertThrows(ApiException.class,()->service.delete("oa_crm_contract_receipt",body(actual,Map.of()))).code());
        var history=groups.stream().filter(g->g.get("id").equals("finance-history")).findFirst().orElseThrow();
        assertEquals(1L,history.get("total")); assertEquals("正确历史",values(relatedRows(history).get(0)).get("content"));
        String context=json.writeValueAsString(history.get("context"));
        assertEquals(1L,service.rows("oa_finance_history",Map.of("relationContext",context,"page","1","size","1")).get("total"));
        assertTrue(rows("oa_finance_history",Map.of("relationContext",context,"filters",filter("entity_type","receivable"))).isEmpty());
        assertThrows(ApiException.class,()->service.rows("sys_user",Map.of("relationContext",context)));
    }
    @Test void relatedNavigationIsScopedRedactedPaginatedAndRejectsStaleOrigins() throws Exception {
        full(); var parent=rows("oa_merchant",Map.of("filters",filter("id",customer))).get(0);
        for(int n=0;n<7;n++) jdbc.update("INSERT INTO oa_merchant_contact(merchant_id,name) VALUES(?,?)",customer,"联系人"+n);
        jdbc.update("INSERT INTO oa_merchant_contact(merchant_id,name) VALUES(?,'外部联系人')",foreignCustomer);
        var group=service.related("oa_merchant",body(parent,Map.of())).stream().filter(g->g.get("table").equals("oa_merchant_contact")).findFirst().orElseThrow();
        assertEquals(7L,group.get("total")); assertEquals(5,relatedRows(group).size());
        String context=json.writeValueAsString(group.get("context"));
        assertEquals(2,rows("oa_merchant_contact",Map.of("relationContext",context,"size","5","page","2")).size());
        var forged=new HashMap<>(body(parent,Map.of())); forged.put("key",Map.of("id",foreignCustomer));
        assertEquals(404,assertThrows(ApiException.class,()->service.related("oa_merchant",forged)).code());
        jdbc.update("UPDATE oa_merchant SET remark='已变化' WHERE id=?",customer);
        assertEquals(409,assertThrows(ApiException.class,()->service.rows("oa_merchant_contact",Map.of("relationContext",context))).code());
        var employee=rows("sys_user",Map.of("filters",filter("user_id",limitedAdmin))).get(0);
        String output=json.writeValueAsString(service.related("sys_user",body(employee,Map.of())));
        assertFalse(output.contains("$2a$")); assertFalse(output.contains("\"password\""));
    }
    @Test void pageScopedWritesRejectOutsideRowsAndChangesToLockedBusinessType() throws Exception {
        full(); long receipt=finance("receipt",customer,"draft"),invoice=finance("invoice",customer,"draft");
        var wrong=body(financeRow(invoice),Map.of()); wrong.put("pageKey","finance-receipt");
        assertEquals(409,assertThrows(ApiException.class,()->service.delete("oa_finance_record",wrong)).code());
        var change=body(financeRow(receipt),Map.of("record_type","invoice")); change.put("pageKey","finance-receipt");
        assertEquals(409,assertThrows(ApiException.class,()->service.save("oa_finance_record",change,false)).code());
        assertEquals("receipt",jdbc.queryForObject("SELECT record_type FROM oa_finance_record WHERE id=?",String.class,receipt));
    }
    @Test void confirmedOrSubmittedFinanceCannotBeRawDeletedEvenByFullAdmin() throws Exception {
        full();
        for(String status:List.of("confirmed","approving","approved","rejected","void")) {
            var row=financeRow(finance("receipt",customer,status));
            assertFalse(row.get("maintenanceBlock").toString().isBlank());
            assertEquals(409,assertThrows(ApiException.class,()->service.delete("oa_finance_record",body(row,Map.of()))).code());
            assertEquals(409,assertThrows(ApiException.class,()->service.save("oa_finance_record",body(row,Map.of("status","draft")),false)).code());
        }
        var row=financeRow(finance("receipt",customer,"draft"));
        assertEquals(409,assertThrows(ApiException.class,()->service.save("oa_finance_record",body(row,Map.of("status","confirmed")),false)).code());
        long request=90000567;
        jdbc.update("INSERT INTO oa_workflow_request(id,tenant_id,module_id,action_code,title) VALUES(?,1,341403,'submit','测试审批')",request);
        jdbc.update("UPDATE oa_finance_record SET workflow_request_id=? WHERE id=?",request,values(row).get("id"));
        final var submitted=financeRow(Long.parseLong(values(row).get("id").toString()));
        assertEquals(409,assertThrows(ApiException.class,()->service.delete("oa_finance_record",body(submitted,Map.of()))).code());
    }
    @Test void pristineDraftDeleteArchivesOnlyItsOwnHistoryAndKeepsMaintenanceAudit() throws Exception {
        full(); long id=finance("receipt",customer,"draft"),other=finance("receipt",customer,"draft");
        jdbc.update("INSERT INTO oa_finance_history(entity_type,entity_id,action,content) VALUES('record',?,'save','需保留审计的草稿历史')",id);
        jdbc.update("INSERT INTO oa_finance_history(entity_type,entity_id,action,content) VALUES('record',?,'save','其他草稿历史')",other);
        jdbc.update("INSERT INTO oa_finance_history(entity_type,entity_id,action,content) VALUES('receivable',?,'save','另一业务历史')",id);
        var b=body(financeRow(id),Map.of()); b.put("pageKey","finance-receipt"); service.delete("oa_finance_record",b);
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM oa_finance_record WHERE id=?",Long.class,id));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM oa_finance_history WHERE entity_type='record' AND entity_id=?",Long.class,id));
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM oa_finance_history WHERE entity_type='record' AND entity_id=?",Long.class,other));
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM oa_finance_history WHERE entity_type='receivable' AND entity_id=?",Long.class,id));
        assertTrue(json.writeValueAsString(service.history("oa_finance_history",1,20)).contains("需保留审计的草稿历史"));
        assertEquals(1L,service.history("oa_finance_record",1,20).get("total"));
    }
    @Test void draftWithReferencesCannotBeDeletedAndItsHistoryIsNotRemoved() throws Exception {
        full(); long id=finance("receipt",customer,"draft"),refund=finance("refund",customer,"draft");
        jdbc.update("UPDATE oa_finance_record SET original_record_id=? WHERE id=?",id,refund);
        jdbc.update("INSERT INTO oa_finance_history(entity_type,entity_id,action,content) VALUES('record',?,'save','仍保留')",id);
        var row=financeRow(id);
        assertFalse(service.impact("oa_finance_record",body(row,Map.of())).isEmpty());
        assertEquals(409,assertThrows(ApiException.class,()->service.delete("oa_finance_record",body(row,Map.of()))).code());
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM oa_finance_history WHERE entity_type='record' AND entity_id=?",Long.class,id));
    }
    @Test void rawDraftCreationUsesDatabaseDefaultsAndRelatedEditsCannotMoveToAnotherParent() throws Exception {
        full();
        var created=service.save("oa_finance_record",body(null,Map.of("record_type","receipt","customer_id",customer,"amount","5.00","create_token",UUID.randomUUID().toString())),true);
        assertEquals("draft",values(created).get("status"));
        var parent=rows("oa_merchant",Map.of("filters",filter("id",customer))).get(0);
        var group=service.related("oa_merchant",body(parent,Map.of())).stream().filter(g->g.get("table").equals("oa_finance_record")).findFirst().orElseThrow();
        long another=insert("INSERT INTO oa_merchant(tenant_id,name) VALUES(1,'另一个客户')");
        var move=body(created,Map.of("customer_id",another)); move.put("relationContext",json.writeValueAsString(group.get("context")));
        assertEquals(409,assertThrows(ApiException.class,()->service.save("oa_finance_record",move,false)).code());
        assertEquals(""+customer,values(financeRow(Long.parseLong(values(created).get("id").toString()))).get("customer_id"));
    }
}
