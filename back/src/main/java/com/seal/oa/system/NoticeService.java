package com.seal.oa.system;

import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.common.JdbcMaps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class NoticeService {
    private final JdbcTemplate jdbc;
    private final SystemService system;
    private static final String SUMMARY = "notice_id,notice_title,notice_type,publish_status,create_by,create_time,published_by,published_time,update_by,update_time";
    public NoticeService(JdbcTemplate jdbc, SystemService system) { this.jdbc = jdbc; this.system = system; }

    public Map<String, Object> list(Map<String, String> query, boolean publishedOnly) {
        var user = system.requireCurrentUser();
        StringBuilder where = new StringBuilder(" WHERE tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        if (publishedOnly) where.append(" AND publish_status='published' AND status='0'");
        for (String key : List.of("noticeTitle", "createBy")) {
            String value = text(query.get(key));
            if (!value.isBlank()) { where.append(key.equals("noticeTitle") ? " AND notice_title LIKE ?" : " AND create_by LIKE ?"); args.add("%" + value + "%"); }
        }
        String type = text(query.get("noticeType"));
        if (!type.isBlank()) { where.append(" AND notice_type=?"); args.add(type); }
        String state = text(query.get("publishStatus"));
        if (!publishedOnly && !state.isBlank()) { where.append(" AND publish_status=?"); args.add(state); }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM sys_notice" + where, Long.class, args.toArray());
        int page = positive(query.get("pageNum"), 1), size = Math.min(positive(query.get("pageSize"), 10), 100);
        args.add(size); args.add((long) (page - 1) * size);
        String order = publishedOnly ? "published_time DESC,notice_id DESC" : "create_time DESC,notice_id DESC";
        return ApiResult.page(JdbcMaps.camelCase(jdbc.queryForList("SELECT " + SUMMARY + " FROM sys_notice" + where + " ORDER BY " + order + " LIMIT ? OFFSET ?", args.toArray())), total);
    }

    public Map<String, Object> detail(long id, boolean publishedOnly) {
        return find(id, publishedOnly, false);
    }

    private Map<String, Object> find(long id, boolean publishedOnly, boolean lock) {
        long tenant = system.requireCurrentUser().tenantId();
        var rows = jdbc.queryForList("SELECT * FROM sys_notice WHERE notice_id=? AND tenant_id=?" +
                (publishedOnly ? " AND publish_status='published' AND status='0'" : "") + (lock ? " FOR UPDATE" : ""), id, tenant);
        if (rows.isEmpty()) throw new ApiException(404, "通知不存在或尚未发布");
        return JdbcMaps.camelCase(rows).get(0);
    }

    @Transactional
    public long save(Map<String, Object> body, Long id) {
        var user = system.requireCurrentUser();
        String title = text(body.get("noticeTitle")), type = text(body.get("noticeType")), content = text(body.get("noticeContent"));
        if (title.isBlank() || title.length() > 200) throw new ApiException(400, "请填写公告标题（最多200字）");
        if (!Set.of("1", "2").contains(type)) throw new ApiException(400, "请选择通知或公告类型");
        if (content.length() > 200000) throw new ApiException(400, "通知正文过长");
        String actor = actor(user);
        if (id != null) {
            find(id, false, true);
            // Editing always returns to draft, so unreviewed edits never appear on employee pages.
            jdbc.update("UPDATE sys_notice SET notice_title=?,notice_type=?,notice_content=?,publish_status='draft',status='0',update_by=?,update_time=CURRENT_TIMESTAMP WHERE notice_id=? AND tenant_id=?",
                    title, type, content, actor, id, user.tenantId());
            return id;
        }
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO sys_notice(tenant_id,notice_title,notice_type,notice_content,status,publish_status,create_by) VALUES(?,?,?,?,'0','draft',?)", new String[]{"notice_id"});
            statement.setLong(1, user.tenantId()); statement.setString(2, title); statement.setString(3, type); statement.setString(4, content); statement.setString(5, actor);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    @Transactional
    public long saveAndPublish(Map<String, Object> body, Long id) {
        long savedId = save(body, id);
        publish(savedId);
        return savedId;
    }

    @Transactional
    public void publish(long id) {
        var row = find(id, false, true);
        if ("published".equals(row.get("publishStatus"))) return;
        String content = text(row.get("noticeContent"));
        if (text(row.get("noticeTitle")).isBlank() || content.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").isBlank() && !content.toLowerCase(Locale.ROOT).contains("<img"))
            throw new ApiException(400, "请先填写标题和正文再发布");
        var user = system.requireCurrentUser();
        jdbc.update("UPDATE sys_notice SET publish_status='published',status='0',published_by=?,published_time=CURRENT_TIMESTAMP,update_by=?,update_time=CURRENT_TIMESTAMP WHERE notice_id=? AND tenant_id=?",
                actor(user), actor(user), id, user.tenantId());
    }

    @Transactional
    public void withdraw(long id) {
        var row = find(id, false, true);
        if (!"published".equals(row.get("publishStatus"))) throw new ApiException(409, "仅已发布的通知可以撤下");
        var user = system.requireCurrentUser();
        jdbc.update("UPDATE sys_notice SET publish_status='withdrawn',update_by=?,update_time=CURRENT_TIMESTAMP WHERE notice_id=? AND tenant_id=?", actor(user), id, user.tenantId());
    }

    @Transactional
    public void delete(String ids) {
        if (!ids.matches("[0-9]+(,[0-9]+)*") || ids.split(",").length > 100) throw new ApiException(400, "请选择有效的公告编号，每次最多100条");
        Set<Long> selected = new LinkedHashSet<>();
        for (String value : ids.split(",")) { try { selected.add(Long.parseLong(value)); } catch (NumberFormatException ex) { throw new ApiException(400, "公告编号无效"); } }
        selected.forEach(id -> find(id, false, true));
        long tenant = system.requireCurrentUser().tenantId();
        selected.forEach(id -> jdbc.update("DELETE FROM sys_notice WHERE notice_id=? AND tenant_id=?", id, tenant));
    }
    private String actor(SystemService.UserAccount user) { return text(user.nickname()).isBlank() ? user.username() : user.nickname(); }
    private static String text(Object value) { return Objects.toString(value, "").trim(); }
    private static int positive(String value, int fallback) { try { return Math.max(1, Integer.parseInt(value)); } catch (Exception ex) { return fallback; } }
}
