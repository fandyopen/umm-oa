package com.seal.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.system.ModuleActionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:umm_oa_recruitment;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1","app.recruit-private-dir=./target/recruitment-test-files"})
@AutoConfigureMockMvc
@Transactional
class RecruitmentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ModuleActionService actions;
    String admin,reviewer;
    long customer,contract;
    final String base="/project/recruitment";
    final LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));

    @BeforeEach void setup()throws Exception{
        admin=login("13610316814");
        jdbc.update("INSERT INTO sys_role (role_id,role_name,role_key,data_scope) VALUES (991,'招聘测试','recruit_test','5')");
        jdbc.update("INSERT INTO sys_user (user_id,tenant_id,dept_id,user_name,nick_name,password,status,del_flag) SELECT 901,1,325,'17690000901','招聘审批员',password,'0','0' FROM sys_user WHERE user_id=4");
        jdbc.update("INSERT INTO sys_user_role (user_id,role_id) VALUES (901,991)");
        jdbc.update("INSERT INTO sys_role_module (role_id,module_id) VALUES (991,341302)");
        reviewer=login("17690000901");
        customer=call(admin,post("/project/merchant"),Map.of("name","招聘闭环测试客户","ownerUserId",1),200).path("data").asLong();
        jdbc.update("INSERT INTO oa_crm_contract (customer_id,code,name,service_type,amount,start_date,end_date,status,effective_version,terms_json) VALUES (?,'RECRUIT-TEST','招聘合同','猎头',1000,?,?,'active',1,'[]')",customer,today.minusDays(1),today.plusDays(365));
        contract=jdbc.queryForObject("SELECT id FROM oa_crm_contract WHERE code='RECRUIT-TEST'",Long.class);
        jdbc.update("INSERT INTO oa_crm_contract_version (contract_id,version_no,snapshot_json,status,submit_user_id,approval_user_id) VALUES (?,1,?,'active',1,901)",contract,mapper.writeValueAsString(Map.of("amount",1000,"endDate",today.plusDays(365).toString(),"feeTerms",List.of(Map.of("key","entry","title","入职服务费","guaranteeDays",0)))));
    }

    @Test void recommendationCreationOnlyAllowsContactingAndAvailableTalents()throws Exception{
        JsonNode j=job();int index=0;
        for(String state:List.of("draft","uncontacted","not_now","dormant","archived","disabled","contacting","available")){
            JsonNode t=call(admin,post(base+"/talents"),Map.of("name","候选状态验证"+index,"phone","1380000250"+index++),200).path("data");
            long talentId=t.path("id").asLong();
            jdbc.update("UPDATE oa_recruit_talent SET status=? WHERE id=?",state,talentId);
            boolean allowed=Set.of("contacting","available").contains(state);
            call(admin,post(base+"/applications"),Map.of("jobId",j.path("id").asLong(),"talentId",talentId,"recommendation","验证原有候选状态限制"),allowed?200:409);
            assertEquals(state,jdbc.queryForObject("SELECT status FROM oa_recruit_talent WHERE id=?",String.class,talentId));
        }
    }

    @Test void directRecommendationUpdatesMetricsAndWithdrawalCanBeResubmitted()throws Exception{
        JsonNode j=job(),t=talent("直接推荐人才","13800002601"),a=recommend(j,t);long id=a.path("id").asLong();
        assertEquals(1,jdbc.queryForObject("SELECT referral_count FROM oa_merchant WHERE id=?",Integer.class,customer));
        assertEquals(1,getJob(j.path("id").asLong()).path("recommendations").asInt());
        assertEquals("创建并提交客户推荐",a.path("events").get(0).path("content").asText());
        call(admin,post(base+"/applications/"+id+"/actions/recommend"),Map.of("revision",a.path("revision").asInt(),"reason","不能二次提交"),409);
        a=act(a,"recommend",Map.of("operation","withdraw"));assertEquals("withdrawn",a.path("stage").asText());
        assertEquals(0,jdbc.queryForObject("SELECT referral_count FROM oa_merchant WHERE id=?",Integer.class,customer));
        jdbc.update("UPDATE oa_recruit_talent SET status='archived' WHERE id=?",t.path("id").asLong());
        call(admin,post(base+"/applications/"+id+"/actions/recommend"),Map.of("revision",a.path("revision").asInt(),"reason","候选状态需重检"),409);
        jdbc.update("UPDATE oa_recruit_talent SET status='available' WHERE id=?",t.path("id").asLong());
        a=act(a,"recommend",Map.of());assertEquals("recommended",a.path("stage").asText());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_application WHERE job_id=? AND talent_id=?",Integer.class,j.path("id").asLong(),t.path("id").asLong()));
        assertEquals(1,jdbc.queryForObject("SELECT referral_count FROM oa_merchant WHERE id=?",Integer.class,customer));
        a=act(a,"feedback",Map.of("status","interested"));assertEquals("feedback",a.path("stage").asText());
    }

    @Test void onlyInterestedFeedbackCanAdvanceAndOtherResultsRemainInFeedback()throws Exception{
        JsonNode a=recommend(job(),talent("反馈阶段校验","13800002602"));long id=a.path("id").asLong();
        for(String feedback:List.of("pending","rejected","need_info")){
            a=act(a,"feedback",Map.of("status",feedback));
            assertEquals("feedback",a.path("stage").asText());assertEquals(feedback,a.path("feedbackStatus").asText());
            JsonNode row=call(admin,get(base+"/applications"),null,200).path("rows").get(0);
            assertEquals("feedback",row.path("stage").asText());assertEquals(feedback,row.path("feedbackStatus").asText());
            call(admin,post(base+"/applications/"+id+"/interviews"),Map.of("revision",a.path("revision").asInt(),"reason","不可越过反馈"),409);
            call(admin,post(base+"/applications/"+id+"/actions/offer"),Map.of("revision",a.path("revision").asInt(),"reason","不可越过反馈","salary",10000),409);
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_interview WHERE application_id=?",Integer.class,id));
        }
        a=act(a,"feedback",Map.of("status","interested"));assertEquals("feedback",a.path("stage").asText());
        a=call(admin,post(base+"/applications/"+id+"/interviews"),Map.of("revision",a.path("revision").asInt(),"roundNo",1,"interviewTime",LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1).withNano(0).toString(),"location","会议室","interviewer","客户主管","reason","客户确认有意向"),200).path("data");
        assertEquals("interview",a.path("stage").asText());assertEquals("scheduled",a.path("interviewStatus").asText());
        a=act(a,"feedback",Map.of("status","need_info"));assertEquals("feedback",a.path("stage").asText());
        call(admin,post(base+"/applications/"+id+"/interviews"),Map.of("revision",a.path("revision").asInt(),"reason","补资料期间不能继续安排"),409);
        a=act(a,"feedback",Map.of("status","rejected"));assertEquals("feedback",a.path("stage").asText());
        assertEquals("cancelled",a.path("interviews").get(0).path("status").asText());
        a=act(a,"feedback",Map.of("status","interested"));
        a=call(admin,post(base+"/applications/"+id+"/interviews"),Map.of("revision",a.path("revision").asInt(),"roundNo",1,"interviewTime",LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(2).withNano(0).toString(),"location","会议室","interviewer","客户主管","reason","重新确认有意向"),200).path("data");
        assertEquals(2,a.path("interviews").size());assertEquals("scheduled",a.path("interviewStatus").asText());
        JsonNode row=call(admin,get(base+"/applications"),null,200).path("rows").get(0);assertEquals("scheduled",row.path("interviewStatus").asText());
    }

    @Test void interviewSubstateUsesTheLatestActiveRoundWithoutLosingHistory()throws Exception{
        JsonNode a=act(recommend(job(),talent("最新面试状态","13800002603")),"feedback",Map.of("status","interested"));long id=a.path("id").asLong();
        for(int round=1;round<=2;round++){
            a=call(admin,post(base+"/applications/"+id+"/interviews"),Map.of("revision",a.path("revision").asInt(),"roundNo",round,"interviewTime",LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(round).withNano(0).toString(),"location","会议室","interviewer","客户主管","reason","第"+round+"轮"),200).path("data");
            JsonNode row=call(admin,get(base+"/applications"),null,200).path("rows").get(0);assertEquals("scheduled",row.path("interviewStatus").asText());assertTrue(row.path("interviewResult").isMissingNode()||row.path("interviewResult").isNull());
            jdbc.update("UPDATE oa_recruit_interview SET status='completed',result='pass' WHERE application_id=? AND round_no=?",id,round);
            a=call(admin,get(base+"/applications/"+id),null,200).path("data");assertEquals("completed",a.path("interviewStatus").asText());assertEquals("pass",a.path("interviewResult").asText());
        }
        assertEquals(2,a.path("interviews").size());
    }

    @Test void declinedFeedbackOnlyAllowsUpdateOrExplicitFinishAndPreservesHistory()throws Exception{
        JsonNode a=act(recommend(job(),talent("手动结束人才","13800002701")),"feedback",Map.of("status","rejected"));long id=a.path("id").asLong();
        assertEquals(Set.of("feedback","finishRecommendation"),operations(a));
        assertEquals(operations(a),operations(call(admin,get(base+"/applications"),null,200).path("rows").get(0)));
        call(admin,post(base+"/applications/"+id+"/actions/recommend"),Map.of("revision",a.path("revision").asInt(),"operation","withdraw","reason","不合适无需撤回"),409);
        call(admin,post(base+"/applications/"+id+"/actions/exception"),Map.of("revision",a.path("revision").asInt(),"type","offer_reject","reason","反馈阶段不能拒绝Offer"),400);
        call(admin,post(base+"/applications/"+id+"/actions/exception"),Map.of("revision",a.path("revision").asInt(),"operation","finish"),400);
        a=act(a,"exception",Map.of("operation","finish"));assertEquals("ended",a.path("stage").asText());assertTrue(operations(a).isEmpty());
        assertEquals("rejected",a.path("feedbackStatus").asText());assertEquals("技能匹配",a.path("recommendation").asText());assertTrue(a.path("events").size()>=3);assertEquals(0,a.path("exceptions").size());
        call(admin,post(base+"/applications/"+id+"/actions/feedback"),Map.of("revision",a.path("revision").asInt(),"status","interested","reason","终结后不能普通推进"),409);
        assertEquals(1,jdbc.queryForObject("SELECT referral_count FROM oa_merchant WHERE id=?",Integer.class,customer));
    }

    @Test void interviewEntriesFollowRoundAndResultBeforeOfferAppears()throws Exception{
        JsonNode a=schedule(act(recommend(job(),talent("轮次入口人才","13800002702")),"feedback",Map.of("status","interested")),1);long id=a.path("id").asLong(),interview=a.path("interviews").get(0).path("id").asLong();
        assertFalse(operations(a).contains("interviewManage"));assertTrue(operations(a).contains("interviewResult"));assertFalse(operations(a).contains("interview"));assertFalse(operations(a).contains("offer"));
        assertEquals(Set.of("save","notify","result","cancel"),operations(a.path("interviews").get(0)));
        call(admin,put(base+"/applications/"+id+"/interviews/"+interview),Map.of("revision",a.path("revision").asInt(),"operation","result","result","pass"),400);
        a=call(admin,put(base+"/applications/"+id+"/interviews/"+interview),Map.of("revision",a.path("revision").asInt(),"operation","notify","reason","实际已通知"),200).path("data");assertFalse(operations(a.path("interviews").get(0)).contains("notify"));
        call(admin,post(base+"/applications/"+id+"/interviews"),Map.of("revision",a.path("revision").asInt(),"reason","不能跳过当前轮次"),409);
        jdbc.update("UPDATE oa_recruit_interview SET interview_time=? WHERE id=?",LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusHours(1),interview);
        a=call(admin,get(base+"/applications/"+id),null,200).path("data");assertEquals(Set.of("result","cancel"),operations(a.path("interviews").get(0)));
        assertTrue(operations(a).contains("interviewResult"));assertEquals(interview,a.path("activeInterviewId").asLong());
        assertEquals(operations(a),operations(call(admin,get(base+"/applications"),null,200).path("rows").get(0)));
        a=call(admin,put(base+"/applications/"+id+"/interviews/"+interview),Map.of("revision",a.path("revision").asInt(),"operation","result","result","pending","reason","等待结论"),200).path("data");
        assertFalse(operations(a).contains("offer"));assertFalse(operations(a).contains("interview"));assertTrue(operations(a).contains("interviewResult"));assertEquals(Set.of("result"),operations(a.path("interviews").get(0)));
        a=call(admin,put(base+"/applications/"+id+"/interviews/"+interview),Map.of("revision",a.path("revision").asInt(),"operation","result","result","pass","reason","补充通过结论"),200).path("data");
        assertTrue(operations(a).containsAll(Set.of("interview","offer")));assertFalse(operations(a).contains("interviewResult"));assertFalse(operations(a).contains("interviewManage"));
        a=schedule(a,2);assertFalse(operations(a).contains("offer"));assertEquals(2,a.path("interviews").size());
    }

    @Test void directInterviewResultCanFinishAnEarlierPendingRoundAndThenContinueToOffer()throws Exception{
        JsonNode a=schedule(act(recommend(job(),talent("直接结果人才","13800002801")),"feedback",Map.of("status","interested")),1);long id=a.path("id").asLong(),first=a.path("interviews").get(0).path("id").asLong();
        LocalDateTime past=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusHours(2);
        jdbc.update("UPDATE oa_recruit_interview SET interview_time=?,status='completed',result='pending' WHERE id=?",past,first);
        jdbc.update("INSERT INTO oa_recruit_interview (application_id,round_no,interview_time,status,result) VALUES (?,2,?,'completed','pass')",id,past.plusHours(1));
        a=call(admin,get(base+"/applications/"+id),null,200).path("data");
        assertEquals("pass",a.path("interviewResult").asText());assertEquals(first,a.path("activeInterviewId").asLong());assertTrue(operations(a).contains("interviewResult"));assertFalse(operations(a).contains("offer"));
        a=call(admin,put(base+"/applications/"+id+"/interviews/"+first),Map.of("revision",a.path("revision").asInt(),"operation","result","result","pass","reason","补齐上一轮待定结论"),200).path("data");
        assertFalse(operations(a).contains("interviewResult"));assertTrue(operations(a).contains("offer"));assertEquals(2,a.path("interviews").size());
        a=act(a,"offer",Map.of("salary",15000));assertEquals("offer",a.path("stage").asText());
    }

    @Test void futureAppointmentDoesNotHideResultEntryOrPreventActualResultRegistration()throws Exception{
        JsonNode a=schedule(act(recommend(job(),talent("提前完成面试人才","13800002803")),"feedback",Map.of("status","interested")),1);long id=a.path("id").asLong(),interview=a.path("interviews").get(0).path("id").asLong();
        String appointment=a.path("interviews").get(0).path("interviewTime").asText();
        assertTrue(operations(a).contains("interviewResult"));assertTrue(operations(a.path("interviews").get(0)).contains("result"));
        assertTrue(operations(call(admin,get(base+"/applications"),null,200).path("rows").get(0)).contains("interviewResult"));
        a=call(admin,put(base+"/applications/"+id+"/interviews/"+interview),Map.of("revision",a.path("revision").asInt(),"operation","result","result","pending","reason","实际已完成面试，等待客户最终结论"),200).path("data");
        assertEquals("pending",a.path("interviewResult").asText());assertTrue(operations(a).contains("interviewResult"));assertEquals(Set.of("result"),operations(a.path("interviews").get(0)));assertFalse(operations(a).contains("offer"));
        a=call(admin,put(base+"/applications/"+id+"/interviews/"+interview),Map.of("revision",a.path("revision").asInt(),"operation","result","result","pass","reason","客户确认面试通过"),200).path("data");
        assertEquals(appointment,a.path("interviews").get(0).path("interviewTime").asText());assertTrue(operations(a).contains("offer"));assertFalse(operations(a).contains("interviewResult"));
        a=act(a,"offer",Map.of("salary",18000));assertEquals("offer",a.path("stage").asText());
    }

    @Test void failedInterviewEndsProgressAndCancelledInterviewCanBeArrangedAgain()throws Exception{
        JsonNode a=schedule(act(recommend(job(),talent("失败结果人才","13800002802")),"feedback",Map.of("status","interested")),1);long id=a.path("id").asLong(),first=a.path("interviews").get(0).path("id").asLong();
        a=call(admin,put(base+"/applications/"+id+"/interviews/"+first),Map.of("revision",a.path("revision").asInt(),"operation","cancel","reason","客户改期，保留原安排"),200).path("data");
        assertTrue(operations(a).contains("interview"));assertFalse(operations(a).contains("interviewResult"));assertFalse(operations(a).contains("interviewManage"));
        a=schedule(a,2);long second=a.path("interviews").get(1).path("id").asLong();
        jdbc.update("UPDATE oa_recruit_interview SET interview_time=? WHERE id=?",LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusHours(1),second);
        a=call(admin,put(base+"/applications/"+id+"/interviews/"+second),Map.of("revision",a.path("revision").asInt(),"operation","result","result","fail","reason","面试不通过"),200).path("data");
        assertEquals("rejected",a.path("stage").asText());assertTrue(operations(a).isEmpty());assertEquals(2,a.path("interviews").size());
    }

    @Test void draftOfferCannotBeAcceptedOrRejectedAndCancelDoesNotCreateFollowup()throws Exception{
        JsonNode a=act(passedInterview("Offer入口人才","13800002703"),"offer",Map.of("salary",15000));long id=a.path("id").asLong();
        assertEquals(Set.of("offer","issueOffer","withdrawOffer","candidateWithdraw","customerCancel"),operations(a));
        call(admin,post(base+"/applications/"+id+"/actions/offer"),Map.of("revision",a.path("revision").asInt(),"operation","reject","reason","草稿尚未发放"),409);
        a=act(a,"offer",Map.of("operation","withdraw"));assertEquals("interview",a.path("stage").asText());assertEquals(0,a.path("exceptions").size());assertTrue(operations(a).contains("offer"));
        a=act(a,"offer",Map.of("salary",16000));a=act(a,"offer",Map.of("operation","issue"));assertFalse(operations(a).contains("offer"));assertTrue(operations(a).containsAll(Set.of("acceptOffer","rejectOffer","withdrawOffer")));
        a=act(a,"offer",Map.of("operation","reject"));assertEquals("rejected",a.path("stage").asText());assertTrue(operations(a).isEmpty());assertEquals(0,a.path("exceptions").size());
    }

    @Test void entryDelayFollowupsAreExplicitDeduplicatedAndResolvable()throws Exception{
        JsonNode a=progressToEntry(recommend(job(),talent("延期跟进人才","13800002704")));long id=a.path("id").asLong();
        a=act(a,"exception",Map.of("type","entry_delay","expectedEntryDate",today.plusDays(1).toString(),"followupRequired",true,"ownerUserId",901));long followup=a.path("exceptions").get(0).path("id").asLong();
        a=act(a,"exception",Map.of("type","entry_delay","expectedEntryDate",today.plusDays(2).toString(),"followupRequired",true,"ownerUserId",901));
        assertEquals(1,a.path("exceptions").size());assertEquals(followup,a.path("exceptions").get(0).path("id").asLong());assertEquals(today.plusDays(2).toString(),a.path("expectedEntryDate").asText());assertTrue(operations(a).contains("followupManage"));
        a=act(a,"exception",Map.of("operation","resolve","exceptionId",followup));assertFalse(operations(a).contains("followupManage"));assertEquals("entry",a.path("stage").asText());
        a=act(a,"exception",Map.of("type","candidate_withdraw"));assertEquals("withdrawn",a.path("stage").asText());assertEquals("withdrawn",a.path("offerStatus").asText());assertFalse(operations(a).contains("recommend"));
        call(admin,post(base+"/applications/"+id+"/actions/recommend"),Map.of("revision",a.path("revision").asInt(),"reason","不能复用旧Offer推荐"),409);assertEquals(1,a.path("interviews").size());
    }

    @Test void guaranteeCompletionOnlyAppearsAtMaturityAndTerminalRecordsCannotBeCorrected()throws Exception{
        JsonNode a=progressToEntry(recommend(job(),talent("保证期入口人才","13800002705")));long id=a.path("id").asLong();
        jdbc.update("UPDATE oa_crm_contract_version SET snapshot_json=? WHERE contract_id=? AND version_no=1",mapper.writeValueAsString(Map.of("feeTerms",List.of(Map.of("key","entry","title","入职服务费","guaranteeDays",30)))),contract);
        a=act(a,"entry",Map.of("actualEntryDate",today.toString(),"termKey","entry","attachmentId",uploadEvidence(id)));
        assertTrue(operations(a).containsAll(Set.of("guarantee","correctEntry")));assertFalse(operations(a).contains("completeGuarantee"));
        call(admin,post(base+"/applications/"+id+"/actions/guarantee"),Map.of("revision",a.path("revision").asInt(),"operation","complete","reason","保证期未到"),409);
        jdbc.update("UPDATE oa_recruit_application SET guarantee_end=? WHERE id=?",today,id);a=call(admin,get(base+"/applications/"+id),null,200).path("data");assertTrue(operations(a).contains("completeGuarantee"));
        a=act(a,"guarantee",Map.of("operation","complete"));assertEquals("completed",a.path("stage").asText());assertTrue(operations(a).isEmpty());
        call(admin,post(base+"/applications/"+id+"/actions/entry"),Map.of("revision",a.path("revision").asInt(),"operation","correct","reason","终结后不能普通纠正"),409);
    }

    @Test void manuallyEndedReplacementAllowsAnotherCandidateWithoutClosingOriginalFollowup()throws Exception{
        JsonNode original=progressToEntry(recommend(job(),talent("替换原人才","13800002706")));long id=original.path("id").asLong();
        original=act(original,"entry",Map.of("actualEntryDate",today.toString(),"termKey","entry","attachmentId",uploadEvidence(id)));
        original=act(original,"guarantee",Map.of("operation","replacement","exitDate",today.toString()));assertTrue(operations(original).contains("replacement"));
        JsonNode t=talent("替换未通过人才","13800002707");JsonNode replacement=call(admin,post(base+"/applications/"+id+"/replacement"),Map.of("revision",original.path("revision").asInt(),"talentId",t.path("id").asLong(),"recommendation","替换推荐"),200).path("data");
        original=call(admin,get(base+"/applications/"+id),null,200).path("data");assertFalse(operations(original).contains("replacement"));assertTrue(operations(original).contains("replacementManage"));
        replacement=act(replacement,"feedback",Map.of("status","rejected"));replacement=act(replacement,"exception",Map.of("operation","finish"));assertEquals("ended",replacement.path("stage").asText());
        original=call(admin,get(base+"/applications/"+id),null,200).path("data");assertTrue(operations(original).contains("replacement"));assertEquals("open",original.path("exceptions").get(0).path("status").asText());
    }

    @Test void jobStatusKeepsDraftUntilDemandIsConfirmed()throws Exception{
        JsonNode j=createJob();long id=j.path("id").asLong();assertEquals("draft",j.path("status").asText());
        call(admin,post(base+"/jobs/"+id+"/actions/status"),Map.of("revision",0,"status","recruiting","reason","尚未确认"),409);
        call(admin,post(base+"/jobs/"+id+"/actions/status"),Map.of("revision",0,"status","draft","reason","仍待启动"),200);
        j=getJob(id);call(admin,post(base+"/jobs/"+id+"/requests/demand"),Map.of("revision",j.path("revision").asInt(),"reason","确认需求"),200);
        j=getJob(id);assertEquals("recruiting",j.path("status").asText());
        call(admin,post(base+"/jobs/"+id+"/actions/status"),Map.of("revision",j.path("revision").asInt(),"status","draft","reason","不可退回待启动"),409);
    }

    @Test void lifecycleUpdatesVacanciesAndCustomerMetricsAndClosesGuarantee()throws Exception{
        JsonNode job=job(),talent=talent("人才一","13800001001");long jobId=job.path("id").asLong();
        JsonNode app=recommend(job,talent);long id=app.path("id").asLong();
        call(admin,post(base+"/applications"),Map.of("jobId",jobId,"talentId",talent.path("id").asLong(),"recommendation","重复"),409);
        app=progressToEntry(app);
        long attachment=uploadEvidence(id);
        call(admin,post(base+"/applications/"+id+"/actions/entry"),Map.of("revision",app.path("revision").asInt(),"reason","未上传凭证","actualEntryDate",today.toString(),"termKey","entry"),400);
        app=act(app,"entry",Map.of("actualEntryDate",today.toString(),"termKey","entry","attachmentId",attachment));
        assertEquals("guarantee",app.path("stage").asText());assertEquals(today.toString(),app.path("guaranteeEnd").asText());
        JsonNode current=getJob(jobId);assertEquals(0,current.path("vacancies").asInt());assertEquals("completed",current.path("status").asText());
        assertEquals(1,jdbc.queryForObject("SELECT hire_count FROM oa_merchant WHERE id=?",Integer.class,customer));
        assertEquals(1,jdbc.queryForObject("SELECT referral_count FROM oa_merchant WHERE id=?",Integer.class,customer));
        call(admin,post(base+"/jobs/"+jobId+"/requests/change"),Map.of("revision",current.path("revision").asInt(),"reason","增加一个招聘名额","fields",Map.of("headcount",2)),200);
        current=getJob(jobId);assertEquals("recruiting",current.path("status").asText());assertEquals(1,current.path("vacancies").asInt());
        call(admin,post(base+"/jobs/"+jobId+"/requests/change"),Map.of("revision",current.path("revision").asInt(),"reason","需求恢复为已入职人数","fields",Map.of("headcount",1)),200);
        current=getJob(jobId);assertEquals("completed",current.path("status").asText());
        call(admin,post(base+"/jobs/"+jobId+"/actions/status"),Map.of("revision",current.path("revision").asInt(),"status","recruiting","reason","不能重开满额职位"),409);
        app=act(app,"guarantee",Map.of("operation","complete"));assertEquals("completed",app.path("stage").asText());
        call(admin,delete(base+"/jobs/"+jobId),null,409);call(admin,delete(base+"/talents/"+talent.path("id").asLong()),null,409);
    }

    @Test void guaranteeExitRestoresVacancyAndCreatesResolvableException()throws Exception{
        JsonNode job=job(),talent=talent("人才二","13800001002");JsonNode app=progressToEntry(recommend(job,talent));long id=app.path("id").asLong();
        app=act(app,"entry",Map.of("actualEntryDate",today.toString(),"termKey","entry","attachmentId",uploadEvidence(id)));
        app=act(app,"guarantee",Map.of("operation","exit","exitDate",today.toString(),"followupRequired",true));assertEquals("exited",app.path("stage").asText());
        assertEquals("recruiting",getJob(job.path("id").asLong()).path("status").asText());assertEquals(0,jdbc.queryForObject("SELECT hire_count FROM oa_merchant WHERE id=?",Integer.class,customer));
        JsonNode exception=app.path("exceptions").get(0);assertEquals("open",exception.path("status").asText());app=act(app,"exception",Map.of("operation","resolve","exceptionId",exception.path("id").asLong()));assertEquals("closed",app.path("exceptions").get(0).path("status").asText());
    }

    @Test void onlyPublishedAndEnabledButtonFlowApprovesFrozenDemand()throws Exception{
        call(admin,put("/system/module/341301/actions/demand"),Map.of("flowDesignEnabled",true),200);
        JsonNode direct=job();assertEquals("approved",direct.path("demandStatus").asText());
        publish("demand");JsonNode draft=createJob();long id=draft.path("id").asLong();
        JsonNode workflow=call(admin,post(base+"/jobs/"+id+"/requests/demand"),Map.of("revision",0,"reason","确认招聘需求"),200).path("data");
        assertEquals("pendingApproval",workflow.path("status").asText());assertEquals("pending",getJob(id).path("demandStatus").asText());assertEquals("draft",getJob(id).path("status").asText());
        Map<String,Object>edit=jobBody();edit.put("revision",0);call(admin,put(base+"/jobs/"+id),edit,409);
        complete(admin,workflow,true,403);complete(reviewer,workflow,true,200);
        JsonNode approved=getJob(id);assertEquals("recruiting",approved.path("status").asText());assertEquals(2,approved.path("versions").size());
        call(admin,post(base+"/jobs/"+id+"/requests/change"),Map.of("revision",approved.path("revision").asInt(),"reason","增加名额","fields",Map.of("headcount",2)),200);
        assertEquals(2,getJob(id).path("headcount").asInt());
    }

    @Test void withdrawnAndRejectedRequestsCanBeCorrectedWithoutGhostTasks()throws Exception{
        call(admin,put("/system/module/341301/actions/demand"),Map.of("flowDesignEnabled",true),200);publish("demand");JsonNode j=createJob();long id=j.path("id").asLong();
        JsonNode flow=call(admin,post(base+"/jobs/"+id+"/requests/demand"),Map.of("revision",0,"reason","需求确认"),200).path("data");
        call(admin,post(base+"/jobs/"+id+"/actions/withdraw"),Map.of("revision",0,"reason","补充资料"),200);
        assertEquals("withdrawn",getJob(id).path("demandStatus").asText());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE process_instance_id=? AND status='todo'",Integer.class,flow.path("instanceId").asText()));
        Map<String,Object>b=jobBody();b.put("revision",0);call(admin,put(base+"/jobs/"+id),b,200);
        flow=call(admin,post(base+"/jobs/"+id+"/requests/demand"),Map.of("revision",1,"reason","重新确认"),200).path("data");complete(reviewer,flow,false,200);assertEquals("rejected",getJob(id).path("demandStatus").asText());
    }

    @Test void privacyScopeResumeAndActionPermissionsCannotBeBypassed()throws Exception{
        JsonNode t=talent("隐私候选人","13800001003");long id=t.path("id").asLong();
        call(reviewer,get(base+"/talents/"+id),null,403);
        jdbc.update("UPDATE oa_recruit_talent SET owner_user_id=901 WHERE id=?",id);
        JsonNode masked=call(reviewer,get(base+"/talents/"+id),null,200).path("data");assertFalse(masked.path("contactVisible").asBoolean());assertTrue(masked.path("phone").asText().contains("****"));
        call(reviewer,post(base+"/talents/"+id+"/actions/status"),Map.of("revision",0,"status","archived","reason","越权"),403);
        MockMultipartFile pdf=new MockMultipartFile("file","简历.pdf","application/pdf","%PDF-1.4\nprivate-resume".getBytes(StandardCharsets.UTF_8));
        JsonNode resume=call(admin,multipart(base+"/talents/"+id+"/resumes").file(pdf),null,200).path("data");
        call(reviewer,get(base+"/resumes/"+resume.path("id").asLong()+"/file"),null,403);
        byte[]bytes=mvc.perform(get(base+"/resumes/"+resume.path("id").asLong()+"/file?preview=true").header("Authorization",admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();assertArrayEquals(pdf.getBytes(),bytes);
        assertTrue(call(reviewer,get(base+"/talents"),null,200).path("rows").get(0).path("phone").asText().contains("****"));
    }

    @Test void duplicateMergeKeepsHistoryAndImportIsAtomic()throws Exception{
        JsonNode a=talent("重复甲","13800001004"),b=talent("重复乙","13800001004");long source=a.path("id").asLong(),target=b.path("id").asLong();
        assertEquals(2,call(admin,get(base+"/talents/duplicates"),null,200).path("total").asInt());
        call(admin,post(base+"/talents/"+source+"/requests/merge"),Map.of("revision",a.path("revision").asInt(),"targetId",target,"reason","手机号重复"),200);
        assertEquals("merged",call(admin,get(base+"/talents/"+source),null,200).path("data").path("status").asText());assertTrue(call(admin,get(base+"/talents/"+target),null,200).path("data").path("events").size()>0);
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_talent",Integer.class);
        call(admin,post(base+"/talents/import"),Map.of("rows",List.of(Map.of("name","可导入","phone","13800001005"),Map.of("name","错误","phone","invalid"))),400);
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_talent",Integer.class));
        jdbc.update("UPDATE sys_module_action SET action_name='自定义确认按钮',flow_design_enabled=1 WHERE module_id=341301 AND action_code='demand'");actions.bootstrap();assertEquals("自定义确认按钮",actions.action(341301,"demand").get("actionName"));
    }

    @Test void ownershipChangesAndAssignedFollowupsHaveARealRecipient()throws Exception{
        JsonNode t=talent("转交候选人","13800001007");long id=t.path("id").asLong();
        JsonNode task=call(admin,post(base+"/talents/"+id+"/tasks"),Map.of("ownerUserId",901,"title","确认求职意向","dueDate",today.toString()),200).path("data");
        assertEquals(1,call(reviewer,get(base+"/talents/"+id),null,200).path("data").path("tasks").size());
        jdbc.update("INSERT INTO sys_role_module_action (role_id,module_id,action_code) VALUES (991,341302,'contact')");
        call(reviewer,post(base+"/tasks/"+task.path("id").asLong()+"/complete"),Map.of("result","候选人确认可推荐"),200);
        t=call(admin,get(base+"/talents/"+id),null,200).path("data");assertFalse(t.path("lastContactTime").isNull());assertEquals("done",t.path("tasks").get(0).path("status").asText());
        call(admin,post(base+"/talents/"+id+"/requests/handover"),Map.of("revision",t.path("revision").asInt(),"ownerUserId",901,"collaborators",List.of(),"reason","交由新顾问负责"),200);
        assertEquals(901,call(reviewer,get(base+"/talents/"+id),null,200).path("data").path("ownerUserId").asLong());
        assertTrue(call(reviewer,get(base+"/options"),null,200).path("data").path("jobs").isEmpty());
    }

    @Test void jobDuplicatesWarningsAndCustomerMetricsStayConsistent()throws Exception{
        JsonNode j=createJob();createJob();assertEquals(2,call(admin,get(base+"/jobs/duplicates"),null,200).path("total").asInt());
        long id=j.path("id").asLong();call(admin,post(base+"/jobs/"+id+"/requests/demand"),Map.of("revision",0,"reason","开始招聘"),200);
        assertTrue(call(admin,get(base+"/warnings"),null,200).path("data").size()>0);
        call(admin,post(base+"/jobs/"+id+"/actions/status"),Map.of("revision",0,"status","paused","reason","旧版本请求"),409);
        call(admin,put("/project/merchant"),Map.of("id",customer,"name","招聘闭环测试客户","ownerUserId",1,"cooperationPositions",999,"referralCount",999,"hireCount",999,"contractAmount",0,"receivedAmount",0),200);
        assertEquals(2,jdbc.queryForObject("SELECT cooperation_positions FROM oa_merchant WHERE id=?",Integer.class,customer));assertEquals(0,jdbc.queryForObject("SELECT hire_count FROM oa_merchant WHERE id=?",Integer.class,customer));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_job",Integer.class));
    }

    @Test void pendingMergeCannotBeUsedToReplaceTargetDataEarly()throws Exception{
        JsonNode a=talent("合并原档案","13800001009"),b=talent("合并保留档案","13800001009");long source=a.path("id").asLong(),target=b.path("id").asLong();
        call(admin,put("/system/module/341302/actions/merge"),Map.of("flowDesignEnabled",true),200);
        Map<String,Object>process=Map.of("nodes",List.of(Map.of("id","start","type","start","name","发起"),Map.of("id","review","type","approval","name","档案合并审批","approver","17690000901"),Map.of("id","end","type","end","name","结束")),"edges",List.of(Map.of("source","start","target","review"),Map.of("source","review","target","end")));
        call(admin,post("/activiti/mongo/setProcess"),Map.of("tenantAppId",1,"menuId",341302,"menuName","人才库","actionCode","merge","approvalEnabled",true,"processData",process),200);
        JsonNode flow=call(admin,post(base+"/talents/"+source+"/requests/merge"),Map.of("revision",a.path("revision").asInt(),"targetId",target,"reason","重复档案归并"),200).path("data");
        assertEquals("available",call(admin,get(base+"/talents/"+source),null,200).path("data").path("status").asText());
        call(admin,post(base+"/talents/"+target+"/actions/status"),Map.of("revision",b.path("revision").asInt(),"status","archived","reason","不能修改审批目标"),409);
        complete(reviewer,flow,true,200);assertEquals("merged",call(admin,get(base+"/talents/"+source),null,200).path("data").path("status").asText());
    }

    @Test void ownershipDisputeRequiresHandlerAndClosesOnlyAfterTransfer()throws Exception{
        JsonNode t=talent("归属争议人才","13800001011");long id=t.path("id").asLong();
        jdbc.update("INSERT INTO sys_role_module_action (role_id,module_id,action_code) VALUES (991,341302,'dispute'),(991,341302,'handover')");
        t=call(admin,post(base+"/talents/"+id+"/disputes"),Map.of("revision",t.path("revision").asInt(),"proposedOwnerId",901,"handlerUserId",901,"reason","依据前期推荐记录核实归属"),200).path("data");
        long dispute=t.path("disputes").get(0).path("id").asLong();
        call(admin,post(base+"/talents/"+id+"/disputes/"+dispute+"/retain"),Map.of("revision",t.path("revision").asInt(),"reason","非指定处理人"),403);
        call(admin,post(base+"/talents/"+id+"/requests/handover"),Map.of("revision",t.path("revision").asInt(),"ownerUserId",901,"reason","绕过争议记录"),409);
        call(reviewer,post(base+"/talents/"+id+"/requests/handover"),Map.of("revision",t.path("revision").asInt(),"ownerUserId",901,"disputeId",dispute,"reason","核实后调整归属"),200);
        t=call(reviewer,get(base+"/talents/"+id),null,200).path("data");assertEquals(901,t.path("ownerUserId").asLong());assertEquals("closed",t.path("disputes").get(0).path("status").asText());
    }

    @Test void guaranteeReplacementIsLinkedAndActualHireClosesOriginalException()throws Exception{
        JsonNode j=job(),t=talent("原入职人才","13800001012");JsonNode original=progressToEntry(recommend(j,t));long id=original.path("id").asLong();
        original=act(original,"entry",Map.of("actualEntryDate",today.toString(),"termKey","entry","attachmentId",uploadEvidence(id)));
        original=act(original,"guarantee",Map.of("operation","replacement","exitDate",today.toString()));
        call(admin,post(base+"/applications/"+id+"/actions/exception"),Map.of("revision",original.path("revision").asInt(),"operation","resolve","exceptionId",original.path("exceptions").get(0).path("id").asLong(),"reason","不能先行结案"),409);
        JsonNode replacementTalent=talent("替换入职人才","13800001013");
        JsonNode replacement=call(admin,post(base+"/applications/"+id+"/replacement"),Map.of("revision",original.path("revision").asInt(),"talentId",replacementTalent.path("id").asLong(),"recommendation","同职位替换推荐"),200).path("data");
        assertEquals(id,replacement.path("replacementFor").asLong());
        original=call(admin,get(base+"/applications/"+id),null,200).path("data");assertEquals("open",original.path("exceptions").get(0).path("status").asText());
        call(admin,post(base+"/applications/"+id+"/replacement"),Map.of("revision",original.path("revision").asInt(),"talentId",t.path("id").asLong(),"recommendation","不能重复办理"),409);
        assertEquals("recommended",replacement.path("stage").asText());
        replacement=progressToEntry(replacement);
        replacement=act(replacement,"entry",Map.of("actualEntryDate",today.toString(),"termKey","entry","attachmentId",uploadEvidence(replacement.path("id").asLong())));
        original=call(admin,get(base+"/applications/"+id),null,200).path("data");assertEquals("closed",original.path("exceptions").get(0).path("status").asText());assertEquals("completed",original.path("replacements").get(0).path("status").asText());
        assertEquals(1,jdbc.queryForObject("SELECT hire_count FROM oa_merchant WHERE id=?",Integer.class,customer));assertEquals("completed",getJob(j.path("id").asLong()).path("status").asText());
    }

    @Test void refundRegistrationRequiresMoneyAndPrivateEvidence()throws Exception{
        JsonNode a=progressToEntry(recommend(job(),talent("退费候选人","13800001014")));long id=a.path("id").asLong(),proof=uploadEvidence(id);
        a=act(a,"entry",Map.of("actualEntryDate",today.toString(),"termKey","entry","attachmentId",proof));
        call(admin,post(base+"/applications/"+id+"/actions/guarantee"),Map.of("revision",a.path("revision").asInt(),"operation","refund","exitDate",today.toString(),"reason","凭证不足"),400);
        a=act(a,"guarantee",Map.of("operation","refund","exitDate",today.toString(),"amount",500,"attachmentId",proof));
        assertEquals("refund",a.path("guaranteeStatus").asText());assertEquals(500,a.path("guaranteeHandlings").get(0).path("amount").asInt());
    }

    @Test void interviewAndOperationsCannotSkipLatestRoundOrFrozenTalent()throws Exception{
        JsonNode a=act(recommend(job(),talent("面试校验人才","13800001015")),"feedback",Map.of("status","interested"));long id=a.path("id").asLong();
        call(admin,post(base+"/applications/"+id+"/actions/recommend"),Map.of("revision",a.path("revision").asInt(),"reason","无效操作","operation","unexpected"),400);
        a=call(admin,post(base+"/applications/"+id+"/interviews"),Map.of("revision",a.path("revision").asInt(),"roundNo",1,"interviewTime",LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1).withNano(0).toString(),"location","客户会议室","interviewer","主管","reason","安排第一轮"),200).path("data");
        long interview=a.path("interviews").get(0).path("id").asLong();
        assertTrue(operations(a.path("interviews").get(0)).contains("result"));
        jdbc.update("UPDATE oa_recruit_interview SET interview_time=?,status='completed',result='pass' WHERE id=?",LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusDays(1),interview);
        jdbc.update("INSERT INTO oa_recruit_interview (application_id,round_no,interview_time,status,result) VALUES (?,2,?,'completed','pending')",id,LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusHours(1));
        call(admin,post(base+"/applications/"+id+"/actions/offer"),Map.of("revision",a.path("revision").asInt(),"operation","save","salary",10000,"reason","最后一轮仍待定"),409);
        JsonNode latest=call(admin,get(base+"/applications/"+id),null,200).path("data");long pending=latest.path("interviews").get(1).path("id").asLong();
        a=call(admin,put(base+"/applications/"+id+"/interviews/"+pending),Map.of("revision",a.path("revision").asInt(),"operation","result","result","pass","reason","补充面试结论"),200).path("data");a=act(a,"offer",Map.of("operation","save","salary",10000));assertEquals("offer",a.path("stage").asText());
        JsonNode t=talent("冻结跟进人才","13800001016"),target=talent("冻结合并目标","13800001016");long talentId=t.path("id").asLong();
        long task=call(admin,post(base+"/talents/"+talentId+"/tasks"),Map.of("ownerUserId",1,"title","联系候选人","dueDate",today.toString()),200).path("data").path("id").asLong();
        jdbc.update("INSERT INTO oa_recruit_approval (workflow_request_id,entity_type,entity_id,related_id,action_code,revision,payload_json,create_by) VALUES (99999,'talent',?,?,'merge',1,'{}',1)",talentId,target.path("id").asLong());
        call(admin,post(base+"/tasks/"+task+"/complete"),Map.of("result","不能改变冻结档案"),409);
        assertEquals("todo",jdbc.queryForObject("SELECT status FROM oa_recruit_task WHERE id=?",String.class,task));
    }

    @Test void disabledHistoricalTagsRemainEditableButCannotBeAssignedToNewRecords()throws Exception{
        call(admin,post(base+"/tags"),Map.of("name","历史技能","status","active"),200);
        JsonNode t=call(admin,post(base+"/talents"),Map.of("name","历史标签人才","phone","13800001017","ownerUserId",1,"tags",List.of("历史技能")),200).path("data");
        long tag=jdbc.queryForObject("SELECT id FROM oa_recruit_tag WHERE name='历史技能'",Long.class);
        call(admin,post(base+"/tags"),Map.of("id",tag,"name","历史技能","status","disabled"),200);
        t=call(admin,put(base+"/talents/"+t.path("id").asLong()),Map.of("revision",t.path("revision").asInt(),"name","修改历史标签人才","phone","13800001017","ownerUserId",1,"tags",List.of("历史技能")),200).path("data");
        assertEquals("历史技能",t.path("tags").get(0).asText());
        call(admin,post(base+"/talents"),Map.of("name","新档案不得赋停用标签","phone","13800001018","ownerUserId",1,"tags",List.of("历史技能")),400);
    }

    Set<String> operations(JsonNode row){Set<String>out=new LinkedHashSet<>();row.path("availableOperations").forEach(value->out.add(value.asText()));return out;}
    JsonNode schedule(JsonNode a,int round)throws Exception{return call(admin,post(base+"/applications/"+a.path("id").asLong()+"/interviews"),Map.of("revision",a.path("revision").asInt(),"roundNo",round,"interviewTime",LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1).withNano(0).toString(),"location","会议室","interviewer","客户主管","reason","安排面试"),200).path("data");}
    JsonNode passedInterview(String name,String phone)throws Exception{JsonNode a=schedule(act(recommend(job(),talent(name,phone)),"feedback",Map.of("status","interested")),1);long id=a.path("id").asLong(),interview=a.path("interviews").get(0).path("id").asLong();jdbc.update("UPDATE oa_recruit_interview SET interview_time=? WHERE id=?",LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusHours(1),interview);return call(admin,put(base+"/applications/"+id+"/interviews/"+interview),Map.of("revision",a.path("revision").asInt(),"operation","result","result","pass","reason","面试通过"),200).path("data");}
    Map<String,Object>jobBody(){Map<String,Object>b=new LinkedHashMap<>();b.put("title","软件工程师");b.put("customerId",customer);b.put("contractId",contract);b.put("ownerUserId",1);b.put("headcount",1);b.put("requirements","Java开发经验");return b;}
    JsonNode createJob()throws Exception{return call(admin,post(base+"/jobs"),jobBody(),200).path("data");}
    JsonNode getJob(long id)throws Exception{return call(admin,get(base+"/jobs/"+id),null,200).path("data");}
    JsonNode job()throws Exception{JsonNode j=createJob();long id=j.path("id").asLong();call(admin,post(base+"/jobs/"+id+"/requests/demand"),Map.of("revision",0,"reason","需求已核实"),200);return getJob(id);}
    JsonNode talent(String name,String phone)throws Exception{JsonNode t=call(admin,post(base+"/talents"),Map.of("name",name,"phone",phone,"ownerUserId",1),200).path("data");return call(admin,post(base+"/talents/"+t.path("id").asLong()+"/actions/status"),Map.of("revision",0,"status","available","reason","本人确认可以推荐"),200).path("data");}
    JsonNode recommend(JsonNode job,JsonNode talent)throws Exception{JsonNode a=call(admin,post(base+"/applications"),Map.of("jobId",job.path("id").asLong(),"talentId",talent.path("id").asLong(),"consultantId",1,"recommendation","技能匹配"),200).path("data");assertEquals("recommended",a.path("stage").asText());assertEquals(today.toString(),a.path("submittedDate").asText());assertEquals(today.plusDays(job.path("feedbackDays").asInt()).toString(),a.path("feedbackDueDate").asText());assertEquals("pending",a.path("feedbackStatus").asText());return a;}
    JsonNode progressToEntry(JsonNode a)throws Exception{a=act(a,"feedback",Map.of("status","interested"));long id=a.path("id").asLong();a=call(admin,post(base+"/applications/"+id+"/interviews"),Map.of("revision",a.path("revision").asInt(),"roundNo",1,"interviewTime",LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1).withNano(0).toString(),"location","会议室","interviewer","客户主管","reason","客户面试"),200).path("data");long interview=a.path("interviews").get(0).path("id").asLong();jdbc.update("UPDATE oa_recruit_interview SET interview_time=? WHERE id=?",LocalDateTime.now().minusMinutes(1),interview);a=call(admin,put(base+"/applications/"+id+"/interviews/"+interview),Map.of("revision",a.path("revision").asInt(),"operation","result","result","pass","reason","面试通过"),200).path("data");a=act(a,"offer",Map.of("salary",20000,"operation","save"));a=act(a,"offer",Map.of("operation","issue"));return act(a,"offer",Map.of("operation","accept","expectedEntryDate",today.toString()));}
    JsonNode act(JsonNode a,String action,Map<String,Object>extra)throws Exception{Map<String,Object>b=new LinkedHashMap<>(extra);b.put("revision",a.path("revision").asInt());b.put("reason","业务记录");return call(admin,post(base+"/applications/"+a.path("id").asLong()+"/actions/"+action),b,200).path("data");}
    long uploadEvidence(long id)throws Exception{return call(admin,multipart(base+"/applications/"+id+"/attachments").file(new MockMultipartFile("file","入职凭证.pdf","application/pdf","%PDF-1.4\nevidence".getBytes(StandardCharsets.UTF_8))),null,200).path("data").path("id").asLong();}
    void publish(String action)throws Exception{call(admin,post("/activiti/mongo/setProcess"),Map.of("tenantAppId",1,"menuId",341301,"menuName","职位管理","actionCode",action,"approvalEnabled",true,"processData",Map.of("nodes",List.of(Map.of("id","start","type","start","name","发起"),Map.of("id","review","type","approval","name","招聘需求审批","approver","17690000901"),Map.of("id","end","type","end","name","结束")),"edges",List.of(Map.of("source","start","target","review"),Map.of("source","review","target","end")))),200);}
    void complete(String token,JsonNode f,boolean pass,int code)throws Exception{call(token,post("/activiti/process/complete"),Map.of("instanceId",f.path("instanceId").asText(),"taskId",f.path("taskId").asText(),"variables",Map.of("pass",pass,"comment","招聘审批意见")),code);}
    String login(String name)throws Exception{return "Bearer "+call(null,post("/login"),Map.of("username",name,"password","admin123123"),200).path("token").asText();}
    JsonNode call(String token,MockHttpServletRequestBuilder req,Object body,int code)throws Exception{if(token!=null)req.header("Authorization",token);if(body!=null)req.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));JsonNode r=mapper.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));assertEquals(code,r.path("code").asInt(),r.toString());return r;}
}
