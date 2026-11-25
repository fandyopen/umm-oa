package com.seal.oa.system;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

@Service
public class JunkCleanupService {
    private final Path project;
    private final JdbcTemplate jdbc;
    private final SystemService system;
    private static final long ACTIVE_LOG_WINDOW_MS = 30L * 60 * 1000;

    public JunkCleanupService(JdbcTemplate jdbc, SystemService system,
            @Value("${app.junk-cleanup-project-root:}") String projectRoot) {
        this.jdbc = jdbc; this.system = system;
        Path working = Path.of("").toAbsolutePath().normalize();
        Path configured = projectRoot == null || projectRoot.isBlank() ? working : Path.of(projectRoot).toAbsolutePath().normalize();
        this.project = Files.isDirectory(configured.resolve("src")) && Files.isDirectory(configured.resolve("back"))
                ? configured
                : Files.isDirectory(configured.getParent() == null ? configured : configured.getParent().resolve("src"))
                    ? configured.getParent() : configured;
    }

    private SystemService.UserAccount admin() {
        if (AuthContext.get() == null) throw new ApiException(401, "请先登录");
        var user = system.requireCurrentUser();
        if (!system.isSuperAdminIdentity(user.userId())) throw new ApiException(403, "垃圾数据清理仅限固定超级管理员使用");
        return user;
    }

    public Map<String, Object> scan() {
        admin();
        List<Map<String, Object>> candidates = candidates();
        long total = candidates.stream().mapToLong(row -> ((Number) row.get("bytes")).longValue()).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidates", candidates); result.put("fileCount", candidates.stream().mapToLong(row -> ((Number) row.get("fileCount")).longValue()).sum());
        result.put("totalBytes", total); result.put("database", databaseStatus()); result.put("protectedFiles", protectedFiles());
        return result;
    }

