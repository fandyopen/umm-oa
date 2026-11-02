package com.seal.oa.recruitment;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static com.seal.oa.recruitment.RecruitmentService.*;

@Service
public class RecruitmentFileService {
    final RecruitmentService s;
    final RecruitmentPipelineService pipeline;
    final Path root;
    public RecruitmentFileService(RecruitmentService s,RecruitmentPipelineService pipeline,@Value("$"+"{app.recruit-private-dir:./data/recruit-private}")String directory){this.s=s;this.pipeline=pipeline;this.root=Path.of(directory).toAbsolutePath().normalize();}
    Map<String,Object> store(MultipartFile file){if(file.isEmpty()||file.getSize()>20*1024*1024)throw new ApiException(400,"文件须为1字节至20MB");String name=required(file.getOriginalFilename(),200,"文件名");if(name.contains("/")||name.contains(String.valueOf((char)92))||name.contains("\r")||name.contains("\n"))throw new ApiException(400,"文件名无效");String extension=name.contains(".")?name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT):"";
        if(!Set.of(".pdf",".doc",".docx",".png",".jpg",".jpeg").contains(extension))throw new ApiException(400,"支持PDF、Word、PNG和JPG文件");String type=switch(extension){case ".pdf"->"application/pdf";case ".doc"->"application/msword";case ".docx"->"application/vnd.openxmlformats-officedocument.wordprocessingml.document";case ".png"->"image/png";default->"image/jpeg";};
        String key=UUID.randomUUID()+extension;Path path=root.resolve(key);try{byte[]header;try(var in=file.getInputStream()){header=in.readNBytes(8);}boolean valid=switch(extension){case ".pdf"->header.length>=5&&new String(header,0,5,StandardCharsets.US_ASCII).equals("%PDF-");case ".docx"->header.length>=4&&header[0]==0x50&&header[1]==0x4b;case ".doc"->header.length>=4&&(header[0]&255)==0xd0&&(header[1]&255)==0xcf;case ".png"->header.length>=4&&(header[0]&255)==0x89&&header[1]==0x50;default->header.length>=3&&(header[0]&255)==255&&(header[1]&255)==216;};if(!valid)throw new ApiException(400,"文件内容与扩展名不匹配");Files.createDirectories(root);try(var in=file.getInputStream()){Files.copy(in,path);}if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{Files.deleteIfExists(path);}catch(Exception ignored){}}});return Map.of("key",key,"name",name,"type",type,"size",file.getSize());}catch(ApiException e){throw e;}catch(Exception e){throw new ApiException(500,"文件保存失败");}}
    @Transactional public Map<String,Object> uploadResume(long talent,MultipartFile file){s.talent(talent,true);s.unlocked("talent",talent);Map<String,Object>f=store(file);long version=s.jdbc().queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM oa_recruit_resume WHERE talent_id=?",Long.class,talent);long id=s.insert("INSERT INTO oa_recruit_resume (talent_id,version_no,file_key,original_name,content_type,file_size,create_by) VALUES (?,?,?,?,?,?,?)",talent,version,f.get("key"),f.get("name"),f.get("type"),f.get("size"),AuthContext.userId());s.event("talent",talent,"resume_upload","上传简历 V"+version,Map.of("resumeId",id));return Map.of("id",id,"versionNo",version);}
    @Transactional public Map<String,Object> uploadAttachment(long application,MultipartFile file){pipeline.application(application,true);Map<String,Object>f=store(file);long id=s.insert("INSERT INTO oa_recruit_attachment (application_id,file_key,original_name,content_type,file_size,create_by) VALUES (?,?,?,?,?,?)",application,f.get("key"),f.get("name"),f.get("type"),f.get("size"),AuthContext.userId());s.event("application",application,"upload","上传Offer或入职凭证",Map.of("attachmentId",id));return Map.of("id",id,"name",f.get("name"));}
    public ResponseEntity<Resource> resume(long id,boolean preview){s.permission(TALENT,preview?"preview":"download");Map<String,Object>f=s.one("SELECT * FROM oa_recruit_resume WHERE id=?",id);long talent=num(f.get("talentId"));s.talent(talent,false);if(preview&&!Set.of("application/pdf","image/png","image/jpeg").contains(text(f.get("contentType"))))throw new ApiException(400,"Word简历请使用下载权限查看");s.event("talent",talent,preview?"resume_preview":"resume_download",preview?"预览简历":"下载简历",Map.of("resumeId",id));return response(f,preview);}
    public ResponseEntity<Resource> attachment(long id,boolean preview){Map<String,Object>f=s.one("SELECT * FROM oa_recruit_attachment WHERE id=?",id);pipeline.application(num(f.get("applicationId")),false);s.event("application",num(f.get("applicationId")),"download","查看或下载凭证",Map.of("attachmentId",id));return response(f,preview);}
    ResponseEntity<Resource> response(Map<String,Object>f,boolean preview){Path path=root.resolve(text(f.get("fileKey"))).normalize();if(!path.startsWith(root)||!Files.isRegularFile(path))throw new ApiException(404,"文件不存在，请确认已同步附件目录");String type=text(f.get("contentType"));boolean inline=preview&&(type.equals("application/pdf")||type.startsWith("image/"));return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,(inline?ContentDisposition.inline():ContentDisposition.attachment()).filename(text(f.get("originalName")),StandardCharsets.UTF_8).build().toString()).contentType(MediaType.parseMediaType(type)).body(new FileSystemResource(path));}
}
