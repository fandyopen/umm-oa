package com.seal.oa.workflow;

import com.seal.oa.common.ApiException;
import com.seal.oa.system.HrMemberService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class HrMemberActionHandler implements WorkflowActionHandler {

    private static final String HANDLER_PREFIX = "hr.member.";
    private static final Set<String> HANDLER_KEYS = Set.of(
            HANDLER_PREFIX + "invite",
            HANDLER_PREFIX + "transfer",
            HANDLER_PREFIX + "dismiss"
    );

    private final HrMemberService hrMemberService;

    public HrMemberActionHandler(HrMemberService hrMemberService) {
        this.hrMemberService = hrMemberService;
    }

    @Override
    public Set<String> handlerKeys() {
        return HANDLER_KEYS;
    }

    @Override
    public void validate(WorkflowActionContext context) {
        switch (operation(context)) {
            case "invite" -> hrMemberService.validateInvite(context.payload());
            case "transfer" -> hrMemberService.validateTransfer(
                    longValue(context.payload().get("deptId"), 0L),
                    userIds(context.payload())
            );
            case "dismiss" -> hrMemberService.validateDismiss(userIds(context.payload()));
            default -> throw new ApiException(400, "不支持的人事管理动作");
        }
    }

    @Override
    public Object execute(WorkflowActionContext context) {
        validate(context);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("actionCode", context.actionCode());
        result.put("handlerKey", context.handlerKey());
        switch (operation(context)) {
            case "invite" -> {
                long userId = hrMemberService.invite(context.payload(), context.operatorName());
                result.put("userId", userId);
                result.put("message", "成员已邀请");
            }
            case "transfer" -> {
                int count = hrMemberService.transfer(
                        longValue(context.payload().get("deptId"), 0L),
                        userIds(context.payload())
                );
                result.put("count", count);
                result.put("message", "部门调整完成");
            }
            case "dismiss" -> {
                int count = hrMemberService.dismiss(userIds(context.payload()));
                result.put("count", count);
                result.put("message", "离职状态设置完成");
            }
            default -> throw new ApiException(400, "不支持的人事管理动作");
        }
        return result;
    }

    private String operation(WorkflowActionContext context) {
        String handlerKey = context.handlerKey() == null ? "" : context.handlerKey().trim();
        if (handlerKey.startsWith(HANDLER_PREFIX)) {
            return handlerKey.substring(HANDLER_PREFIX.length());
        }
        return context.actionCode();
    }

    private List<Long> userIds(Map<String, Object> payload) {
        Object raw = payload.get("userIds");
        if (raw == null) {
            raw = payload.get("userId");
        }
        List<Long> result = new ArrayList<>();
        if (raw instanceof List<?> values) {
            for (Object value : values) {
                addId(result, value);
            }
        } else if (raw != null) {
            for (String value : String.valueOf(raw).split(",")) {
                addId(result, value);
            }
        }
        return result;
    }

    private void addId(List<Long> values, Object raw) {
        long id = longValue(raw, 0L);
        if (id > 0L && !values.contains(id)) {
            values.add(id);
        }
    }

    private long longValue(Object value, long defaultValue) {
        try {
            return value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}