    public Map<String, Object> clean(Map<String, Object> body) {
        var user = admin();
        String reason = Objects.toString(body.get("reason"), "").trim();
        if (reason.isEmpty() || reason.length() > 500) throw new ApiException(400, "请填写清理原因（最多500字）");
        Set<String> selected = new LinkedHashSet<>();
        Object raw = body.get("candidateIds");
        if (raw instanceof Collection<?> collection) collection.forEach(value -> selected.add(String.valueOf(value)));
        if (selected.isEmpty()) throw new ApiException(400, "请选择要清理的候选项");
        List<Map<String, Object>> all = candidates();
        List<Map<String, Object>> chosen = all.stream().filter(row -> selected.contains(row.get("id"))).toList();
        if (chosen.size() != selected.size()) throw new ApiException(409, "扫描结果已变化，请重新扫描后再清理");
        for (Map<String, Object> item : chosen) {
            try {
                Size current = measure(safePath(String.valueOf(item.get("relativePath"))), Boolean.TRUE.equals(item.get("oldLog")), protectedPath(item));
                if (current.bytes != ((Number) item.get("bytes")).longValue() || current.count != ((Number) item.get("fileCount")).longValue())
                    throw new ApiException(409, "候选文件已变化，请重新扫描：" + item.get("relativePath"));
            } catch (IOException ex) { throw new ApiException(409, "候选文件无法读取，请重新扫描：" + item.get("relativePath")); }
        }
        List<Map<String, Object>> results = new ArrayList<>();
        long bytes = 0, files = 0;
        for (Map<String, Object> item : chosen) {
            Path target = safePath(String.valueOf(item.get("relativePath")));
            long expectedBytes = ((Number) item.get("bytes")).longValue();
            long expectedCount = ((Number) item.get("fileCount")).longValue();
            try {
                remove(target, protectedPath(item));
                bytes += expectedBytes; files += expectedCount;
                results.add(Map.of("name", item.get("name"), "relativePath", item.get("relativePath"), "status", "已清理", "bytes", expectedBytes, "fileCount", expectedCount));
            } catch (IOException ex) {
                Size remaining;
                try { remaining = measure(target, Boolean.TRUE.equals(item.get("oldLog")), protectedPath(item)); } catch (IOException ignored) { remaining = new Size(0, 0); }
                long releasedBytes = Math.max(0, expectedBytes - remaining.bytes), releasedCount = Math.max(0, expectedCount - remaining.count);
                bytes += releasedBytes; files += releasedCount;
                results.add(Map.of("name", item.get("name"), "relativePath", item.get("relativePath"), "status", "部分失败：" + Objects.toString(ex.getMessage(), "IO错误"), "bytes", releasedBytes, "fileCount", releasedCount));
            }
        }
        String id = UUID.randomUUID().toString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id); result.put("files", results); result.put("releasedBytes", bytes); result.put("releasedFileCount", files);
        jdbc.update("INSERT INTO sys_junk_cleanup_history(id,tenant_id,operator_id,operator_name,reason,result_json,released_bytes,released_file_count) VALUES(?,?,?,?,?,?,?,?)",
                id, user.tenantId(), user.userId(), user.username(), reason, json(result), bytes, files);
        return result;
    }

    public List<Map<String, Object>> history() {
        var user = admin();
        return jdbc.queryForList("SELECT id,operator_name,reason,result_json,released_bytes,released_file_count,create_time FROM sys_junk_cleanup_history WHERE tenant_id=? ORDER BY create_time DESC LIMIT 100", user.tenantId());
    }

    private List<Map<String, Object>> candidates() {
        List<Map<String, Object>> rows = new ArrayList<>();
        addDirectory(rows, "frontend-vite-cache", "前端", "Vite 依赖缓存", "node_modules/.vite", false);
        addDirectory(rows, "frontend-dist", "前端", "前端构建产物", "dist", false);
        addDirectory(rows, "backend-target", "后端", "后端构建产物", "back/target", false);
        addOldLogs(rows, "back", 1); addOldLogs(rows, "back/logs"); addOldLogs(rows, "back/data"); addOldLogs(rows, "logs");
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("relativePath"))));
        return rows;
    }

    private void addDirectory(List<Map<String, Object>> rows, String id, String side, String name, String relative, boolean oldLog) {
        Path target = safePath(relative);
        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) return;
        try {
            Path preserve = "back/target".equals(relative) ? runningJar() : null;
            Size size = measure(target, oldLog, preserve);
            if (size.count > 0) {
                Map<String, Object> row = candidate(id, side, name, relative, size, oldLog);
                if (preserve != null) row.put("preservePath", preserve.toString());
                rows.add(row);
            }
        } catch (IOException ignored) { }
    }

    private void addOldLogs(List<Map<String, Object>> rows, String relativeDir) {
        addOldLogs(rows, relativeDir, 2);
    }

    private void addOldLogs(List<Map<String, Object>> rows, String relativeDir, int maxDepth) {
        Path dir = safePath(relativeDir);
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(dir)) return;
        try (Stream<Path> paths = Files.walk(dir, maxDepth)) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".log"))
                    .filter(path -> oldEnough(path))
                    .forEach(path -> {
                        String relative = project.relativize(path).toString().replace('\\', '/');
                        try { rows.add(candidate("log-" + Integer.toHexString(relative.hashCode()), relative.startsWith("back/") ? "后端" : "前端", "过期日志", relative, measure(path, true), true)); }
                        catch (IOException ignored) { }
                    });
        } catch (IOException ignored) { }
    }

    private boolean oldEnough(Path file) {
        try { return Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toMillis() < System.currentTimeMillis() - ACTIVE_LOG_WINDOW_MS; }
        catch (IOException ex) { return false; }
    }

    private Map<String, Object> candidate(String id, String side, String name, String relative, Size size, boolean oldLog) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id); row.put("side", side); row.put("name", name); row.put("relativePath", relative);
        row.put("bytes", size.bytes); row.put("fileCount", size.count); row.put("oldLog", oldLog); return row;
    }

    private Path safePath(String relative) {
        Path target = project.resolve(relative).normalize();
        if (!target.startsWith(project) || relative.contains("..")) throw new ApiException(400, "非法清理路径");
        Path current = project;
        for (Path part : project.relativize(target)) { current = current.resolve(part); if (Files.isSymbolicLink(current)) throw new ApiException(400, "不支持清理符号链接"); }
        return target;
    }

    private Size measure(Path path, boolean oldLog) throws IOException { return measure(path, oldLog, null); }

    private Size measure(Path path, boolean oldLog, Path preserve) throws IOException {
        final long[] values = {0, 0};
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return new Size(0, 0);
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isRegularFile() && !Files.isSymbolicLink(file) && (preserve == null || !file.toAbsolutePath().normalize().equals(preserve)) && (!oldLog || oldEnough(file))) { values[0] += attrs.size(); values[1]++; }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(dir)) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }
        });
        return new Size(values[0], values[1]);
    }

    private void remove(Path target, Path preserve) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(target, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (preserve != null && file.toAbsolutePath().normalize().equals(preserve)) return FileVisitResult.CONTINUE;
                if (Files.isSymbolicLink(file)) throw new IOException("发现符号链接"); Files.delete(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                if (preserve != null && preserve.startsWith(dir.toAbsolutePath().normalize())) return FileVisitResult.CONTINUE;
                Files.delete(dir); return FileVisitResult.CONTINUE;
            }
        });
    }

    private Path runningJar() {
        String classPath = System.getProperty("java.class.path", "");
        for (String entry : classPath.split(java.util.regex.Pattern.quote(System.getProperty("path.separator")))) {
            try {
                Path path = Path.of(entry).toAbsolutePath().normalize();
                if (path.startsWith(project.resolve("back/target").normalize()) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return path;
            } catch (InvalidPathException ignored) { }
        }
        return null;
    }

    private Path protectedPath(Map<String, Object> item) {
        Object value = item.get("preservePath");
        if (value == null) return null;
        Path path = Path.of(String.valueOf(value)).toAbsolutePath().normalize();
        if (!path.startsWith(project.resolve("back/target").normalize())) throw new ApiException(400, "非法保留文件路径");
        return path;
    }

    private List<Map<String, Object>> protectedFiles() {
        Path jar = runningJar();
        if (jar == null) return List.of();
        try {
            return List.of(Map.of("side", "后端", "name", "正在运行的后端程序", "relativePath", project.relativize(jar).toString().replace('\\', '/'),
                    "bytes", Files.size(jar), "reason", "当前服务正在使用此 JAR，在线清理会中断服务；停止后端后可在服务器上手动删除。"));
        } catch (IOException ex) { return List.of(); }
    }

    private Map<String, Object> databaseStatus() {
        Path db = safePath("back/data/umm_oa.mv.db");
        long bytes = 0;
        try { if (Files.isRegularFile(db, LinkOption.NOFOLLOW_LINKS)) bytes = Files.size(db); } catch (IOException ignored) { }
        return Map.of("name", "本地业务数据库", "relativePath", "back/data/umm_oa.mv.db", "bytes", bytes, "autoClean", false,
                "message", "业务数据库包含正式业务数据，通用垃圾清理不删除数据库表或数据库文件；客户关联记录请走客户数据清理。");
    }

    private String json(Object value) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value); }
        catch (Exception ex) { throw new ApiException(500, "无法记录清理结果"); }
    }
    private record Size(long bytes, long count) { }
}
