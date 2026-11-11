package com.seal.oa.system;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.lowcode.ModuleFlowBindingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

/** 独立流程目录与原按钮入口共用发布表和按钮绑定，不替换原有配置方式。 */
@Service
public class WorkflowCatalogService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SystemService system;
    private final ModuleActionService actions;
    private final ModuleFlowBindingService bindings;

    public WorkflowCatalogService(JdbcTemplate jdbc, ObjectMapper json, SystemService system,
                                  ModuleActionService actions, ModuleFlowBindingService bindings) {
        this.jdbc = jdbc; this.json = json; this.system = system; this.actions = actions; this.bindings = bindings;
    }

    public boolean canDesign() {
        Long user = system.requireCurrentUser().userId();
        return system.hasModuleAction(user, 340773L, "design") || system.hasModuleAction(user, 340772L, "manage")
                || system.hasModuleAction(user, 340772L, "workflow") || system.hasModuleAction(user, 340771L, "manage");
    }

    public void requireDesigner() {
        if (!canDesign()) throw new ApiException(403, "没有审批流程配置权限");
    }

    public Map<String, Object> options(long moduleId) {
        requireDesigner();
        List<Map<String, Object>> modules = rows("SELECT module_id,module_name,component FROM sys_module WHERE tenant_id=? AND status='0' AND component NOT IN ('Layout','ParentView','') ORDER BY module_sort,module_id", tenant());
        List<Map<String, Object>> buttons = new ArrayList<>();
        List<Map<String, Object>> workflows = List.of();
        if (moduleId > 0) {
            requireModule(moduleId, false);
            for (Map<String, Object> action : actions.allActions(moduleId)) {
                String code = text(action.get("actionCode"));
                boolean frontend = WorkflowIntegrationRegistry.integrated(moduleId, code);
                String error = "";
                try { actions.requireWorkflowExecution(moduleId, code); }
                catch (ApiException exception) { error = exception.getMessage(); }
                boolean ready = frontend && error.isEmpty() && "0".equals(text(action.get("status")));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("actionCode", code); item.put("actionName", action.get("actionName"));
                item.put("ready", ready);
                item.put("integrationLabel", ready ? "已接入" : !frontend ? "待接入：前端尚未使用统一提交" : !error.isEmpty() ? "待接入：业务执行配置未完成" : "按钮已停用");
                Map<String, Object> binding = bindings.binding(1, moduleId, code);
                item.put("currentProcessId", text(binding.get("processDesignId")));
                buttons.add(item);
            }
            workflows = rows("""
                    SELECT c.id,c.flow_name,c.revision,d.status AS process_status,
                           b.action_code AS bound_action_code
                    FROM oa_workflow_catalog c LEFT JOIN oa_process_design d ON d.id=c.id
                    LEFT JOIN oa_module_action_flow b ON b.process_design_id=c.id AND b.tenant_app_id=1
                    WHERE c.tenant_id=? AND c.module_id=? ORDER BY c.create_time,c.id
                    """, tenant(), moduleId);
        }
        return Map.of("modules", modules, "buttons", buttons, "workflows", workflows);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        requireDesigner();
        long moduleId = number(body.get("moduleId"));
        requireModule(moduleId, true);
        String name = text(body.get("flowName")).trim();
        if (name.isEmpty() || name.length() > 120) throw new ApiException(400, "请填写120字以内的流程名称");
        String id = "catalog-" + UUID.randomUUID();
        jdbc.update("INSERT INTO oa_workflow_catalog(id,tenant_id,module_id,flow_name,create_by) VALUES (?,?,?,?,?)",
                id, tenant(), moduleId, name, AuthContext.username());
        return Map.of("id", id, "moduleId", moduleId, "flowName", name);
    }

    public boolean contains(String id) {
        return !rows("SELECT id FROM oa_workflow_catalog WHERE id=? AND tenant_id=?", id, tenant()).isEmpty();
    }

    public Map<String, Object> designer(String id) {
        requireDesigner();
        Map<String, Object> entry = entry(id, false);
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> published = rows("SELECT process_data FROM oa_process_design WHERE id=? AND tenant_id=?", id, tenant());
        boolean draft = entry.get("draftData") != null;
        if (draft || !published.isEmpty()) result.put("processData", draft ? object(entry.get("draftData")) : object(published.get(0).get("processData")));
        result.put("_id", id);
        result.put("workflowId", id);
        result.put("menuId", entry.get("moduleId"));
        result.put("flowName", entry.get("flowName"));
        result.put("workflowRevision", entry.get("revision"));
        result.put("draft", draft);
        result.put("approvalEnabled", enabled(draft ? entry.get("draftApprovalEnabled") : entry.get("approvalEnabled")));
        return result;
    }

    @Transactional
    public String save(String id, Map<String, Object> body) {
        requireDesigner();
        Map<String, Object> entry = entry(id, true);
        checkRevision(entry, body.get("workflowRevision"));
        boolean draft = enabled(body.get("draft"));
        boolean approval = !body.containsKey("approvalEnabled") || enabled(body.get("approvalEnabled"));
        Map<String, Object> process = object(body.get("processData"));
        if (draft) {
            jdbc.update("UPDATE oa_workflow_catalog SET draft_data=?,draft_approval_enabled=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",
                    json(process), approval ? 1 : 0, id);
        } else {
            validateProcess(process);
            Map<String, Object> binding = bindingFor(id);
            if (!binding.isEmpty() && approval) requireIntegrated(number(entry.get("moduleId")), text(binding.get("actionCode")));
            Map<String, Object> module = requireModule(number(entry.get("moduleId")), false);
            int updated = jdbc.update("UPDATE oa_process_design SET process_data=?,menu_name=?,status='published',update_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                    json(process), entry.get("flowName"), id, tenant());
            if (updated == 0) jdbc.update("INSERT INTO oa_process_design(id,tenant_id,tenant_app_id,menu_id,menu_name,process_data,status,create_by) VALUES (?,?,1,?,?,?,'published',?)",
                    id, tenant(), module.get("moduleId"), entry.get("flowName"), json(process), AuthContext.username());
            jdbc.update("UPDATE oa_workflow_catalog SET approval_enabled=?,draft_data=NULL,draft_approval_enabled=NULL,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?", approval ? 1 : 0, id);
            if (!binding.isEmpty()) {
                bindings.saveBinding(1, number(entry.get("moduleId")), text(binding.get("actionCode")), id, approval, AuthContext.username());
                // 原按钮入口关闭的开关继续保留，发布本身不擅自重新打开。
            }
        }
        return id;
    }

    @Transactional
    public void associate(String id, long moduleId, String code, Object expectedProcessId, Object revision) {
        requireDesigner();
        Map<String, Object> entry = entry(id, true);
        if (number(entry.get("moduleId")) != moduleId) throw new ApiException(400, "流程与按钮必须属于同一模块");
        checkRevision(entry, revision);
        requireIntegrated(moduleId, code);
        Map<String, Object> current = bindings.binding(1, moduleId, code);
        if (expectedProcessId == null || !text(current.get("processDesignId")).equals(text(expectedProcessId))) throw new ApiException(409, "按钮关联已变化，请刷新后重新选择");
        Map<String, Object> used = bindingFor(id);
        if (!used.isEmpty() && (!text(used.get("actionCode")).equals(code) || number(used.get("moduleId")) != moduleId)) throw new ApiException(409, "该流程已关联其他按钮，请复制新建流程后关联");
        if (rows("SELECT id FROM oa_process_design WHERE id=? AND tenant_id=? AND status='published'", id, tenant()).isEmpty()) throw new ApiException(400, "请先设计并发布流程，再关联按钮");
        jdbc.update("UPDATE sys_module_action SET flow_design_enabled=1,update_time=CURRENT_TIMESTAMP WHERE module_id=? AND action_code=?", moduleId, code);
        bindings.saveBinding(1, moduleId, code, id, enabled(entry.get("approvalEnabled")), AuthContext.username());
        jdbc.update("DELETE FROM biz_record WHERE tenant_id=? AND module='lowcode:button-flow-draft' AND business_key=?", tenant(), "1:" + moduleId + ":" + code);
        jdbc.update("UPDATE oa_workflow_catalog SET revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?", id);
    }

    public Map<String, Object> bindingFor(String id) {
        List<Map<String, Object>> found = rows("SELECT module_id,action_code,approval_enabled FROM oa_module_action_flow WHERE process_design_id=? AND tenant_app_id=1 AND tenant_id=?", id, tenant());
        if (found.size() > 1) throw new ApiException(409, "流程存在重复按钮关联，请联系管理员");
        return found.isEmpty() ? Map.of() : found.get(0);
    }

    private void requireIntegrated(long moduleId, String code) {
        if (!WorkflowIntegrationRegistry.integrated(moduleId, code)) throw new ApiException(400, "该按钮前端尚未接入统一提交，不能启用审批");
        List<Map<String, Object>> found = rows("SELECT action_code FROM sys_module_action WHERE module_id=? AND action_code=? AND status='0' FOR UPDATE", moduleId, code);
        if (found.isEmpty()) throw new ApiException(400, "按钮不存在或已停用");
        actions.requireWorkflowExecution(moduleId, code);
    }

    private Map<String, Object> entry(String id, boolean lock) {
        List<Map<String, Object>> found = rows("SELECT * FROM oa_workflow_catalog WHERE id=? AND tenant_id=?", id, tenant());
        if (found.isEmpty()) throw new ApiException(404, "流程不存在或不属于当前企业");
        if (lock) {
            requireModule(number(found.get(0).get("moduleId")), true);
            found = rows("SELECT * FROM oa_workflow_catalog WHERE id=? AND tenant_id=? FOR UPDATE", id, tenant());
        }
        return found.get(0);
    }

    private Map<String, Object> requireModule(long id, boolean lock) {
        List<Map<String, Object>> found = rows("SELECT module_id,module_name,parent_id,component FROM sys_module WHERE module_id=? AND tenant_id=? AND status='0'" + (lock ? " FOR UPDATE" : ""), id, tenant());
        if (found.isEmpty()) throw new ApiException(404, "所属模块不存在、已停用或不属于当前企业");
        if (Set.of("", "Layout", "ParentView").contains(text(found.get(0).get("component")))) throw new ApiException(400, "请选择页面模块");
        long parent = number(found.get(0).get("parentId"));
        Set<Long> visited = new HashSet<>();
        visited.add(id);
        while (parent > 0) {
            if (!visited.add(parent)) throw new ApiException(400, "模块层级配置异常");
            List<Map<String, Object>> ancestors = rows("SELECT parent_id,status FROM sys_module WHERE module_id=? AND tenant_id=?", parent, tenant());
            if (ancestors.isEmpty() || !"0".equals(text(ancestors.get(0).get("status")))) throw new ApiException(400, "所属上级模块不存在或已停用");
            parent = number(ancestors.get(0).get("parentId"));
        }
        return found.get(0);
    }

    private void checkRevision(Map<String, Object> entry, Object revision) {
        if (revision == null || number(revision) != number(entry.get("revision"))) throw new ApiException(409, "流程已被修改，请重新打开后再操作");
    }

    private void validateProcess(Map<String, Object> process) {
        if (!(process.get("nodes") instanceof List<?> nodes) || nodes.isEmpty() || !(process.get("edges") instanceof List<?> edges) || edges.isEmpty()) throw new ApiException(400, "请配置完整审批流程后发布");
        long starts = nodes.stream().map(this::object).filter(node -> "start".equals(text(node.get("type")))).count();
        long ends = nodes.stream().map(this::object).filter(node -> "end".equals(text(node.get("type")))).count();
        if (starts != 1 || ends != 1 || nodes.stream().map(this::object).noneMatch(node -> "approval".equals(text(node.get("type"))))) throw new ApiException(400, "流程须包含开始、审批和结束节点");
        for (Object node : nodes) {
            Map<String, Object> item = object(node);
            if ("approval".equals(text(item.get("type"))) && text(item.get("approver")).isBlank()) throw new ApiException(400, "请为审批节点设置审批人");
        }
        Set<String> ids = new HashSet<>();
        List<Map<String, Object>> ordered = nodes.stream().map(this::object).toList();
        if (!"start".equals(text(ordered.get(0).get("type"))) || !"end".equals(text(ordered.get(ordered.size()-1).get("type")))) throw new ApiException(400, "流程必须以开始节点起始、结束节点收尾");
        for (Map<String, Object> node : ordered) {
            String nodeId = text(node.get("id"));
            if (nodeId.isBlank() || !ids.add(nodeId) || !Set.of("start", "end", "approval", "condition").contains(text(node.get("type")))) throw new ApiException(400, "节点类型或标识无效");
        }
        Set<String> links = new HashSet<>();
        for (Object edge : edges) {
            Map<String, Object> item = object(edge);
            links.add(text(item.get("source")) + "->" + text(item.get("target")));
        }
        if (edges.size() != ordered.size()-1 || links.size() != edges.size()) throw new ApiException(400, "流程连线不完整");
        for (int i=0; i<ordered.size()-1; i++) {
            if (!links.contains(text(ordered.get(i).get("id")) + "->" + text(ordered.get(i+1).get("id")))) throw new ApiException(400, "流程连线须按节点顺序连接");
        }
    }

    private long tenant() { return system.requireCurrentUser().tenantId(); }
    private List<Map<String, Object>> rows(String sql, Object... args) { return JdbcMaps.camelCase(jdbc.queryForList(sql, args)); }
    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private long number(Object value) { try { return Long.parseLong(text(value)); } catch (Exception ignored) { return 0; } }
    private boolean enabled(Object value) { return ModuleActionService.enabled(value); }
    private String json(Object value) { try { return json.writeValueAsString(value); } catch (Exception exception) { throw new ApiException(400, "流程数据格式无效"); } }
    private Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> raw) { Map<String, Object> copy = new LinkedHashMap<>(); raw.forEach((key, item) -> copy.put(String.valueOf(key), item)); return copy; }
        try { return value == null ? Map.of() : json.readValue(text(value), new TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (Exception exception) { throw new ApiException(400, "流程数据格式无效"); }
    }
}
