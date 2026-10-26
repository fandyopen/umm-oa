package com.seal.oa.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class CommonController {

    private final Path uploadRoot;

    public CommonController(@Value("${app.upload-dir:./data/uploads}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @PostMapping(value = "/common/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(400, "上传文件不能为空");
        }
        try {
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            Path directory = uploadRoot.resolve(datePath);
            Files.createDirectories(directory);
            String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
            String extension = original.contains(".")
                    ? original.substring(original.lastIndexOf('.'))
                    : "";
            String newFileName = UUID.randomUUID().toString().replace("-", "") + extension;
            Path target = directory.resolve(newFileName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String url = "/profile/" + datePath + "/" + newFileName;
            Map<String, Object> result = ApiResult.ok();
            result.put("fileName", url);
            result.put("newFileName", newFileName);
            result.put("originalFilename", original);
            result.put("url", url);
            return result;
        } catch (Exception exception) {
            throw new ApiException(500, "上传失败: " + exception.getMessage());
        }
    }

    @GetMapping("/common/download")
    public ResponseEntity<Resource> download(@RequestParam String fileName) {
        return fileResponse(resolve(fileName), fileName);
    }

    @GetMapping("/common/download/resource")
    public ResponseEntity<Resource> downloadResource(@RequestParam String resource) {
        return fileResponse(resolve(resource), resource);
    }

    @GetMapping("/common/download/doc")
    public ResponseEntity<Resource> downloadDocument(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String url
    ) {
        String fileName = name == null ? url : name;
        Path path = resolve(url == null ? fileName : url);
        return fileResponse(path, fileName);
    }

    private ResponseEntity<Resource> fileResponse(Path path, String fileName) {
        if (!Files.isRegularFile(path)) {
            throw new ApiException(404, "文件不存在");
        }
        Resource resource = new FileSystemResource(path);
        String downloadName = fileName == null ? path.getFileName().toString() : path.getFileName().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    private Path resolve(String fileName) {
        String normalized = fileName == null ? "" : fileName.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("profile/")) {
            normalized = normalized.substring("profile/".length());
        }
        Path resolved = uploadRoot.resolve(normalized).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new ApiException(403, "非法文件路径");
        }
        return resolved;
    }
}
