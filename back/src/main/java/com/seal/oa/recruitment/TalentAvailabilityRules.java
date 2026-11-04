package com.seal.oa.recruitment;

import com.seal.oa.common.ApiException;

import java.util.Map;
import java.util.Set;

import static com.seal.oa.recruitment.RecruitmentService.num;
import static com.seal.oa.recruitment.RecruitmentService.text;

/** 人才意向由顾问维护，招聘占用从真实流程计算，不能手工改状态绕过。 */
final class TalentAvailabilityRules {
    private TalentAvailabilityRules() {}

    static final String BLOCKING_CONDITION = """
            (a.stage IN ('recommended','feedback','interview','offer','entry','entered','guarantee')
             OR (a.stage = 'completed' AND a.actual_entry_date IS NOT NULL))
            """;
    static final String SUMMARY_JOIN = """
            LEFT JOIN (
                SELECT a.talent_id, COUNT(*) AS recruitment_lock_count, MIN(a.stage) AS recruitment_stage,
                    SUM(CASE WHEN a.actual_entry_date IS NOT NULL AND a.stage IN ('entered','guarantee','completed')
                        THEN 1 ELSE 0 END) AS employment_count
                FROM oa_recruit_application a WHERE a.tenant_id = 1 AND %s
                GROUP BY a.talent_id
            ) occupancy ON occupancy.talent_id = t.id
            """.formatted(BLOCKING_CONDITION);
    private static final Map<String, String> STAGES = Map.of(
            "recommended", "已推荐", "feedback", "客户反馈", "interview", "面试中",
            "offer", "Offer", "entry", "待入职", "entered", "已入职",
            "guarantee", "保证期", "completed", "保证期已结案、仍在职");

    static Map<String, Object> decorate(Map<String, Object> talent) {
        long count = num(talent.get("recruitmentLockCount"));
        boolean locked = count > 0;
        boolean employed = num(talent.get("employmentCount")) > 0;
        boolean eligible = Set.of("contacting", "available").contains(text(talent.get("status")));
        boolean pending = num(talent.get("pendingApprovalCount")) > 0;
        String state = employed ? "employed" : locked ? "in_process" : "free";
        String stage = STAGES.getOrDefault(text(talent.get("recruitmentStage")), "招聘处理中");
        String reason = employed ? "候选人已有有效入职记录，登记离职后方可重新推荐"
                : locked ? "候选人已有" + count + "条占用中的招聘流程（" + stage + "），请先结束原流程"
                : pending ? "候选人存在待审批申请，请审批完成或撤回后推荐"
                : eligible ? "" : "候选人须处于沟通中或可推荐状态";
        talent.put("recruitmentLocked", locked);
        talent.put("recruitmentAvailability", state);
        talent.put("recruitmentAvailabilityLabel", employed ? "已入职锁定" : locked ? "招聘中锁定" : "未被占用");
        talent.put("recruitmentStageLabel", locked ? stage : "");
        talent.put("canRecommend", !locked && !pending && eligible);
        talent.put("recommendationBlockReason", reason);
        return talent;
    }

    static void requireRecommendable(Map<String, Object> talent) {
        decorate(talent);
        if (!Boolean.TRUE.equals(talent.get("canRecommend"))) {
            throw new ApiException(409, text(talent.get("recommendationBlockReason")));
        }
    }
}
