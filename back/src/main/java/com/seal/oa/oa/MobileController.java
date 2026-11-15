package com.seal.oa.oa;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.system.SystemService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Mobile navigation adapter. Business data and task visibility remain owned by existing services. */
@RestController
@RequestMapping("/mobile")
public class MobileController {
    private final ActivityController activity;
    private final SystemService system;
    private final JdbcTemplate jdbc;

    public MobileController(ActivityController activity, SystemService system, JdbcTemplate jdbc) {
        this.activity = activity; this.system = system; this.jdbc = jdbc;
    }

    @GetMapping("/tasks/{kind}")
    public Map<String,Object> tasks(@PathVariable String kind,
            @RequestParam(defaultValue="1") int pageNum, @RequestParam(defaultValue="20") int pageSize) {
        var user = system.requireCurrentUser();
        Map<String,Object> page = source(kind, Math.max(1,pageNum), Math.min(100,Math.max(1,pageSize)));
        List<Map<String,Object>> rows = rows(page);
        Set<String> read = new HashSet<>(jdbc.queryForList(
            "SELECT message_key FROM oa_mobile_message_read WHERE tenant_id = ? AND user_id = ?",
            String.class, user.tenantId(), user.userId()));
        for (Map<String,Object> row : rows) {
            String key = key(kind, row);
            row.put("mobileKey", key); row.put("mobileKind", kind); row.put("read", read.contains(key));
        }
        return ApiResult.page(rows, ((Number)page.getOrDefault("total", rows.size())).longValue());
    }

    @GetMapping("/tasks/{kind}/{key}")
    public Map<String,Object> detail(@PathVariable String kind, @PathVariable String key) {
        system.requireCurrentUser();
        return ApiResult.ok(find(kind,key));
    }

    @PostMapping("/tasks/{kind}/{key}/read")
    public Map<String,Object> read(@PathVariable String kind, @PathVariable String key) {
        var user = system.requireCurrentUser();
        find(kind,key); // A guessed key can never mark another user's message.
        try { jdbc.update("""
            INSERT INTO oa_mobile_message_read (tenant_id,user_id,message_key,read_time)
            SELECT ?,?,?,CURRENT_TIMESTAMP WHERE NOT EXISTS (
              SELECT 1 FROM oa_mobile_message_read WHERE tenant_id=? AND user_id=? AND message_key=?)
            """, user.tenantId(), user.userId(), key, user.tenantId(), user.userId(), key);
        } catch (org.springframework.dao.DuplicateKeyException alreadyRead) {
            // Concurrent taps from H5 and a mini program are the same read receipt.
        }
        return ApiResult.ok();
    }

    private Map<String,Object> find(String kind,String key) {
        if (!key.matches("[0-9a-f]{64}")) throw new ApiException(400,"消息标识无效");
        for (int pageNum=1;;pageNum++) {
            Map<String,Object> page=source(kind,pageNum,100);
            List<Map<String,Object>> rows=rows(page);
            for (Map<String,Object> row:rows) if (key.equals(key(kind,row))) return row;
            if (rows.isEmpty() || pageNum*100L >= ((Number)page.getOrDefault("total",0)).longValue()) break;
        }
        throw new ApiException(404,"该待办已处理、已撤回或你没有访问权限，请刷新列表");
    }

    private Map<String,Object> source(String kind,int page,int size) {
        return switch(kind) {
            case "todo" -> activity.waitingTasks(page,size,Map.of());
            case "done" -> activity.completedTasks(page,size);
            case "copy" -> activity.copiedTasks(page,size);
            case "start" -> {
                Map<String,Object> all=activity.startedTasks();
                List<Map<String,Object>> values=rows(all);
                int from=(int)Math.min((long)(page-1)*size,values.size());
                yield ApiResult.page(values.subList(from,Math.min(from+size,values.size())),values.size());
            }
            default -> throw new ApiException(400,"不支持的待办类型");
        };
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> rows(Map<String,Object> page) {
        return ((List<Map<String,Object>>)page.getOrDefault("rows",List.of())).stream()
            .map(LinkedHashMap<String,Object>::new).map(row -> (Map<String,Object>)row).toList();
    }

    static String key(String kind,Map<String,Object> row) {
        String value=kind+"|"+row.getOrDefault("id","")+"|"+row.getOrDefault("taskId","")+"|"
            +row.getOrDefault("instanceId",row.getOrDefault("processInstanceId",""))+"|"+row.getOrDefault("createTime","");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
