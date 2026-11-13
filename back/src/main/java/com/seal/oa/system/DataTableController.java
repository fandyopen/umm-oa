package com.seal.oa.system;

import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/system/data-tables")
@RequiresModuleAction(moduleId=341500L, action="access")
public class DataTableController {
    private final DataTableService service;
    public DataTableController(DataTableService service) { this.service = service; }
    @GetMapping public Map<String,Object> list() { return ApiResult.ok(service.list()); }
    @GetMapping("/{table}/structure") public Map<String,Object> structure(@PathVariable String table) { return ApiResult.ok(service.detail(table)); }
    @GetMapping("/{table}/rows") public Map<String,Object> rows(@PathVariable String table,@RequestParam Map<String,String> query) { return ApiResult.ok(service.rows(table,query)); }
    @GetMapping("/{table}/history") public Map<String,Object> history(@PathVariable String table,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return ApiResult.ok(service.history(table,page,size)); }
    @PostMapping("/{table}/impact") public Map<String,Object> impact(@PathVariable String table,@RequestBody Map<String,Object> body) { return ApiResult.ok(service.impact(table,body)); }
    @PostMapping("/{table}/rows") @RequiresModuleAction(moduleId=341500L,action="add")
    public Map<String,Object> create(@PathVariable String table,@RequestBody Map<String,Object> body) { return save(table,body,true); }
    @PutMapping("/{table}/rows") @RequiresModuleAction(moduleId=341500L,action="edit")
    public Map<String,Object> update(@PathVariable String table,@RequestBody Map<String,Object> body) { return save(table,body,false); }
    private Map<String,Object> save(String table,Map<String,Object> body,boolean create) {
        try { return ApiResult.ok(service.save(table,body,create)); }
        catch (DataIntegrityViolationException e) { throw new ApiException(400,"保存失败：请检查必填项、唯一值及关联数据"); }
    }
    @DeleteMapping("/{table}/rows") @RequiresModuleAction(moduleId=341500L,action="remove")
    public Map<String,Object> delete(@PathVariable String table,@RequestBody Map<String,Object> body) {
        try { service.delete(table,body); return ApiResult.ok(); }
        catch (DataIntegrityViolationException e) { throw new ApiException(409,"存在关联数据，不能删除"); }
    }
}
