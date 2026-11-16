package com.seal.oa.workflow;

import java.util.Objects;
import java.util.function.Supplier;

/** Server-only capability, scoped to one authorized inbox decision. Never changes AuthContext. */
public final class WorkflowApprovalContext {
    private record Decision(String instance, String task, Long operator, long assignee, boolean selfApprovalAllowed) {}
    private static final ThreadLocal<Decision> CURRENT = new ThreadLocal<>();

    private WorkflowApprovalContext() {}

    static <T> T decide(String instance, String task, Long operator, long assignee, boolean selfApprovalAllowed, Supplier<T> work) {
        Decision previous = CURRENT.get();
        CURRENT.set(new Decision(instance, task, operator, assignee, selfApprovalAllowed));
        try { return work.get(); }
        finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public static boolean active(Long operator) {
        Decision decision = CURRENT.get();
        return decision != null && Objects.equals(decision.operator(), operator);
    }

    public static boolean allowsSelfApproval(Long operator) {
        return active(operator) && CURRENT.get().selfApprovalAllowed();
    }

    public static boolean permits(String instance, String task, Long operator, long assignee) {
        Decision decision = CURRENT.get();
        return active(operator) && decision.instance().equals(instance) && decision.task().equals(task)
                && decision.assignee() == assignee;
    }
}
