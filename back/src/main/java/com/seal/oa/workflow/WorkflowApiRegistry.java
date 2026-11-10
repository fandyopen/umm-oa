package com.seal.oa.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.system.ModuleActionService;
import com.seal.oa.system.SystemService;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Calls approved local controller methods, never arbitrary URLs or scripts. */
@Service
public class WorkflowApiRegistry {
    public static final String HANDLER = "workflow.business-api";
    private final ObjectProvider<RequestMappingHandlerMapping> mappings;
    private final ObjectProvider<SystemService> system;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private volatile Map<String, Endpoint> endpoints;

    public WorkflowApiRegistry(@Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> mappings,
                               ObjectProvider<SystemService> system, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.mappings = mappings;
        this.system = system;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public List<Map<String, Object>> catalog(long moduleId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Endpoint endpoint : entries().values()) {
            if (endpoint.moduleId() != moduleId) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", endpoint.id());
            row.put("name", endpoint.name());
            row.put("moduleId", moduleId);
            row.put("requiredAction", endpoint.action());
            row.put("method", endpoint.httpMethod());
            row.put("path", endpoint.path());
            row.put("hasBody", endpoint.hasBody());
            row.put("pathParameters", pathNames(endpoint.method().getMethod()));
            result.add(row);
        }
        result.sort(Comparator.comparing(row -> String.valueOf(row.get("path")) + row.get("method")));
        return result;
    }

    public void requireCatalogTenant(long moduleId, long tenantId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_module WHERE module_id=? AND tenant_id=? AND status='0'", Long.class, moduleId, tenantId);
        if (count == null || count == 0) throw new ApiException(404, "当前企业不存在此启用模块");
    }

    public Endpoint require(long moduleId, Map<String, Object> config) {
        Endpoint endpoint = entries().get(String.valueOf(config.getOrDefault("endpoint", "")));
        if (endpoint == null || endpoint.moduleId() != moduleId) {
            throw new ApiException(400, "请选择当前模块允许接入的业务接口");
        }
        Map<String, Object> paths = map(config.get("pathMapping"));
        if (!Set.copyOf(pathNames(endpoint.method().getMethod())).equals(paths.keySet())) {
            throw new ApiException(400, "请完整配置业务接口的路径参数映射");
        }
        paths.values().forEach(this::validateSource);
        Map<String, Object> body = map(config.get("bodyMapping"));
        if (!endpoint.hasBody() && !body.isEmpty()) throw new ApiException(400, "该接口没有请求体");
        body.forEach((target, source) -> { validateSource(target); validateSource(source); });
        Object fields = config.get("displayFields");
        if (fields != null && !(fields instanceof List<?>)) throw new ApiException(400, "审批展示字段配置无效");
        if (fields instanceof List<?> list) for (Object field : list) {
            Map<String, Object> item = map(field);
            validateSource(item.get("field"));
            if (String.valueOf(item.getOrDefault("label", "")).isBlank()) throw new ApiException(400, "请填写审批字段名称");
        }
        String title = String.valueOf(config.getOrDefault("titleTemplate", ""));
        if (title.length() > 200) throw new ApiException(400, "申请标题模板最多200字");
        return endpoint;
    }

    public void validate(WorkflowActionContext context) {
        Endpoint endpoint = require(context.moduleId(), context.config());
        asApplicant(context, () -> {
            Long user = AuthContext.userId();
            Long allowedTenant = jdbc.queryForObject("SELECT COUNT(*) FROM sys_module m JOIN sys_user u ON u.tenant_id=m.tenant_id WHERE m.module_id=? AND u.user_id=? AND m.status='0'", Long.class, context.moduleId(), user);
            if (allowedTenant == null || allowedTenant == 0) throw new ApiException(403, "不能操作其他企业或已停用的模块");
            if (!system.getObject().hasModuleAction(user, endpoint.moduleId(), endpoint.action())
                    || !system.getObject().hasModuleAction(user, context.moduleId(), context.actionCode())) {
                throw new ApiException(403, "申请人没有该业务接口的操作权限");
            }
            Object[] arguments = arguments(endpoint, context);
            WorkflowEndpoint options = endpoint.method().getMethodAnnotation(WorkflowEndpoint.class);
            if (options != null && !options.validator().isBlank()) {
                try {
                    Method validator = endpoint.method().getBeanType().getMethod(options.validator(), endpoint.method().getMethod().getParameterTypes());
                    invoke(endpoint.method().createWithResolvedBean().getBean(), validator, arguments);
                } catch (NoSuchMethodException exception) {
                    throw new ApiException(500, "业务接口校验器配置错误");
                }
            }
            return null;
        });
    }

