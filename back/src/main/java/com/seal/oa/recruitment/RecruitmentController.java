package com.seal.oa.recruitment;

import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.web.RequiresModuleAction;
import com.seal.oa.web.RequiresAnyModuleAction;
import com.seal.oa.web.ModuleActionRequirement;
import com.seal.oa.workflow.WorkflowActionService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import static com.seal.oa.recruitment.RecruitmentService.*;

@RestController
@RequestMapping("/project/recruitment")
public class RecruitmentController {
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public Map<String,Object> invalidParameter(){return ApiResult.error(400,"业务编号或参数格式无效，请刷新后重试");}
    final RecruitmentService s;
    final RecruitmentPipelineService pipeline;
    final RecruitmentApprovalService approvals;
    final RecruitmentFileService files;
    final RecruitmentOwnershipService ownership;
    final WorkflowActionService workflows;
    public RecruitmentController(RecruitmentService s,RecruitmentPipelineService pipeline,RecruitmentApprovalService approvals,RecruitmentFileService files,RecruitmentOwnershipService ownership,WorkflowActionService workflows){this.s=s;this.pipeline=pipeline;this.approvals=approvals;this.files=files;this.ownership=ownership;this.workflows=workflows;}

    @GetMapping("/options")
    @RequiresAnyModuleAction({@ModuleActionRequirement(moduleId=341301,action="access"),@ModuleActionRequirement(moduleId=341302,action="access"),@ModuleActionRequirement(moduleId=341303,action="access")})
    public Map<String,Object> options(){return ApiResult.ok(s.options());}
    @GetMapping("/warnings")
    @RequiresAnyModuleAction({@ModuleActionRequirement(moduleId=341301,action="access"),@ModuleActionRequirement(moduleId=341303,action="access")})
    public Map<String,Object> warnings(){return ApiResult.ok(pipeline.warnings());}
    @GetMapping("/jobs") @RequiresModuleAction(moduleId=341301,action="access")
    public Map<String,Object> jobs(@RequestParam Map<String,String>q){List<Map<String,Object>>r=s.jobs(q);Map<String,Object>result=s.page(r,q);result.put("summary",Map.of("jobs",r.size(),"hired",r.stream().mapToLong(x->num(x.get("hired"))).sum(),"vacancies",r.stream().mapToLong(x->num(x.get("vacancies"))).sum(),"recommendations",r.stream().mapToLong(x->num(x.get("recommendations"))).sum()));return result;}
    @GetMapping("/jobs/{id}") @RequiresModuleAction(moduleId=341301,action="access")
    public Map<String,Object> job(@PathVariable long id){return ApiResult.ok(s.jobDetail(id));}
    @GetMapping("/jobs/duplicates") @RequiresModuleAction(moduleId=341301,action="access")
    public Map<String,Object> duplicateJobs(@RequestParam Map<String,String>q){return s.page(s.duplicateJobs(q),q);}
    @PostMapping("/jobs") @RequiresModuleAction(moduleId=341301,action="add")
    public Map<String,Object> createJob(@RequestBody Map<String,Object>b){return ApiResult.ok(s.createJob(b));}
    @PutMapping("/jobs/{id}") @RequiresModuleAction(moduleId=341301,action="edit")
    public Map<String,Object> editJob(@PathVariable long id,@RequestBody Map<String,Object>b){return ApiResult.ok(s.updateJob(id,b));}
    @DeleteMapping("/jobs/{id}") @RequiresModuleAction(moduleId=341301,action="remove")
    public Map<String,Object> deleteJob(@PathVariable long id){s.deleteJob(id);return ApiResult.ok();}
    @PostMapping("/jobs/{id}/copy") @RequiresModuleAction(moduleId=341301,action="copy")
    public Map<String,Object> copyJob(@PathVariable long id){return ApiResult.ok(s.copyJob(id));}
    @PostMapping("/jobs/{id}/actions/{action}")
    public Map<String,Object> jobAction(@PathVariable long id,@PathVariable String action,@RequestBody Map<String,Object>b){if(!Set.of("status","assign","warning","withdraw").contains(action))throw new ApiException(400,"未知职位操作");s.permission(JOB,action);if("withdraw".equals(action)){approvals.withdraw("job",id,b);return ApiResult.ok();}return ApiResult.ok(s.jobAction(id,action,b));}
    @PostMapping("/jobs/{id}/requests/{action}")
    public Map<String,Object> jobRequest(@PathVariable long id,@PathVariable String action,@RequestBody Map<String,Object>b){if(!Set.of("demand","change").contains(action))throw new ApiException(400,"未知审批动作");return submit(JOB,id,action,b);}
    @PostMapping("/jobs/import") @RequiresModuleAction(moduleId=341301,action="import")
    public Map<String,Object> importJobs(@RequestBody Map<String,Object>b){return ApiResult.ok(s.importRows("jobs",b));}
    @GetMapping("/jobs/export") @RequiresModuleAction(moduleId=341301,action="export")
    public Map<String,Object> exportJobs(@RequestParam Map<String,String>q){return ApiResult.ok(s.exportRows("jobs",q));}
    @GetMapping("/talents") @RequiresModuleAction(moduleId=341302,action="access")
    public Map<String,Object> talents(@RequestParam Map<String,String>q){return s.page(s.talents(q),q);}
    @GetMapping("/talents/duplicates") @RequiresModuleAction(moduleId=341302,action="access")
    public Map<String,Object> duplicates(@RequestParam Map<String,String>q){return s.page(s.duplicates(q),q);}
    @GetMapping("/talents/{id}") @RequiresModuleAction(moduleId=341302,action="access")
    public Map<String,Object> talent(@PathVariable long id){return ApiResult.ok(s.talentDetail(id));}
    @PostMapping("/talents") @RequiresModuleAction(moduleId=341302,action="add")
    public Map<String,Object> createTalent(@RequestBody Map<String,Object>b){return ApiResult.ok(s.createTalent(b));}
    @PutMapping("/talents/{id}") @RequiresModuleAction(moduleId=341302,action="edit")
    public Map<String,Object> editTalent(@PathVariable long id,@RequestBody Map<String,Object>b){return ApiResult.ok(s.updateTalent(id,b));}
    @DeleteMapping("/talents/{id}") @RequiresModuleAction(moduleId=341302,action="remove")
    public Map<String,Object> deleteTalent(@PathVariable long id){s.deleteTalent(id);return ApiResult.ok();}
    @PostMapping("/talents/{id}/actions/{action}")
    public Map<String,Object> talentAction(@PathVariable long id,@PathVariable String action,@RequestBody Map<String,Object>b){if(!Set.of("status","contact","collaborators","withdraw").contains(action))throw new ApiException(400,"未知候选人操作");s.permission(TALENT,"collaborators".equals(action)?"assign":action);if("withdraw".equals(action)){approvals.withdraw("talent",id,b);return ApiResult.ok();}return ApiResult.ok(s.talentAction(id,action,b));}
    @PostMapping("/talents/{id}/requests/{action}")
    public Map<String,Object> talentRequest(@PathVariable long id,@PathVariable String action,@RequestBody Map<String,Object>b){if(!Set.of("merge","handover").contains(action))throw new ApiException(400,"未知审批动作");return submit(TALENT,id,action,b);}
    @PostMapping("/talents/{id}/disputes") @RequiresModuleAction(moduleId=341302,action="dispute")
    public Map<String,Object> createDispute(@PathVariable long id,@RequestBody Map<String,Object>b){return ApiResult.ok(ownership.create(id,b));}
    @PostMapping("/talents/{id}/disputes/{disputeId}/retain") @RequiresModuleAction(moduleId=341302,action="dispute")
    public Map<String,Object> retainDispute(@PathVariable long id,@PathVariable long disputeId,@RequestBody Map<String,Object>b){return ApiResult.ok(ownership.retain(id,disputeId,b));}
    @PostMapping("/talents/import") @RequiresModuleAction(moduleId=341302,action="import")
    public Map<String,Object> importTalents(@RequestBody Map<String,Object>b){return ApiResult.ok(s.importRows("talents",b));}
    @GetMapping("/talents/export") @RequiresModuleAction(moduleId=341302,action="export")
    public Map<String,Object> exportTalents(@RequestParam Map<String,String>q){return ApiResult.ok(s.exportRows("talents",q));}
    @PostMapping("/talents/tags") @RequiresModuleAction(moduleId=341302,action="tag")
    public Map<String,Object> batchTags(@RequestBody Map<String,Object>b){s.batchTags(b);return ApiResult.ok();}
    @PostMapping("/tags") @RequiresModuleAction(moduleId=341302,action="tag")
    public Map<String,Object> saveTag(@RequestBody Map<String,Object>b){s.saveTag(b);return ApiResult.ok();}
    @PostMapping("/searches") @RequiresModuleAction(moduleId=341302,action="access")
    public Map<String,Object> search(@RequestBody Map<String,Object>b){s.saveSearch(b);return ApiResult.ok();}
    @PostMapping("/talents/{id}/tasks") @RequiresModuleAction(moduleId=341302,action="assign")
    public Map<String,Object> task(@PathVariable long id,@RequestBody Map<String,Object>b){return ApiResult.ok(Map.of("id",s.createTask(id,b)));}
    @PostMapping("/tasks/{id}/complete") @RequiresModuleAction(moduleId=341302,action="contact")
    public Map<String,Object> completeTask(@PathVariable long id,@RequestBody Map<String,Object>b){s.completeTask(id,b);return ApiResult.ok();}
    @PostMapping(value="/talents/{id}/resumes",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @RequiresModuleAction(moduleId=341302,action="resume")
    public Map<String,Object> uploadResume(@PathVariable long id,@RequestParam("file")MultipartFile file){return ApiResult.ok(files.uploadResume(id,file));}
    @GetMapping("/resumes/{id}/file") @RequiresModuleAction(moduleId=341302,action="access")
    public ResponseEntity<Resource> resume(@PathVariable long id,@RequestParam(defaultValue="false")boolean preview){return files.resume(id,preview);}
    @GetMapping("/applications") @RequiresModuleAction(moduleId=341303,action="access")
    public Map<String,Object> applications(@RequestParam Map<String,String>q){return s.page(pipeline.list(q),q);}
    @GetMapping("/applications/{id}") @RequiresModuleAction(moduleId=341303,action="access")
    public Map<String,Object> application(@PathVariable long id){return ApiResult.ok(pipeline.detail(id));}
    @PostMapping("/applications") @RequiresModuleAction(moduleId=341303,action="add")
    public Map<String,Object> createApplication(@RequestBody Map<String,Object>b){return ApiResult.ok(pipeline.create(b));}
    @PostMapping("/applications/{id}/replacement")
    public Map<String,Object> replacement(@PathVariable long id,@RequestBody Map<String,Object>b){s.permission(PIPELINE,"guarantee");s.permission(PIPELINE,"add");return ApiResult.ok(pipeline.replacement(id,b));}
    @GetMapping("/applications/export") @RequiresModuleAction(moduleId=341303,action="export")
    public Map<String,Object> exportApplications(@RequestParam Map<String,String>q){List<Map<String,Object>>r=pipeline.list(q);if(r.size()>500)throw new ApiException(400,"每次最多导出500条，请缩小筛选范围");for(Map<String,Object>a:r)s.event("application",num(a.get("id")),"export","导出招聘流程",Map.of());return ApiResult.ok(r);}
    @PostMapping("/applications/{id}/actions/{action}")
    public Map<String,Object> pipelineAction(@PathVariable long id,@PathVariable String action,@RequestBody Map<String,Object>b){if(!Set.of("recommend","feedback","offer","entry","guarantee","exception").contains(action))throw new ApiException(400,"未知流程操作");s.permission(PIPELINE,action);return ApiResult.ok(pipeline.action(id,action,b));}
    @PostMapping("/applications/{id}/interviews") @RequiresModuleAction(moduleId=341303,action="interview")
    public Map<String,Object> createInterview(@PathVariable long id,@RequestBody Map<String,Object>b){return ApiResult.ok(pipeline.interview(id,0,b));}
    @PutMapping("/applications/{id}/interviews/{interviewId}") @RequiresModuleAction(moduleId=341303,action="interview")
    public Map<String,Object> interview(@PathVariable long id,@PathVariable long interviewId,@RequestBody Map<String,Object>b){return ApiResult.ok(pipeline.interview(id,interviewId,b));}
    @PostMapping(value="/applications/{id}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresAnyModuleAction({@ModuleActionRequirement(moduleId=341303,action="offer"),@ModuleActionRequirement(moduleId=341303,action="entry")})
    public Map<String,Object> uploadAttachment(@PathVariable long id,@RequestParam("file")MultipartFile file){return ApiResult.ok(files.uploadAttachment(id,file));}
    @GetMapping("/attachments/{id}/file") @RequiresModuleAction(moduleId=341303,action="access")
    public ResponseEntity<Resource> attachment(@PathVariable long id,@RequestParam(defaultValue="false")boolean preview){return files.attachment(id,preview);}
    Map<String,Object> submit(long module,long id,String action,Map<String,Object>b){s.permission(module,action);Map<String,Object>p=new LinkedHashMap<>(b);p.put("entityId",id);p.put("title","招聘申请："+action+" #"+id);return ApiResult.ok(workflows.execute(Map.of("moduleId",module,"actionCode",action,"payload",p)));}
}
