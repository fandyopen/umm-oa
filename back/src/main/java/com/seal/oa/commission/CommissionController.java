package com.seal.oa.commission;

import com.seal.oa.common.ApiResult;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/project/commission")
public class CommissionController {
    private final CommissionService service;
    public CommissionController(CommissionService service){this.service=service;}
    @GetMapping("/rules") public Map<String,Object> rules(){return ApiResult.ok(service.rules());}
    @PostMapping("/rules") public Map<String,Object> saveRule(@RequestBody Map<String,Object>b){return ApiResult.ok(service.saveRule(b));}
    @PostMapping("/rules/{id}/enabled") public Map<String,Object> enable(@PathVariable long id,@RequestBody Map<String,Object>b){service.toggleRule(id,b);return ApiResult.ok();}
    @GetMapping("/options") public Map<String,Object> options(){return ApiResult.ok(service.options());}
    @GetMapping("/applications/{id}") public Map<String,Object> attribution(@PathVariable long id){return ApiResult.ok(service.attributionForApplication(id));}
    @PostMapping("/applications/{id}") public Map<String,Object> saveAttribution(@PathVariable long id,@RequestBody Map<String,Object>b){return ApiResult.ok(service.saveAttribution(id,b));}
    @PostMapping("/attributions/{id}/confirm") public Map<String,Object> confirm(@PathVariable long id,@RequestBody Map<String,Object>b){service.confirmAttribution(id,b,true);return ApiResult.ok();}
    @PostMapping("/attributions/{id}/reopen") public Map<String,Object> reopen(@PathVariable long id,@RequestBody Map<String,Object>b){service.confirmAttribution(id,b,false);return ApiResult.ok();}
    @GetMapping("/receipts/{id}/allocation") public Map<String,Object> allocation(@PathVariable long id){
        var result=new java.util.LinkedHashMap<>(service.allocation(id));result.put("signature",service.allocationSignature(id));return ApiResult.ok(result);
    }
    @PostMapping("/receipts/{id}/allocation") public Map<String,Object> allocate(@PathVariable long id,@RequestBody Map<String,Object>b){service.allocate(id,b);return ApiResult.ok();}
    @GetMapping("/overview") public Map<String,Object> overview(@RequestParam(required=false) String cutoff){return ApiResult.ok(service.overview(cutoff));}
    @GetMapping("/batches") public Map<String,Object> batches(){return ApiResult.ok(service.batches());}
    @PostMapping("/batches") public Map<String,Object> generate(@RequestBody Map<String,Object>b){return ApiResult.ok(service.generate(0,b));}
    @GetMapping("/batches/{id}") public Map<String,Object> detail(@PathVariable long id){return ApiResult.ok(service.detail(id));}
    @PostMapping("/batches/{id}/recalculate") public Map<String,Object> regenerate(@PathVariable long id,@RequestBody Map<String,Object>b){return ApiResult.ok(service.generate(id,b));}
    @PostMapping("/batches/{id}/void") public Map<String,Object> voidBatch(@PathVariable long id,@RequestBody Map<String,Object>b){service.voidBatch(id,b);return ApiResult.ok();}
    @PostMapping("/batches/{id}/payroll") public Map<String,Object> payroll(@PathVariable long id,@RequestBody Map<String,Object>b){return ApiResult.ok(service.toPayroll(id,b));}
    @GetMapping("/self") public Map<String,Object> self(){return ApiResult.ok(service.self());}
}
