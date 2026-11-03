package com.seal.oa.recruitment;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import static com.seal.oa.recruitment.RecruitmentService.*;

final class RecruitmentPipelineRules {
    private RecruitmentPipelineRules() {}

    static Set<String> operations(Map<String,Object> r) {
        Set<String> out=new LinkedHashSet<>();
        String stage=text(r.get("stage")),feedback=text(r.get("feedbackStatus")),offer=text(r.get("offerStatus"));
        boolean interested="interested".equals(feedback),pending=num(r.get("scheduledCount"))>0;
        boolean pendingResult=num(r.get("pendingResultCount"))>0||"completed".equals(r.get("interviewStatus"))&&"pending".equals(r.get("interviewResult"));
        boolean hasInterviews=num(r.get("interviewCount"))>0;
        boolean jobReady="recruiting".equals(r.get("jobStatus"))&&"approved".equals(r.get("demandStatus"))&&num(r.get("jobHired"))<num(r.get("jobHeadcount"));
        switch(stage) {
            case "recommended","feedback" -> {
                out.add("feedback");
                if(!hasInterviews&&!"rejected".equals(feedback))out.add("withdrawRecommendation");
                if("feedback".equals(stage)&&interested) {
                    if(!pending&&!pendingResult)out.add("interview");
                }
                if("feedback".equals(stage)&&"rejected".equals(feedback))out.add("finishRecommendation");
            }
            case "interview" -> {
                out.add("feedback"); // Low-frequency correction, shown in detail only.
                if(interested&&!pending&&!pendingResult) {
                    out.add("interview");
                    if("completed".equals(r.get("interviewStatus"))&&"pass".equals(r.get("interviewResult")))out.add("offer");
                }
            }
            case "offer" -> {
                if("draft".equals(offer)){out.add("offer");out.add("issueOffer");out.add("withdrawOffer");}
                if("issued".equals(offer)){out.add("acceptOffer");out.add("rejectOffer");out.add("withdrawOffer");}
            }
            case "entry" -> {if(jobReady)out.add("entry");out.add("expectedEntry");out.add("entryDelay");}
            case "guarantee" -> {
                if("active".equals(r.get("guaranteeStatus"))) {
                    out.add("correctEntry");out.add("guarantee");
                    out.add("guaranteeExit");out.add("guaranteeReplacement");out.add("guaranteeRefund");
                    if(r.get("guaranteeEnd")!=null&&!today().isBefore(date(r.get("guaranteeEnd"),"保证期结束")))out.add("completeGuarantee");
                }
            }
            case "withdrawn" -> {
                // Later-stage withdrawals retain their evidence and cannot silently reuse an old Offer/interview.
                if(!hasInterviews&&offer.isBlank()&&r.get("actualEntryDate")==null
                        &&jobReady&&num(r.get("talentPendingCount"))==0
                        &&Set.of("contacting","available").contains(text(r.get("talentStatus"))))out.add("recommend");
            }
            case "exited" -> {
                if("replacement".equals(r.get("guaranteeStatus"))) {
                    if(num(r.get("replacementCount"))>0)out.add("replacementManage");
                    if(jobReady&&num(r.get("openReplacementCount"))>0&&num(r.get("blockingReplacementCount"))==0)out.add("replacement");
                }
            }
            default -> { }
        }
        if(Set.of("feedback","interview").contains(stage)&&interested&&num(r.get("activeInterviewId"))>0)out.add("interviewResult");
        if(Set.of("recommended","feedback","interview","offer","entry").contains(stage)
                &&!("feedback".equals(stage)&&"rejected".equals(feedback))) {
            out.add("candidateWithdraw");out.add("customerCancel");
        }
        if(num(r.get("openFollowupCount"))>0)out.add("followupManage");
        return out;
    }
}