    public Object execute(WorkflowActionContext context) {
        validate(context);
        Endpoint endpoint = require(context.moduleId(), context.config());
        if (context.requestId() == 0) requireDirectExecutionAllowed(endpoint);
        return asApplicant(context, () -> {
            Object result = invoke(endpoint.method().createWithResolvedBean().getBean(), endpoint.method().getMethod(), arguments(endpoint, context));
            if (result instanceof Map<?, ?> response && response.get("code") instanceof Number code && code.intValue() != 200) {
                throw new ApiException(code.intValue(), String.valueOf(response.get("msg")));
            }
            return result;
        });
    }

    public void requireDirectExecutionAllowed(Endpoint endpoint) {
        requireDirectExecutionAllowed(endpoint.moduleId(), endpoint.action());
    }

    public void requireDirectExecutionAllowed(long moduleId, String action) {
        for (String json : jdbc.queryForList("""
                SELECT a.config_json FROM sys_module_action a
                JOIN oa_module_action_flow b ON b.module_id=a.module_id AND b.action_code=a.action_code AND b.tenant_app_id=1
                JOIN oa_process_design d ON d.id=b.process_design_id
                WHERE a.module_id=? AND a.handler_key=? AND a.status='0' AND a.flow_design_enabled=1
                  AND b.approval_enabled=1 AND d.status='published'
                """, String.class, moduleId, HANDLER)) {
            try {
                Endpoint protectedEndpoint = entries().get(mapper.readValue(json, Map.class).get("endpoint"));
                if (protectedEndpoint == null) throw new ApiException(400, "已启用审批的业务接口配置无效，请联系管理员");
                if (protectedEndpoint.action().equals(action))
                    throw new ApiException(409, "此业务已启用审批，请通过已配置审批的按钮提交，不能直接保存");
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new ApiException(400, "按钮业务接口配置无效，请联系管理员");
            }
        }
    }

