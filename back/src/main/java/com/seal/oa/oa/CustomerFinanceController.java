package com.seal.oa.oa;

import com.seal.oa.common.ApiResult;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/project/crm/finance")
public class CustomerFinanceController {
    private final CustomerFinanceService finance;
    public CustomerFinanceController(CustomerFinanceService finance) { this.finance = finance; }

    @GetMapping("/ledger")
    @RequiresModuleAction(moduleId = 341400, action = "access")
    public Map<String,Object> ledger(@RequestParam Map<String,String> query) { return ApiResult.ok(finance.ledger(query)); }

    @GetMapping("/report")
    @RequiresModuleAction(moduleId = 341400, action = "access")
    public Map<String,Object> report(@RequestParam Map<String,String> query) { return ApiResult.ok(finance.report(query)); }

    @GetMapping("/{type}")
    @RequiresModuleAction(moduleId = 341400, action = "access")
    public Map<String,Object> records(@PathVariable String type, @RequestParam Map<String,String> query) { return ApiResult.ok(finance.records(type, query)); }

    @GetMapping("/records/{id}")
    @RequiresModuleAction(moduleId = 341400, action = "access")
    public Map<String,Object> detail(@PathVariable long id) { return ApiResult.ok(finance.detail(id)); }

    @PostMapping("/{type}")
    @RequiresModuleAction(moduleId = 341400, action = "add")
    public Map<String,Object> create(@PathVariable String type, @RequestBody Map<String,Object> body) { return ApiResult.ok(finance.save(type, 0, body)); }

    @PutMapping("/{type}/{id}")
    @RequiresModuleAction(moduleId = 341400, action = "edit")
    public Map<String,Object> update(@PathVariable String type, @PathVariable long id, @RequestBody Map<String,Object> body) { return ApiResult.ok(finance.save(type, id, body)); }

    @PostMapping("/{type}/{id}/issue")
    @RequiresModuleAction(moduleId = 341400, action = "invoice_issue")
    public Map<String,Object> issue(@PathVariable long id, @RequestBody Map<String,Object> body) { return ApiResult.ok(finance.operate(id,"issue",body)); }

    @PostMapping("/{type}/{id}/pay")
    @RequiresModuleAction(moduleId = 341400, action = "refund_pay")
    public Map<String,Object> pay(@PathVariable long id, @RequestBody Map<String,Object> body) { return ApiResult.ok(finance.operate(id,"pay",body)); }

    @PostMapping("/{type}/{id}/void")
    @RequiresModuleAction(moduleId = 341400, action = "void")
    public Map<String,Object> voidRecord(@PathVariable long id, @RequestBody Map<String,Object> body) { return ApiResult.ok(finance.operate(id,"void",body)); }

    @PostMapping("/receivables")
    @RequiresModuleAction(moduleId = 341400, action = "add")
    public Map<String,Object> saveReceivable(@RequestBody Map<String,Object> body) { return ApiResult.ok(finance.saveReceivable(0, body)); }

    @PutMapping("/receivables/{id}")
    @RequiresModuleAction(moduleId = 341400, action = "edit")
    public Map<String,Object> updateReceivable(@PathVariable long id, @RequestBody Map<String,Object> body) { return ApiResult.ok(finance.saveReceivable(id, body)); }

    @PostMapping("/receivables/{id}/confirm")
    @RequiresModuleAction(moduleId = 341400, action = "confirm")
    public Map<String,Object> confirmReceivable(@PathVariable long id, @RequestBody Map<String,Object> body) { finance.confirmReceivable(id, body, true); return ApiResult.ok(); }

    @PostMapping("/receivables/{id}/revoke")
    @RequiresModuleAction(moduleId = 341400, action = "confirm")
    public Map<String,Object> revokeReceivable(@PathVariable long id, @RequestBody Map<String,Object> body) { finance.confirmReceivable(id, body, false); return ApiResult.ok(); }

    @GetMapping("/receipts")
    @RequiresModuleAction(moduleId = 341400, action = "access")
    public Map<String,Object> receipts(@RequestParam Map<String,String> query) { return ApiResult.ok(finance.receipts(query)); }

    @GetMapping("/collections")
    @RequiresModuleAction(moduleId = 341400, action = "access")
    public Map<String,Object> collections(@RequestParam Map<String,String> query) { return ApiResult.ok(finance.collections(query)); }

    @PostMapping("/collections")
    @RequiresModuleAction(moduleId = 341400, action = "collect")
    public Map<String,Object> collect(@RequestBody Map<String,Object> body) { finance.collect(body); return ApiResult.ok(); }

    @GetMapping("/options")
    @RequiresModuleAction(moduleId = 341400, action = "access")
    public Map<String,Object> options() { return ApiResult.ok(finance.options()); }

    @GetMapping("/receivables")
    @RequiresModuleAction(moduleId = 341400, action = "access")
    public Map<String,Object> receivables(@RequestParam Map<String,String> query) { return ApiResult.ok(finance.records("receivable",query)); }

    @GetMapping("/export/{type}")
    @RequiresModuleAction(moduleId = 341400, action = "export")
    public Map<String,Object> export(@PathVariable String type, @RequestParam Map<String,String> query) { return ApiResult.ok(finance.exportRows(type,query)); }
}
