package com.seal.oa.system;

import java.util.Map;
import java.util.Set;

/** 已完成前端统一提交接入的动作。新增接入时在此登记；仅有接口不能视为已接入。 */
public final class WorkflowIntegrationRegistry {
    private static final Map<Long, Set<String>> ACTIONS = Map.ofEntries(
            Map.entry(340352L, Set.of("track")),
            Map.entry(340590L, Set.of("add", "edit")),
            Map.entry(341201L, Set.of("submit", "sign")),
            Map.entry(341400L, Set.of("invoice_submit", "receipt_confirm", "refund_submit", "adjust_confirm", "cost_confirm")),
            Map.entry(341410L, Set.of("submit")),
            Map.entry(341001L, Set.of("invite", "transfer", "dismiss")),
            Map.entry(340782L, Set.of("invite", "transfer", "dismiss")),
            Map.entry(341002L, Set.of("salary_submit")),
            Map.entry(341003L, Set.of("submit")),
            Map.entry(341005L, Set.of("submit")),
            Map.entry(341301L, Set.of("demand", "change")),
            Map.entry(341302L, Set.of("merge", "handover"))
    );

    private WorkflowIntegrationRegistry() {}

    public static boolean integrated(long moduleId, String actionCode) {
        return ACTIONS.getOrDefault(moduleId, Set.of()).contains(actionCode);
    }
}
