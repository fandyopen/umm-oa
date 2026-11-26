package com.seal.oa.system;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JunkCleanupServiceTest {
    @TempDir Path project;
    private JdbcTemplate jdbc;
    private JunkCleanupService service;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(project.resolve("src"));
        Files.createDirectories(project.resolve("back"));
        jdbc = mock(JdbcTemplate.class);
        SystemService system = mock(SystemService.class);
        when(system.requireCurrentUser()).thenReturn(new SystemService.UserAccount(1L, 1L, 1L,
                "admin", "管理员", "", "", "", "", "0"));
        when(system.isSuperAdminIdentity(1L)).thenReturn(true);
        AuthContext.set(new AuthContext.CurrentUser(1L, "admin"));
        service = new JunkCleanupService(jdbc, system, project.toString());
    }

    @AfterEach
    void clearUser() { AuthContext.clear(); }

    @Test
    void scanExcludesDependencyLibrariesCachesAndTheirLogs() throws Exception {
        write("node_modules/.vite/deps/jsencrypt.js", "dependency");
        for (String directory : List.of("node_modules", ".vite", ".pnpm", ".yarn", ".m2", ".gradle")) {
            Path log = write("logs/" + directory + "/old.log", "dependency log");
            Files.setLastModifiedTime(log, FileTime.fromMillis(System.currentTimeMillis() - 3600000));
        }
        Map<String, Object> report = service.scan();
        assertEquals(List.of(), report.get("candidates"));
        assertEquals(0L, report.get("totalBytes"));
        assertTrue(Files.exists(project.resolve("node_modules/.vite/deps/jsencrypt.js")));
    }

    @Test
    void oldViteCacheSelectionIsRejectedWithoutDeletingDependencies() throws Exception {
        Path dependency = write("node_modules/.vite/deps/jsencrypt.js", "dependency");
        ApiException failure = assertThrows(ApiException.class, () -> service.clean(Map.of(
                "reason", "旧页面提交", "candidateIds", List.of("frontend-vite-cache"))));
        assertEquals(409, failure.code());
        assertEquals("dependency", Files.readString(dependency));
        verifyNoInteractions(jdbc);
    }

    @Test
    void buildCleanupPreservesNestedDependenciesAndStillRemovesBuildFiles() throws Exception {
        Path output = write("dist/app.js", "build output");
        Path dependency = write("dist/vendor/node_modules/example/index.js", "keep dependency");
        List<?> candidates = (List<?>) service.scan().get("candidates");
        assertEquals(1, candidates.size());
        assertEquals(1L, ((Map<?, ?>) candidates.get(0)).get("fileCount"));
        Map<String, Object> result = service.clean(Map.of("reason", "清理构建文件",
                "candidateIds", List.of("frontend-dist")));
        assertEquals(1L, result.get("releasedFileCount"));
        assertFalse(Files.exists(output));
        assertEquals("keep dependency", Files.readString(dependency));
        assertEquals(List.of(), service.scan().get("candidates"));
    }

    private Path write(String relative, String content) throws Exception {
        Path path = project.resolve(relative);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, content);
    }
}
