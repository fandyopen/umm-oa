package com.seal.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.auth.JwtService;
import com.seal.oa.recruitment.RecruitmentMonitorService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:recruitment_monitor;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class RecruitmentMonitorIntegrationTest {
    @Autowired RecruitmentMonitorService monitor;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    final LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).withNano(0);
    final long employee = 99881, outsider = 99882, role = 99881, dept = 99881, otherDept = 99882;
    long customer, job, talent, app;

    @BeforeEach void setup() {
        jdbc.update("INSERT INTO sys_dept(dept_id,dept_name) VALUES (?,'看板测试招聘部'),(?,'看板测试外部')",dept,otherDept);
        jdbc.update("INSERT INTO sys_role(role_id,role_name,role_key,data_scope) VALUES (?,'看板统计测试','monitor_test','3')",role);
        for (long id : List.of(employee, outsider)) {
            jdbc.update("INSERT INTO sys_user(user_id,tenant_id,dept_id,user_name,nick_name,password,status,del_flag) SELECT ?,1,?,?,?,password,'0','0' FROM sys_user WHERE user_id=4",id,id==employee?dept:otherDept,"monitor"+id,id==employee?"招聘甲":"招聘乙");
        }
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)",employee,role);
        jdbc.update("INSERT INTO sys_role_module(role_id,module_id) VALUES (?,341304)",role);
        jdbc.update("INSERT INTO oa_merchant(name,owner_user_id) VALUES ('看板测试客户',?)",outsider);
        customer=jdbc.queryForObject("SELECT MAX(id) FROM oa_merchant",Long.class);
        jdbc.update("INSERT INTO oa_recruit_job(customer_id,owner_user_id,title,code,create_by,status,demand_status) VALUES (?,?,'跨部门职位','MONITOR-TEST',?,'recruiting','approved')",customer,outsider,outsider);
        job=jdbc.queryForObject("SELECT MAX(id) FROM oa_recruit_job",Long.class);
        talent=talent(employee,"人才甲");
        app=application(talent,employee,"recommended");
        AuthContext.set(new AuthContext.CurrentUser(employee,"monitor"+employee));
    }
    @AfterEach void clear() { AuthContext.clear(); }

    @Test void moduleSelectionGrantsStatisticsButNeverChangesBusinessPermissions() throws Exception {
        AuthContext.clear();
        String token="Bearer "+jwt.createToken(employee,"monitor"+employee);
        mvc.perform(get("/project/recruitment/monitor").header("Authorization",token))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.summary.active").value(1));
        mvc.perform(get("/project/recruitment/applications").header("Authorization",token)).andExpect(jsonPath("$.code").value(403));
        mvc.perform(get("/project/recruitment/monitor/details").param("metric","active").header("Authorization",token))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.total").value(1));
        jdbc.update("DELETE FROM sys_role_module WHERE role_id=?",role);
        mvc.perform(get("/project/recruitment/monitor").header("Authorization",token)).andExpect(jsonPath("$.code").value(403));
    }

    @Test void personnelScopeProtectsOptionsTotalsRankingsAndDrilldowns() {
        long external = application(talent(outsider,"外部人才"), outsider,"recommended");
        event(app,"create",now.minusDays(2),Map.of());
        event(external,"create",now.minusDays(1),Map.of());
        JsonNode data=overview(Map.of());
        assertEquals(1,data.path("summary").path("active").asInt());
        assertEquals(1,data.path("summary").path("recommended").asInt());
        assertEquals(1,data.path("options").path("users").size());
        assertEquals(employee,data.path("options").path("users").get(0).path("id").asLong());
        assertEquals(0,overview(Map.of("userId",String.valueOf(outsider))).path("summary").path("active").asInt());
        assertEquals(0,mapper.valueToTree(monitor.details(Map.of("metric","active","deptId",String.valueOf(otherDept)))).path("total").asInt());
        // The job and customer are owned by another department, but this consultant's work remains visible.
        assertEquals(1,overview(Map.of("jobId",String.valueOf(job))).path("summary").path("active").asInt());
        assertEquals(1,mapper.valueToTree(monitor.details(Map.of("metric","active"))).path("total").asInt());
    }

    @Test void stagnantStartsAt72HoursAndAdministrativeEditsDoNotResetIt() {
        jdbc.update("UPDATE oa_recruit_application SET create_time=? WHERE id=?",now.minusHours(73),app);
        event(app,"create",now.minusHours(73),Map.of());
        event(app,"export",now.minusMinutes(10),Map.of());
        event(app,"attachment",now.minusMinutes(8),Map.of());
        event(app,"offer",now.minusMinutes(5),Map.of("operation","save"));
        assertEquals(1,overview(Map.of()).path("summary").path("stagnant").asInt());
        event(app,"feedback",now.minusHours(71),Map.of());
        assertEquals(0,overview(Map.of()).path("summary").path("stagnant").asInt());
        jdbc.update("UPDATE oa_recruit_application SET feedback_due_date=?,feedback_status='pending' WHERE id=?",now.toLocalDate().minusDays(1),app);
        event(app,"feedback",now.minusHours(1),Map.of());
        assertEquals(1,overview(Map.of()).path("summary").path("overdue").asInt(),"Recent followup must not erase an overdue deadline");
        assertEquals(0,overview(Map.of()).path("summary").path("stagnant").asInt());
    }

    @Test void milestonesDeduplicateAcrossRoundsReissuesAndMonthsAndUseActualEntryDate() {
        LocalDateTime previous = now.toLocalDate().withDayOfMonth(1).minusDays(1).atTime(12,0);
        event(app,"create",previous,Map.of());
        event(app,"recommend",now.minusMinutes(5),Map.of("operation","submit"));
        event(app,"interview_result",previous,Map.of("result","pending"));
        event(app,"interview_result",now.minusMinutes(4),Map.of("result","pass"));
        event(app,"offer",previous,Map.of("operation","issue"));
        event(app,"offer",now.minusMinutes(3),Map.of("operation","issue"));
        event(app,"entry",now.minusMinutes(2),Map.of("operation","confirm"));
        event(app,"guarantee",now.minusMinutes(1),Map.of("operation","complete"));
        event(app,"guarantee",now,Map.of("operation","complete"));
        jdbc.update("UPDATE oa_recruit_application SET actual_entry_date=?,stage='completed',guarantee_status='completed' WHERE id=?",previous.toLocalDate(),app);
        JsonNode current=overview(Map.of());
        for(String key:List.of("recommended","interviewed","offered","entered")) assertEquals(0,current.path("summary").path(key).asInt(),key);
        assertEquals(1,current.path("summary").path("completed").asInt());
        JsonNode all=overview(Map.of("startDate",previous.toLocalDate().toString()));
        for(String key:List.of("recommended","interviewed","offered","entered","completed")) assertEquals(1,all.path("summary").path(key).asInt(),key);
        assertEquals(0,all.path("summary").path("active").asInt());
        jdbc.update("UPDATE oa_recruit_application SET actual_entry_date=? WHERE id=?",now.toLocalDate(),app);
        assertEquals(1,overview(Map.of()).path("summary").path("entered").asInt());
    }

    @Test void contactIsCountedOnceAndResetsPipelineButNotIndependentTasks() {
        jdbc.update("UPDATE oa_recruit_application SET create_time=? WHERE id=?",now.minusDays(5),app);
        jdbc.update("INSERT INTO oa_recruit_event(entity_type,entity_id,event_type,content,payload_json,operator_id,create_time) VALUES ('talent',?,'contact','联系结果','{}',?,?)",talent,employee,now.minusHours(1));
        jdbc.update("INSERT INTO oa_recruit_task(talent_id,owner_user_id,title,due_date,create_by,create_time) VALUES (?,?,'独立跟进任务',?,?,?)",talent,employee,now.toLocalDate().minusDays(1),employee,now.minusDays(5));
        JsonNode data=overview(Map.of());
        assertEquals(1,data.path("summary").path("followups").asInt());
        assertEquals(1,data.path("summary").path("stagnant").asInt(),"Old task must remain stagnant");
        assertEquals(1,data.path("summary").path("overdue").asInt());
        assertEquals(2,data.path("summary").path("pending").asInt());
        JsonNode filtered=overview(Map.of("customerId",String.valueOf(customer)));
        assertEquals(1,filtered.path("summary").path("pending").asInt());
        assertEquals(0,filtered.path("summary").path("stagnant").asInt());
        assertEquals(1,filtered.path("summary").path("followups").asInt());
    }

    @Test void pendingResultAndPastInterviewAreMonitoredButTerminalRecordsAreNotStagnant() {
        jdbc.update("UPDATE oa_recruit_application SET stage='interview',create_time=? WHERE id=?",now.minusDays(5),app);
        jdbc.update("INSERT INTO oa_recruit_interview(application_id,round_no,interview_time,status,result,update_time) VALUES (?,1,?,'scheduled',null,?)",app,now.minusHours(1),now);
        assertEquals(1,overview(Map.of()).path("summary").path("overdue").asInt());
        jdbc.update("UPDATE oa_recruit_interview SET status='completed',result='pending',update_time=? WHERE application_id=?",now.minusDays(4),app);
        assertEquals(1,overview(Map.of()).path("summary").path("overdue").asInt());
        jdbc.update("UPDATE oa_recruit_application SET stage='ended' WHERE id=?",app);
        assertEquals(0,overview(Map.of()).path("summary").path("stagnant").asInt());
        assertEquals(0,overview(Map.of()).path("summary").path("overdue").asInt());
    }

    @Test void invalidDatesAndMetricFailAndPaginationMatchesSummary() {
        assertThrows(com.seal.oa.common.ApiException.class,()->monitor.overview(Map.of("startDate","2026-12-31","endDate","2026-01-01")));
        assertThrows(com.seal.oa.common.ApiException.class,()->monitor.details(Map.of("metric","unknown")));
        application(talent(employee,"人才丙"),employee,"recommended");
        JsonNode details=mapper.valueToTree(monitor.details(Map.of("metric","active","pageSize","1","pageNum","2")));
        assertEquals(2,details.path("total").asInt()); assertEquals(1,details.path("rows").size());
        assertFalse(details.toString().contains("payloadJson"));
        assertFalse(details.toString().contains("phone"));
    }

    JsonNode overview(Map<String,String> query) { return mapper.valueToTree(monitor.overview(query)); }
    long talent(long owner,String name) {
        jdbc.update("INSERT INTO oa_recruit_talent(owner_user_id,name,status,create_by) VALUES (?,?,'available',?)",owner,name,owner);
        return jdbc.queryForObject("SELECT MAX(id) FROM oa_recruit_talent",Long.class);
    }
    long application(long talentId,long consultant,String stage) {
        jdbc.update("INSERT INTO oa_recruit_application(job_id,talent_id,consultant_id,stage,submitted_date) VALUES (?,?,?,?,?)",job,talentId,consultant,stage,now.toLocalDate());
        return jdbc.queryForObject("SELECT MAX(id) FROM oa_recruit_application",Long.class);
    }
    void event(long application,String type,LocalDateTime at,Map<String,Object> payload) {
        try { jdbc.update("INSERT INTO oa_recruit_event(entity_type,entity_id,event_type,content,payload_json,operator_id,create_time) VALUES ('application',?,?,?, ?,?,?)",application,type,type,mapper.writeValueAsString(payload),employee,at); }
        catch(Exception e) { throw new RuntimeException(e); }
    }
}
