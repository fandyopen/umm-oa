package com.seal.oa.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Explicit customer ownership graph. Never infers ownership using names, LIKE or bare business IDs. */
@Service
public class CustomerCleanupService {
    public static final long MODULE = 341510L;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SystemService system;
    private final CustomerCleanupGate gate;
    private final TransactionTemplate transaction;
    private final Path backups;

    public CustomerCleanupService(JdbcTemplate jdbc, ObjectMapper json, SystemService system,
            CustomerCleanupGate gate, PlatformTransactionManager manager,
            @Value("${app.customer-cleanup-backup-dir:./data/customer-cleanup-backups}") String backups) {
        this.jdbc=jdbc; this.json=json; this.system=system; this.gate=gate;
        this.backups=Path.of(backups).toAbsolutePath().normalize();
        transaction=new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    private SystemService.UserAccount admin() {
        if (AuthContext.get()==null) throw new ApiException(401,"请先登录");
        var user=system.requireCurrentUser();
        if (!system.isSuperAdminIdentity(user.userId())) throw new ApiException(403,"特殊功能仅限固定超级管理员使用");
        return user;
    }
    public List<Map<String,Object>> search(String keyword) {
        long tenant=admin().tenantId();
        String key=keyword==null?"":keyword.trim();
        if (key.isEmpty()) return List.of();
        if (key.length()>200) throw new ApiException(400,"查询内容过长");
        return jdbc.queryForList("SELECT id,serial,name,owner_user_name,status FROM oa_merchant WHERE tenant_id=? ORDER BY id DESC",tenant)
                .stream().filter(r -> key.equals(str(r.get("id"))) || str(r.get("name")).contains(key)).limit(50).toList();
    }
    public Map<String,Object> preview(long id) { return summary(plan(id,admin().tenantId())); }
    public List<Map<String,Object>> history() {
        return jdbc.queryForList("SELECT id,customer_id,customer_name,operation,reason,operator_name,create_time,result_json FROM sys_customer_cleanup_history WHERE tenant_id=? ORDER BY create_time DESC LIMIT 100",admin().tenantId());
    }
    public byte[] backup(String id) {
        var history=jdbc.queryForList("SELECT backup_name FROM sys_customer_cleanup_history WHERE id=? AND tenant_id=?",id,admin().tenantId());
        if(history.isEmpty()) throw new ApiException(404,"清理记录不存在");
        Path file=backups.resolve(str(history.get(0).get("backup_name"))).normalize();
        if(!file.getParent().equals(backups)) throw new ApiException(400,"无效备份路径");
        try { return Files.readAllBytes(file); } catch(Exception e) { throw new ApiException(404,"备份文件不存在"); }
    }

    public Map<String,Object> execute(long id, Map<String,Object> body) {
        var user=admin();
        String mode=str(body.get("operation")),reason=str(body.get("reason")).trim();
        if(!Set.of("cleanup","delete").contains(mode)) throw new ApiException(400,"请选择清理或删除操作");
        if(reason.isEmpty()||reason.length()>1000) throw new ApiException(400,"请填写清理原因，最多1000字");
        return gate.exclusive(() -> transaction.execute(status -> {
            var customer=jdbc.queryForList("SELECT * FROM oa_merchant WHERE id=? AND tenant_id=? FOR UPDATE",id,user.tenantId());
            if(customer.isEmpty()) throw new ApiException(404,"客户不存在或不属于当前企业");
            Plan p=plan(id,user.tenantId());
            if(!str(p.customer.get("name")).equals(str(body.get("confirmName"))) || !String.valueOf(id).equals(str(body.get("confirmId"))))
                throw new ApiException(400,"确认的客户ID或名称不一致");
            if(!fingerprint(p).equals(str(body.get("signature")))) throw new ApiException(409,"关联数据已变化，请重新预览后确认");
            if("delete".equals(mode) && p.total()>0) throw new ApiException(409,"请先清理关联数据，再彻底删除客户档案");
            String operationId=UUID.randomUUID().toString(), backupName=operationId+".zip";
            // A backup failure aborts before the first database write.
            writeBackup(backupName,Map.of("customer",p.customer,"deleteRows",p.deleted,"sharedRows",p.shared,"reason",reason,"operation",mode,"operator",user.username()));
            if("cleanup".equals(mode)) {
                adjustShared(p,operationId,user.userId());
                for(var entry:p.deleted.entrySet()) for(var row:entry.getValue()) {
                    if("oa_workflow_action_submission".equals(entry.getKey()))
                        jdbc.update("DELETE FROM oa_workflow_action_submission WHERE user_id=? AND submission_key=?",row.get("user_id"),row.get("submission_key"));
                    else jdbc.update("DELETE FROM "+entry.getKey()+" WHERE id=?",row.get("id"));
                }
                jdbc.update("UPDATE oa_merchant SET cooperation_positions=0,referral_count=0,hire_count=0,contract_amount=0,received_amount=0,last_followup_time=NULL,contract_expire_date=NULL,last_payment_date=NULL,update_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",id,user.tenantId());
                Plan residual=plan(id,user.tenantId());
                if(residual.total()>0 || residual.shared.values().stream().anyMatch(rows -> !rows.isEmpty()))
                    throw new ApiException(409,"仍有未清理的关联，本次操作已回滚，请重新预览");
            } else jdbc.update("DELETE FROM oa_merchant WHERE id=? AND tenant_id=?",id,user.tenantId());
            var result=new LinkedHashMap<String,Object>();
            result.put("id",operationId); result.put("operation",mode); result.put("deleted",summary(p).get("tables"));
            result.put("deletedCount",p.total()); result.put("customerDeleted","delete".equals(mode)); result.put("remainingCount",0);
            result.put("payroll",payrollSummary(p));
            jdbc.update("INSERT INTO sys_customer_cleanup_history(id,tenant_id,customer_id,customer_name,operation,reason,operator_id,operator_name,backup_name,result_json) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    operationId,user.tenantId(),id,p.customer.get("name"),mode,reason,user.userId(),user.username(),backupName,encode(result));
            return result;
        }));
    }

    private final class Plan {
        final long tenant,customerId;
        final Map<String,Object> customer;
        final LinkedHashMap<String,List<Map<String,Object>>> deleted=new LinkedHashMap<>(),shared=new LinkedHashMap<>();
        final Map<String,List<Map<String,Object>>> cache=new HashMap<>();
        Plan(long id,long tenant) {
            this.customerId=id;this.tenant=tenant;
            var found=jdbc.queryForList("SELECT * FROM oa_merchant WHERE id=? AND tenant_id=?",id,tenant);
            if(found.isEmpty())throw new ApiException(404,"客户不存在或不属于当前企业");
            customer=found.get(0);
        }
        List<Map<String,Object>> all(String table) {
            return cache.computeIfAbsent(table,t -> jdbc.queryForList("SELECT * FROM "+t+" ORDER BY "+("oa_workflow_action_submission".equals(t)?"user_id,submission_key":"id"))
                    .stream().filter(r -> !r.containsKey("tenant_id") || str(r.get("tenant_id")).equals(String.valueOf(tenant))).toList());
        }
        List<Map<String,Object>> select(String table,Predicate<Map<String,Object>> filter) { return all(table).stream().filter(filter).toList(); }
        void delete(String table,Predicate<Map<String,Object>> filter) { deleted.put(table,select(table,filter)); }
        long total() { return deleted.values().stream().mapToLong(List::size).sum(); }
    }

    private Plan plan(long customerId,long tenant) {
        Plan p=new Plan(customerId,tenant);
        var contracts=p.select("oa_crm_contract",r -> matches(r,"customer_id",customerId)); var contractIds=ids(contracts);
        var jobs=p.select("oa_recruit_job",r -> matches(r,"customer_id",customerId)); var jobIds=ids(jobs);
        var applications=p.select("oa_recruit_application",r -> has(jobIds,r,"job_id")); var appIds=ids(applications);
        var receivables=p.select("oa_finance_receivable",r -> matches(r,"customer_id",customerId)); var receivableIds=ids(receivables);
        var finance=p.select("oa_finance_record",r -> matches(r,"customer_id",customerId)); var financeIds=ids(finance);
        var receipts=p.select("oa_crm_contract_receipt",r -> has(contractIds,r,"contract_id")); var receiptIds=ids(receipts);
        var followups=p.select("oa_customer_followup",r -> matches(r,"merchant_id",customerId)); var followupIds=ids(followups);
        var attributions=p.select("oa_commission_attribution",r -> matches(r,"customer_id",customerId)); var attributionIds=ids(attributions);
        var lines=p.select("oa_commission_line",r -> has(attributionIds,r,"attribution_id"));
        var batches=p.select("oa_commission_batch",r -> has(idsOf(lines,"batch_id"),r,"id") || arrayContains(r.get("scope_json"),attributionIds));
        var batchIds=ids(batches);
        var payrollItems=p.select("oa_hr_payroll_item",r -> has(idsOf(lines,"payroll_item_id"),r,"id"));
        var payrolls=p.select("oa_hr_payroll",r -> has(idsOf(payrollItems,"payroll_id"),r,"id")||has(idsOf(batches,"payroll_id"),r,"id")); var payrollIds=ids(payrolls);
        var payrollItemIds=ids(payrollItems);
        Set<String> eraseBatches=new LinkedHashSet<>(),erasePayrolls=new LinkedHashSet<>(),eraseItems=new LinkedHashSet<>();
        for(var batch:batches) {
            boolean exclusive=p.all("oa_commission_line").stream().noneMatch(r -> str(r.get("batch_id")).equals(str(batch.get("id")))&&!has(attributionIds,r,"attribution_id"));
            for(var scope:parse(batch.get("scope_json")))if(!attributionIds.contains(scope.asText()))exclusive=false;
            String payroll=str(batch.get("payroll_id"));
            var items=p.select("oa_hr_payroll_item",r -> payroll.equals(str(r.get("payroll_id"))));
            Set<String> itemIds=ids(items);
            boolean paid=p.all("oa_hr_payment").stream().anyMatch(r -> has(itemIds,r,"item_id")&&"paid".equals(r.get("status")));
            boolean ownItems=items.stream().allMatch(item -> p.all("oa_commission_line").stream().anyMatch(r -> str(item.get("id")).equals(str(r.get("payroll_item_id")))&&has(attributionIds,r,"attribution_id"))
                    && p.all("oa_commission_line").stream().noneMatch(r -> str(item.get("id")).equals(str(r.get("payroll_item_id")))&&!has(attributionIds,r,"attribution_id")));
            if(exclusive && !paid && (payroll.isEmpty()||ownItems)) {
                eraseBatches.add(str(batch.get("id")));
                if(!payroll.isEmpty()) { erasePayrolls.add(payroll);eraseItems.addAll(itemIds); }
            }
        }
        p.shared.put("oa_commission_batch",batches.stream().filter(r -> !has(eraseBatches,r,"id")).toList());
        p.shared.put("oa_commission_line",p.select("oa_commission_line",r -> (has(batchIds,r,"batch_id")&&!has(eraseBatches,r,"batch_id"))||(has(payrollItemIds,r,"payroll_item_id")&&!has(eraseItems,r,"payroll_item_id"))));
        p.shared.put("oa_hr_payroll",payrolls.stream().filter(r -> !has(erasePayrolls,r,"id")).toList());
        p.shared.put("oa_hr_payroll_item",payrollItems.stream().filter(r -> !has(eraseItems,r,"id")).toList());
        p.shared.put("oa_hr_payment",p.select("oa_hr_payment",r -> has(payrollItemIds,r,"item_id")&&!has(eraseItems,r,"item_id")));
        p.shared.put("oa_commission_history",p.select("oa_commission_history",r -> "batch".equals(r.get("entity_type")) && has(batchIds,r,"entity_id")&&!has(eraseBatches,r,"entity_id")));
        p.shared.put("oa_hr_history",p.select("oa_hr_history",r -> "payroll".equals(r.get("entity_type")) && has(payrollIds,r,"entity_id")&&!has(erasePayrolls,r,"entity_id")));
        var recruitApprovals=p.select("oa_recruit_approval",r -> "job".equals(r.get("entity_type")) && (has(jobIds,r,"entity_id")||has(jobIds,r,"related_id")));
        Set<String> requestIds=new LinkedHashSet<>();
        for(var rows:List.of(finance,recruitApprovals))requestIds.addAll(idsOf(rows,"workflow_request_id"));
        Set<String> sharedRequests=new LinkedHashSet<>();
        for(var rows:List.of(batches,payrolls))for(var row:rows) {
            boolean erased=(rows==batches?eraseBatches:erasePayrolls).contains(str(row.get("id")));
            if(!erased&&row.get("workflow_request_id")!=null)sharedRequests.add(str(row.get("workflow_request_id")));
            if((erased||"approving".equals(row.get("status")))&&row.get("workflow_request_id")!=null)requestIds.add(str(row.get("workflow_request_id")));
        }
        Map<String,Set<String>> references=new HashMap<>();
        references.put("customerId",Set.of(String.valueOf(customerId))); references.put("merchantId",Set.of(String.valueOf(customerId)));
        references.put("contractId",contractIds);references.put("jobId",jobIds);references.put("applicationId",appIds);
        references.put("receivableId",receivableIds);references.put("financeRecordId",financeIds);
        references.put("attributionId",attributionIds);references.put("followupId",followupIds);
        // module_id disambiguates recordId/entityId: HR and finance IDs often overlap.
        var biz=p.select("biz_record",r -> references(parse(r.get("payload_json")),references));Set<String> bizIds=new LinkedHashSet<>();
        for(var row:biz)if("/project/business/list".equals(row.get("module"))) {
            JsonNode data=parse(row.get("payload_json"));bizIds.add(data.hasNonNull("id")?data.get("id").asText():str(row.get("id")));
        }
        references.put("opportunityId",bizIds);
        var requests=p.select("oa_workflow_request",r -> {
            JsonNode payload=parse(r.get("payload_json"));
            if(has(sharedRequests,r,"id")&&!"running".equals(r.get("status")))return false;
            return has(requestIds,r,"id") || references(payload,references)
                    || (matches(r,"module_id",341400) && references(payload,Map.of("recordId",financeIds)))
                    || (matches(r,"module_id",341301) && references(payload,Map.of("entityId",jobIds)));
        });
        requestIds.addAll(ids(requests));
        var forms=p.select("oa_form_submission",r -> references(parse(r.get("data_json")),references));var formIds=ids(forms);
        var collections=p.select("oa_finance_collection",r -> matches(r,"customer_id",customerId)); var collectionIds=ids(collections);
        var instances=p.select("oa_process_instance",r -> has(idsOf(requests,"process_instance_id"),r,"id")
                || ("workflowAction".equals(r.get("business_type"))&&has(requestIds,r,"business_id"))
                || ("lowcode".equals(r.get("business_type"))&&has(formIds,r,"business_id"))
                || businessMatches(r,customerId,contractIds,bizIds));
        var instanceIds=ids(instances);
        p.delete("oa_todo",r -> has(instanceIds,r,"process_instance_id")
                || ("workflowAction".equals(r.get("business_type"))&&has(requestIds,r,"business_id"))
                || ("lowcode".equals(r.get("business_type"))&&has(formIds,r,"business_id"))
                || businessMatches(r,customerId,contractIds,bizIds)
                || ("hrPayslip".equals(r.get("business_type"))&&has(eraseItems,r,"business_id"))
                || ("financeCollection".equals(r.get("business_type"))&&has(collectionIds,r,"business_id")));
        p.delete("oa_process_task",r -> has(instanceIds,r,"instance_id"));
        p.deleted.put("oa_process_instance",instances);
        p.delete("oa_workflow_action_submission",r -> {
            JsonNode response=parse(r.get("response_json"));
            return references(response,Map.of("requestId",requestIds,"instanceId",instanceIds))
                    || (response.path("moduleId").asLong()==341400 && (financeIds.contains(response.path("result").path("id").asText())||financeIds.contains(response.path("result").path("recordId").asText())))
                    || (response.path("moduleId").asLong()==341301 && jobIds.contains(response.path("result").path("id").asText()));
        });
        p.deleted.put("oa_recruit_approval",recruitApprovals);
        p.deleted.put("oa_workflow_request",requests);
        p.deleted.put("oa_form_submission",forms);
        p.deleted.put("biz_record",biz);
        p.delete("oa_hr_handover",r -> (Set.of("customer","merchant").contains(str(r.get("business_type"))) && matches(r,"business_id",customerId))
                || ("job".equals(r.get("business_type"))&&has(jobIds,r,"business_id"))
                || ("application".equals(r.get("business_type"))&&has(appIds,r,"business_id")));
        p.delete("oa_commission_history",r -> ("attribution".equals(r.get("entity_type"))&&has(attributionIds,r,"entity_id"))
                || ("allocation".equals(r.get("entity_type"))&&has(receiptIds,r,"entity_id"))
                || ("batch".equals(r.get("entity_type"))&&has(eraseBatches,r,"entity_id")));
        p.deleted.put("oa_commission_line",lines);
        p.delete("oa_hr_history",r -> "payroll".equals(r.get("entity_type"))&&has(erasePayrolls,r,"entity_id"));
        p.delete("oa_hr_payment",r -> has(eraseItems,r,"item_id"));
        p.delete("oa_hr_payroll_item",r -> has(eraseItems,r,"id"));
        p.delete("oa_hr_payroll",r -> has(erasePayrolls,r,"id"));
        p.delete("oa_commission_batch",r -> has(eraseBatches,r,"id"));
        p.delete("oa_commission_allocation",r -> has(attributionIds,r,"attribution_id")||has(receiptIds,r,"receipt_id"));
        p.deleted.put("oa_commission_attribution",attributions);
        p.delete("oa_recruit_guarantee_handling",r -> has(appIds,r,"application_id"));
        p.delete("oa_recruit_replacement",r -> has(appIds,r,"original_application_id")||has(appIds,r,"replacement_application_id"));
        for(String table:List.of("oa_recruit_interview","oa_recruit_exception","oa_recruit_attachment"))p.delete(table,r -> has(appIds,r,"application_id"));
        p.delete("oa_recruit_event",r -> ("job".equals(r.get("entity_type"))&&has(jobIds,r,"entity_id"))
                || ("application".equals(r.get("entity_type"))&&has(appIds,r,"entity_id")));
        p.delete("oa_recruit_version",r -> has(jobIds,r,"job_id"));
        p.deleted.put("oa_recruit_application",applications);
        p.delete("oa_finance_history",r -> ("record".equals(r.get("entity_type"))&&has(financeIds,r,"entity_id"))
                || ("receivable".equals(r.get("entity_type"))&&has(receivableIds,r,"entity_id"))
                || ("collection".equals(r.get("entity_type"))&&has(collectionIds,r,"entity_id")));
        p.deleted.put("oa_finance_collection",collections);
        p.deleted.put("oa_finance_record",finance);
        p.deleted.put("oa_finance_receivable",receivables);
        p.deleted.put("oa_crm_contract_receipt",receipts);
        p.deleted.put("oa_recruit_job",jobs);
        p.delete("oa_crm_contract_reminder_log",r -> has(contractIds,r,"contract_id"));
        p.delete("oa_crm_contract_history",r -> matches(r,"customer_id",customerId) || has(contractIds,r,"contract_id"));
        p.delete("oa_crm_contract_version",r -> has(contractIds,r,"contract_id"));
        p.deleted.put("oa_crm_contract",contracts);
        p.deleted.put("oa_customer_followup",followups);
        for(String table:List.of("oa_merchant_contact","oa_merchant_account","oa_merchant_collaborator"))p.delete(table,r -> matches(r,"merchant_id",customerId));
        // Legacy opportunity rows have no customer_id. Only explicit contract linkage proves ownership.
        p.delete("oa_business_opportunity",r -> has(idsOf(contracts,"opportunity_id"),r,"id")
                && p.all("oa_crm_contract").stream().noneMatch(c -> !matches(c,"customer_id",customerId)&&str(c.get("opportunity_id")).equals(str(r.get("id")))));
        return p;
    }

    private boolean businessMatches(Map<String,Object> row,long customer,Set<String> contracts,Set<String> opportunities) {
        String type=str(row.get("business_type"));
        return (Set.of("crmCustomerFollowup","crmRisk","merchant","customer").contains(type)&&matches(row,"business_id",customer))
                || (Set.of("crmContractApproval","crmContract","contract").contains(type)&&has(contracts,row,"business_id"))
                || (Set.of("crmOpportunityFollowup","business").contains(type)&&has(opportunities,row,"business_id"));
    }

    private void adjustShared(Plan p,String operationId,long operator) {
        Set<String> attributionIds=ids(p.deleted.get("oa_commission_attribution"));
        Set<String> removedLines=ids(p.deleted.get("oa_commission_line"));
        for(var batch:p.shared.get("oa_commission_batch")) {
            List<String> scope=new ArrayList<>();parse(batch.get("scope_json")).forEach(n -> { if(!attributionIds.contains(n.asText()))scope.add(n.asText()); });
            long remaining=p.all("oa_commission_line").stream().filter(r -> str(r.get("batch_id")).equals(str(batch.get("id")))&&!removedLines.contains(str(r.get("id")))).count();
            String state=str(batch.get("status"));
            if("approving".equals(state))state="draft";
            if(remaining==0 && str(batch.get("payroll_id")).isEmpty())state="void";
            jdbc.update("UPDATE oa_commission_batch SET scope_json=?,status=?,workflow_request_id=NULL,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",encode(scope),state,batch.get("id"));
            jdbc.update("INSERT INTO oa_commission_history(tenant_id,entity_type,entity_id,action,detail_json,operator_id) VALUES(?,'batch',?,'客户业务清理',?,?)",
                    p.tenant,batch.get("id"),encode(Map.of("cleanupOperationId",operationId,"remainingLines",remaining)),operator);
        }
        Set<String> changedPayrolls=new LinkedHashSet<>();
        for(var item:p.shared.get("oa_hr_payroll_item")) {
            boolean paid=p.shared.get("oa_hr_payment").stream().anyMatch(r -> str(r.get("item_id")).equals(str(item.get("id")))&&"paid".equals(r.get("status")));
            if(paid) continue; // A combined/partially paid item is a payment fact: keep amounts and snapshots intact.
            BigDecimal amount=p.all("oa_commission_line").stream().filter(r -> str(r.get("payroll_item_id")).equals(str(item.get("id")))&&!removedLines.contains(str(r.get("id"))))
                    .map(r -> money(r.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal gross=amount;
            for(String field:List.of("base_amount","allowance","overtime","bonus"))gross=gross.add(money(item.get(field)));
            BigDecimal net=gross;
            for(String field:List.of("deduction","tax","contribution"))net=net.subtract(money(item.get(field)));
            jdbc.update("UPDATE oa_hr_payroll_item SET commission=?,adjustment=?,gross_amount=?,net_amount=?,verified=0 WHERE id=?",
                    amount.max(BigDecimal.ZERO),amount.min(BigDecimal.ZERO),gross,net,item.get("id"));
            changedPayrolls.add(str(item.get("payroll_id")));
        }
        for(var payroll:p.shared.get("oa_hr_payroll")) {
            boolean changed=changedPayrolls.contains(str(payroll.get("id")));
            jdbc.update("UPDATE oa_hr_payroll SET revision=revision+1,workflow_request_id=NULL,status=CASE WHEN ? OR status='approving' THEN 'draft' ELSE status END,published=CASE WHEN ? THEN 0 ELSE published END WHERE id=?",changed,changed,payroll.get("id"));
            jdbc.update("INSERT INTO oa_hr_history(entity_type,entity_id,action,detail_json,operator_id) VALUES('payroll',?,'客户业务清理',?,?)",
                    payroll.get("id"),encode(Map.of("cleanupOperationId",operationId,"unpaidRecalculated",changed,"paidHistoryPreserved",true)),operator);
        }
    }

    private Map<String,Object> payrollSummary(Plan p) {
        var rows=new ArrayList<Map<String,Object>>();
        for(var item:p.shared.getOrDefault("oa_hr_payroll_item",List.of())) {
            boolean paid=p.shared.get("oa_hr_payment").stream().anyMatch(r -> str(r.get("item_id")).equals(str(item.get("id")))&&"paid".equals(r.get("status")));
            rows.add(Map.of("itemId",item.get("id"),"payrollId",item.get("payroll_id"),"userId",item.get("user_id"),"action",paid?"保留已发放/部分发放工资金额与付款事实，解除客户提成来源":"移除本客户提成，重算未发放工资，取消核对和发布标识"));
        }
        return Map.of("items",rows,"sharedBatches",p.shared.getOrDefault("oa_commission_batch",List.of()).size());
    }
    private Map<String,Object> summary(Plan p) {
        var tables=new ArrayList<Map<String,Object>>();
        p.deleted.forEach((table,rows) -> { if(!rows.isEmpty()) tables.add(Map.of("table",table,"label",DataTableRegistry.describe(table,table).label(),"count",rows.size())); });
        var customer=new LinkedHashMap<String,Object>();
        for(String key:List.of("id","name","serial","owner_user_name","status"))customer.put(key,p.customer.get(key));
        return Map.of("customer",customer,"signature",fingerprint(p),"tables",tables,"total",p.total(),"payroll",payrollSummary(p),
                "amounts",Map.of("financeRecords",p.deleted.get("oa_finance_record").stream().map(r -> money(r.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add),
                        "netReceipts",p.deleted.get("oa_crm_contract_receipt").stream().map(r -> money(r.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add)));
    }
    private String fingerprint(Plan p) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(List.of(p.customer,p.deleted,p.shared)).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private void writeBackup(String name,Object snapshot) {
        try {
            Files.createDirectories(backups);
            try(OutputStream file=Files.newOutputStream(backups.resolve(name),StandardOpenOption.CREATE_NEW);var zip=new ZipOutputStream(file)) {
                zip.putNextEntry(new ZipEntry("customer-data.json"));zip.write(encode(snapshot).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
        } catch(Exception e) { throw new ApiException(500,"备份失败，未执行清理，请检查备份目录空间和权限"); }
    }
    private String encode(Object value) { try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("清理快照序列化失败",e);} }
    private JsonNode parse(Object value) {
        if(value==null||str(value).isBlank())return json.createObjectNode();
        try{return json.readTree(str(value));}catch(Exception e){return json.createObjectNode();}
    }
    private boolean references(JsonNode node,Map<String,Set<String>> ids) {
        if(node==null)return false;
        if(node.isObject()) {
            var fields=node.fields();while(fields.hasNext()) {
                var field=fields.next();
                if(ids.containsKey(field.getKey())&&field.getValue().isValueNode()&&ids.get(field.getKey()).contains(field.getValue().asText()))return true;
                if(field.getValue().isContainerNode()&&references(field.getValue(),ids))return true;
            }
        } else if(node.isArray())for(var child:node)if(references(child,ids))return true;
        return false;
    }
    private boolean arrayContains(Object value,Set<String> keys) { for(var node:parse(value))if(keys.contains(node.asText()))return true;return false; }
    private static String str(Object value) { return value==null?"":String.valueOf(value); }
    private static boolean matches(Map<String,Object> row,String column,long id) { return str(row.get(column)).equals(String.valueOf(id)); }
    private static boolean has(Set<String> ids,Map<String,Object> row,String field) { return ids.contains(str(row.get(field))); }
    private static Set<String> ids(List<Map<String,Object>> rows) { return idsOf(rows,"id"); }
    private static Set<String> idsOf(List<Map<String,Object>> rows,String column) {
        Set<String> result=new LinkedHashSet<>();for(var row:rows)if(row.get(column)!=null)result.add(str(row.get(column)));return result;
    }
    private static BigDecimal money(Object value) { return value==null?BigDecimal.ZERO:new BigDecimal(str(value)); }
}
