package com.seal.oa.oa;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** 商机源记录的锁由新增、修改、阶段推进和删除业务持有。 */
@Service
public class BusinessFollowupReminderService {
    public static final String BUSINESS_TYPE = "crmOpportunityFollowup";
    private final OwnerFollowupTodoService todos;

    public BusinessFollowupReminderService(OwnerFollowupTodoService todos) {
        this.todos = todos;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void synchronize(Map<String, Object> opportunity) {
        long id = Long.parseLong(String.valueOf(opportunity.get("id")));
        String stage = String.valueOf(opportunity.getOrDefault("stage", opportunity.getOrDefault("businessState", "初步接洽")));
        String status = String.valueOf(opportunity.getOrDefault("statusLabel", "推进中"));
        boolean ended = List.of("赢单", "输单").contains(stage) || List.of("已赢单", "已输单").contains(status);
        boolean followed = !"初步接洽".equals(stage)
                || opportunity.get("sealBusinessStages") instanceof List<?> stages && stages.size() > 1;
        todos.synchronize(BUSINESS_TYPE, id, opportunity.getOrDefault("tenantId", 1L), opportunity.get("ownerUserId"),
                "商机待跟进：" + opportunity.getOrDefault("name", ""), !ended, followed);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(long id) {
        todos.close(BUSINESS_TYPE, id, "cancelled");
    }
}
