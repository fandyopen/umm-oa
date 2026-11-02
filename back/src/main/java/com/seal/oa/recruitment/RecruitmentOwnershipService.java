package com.seal.oa.recruitment;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.seal.oa.recruitment.RecruitmentService.*;

@Service
public class RecruitmentOwnershipService {
    private final RecruitmentService s;

    public RecruitmentOwnershipService(RecruitmentService s) { this.s = s; }

    @Transactional
    public Map<String,Object> create(long talentId, Map<String,Object> body) {
        Map<String,Object> talent = s.talent(talentId, true);
        s.revision(talent, body);
        s.unlocked("talent", talentId);
        if (Set.of("merged", "archived", "disabled").contains(text(talent.get("status"))))
            throw new ApiException(409, "请先激活有效候选人档案");
        if (s.jdbc().queryForObject("SELECT COUNT(*) FROM oa_recruit_dispute WHERE talent_id=? AND status='open'", Long.class, talentId) > 0)
            throw new ApiException(409, "该人才已有待处理归属争议，请继续处理原记录");
        long proposed = num(body.get("proposedOwnerId"));
        long handler = num(body.get("handlerUserId"));
        s.user(proposed);
        s.user(handler);
        if (proposed == num(talent.get("ownerUserId"))) throw new ApiException(400, "拟归属人与当前负责人相同");
        if (!s.system().hasModuleAction(handler, TALENT, "dispute"))
            throw new ApiException(400, "处理人须具有人才库归属争议处理权限");
        String reason = required(body.get("reason"), 2000, "争议事实及依据");
        long id = s.insert("INSERT INTO oa_recruit_dispute (talent_id,proposed_owner_id,handler_user_id,content,create_by) VALUES (?,?,?,?,?)",
                talentId, proposed, handler, reason, AuthContext.userId());
        LinkedHashSet<Object> collaborators = new LinkedHashSet<>(s.array(talent.get("collaboratorsJson")));
        collaborators.add(handler);
        s.jdbc().update("UPDATE oa_recruit_talent SET collaborators_json=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?", s.json(collaborators), talentId);
        s.event("talent", talentId, "dispute", "登记归属争议：" + reason, Map.of("disputeId", id, "proposedOwnerId", proposed, "handlerUserId", handler));
        return s.talentDetail(talentId);
    }

    @Transactional
    public Map<String,Object> retain(long talentId, long disputeId, Map<String,Object> body) {
        Map<String,Object> talent = s.talent(talentId, true);
        s.revision(talent, body);
        s.unlocked("talent", talentId);
        Map<String,Object> dispute = s.one("SELECT * FROM oa_recruit_dispute WHERE id=? AND talent_id=? FOR UPDATE", disputeId, talentId);
        requireHandler(dispute);
        String result = required(body.get("reason"), 2000, "核实结论及依据");
        s.jdbc().update("UPDATE oa_recruit_dispute SET status='closed',result=?,close_time=CURRENT_TIMESTAMP WHERE id=?", "保留当前归属：" + result, disputeId);
        s.jdbc().update("UPDATE oa_recruit_talent SET revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?", talentId);
        s.event("talent", talentId, "dispute_resolved", "归属争议结案（保留当前负责人）：" + result, Map.of("disputeId", disputeId));
        return s.talentDetail(talentId);
    }

    static void requireHandler(Map<String,Object> dispute) {
        if (!"open".equals(dispute.get("status"))) throw new ApiException(409, "归属争议已结案");
        if (num(dispute.get("handlerUserId")) != AuthContext.userId())
            throw new ApiException(403, "请由指定争议处理人核实并办理");
    }
}