    private synchronized Map<String, Endpoint> entries() {
        if (endpoints != null) return endpoints;
        Map<String, Endpoint> found = new LinkedHashMap<>();
        mappings.getObject().getHandlerMethods().forEach((mapping, method) -> {
            String owner = method.getBeanType().getPackageName();
            if (!Set.of("com.seal.oa.oa", "com.seal.oa.recruitment", "com.seal.oa.hr").contains(owner)) return;
            RequiresModuleAction permission = method.getMethodAnnotation(RequiresModuleAction.class);
            if (method.hasMethodAnnotation(com.seal.oa.web.RequiresAnyModuleAction.class)) return;
            if (permission == null || Set.of("", "access", "write", "manage", "approve").contains(permission.action())) return;
            if (ModuleActionService.isManagedBusinessAction(permission.moduleId(), permission.action())) return;
            if (!Map.class.isAssignableFrom(method.getMethod().getReturnType()) || !eligibleParameters(method.getMethod())) return;
            for (RequestMethod http : mapping.getMethodsCondition().getMethods()) {
                if (!Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH).contains(http)) continue;
                for (String path : mapping.getPatternValues()) {
                    WorkflowEndpoint label = method.getMethodAnnotation(WorkflowEndpoint.class);
                    String id = http.name() + " " + path;
                    found.put(id, new Endpoint(id, permission.moduleId(), permission.action(),
                            label == null ? method.getMethod().getName() : label.name(), http.name(), path, method,
                            java.util.Arrays.stream(method.getMethod().getParameters()).anyMatch(p -> p.isAnnotationPresent(RequestBody.class))));
                }
            }
        });
        endpoints = Map.copyOf(found);
        return endpoints;
    }

    private boolean eligibleParameters(Method method) {
        for (Parameter parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(RequestBody.class)) {
                if (!Map.class.isAssignableFrom(parameter.getType())) return false;
            } else if (parameter.isAnnotationPresent(PathVariable.class)) {
                if (!Set.of(Long.class, long.class, Integer.class, int.class, String.class).contains(parameter.getType())) return false;
            } else return false;
        }
        return true;
    }

    private Object[] arguments(Endpoint endpoint, WorkflowActionContext context) {
        Parameter[] parameters = endpoint.method().getMethod().getParameters();
        Object[] args = new Object[parameters.length];
        Map<String, Object> paths = map(context.config().get("pathMapping"));
        Map<String, Object> bodyMapping = map(context.config().get("bodyMapping"));
        for (int index = 0; index < parameters.length; index++) {
            Parameter parameter = parameters[index];
            if (parameter.isAnnotationPresent(RequestBody.class)) {
                Map<String, Object> body = new LinkedHashMap<>();
                if (bodyMapping.isEmpty()) context.payload().forEach((key, value) -> { if (!key.startsWith("_")) body.put(key, value); });
                else bodyMapping.forEach((target, source) -> put(body, target, read(context.payload(), String.valueOf(source))));
                args[index] = mapper.convertValue(body, mapper.constructType(parameter.getParameterizedType()));
            } else {
                String name = pathName(parameter);
                Object value = read(context.payload(), String.valueOf(paths.get(name)));
                if (value == null || String.valueOf(value).isBlank()) throw new ApiException(400, "缺少路径参数：" + name);
                try { args[index] = mapper.convertValue(value, parameter.getType()); }
                catch (IllegalArgumentException exception) { throw new ApiException(400, "路径参数格式错误：" + name); }
            }
        }
        return args;
    }

    private <T> T asApplicant(WorkflowActionContext context, Supplier<T> operation) {
        AuthContext.CurrentUser previous = AuthContext.get();
        Long applicantId = context.operatorId();
        if (context.requestId() > 0 && context.payload().get("_workflowApplicantId") instanceof Number id) applicantId = id.longValue();
        List<AuthContext.CurrentUser> users = applicantId != null
                ? jdbc.query("SELECT user_id,user_name FROM sys_user WHERE user_id=? AND status='0' AND del_flag='0'", (rs, i) -> new AuthContext.CurrentUser(rs.getLong(1), rs.getString(2)), applicantId)
                : jdbc.query("SELECT user_id,user_name FROM sys_user WHERE user_name=? AND status='0' AND del_flag='0'", (rs, i) -> new AuthContext.CurrentUser(rs.getLong(1), rs.getString(2)), context.operatorName());
        if (users.size() != 1) throw new ApiException(403, "申请人不存在或已停用，无法执行业务");
        AuthContext.set(users.get(0));
        try { return operation.get(); }
        finally { if (previous == null) AuthContext.clear(); else AuthContext.set(previous); }
    }

    private Object invoke(Object bean, Method method, Object[] args) {
        try { return method.invoke(bean, args); }
        catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException cause) throw cause;
            throw new ApiException(500, "业务接口执行失败");
        } catch (IllegalAccessException exception) { throw new ApiException(500, "业务接口不可调用"); }
    }

    private List<String> pathNames(Method method) {
        return java.util.Arrays.stream(method.getParameters()).filter(p -> p.isAnnotationPresent(PathVariable.class)).map(this::pathName).toList();
    }

    private String pathName(Parameter parameter) {
        PathVariable path = parameter.getAnnotation(PathVariable.class);
        return !path.value().isBlank() ? path.value() : !path.name().isBlank() ? path.name() : parameter.getName();
    }

    private void validateSource(Object source) {
        if (!(source instanceof String path) || !path.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*")
                || List.of(path.split("\\.")).stream().anyMatch(Set.of("class", "constructor", "prototype", "__proto__")::contains)) {
            throw new ApiException(400, "字段映射只支持普通字段名及点分隔路径");
        }
    }

    public static Object read(Map<String, Object> payload, String path) {
        Object value = payload;
        for (String part : path.split("\\.")) value = value instanceof Map<?, ?> map ? map.get(part) : null;
        return value;
    }

    @SuppressWarnings("unchecked")
    private void put(Map<String, Object> body, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = body;
        for (int i = 0; i < parts.length - 1; i++) {
            Object child = current.computeIfAbsent(parts[i], ignored -> new LinkedHashMap<String, Object>());
            if (!(child instanceof Map<?, ?>)) throw new ApiException(400, "请求字段映射存在冲突：" + path);
            current = (Map<String, Object>) child;
        }
        current.put(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?>)) throw new ApiException(400, "业务接口配置必须为对象");
        return (Map<String, Object>) value;
    }

    public record Endpoint(String id, long moduleId, String action, String name, String httpMethod, String path, HandlerMethod method, boolean hasBody) { }
}
