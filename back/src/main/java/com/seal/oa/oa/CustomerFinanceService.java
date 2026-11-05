package com.seal.oa.oa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.system.SystemService;
import com.seal.oa.workflow.WorkflowActionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Signed contract terms are the source of contract receivables. Only confirmed records affect balances. */
@Service
public class CustomerFinanceService {
    public static final long MODULE = 341400L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> TYPES = Set.of("invoice", "receipt", "refund", "cost", "writeoff", "reversal");
    private static final Set<String> RESERVED = Set.of("approving", "approved", "confirmed");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CustomerAccessService access;
    private final SystemService system;
    private final CrmContractService contracts;

    public CustomerFinanceService(JdbcTemplate jdbc, ObjectMapper mapper, CustomerAccessService access,
                                  SystemService system, CrmContractService contracts) {
        this.jdbc = jdbc; this.mapper = mapper; this.access = access; this.system = system; this.contracts = contracts;
    }

    public Map<String, Object> options() {
        List<Map<String, Object>> customers = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT id,name,owner_user_id FROM oa_merchant WHERE status='0' AND public_pool='0' AND owner_user_id IS NOT NULL"))
                .stream().filter(this::visible).toList();
        Set<Long> ids = new HashSet<>(); customers.forEach(c -> ids.add(id(c.get("id"))));
        List<Map<String, Object>> jobs = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT id,title,customer_id,contract_id FROM oa_recruit_job"))
                .stream().filter(j -> ids.contains(id(j.get("customerId")))).toList();
        List<Map<String, Object>> linkedContracts = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT id,name,code,customer_id FROM oa_crm_contract WHERE effective_version>0"))
                .stream().filter(c -> ids.contains(id(c.get("customerId")))).toList();
        List<Map<String, Object>> owners = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT user_id AS id,nick_name AS name FROM sys_user WHERE status='0' AND del_flag='0' ORDER BY nick_name"));
        return Map.of("customers", customers, "jobs", jobs, "contracts", linkedContracts, "owners", owners, "sources", ledgerRows(Map.of()).stream()
                .filter(r -> "confirmed".equals(r.get("status"))).toList());
    }

    public Map<String, Object> ledger(Map<String, String> query) { return page(ledgerRows(query), query); }

    private List<Map<String, Object>> ledgerRows(Map<String, String> query) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> records = rawRecords();
        List<Map<String, Object>> terms = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT c.id,c.customer_id,c.code,c.name,c.status,c.effective_version,v.snapshot_json,
                       m.name AS customer_name,m.owner_user_id,m.owner_user_name
                FROM oa_crm_contract c JOIN oa_merchant m ON m.id=c.customer_id
                JOIN oa_crm_contract_version v ON v.contract_id=c.id AND v.version_no=c.effective_version
                WHERE c.effective_version>0 ORDER BY c.id DESC
                """));
        for (Map<String, Object> c : terms) {
            if (!visible(c)) continue;
            for (Map<String, Object> term : readTerms(c.get("snapshotJson"))) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sourceKey", "contract:" + c.get("id") + ":" + term.get("key"));
                row.put("contractId", c.get("id")); row.put("termKey", term.get("key"));
                row.put("contractCode", c.get("code")); row.put("contractName", c.get("name"));
                row.put("contractStatus", c.get("status")); row.put("effectiveVersion", c.get("effectiveVersion"));
                row.put("customerId", c.get("customerId")); row.put("customerName", c.get("customerName"));
                row.put("ownerUserName", c.get("ownerUserName")); row.put("ownerUserId", c.get("ownerUserId"));
                row.put("title", term.get("title")); row.put("amount", term.get("amount")); row.put("dueDate", term.get("dueDate"));
                row.put("sourceEvent", "签署生效合同 V" + c.get("effectiveVersion")); row.put("status", "confirmed");
                row.put("manual", false);
                BigDecimal cash = sum("SELECT COALESCE(SUM(amount),0) FROM oa_crm_contract_receipt WHERE contract_id=? AND term_key=?",
                        c.get("id"), term.get("key"));
                decorateBalance(row, records, cash); result.add(row);
            }
        }
        for (Map<String, Object> r : JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT r.*,m.name AS customer_name,m.owner_user_id,m.owner_user_name
                FROM oa_finance_receivable r JOIN oa_merchant m ON m.id=r.customer_id ORDER BY r.id DESC
                """))) {
            if (!visible(r)) continue;
            r.put("manual", true); r.put("receivableId", r.get("id")); r.put("sourceKey", "manual:" + r.get("id"));
            BigDecimal cash = BigDecimal.ZERO;
            for (Map<String, Object> doc : records) if (sameSource(r, doc) && "confirmed".equals(doc.get("status"))) {
                String type = text(doc.get("recordType"));
                if ("receipt".equals(type)) cash = cash.add(money(doc.get("amount")));
                if (Set.of("refund", "reversal").contains(type)) cash = cash.subtract(money(doc.get("amount")));
            }
            decorateBalance(r, records, cash); result.add(r);
        }
        result.removeIf(r -> !matches(r, query, "dueDate") || (!blank(query.get("paymentStatus"))
                && !query.get("paymentStatus").equals(r.get("paymentStatus")))
                || (!blank(query.get("overdue")) && !"overdue".equals(r.get("paymentStatus"))));
        return result;
    }

    private void decorateBalance(Map<String, Object> r, List<Map<String, Object>> records, BigDecimal cash) {
        BigDecimal billed = BigDecimal.ZERO, refund = BigDecimal.ZERO, writeoff = BigDecimal.ZERO;
        BigDecimal invoiceReserved = BigDecimal.ZERO, receiptReserved = BigDecimal.ZERO;
        for (Map<String, Object> d : records) if (sameSource(r, d)) {
            String type = text(d.get("recordType")); BigDecimal amount = money(d.get("amount"));
            if ("invoice".equals(type) && RESERVED.contains(text(d.get("status")))) invoiceReserved = invoiceReserved.add(amount);
            if (Set.of("receipt", "writeoff").contains(type) && "approving".equals(d.get("status"))) receiptReserved = receiptReserved.add(amount);
            if (!"confirmed".equals(d.get("status"))) continue;
            if ("invoice".equals(type)) billed = billed.add(amount);
            if ("refund".equals(type)) refund = refund.add(amount);
            if ("writeoff".equals(type)) writeoff = writeoff.add(amount);
        }
        BigDecimal due = money(r.get("amount")); BigDecimal outstanding = due.subtract(cash).subtract(writeoff);
        long days = Math.max(0, ChronoUnit.DAYS.between(day(r.get("dueDate")), today()));
        r.put("receivableAmount", due); r.put("netReceivedAmount", cash); r.put("refundedAmount", refund);
        r.put("invoicedAmount", billed); r.put("writeoffAmount", writeoff); r.put("outstandingAmount", outstanding);
        r.put("invoiceAvailable", due.subtract(invoiceReserved)); r.put("receiptAvailable", outstanding.subtract(receiptReserved));
        r.put("overdueDays", outstanding.signum() > 0 ? days : 0);
        r.put("paymentStatus", !"confirmed".equals(r.get("status")) ? "draft" : outstanding.signum() <= 0 ? "paid" : days > 0 ? "overdue" : "unpaid");
    }

    public Map<String, Object> records(String type, Map<String, String> query) {
        if ("receivable".equals(type)) return page(manualRows(query), query);
        validateType(type, true);
        return page(filteredRecords(type, query), query);
    }

    private List<Map<String, Object>> manualRows(Map<String, String> query) {
        List<Map<String,Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT r.*,m.name AS customer_name,m.owner_user_id,m.owner_user_name
                FROM oa_finance_receivable r JOIN oa_merchant m ON m.id=r.customer_id ORDER BY r.id DESC
                """));
        return rows.stream().filter(this::visible).filter(r -> matches(r, query, "dueDate")).toList();
    }

    private List<Map<String, Object>> filteredRecords(String type, Map<String, String> query) {
        return rawRecords().stream().filter(this::visible)
                .filter(r -> "all".equals(type) || ("adjustment".equals(type)
                        ? Set.of("writeoff", "reversal").contains(text(r.get("recordType"))) : type.equals(r.get("recordType"))))
                .filter(r -> matches(r, query, "businessDate")).toList();
    }

    private List<Map<String, Object>> rawRecords() {
        return JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT r.*,m.name AS customer_name,m.owner_user_id,c.name AS contract_name,c.code AS contract_code,
                       j.title AS job_title,w.status AS workflow_status,w.process_instance_id AS instance_id,
                       w.process_task_name AS task_name
                FROM oa_finance_record r JOIN oa_merchant m ON m.id=r.customer_id
                LEFT JOIN oa_crm_contract c ON c.id=r.contract_id LEFT JOIN oa_recruit_job j ON j.id=r.job_id
                LEFT JOIN oa_workflow_request w ON w.id=r.workflow_request_id ORDER BY r.id DESC
                """));
    }

    public Map<String, Object> detail(long id) {
        Map<String, Object> row = rawRecords().stream().filter(r -> id(r.get("id")) == id).findFirst().orElseThrow(() -> new ApiException(404, "财务记录不存在")); checkCustomer(id(row.get("customerId")));
        row.put("history", JdbcMaps.camelCase(jdbc.queryForList("SELECT * FROM oa_finance_history WHERE entity_type='record' AND entity_id=? ORDER BY id DESC", id)));
        return row;
    }

    @Transactional
    public Map<String, Object> saveReceivable(long id, Map<String, Object> body) {
        long customer = positiveId(body.get("customerId")); checkCustomer(customer);
        if (id > 0) {
            Map<String, Object> current = manual(id, true); checkCustomer(id(current.get("customerId"))); revision(current, body);
            expect(current, "draft");
        }
        String title = required(body.get("title"), 200, "应收名称"); BigDecimal amount = positiveMoney(body.get("amount"));
        Date due = Date.valueOf(day(body.get("dueDate")));
        String event = limited(body.get("sourceEvent"), 200), remark = limited(body.get("remark"), 1000);
        String attachment = attachments(body.get("attachment"));
        if (id == 0) id = insert("INSERT INTO oa_finance_receivable(customer_id,title,amount,due_date,source_event,remark,attachment,create_by) VALUES(?,?,?,?,?,?,?,?)",
                customer, title, amount, due, event, remark, attachment, AuthContext.username());
        else jdbc.update("UPDATE oa_finance_receivable SET customer_id=?,title=?,amount=?,due_date=?,source_event=?,remark=?,attachment=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",
                customer, title, amount, due, event, remark, attachment, id);
        history("receivable", id, "保存草稿", body); return manual(id, false);
    }

    @Transactional
    public void confirmReceivable(long id, Map<String, Object> body, boolean confirm) {
        Map<String, Object> row = manual(id, true); checkCustomer(id(row.get("customerId"))); revision(row, body);
        expect(row, confirm ? "draft" : "confirmed");
        if (!confirm && count("SELECT COUNT(*) FROM oa_finance_record WHERE receivable_id=? AND status NOT IN ('void','rejected','draft')", id) > 0)
            throw new ApiException(409, "已有已提交财务记录，不能撤销应收确认");
        jdbc.update("UPDATE oa_finance_receivable SET status=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?", confirm ? "confirmed" : "draft", id);
        history("receivable", id, confirm ? "确认应收" : "撤销确认", body);
    }

    @Transactional
    public Map<String, Object> save(String type, long id, Map<String, Object> body) {
        validateType(type, false);
        Map<String, Object> existing = id > 0 ? record(id, false) : Map.of();
        if (id > 0) {
            checkCustomer(id(existing.get("customerId"))); revision(existing, body); expect(existing, "draft", "rejected");
            if (!type.equals(existing.get("recordType"))) throw new ApiException(400, "不能更改记录类型");
        }
        Map<String, Object> source = "cost".equals(type) ? Map.of() : source(body, true);
        if (id > 0) { existing = record(id, true); revision(existing, body); expect(existing, "draft", "rejected"); }
        long customer = "cost".equals(type) ? positiveId(body.get("customerId")) : id(source.get("customerId"));
        checkCustomer(customer);
        long contractId = "cost".equals(type) ? id(body.get("contractId")) : id(source.get("contractId"));
        if ("cost".equals(type) && contractId > 0) {
            Map<String, Object> c = one("SELECT customer_id FROM oa_crm_contract WHERE id=?", contractId);
            if (id(c.get("customerId")) != customer) throw new ApiException(400, "成本合同与客户不匹配");
        }
        long job = id(body.get("jobId"));
        if (job > 0 && count("SELECT COUNT(*) FROM oa_recruit_job WHERE id=? AND customer_id=?", job, customer) == 0)
            throw new ApiException(400, "职位不属于所选客户");
        if (job > 0 && contractId > 0 && count("SELECT COUNT(*) FROM oa_recruit_job WHERE id=? AND contract_id IS NOT NULL AND contract_id<>?", job, contractId) > 0)
            throw new ApiException(400, "职位与所选合同不匹配");
        BigDecimal amount = positiveMoney(body.get("amount"));
        String title = "invoice".equals(type) ? required(body.get("invoiceTitle"), 200, "发票抬头") : null;
        String category = "cost".equals(type) ? required(body.get("category"), 100, "成本类别") : null;
        String reason = Set.of("refund", "reversal", "writeoff").contains(type) ? required(body.get("reason"), 1000, "办理原因") : limited(body.get("reason"), 1000);
        Object[] fields = { customer, nullable(contractId), source.get("termKey"), source.get("receivableId"), nullable(job),
                limited(body.get("projectName"), 200), nullable(id(body.get("originalReceiptId"))), nullable(id(body.get("originalRecordId"))),
                amount, body.get("businessDate") == null || blank(text(body.get("businessDate"))) ? null : Date.valueOf(actualDay(body.get("businessDate"))),
                category, limited(body.get("payee"), 200), limited(body.get("channel"), 100), limited(body.get("referenceNo"), 100),
                title, limited(body.get("taxNo"), 100), reason, limited(body.get("remark"), 1000), attachments(body.get("attachment")) };
        if (id == 0) {
            String token = required(body.get("createToken"), 64, "提交标识");
            List<Map<String, Object>> duplicate = JdbcMaps.camelCase(jdbc.queryForList("SELECT * FROM oa_finance_record WHERE create_token=?", token));
            if (!duplicate.isEmpty()) {
                Map<String, Object> d = duplicate.get(0);
                if (id(d.get("createUserId")) != AuthContext.userId() || !type.equals(d.get("recordType"))) throw new ApiException(409, "提交标识已使用");
                return detail(id(d.get("id")));
            }
            List<Object> args = new ArrayList<>(Arrays.asList(fields)); args.add(type); args.add(token); args.add(AuthContext.username()); args.add(AuthContext.userId());
            id = insert("""
                    INSERT INTO oa_finance_record(customer_id,contract_id,term_key,receivable_id,job_id,project_name,
                    original_receipt_id,original_record_id,amount,business_date,category,payee,channel,reference_no,
                    invoice_title,tax_no,reason,remark,attachment,record_type,create_token,create_by,create_user_id)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, args.toArray());
        } else {
            List<Object> args = new ArrayList<>(Arrays.asList(fields)); args.add(id);
            jdbc.update("""
                    UPDATE oa_finance_record SET customer_id=?,contract_id=?,term_key=?,receivable_id=?,job_id=?,project_name=?,
                    original_receipt_id=?,original_record_id=?,amount=?,business_date=?,category=?,payee=?,channel=?,reference_no=?,
                    invoice_title=?,tax_no=?,reason=?,remark=?,attachment=?,status='draft',workflow_request_id=NULL,
                    revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?
                    """, args.toArray());
        }
        history("record", id, "保存草稿", body); return detail(id);
    }

    /** Called exclusively through the generic button action service; freezes record and source, never trusts payload amounts. */
    @Transactional
    public void validateWorkflow(WorkflowActionContext ctx) {
        Map<String, Object> row = record(positiveId(ctx.payload().get("recordId")), false);
        String action = actionFor(text(row.get("recordType")));
        if (ctx.moduleId() != MODULE || !action.equals(ctx.actionCode()) || !("crm.finance." + action).equals(ctx.handlerKey()))
            throw new ApiException(400, "财务业务与流程按钮不匹配");
        if (ctx.requestId() == 0) {
            checkCustomer(id(row.get("customerId"))); lockSource(row); row = record(id(row.get("id")), true);
            revision(row, ctx.payload()); expect(row, "draft", "rejected"); validateAmount(row);
            if (Set.of("receipt", "cost", "reversal", "writeoff").contains(text(row.get("recordType"))))
                actualDay(row.get("businessDate"));
            if (Set.of("receipt", "cost", "reversal").contains(text(row.get("recordType")))) {
                if ("[]".equals(attachments(row.get("attachment"))))
                    throw new ApiException(400, "请先补充实际办理凭证，再提交确认");
            }
        } else {
            lockSource(row); row = record(id(row.get("id")), true); expect(row, "approving");
            if (id(row.get("workflowRequestId")) != ctx.requestId()) throw new ApiException(409, "审批与当前财务申请不一致");
        }
    }

    @Transactional
    public void submitted(WorkflowActionContext ctx) {
        validateWorkflow(new WorkflowActionContext(0, ctx.moduleId(), ctx.actionCode(), ctx.handlerKey(), ctx.config(), ctx.payload(), ctx.operatorId(), ctx.operatorName()));
        long id = positiveId(ctx.payload().get("recordId"));
        jdbc.update("UPDATE oa_finance_record SET status='approving',workflow_request_id=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?", ctx.requestId(), id);
        Map<String, Object> row = detail(id);
        if (!"cost".equals(row.get("recordType"))) row.put("receivableTitle", source(row, false).get("title"));
        ctx.payload().clear(); ctx.payload().putAll(row); ctx.payload().put("recordId", id); ctx.payload().put("financeRecord", true);
        ctx.payload().put("title", "财务申请：" + row.get("customerName") + " / " + row.get("recordType") + " / " + row.get("amount"));
        history("record", id, "提交审批", Map.of("requestId", ctx.requestId()));
    }

    @Transactional
    public Object execute(WorkflowActionContext ctx) {
        validateWorkflow(ctx);
        long id = positiveId(ctx.payload().get("recordId")); Map<String, Object> row = record(id, true); validateAmount(row);
        String type = text(row.get("recordType"));
        if (Set.of("invoice", "refund").contains(type)) {
            jdbc.update("UPDATE oa_finance_record SET status='approved',revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?", id);
            history("record", id, ctx.requestId() > 0 ? "审批通过，等待实际办理" : "直接确认，等待实际办理", Map.of());
        } else settle(row, row, "财务确认");
        return Map.of("recordId", id, "status", record(id, false).get("status"));
    }

    @Transactional
    public void workflowRejected(WorkflowActionContext ctx, String status) {
        if (!"审批未通过".equals(status)) return;
        validateWorkflow(ctx); long id = positiveId(ctx.payload().get("recordId"));
        jdbc.update("UPDATE oa_finance_record SET status='rejected',revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?", id);
        history("record", id, "审批驳回", Map.of("requestId", ctx.requestId()));
    }

    public void validateDecision(WorkflowActionContext ctx, boolean pass, String comment, Long operator) {
        validateWorkflow(ctx);
        if (operator == null || !system.hasModuleAction(operator, MODULE, "approve")) throw new ApiException(403, "审批人需要财务审批权限");
        Map<String, Object> row = record(positiveId(ctx.payload().get("recordId")), false);
        if (operator == id(row.get("createUserId"))) throw new ApiException(403, "不能审批本人提交的财务申请");
        Map<String, Object> customer = one("SELECT owner_user_id FROM oa_merchant WHERE id=?", row.get("customerId"));
        if (!access.canAccessOwner(operator, customer.get("ownerUserId"))) throw new ApiException(403, "无权审批该客户的财务申请");
        if (!pass && blank(comment)) throw new ApiException(400, "驳回时必须填写意见");
    }

    @Transactional
    public Map<String, Object> operate(long id, String action, Map<String, Object> body) {
        Map<String, Object> row = record(id, false); checkCustomer(id(row.get("customerId"))); lockSource(row);
        row = record(id, true); revision(row, body); String type = text(row.get("recordType"));
        switch (action) {
            case "issue", "pay" -> {
                if (!("issue".equals(action) && "invoice".equals(type)) && !("pay".equals(action) && "refund".equals(type)))
                    throw new ApiException(400, "办理动作不匹配");
                expect(row, "approved"); validateAmount(row); settle(row, body, "issue".equals(action) ? "登记实际开票" : "登记实际退款");
            }
            case "void" -> {
                expect(row, "draft", "rejected", "approved", "confirmed"); required(body.get("reason"), 1000, "作废原因");
                if ("confirmed".equals(row.get("status")))
                    throw new ApiException(409, "已确认记录不能作废；资金调整请关联原到账登记冲销或退款");
                jdbc.update("UPDATE oa_finance_record SET status='void',revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?", id);
                history("record", id, "作废", body); sync(row);
            }
            default -> throw new ApiException(400, "财务操作无效");
        }
        return detail(id);
    }

    private void settle(Map<String, Object> row, Map<String, Object> body, String action) {
        long id = id(row.get("id")); String type = text(row.get("recordType"));
        LocalDate day = actualDay(body.get("businessDate")); String attachment = attachments(body.get("attachment"));
        if (Set.of("refund", "reversal").contains(type) && day.isBefore(day(original(row).get("businessDate"))))
            throw new ApiException(400, "退款或冲销日期不能早于原到账");
        if ("[]".equals(attachment) && Set.of("invoice", "receipt", "refund", "cost", "reversal").contains(type))
            throw new ApiException(400, "请上传实际办理凭证");
        String invoiceNo = "invoice".equals(type) ? required(body.get("invoiceNo"), 100, "发票号码") : null;
        if (invoiceNo != null && count("SELECT COUNT(*) FROM oa_finance_record WHERE invoice_no=? AND id<>?", invoiceNo, id) > 0)
            throw new ApiException(409, "发票号码已登记");
        Long receiptId = null;
        if (id(row.get("contractId")) > 0 && Set.of("receipt", "refund", "reversal").contains(type)) {
            BigDecimal amount = money(row.get("amount")); if (!"receipt".equals(type)) amount = amount.negate();
            receiptId = insert("INSERT INTO oa_crm_contract_receipt(contract_id,term_key,amount,receipt_date,remark,create_by) VALUES(?,?,?,?,?,?)",
                    row.get("contractId"), row.get("termKey"), amount, Date.valueOf(day), action + " #" + id + " " + text(row.get("reason")), AuthContext.username());
        }
        jdbc.update("""
                UPDATE oa_finance_record SET status='confirmed',business_date=?,attachment=?,invoice_no=?,
                reference_no=?,receipt_id=?,confirm_by=?,confirm_time=CURRENT_TIMESTAMP,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?
                """, Date.valueOf(day), attachment, invoiceNo, limited(body.get("referenceNo"), 100), receiptId, AuthContext.username(), id);
        history("record", id, action, body); sync(row);
    }

    private void validateAmount(Map<String, Object> row) {
        String type = text(row.get("recordType"));
        if ("cost".equals(type)) return;
        Map<String, Object> source = source(row, false);
        if (!"confirmed".equals(source.get("status"))) throw new ApiException(409, "请先确认应收");
        BigDecimal amount = money(row.get("amount"));
        List<Map<String, Object>> others = rawRecords().stream().filter(d -> id(d.get("id")) != id(row.get("id")) && sameSource(row, d)).toList();
        BigDecimal used = BigDecimal.ZERO;
        if ("invoice".equals(type)) {
            for (Map<String, Object> d : others) if ("invoice".equals(d.get("recordType")) && RESERVED.contains(text(d.get("status")))) used = used.add(money(d.get("amount")));
            if (amount.add(used).compareTo(money(source.get("amount"))) > 0) throw new ApiException(409, "累计开票申请不能超过应收金额");
        } else if (Set.of("receipt", "writeoff").contains(type)) {
            for (Map<String, Object> d : others) if (Set.of("receipt", "writeoff").contains(text(d.get("recordType"))) && "approving".equals(d.get("status"))) used = used.add(money(d.get("amount")));
            if (amount.add(used).compareTo(money(source.get("outstandingAmount"))) > 0) throw new ApiException(409, "金额超过未收余额或已被待审申请占用");
            if ("receipt".equals(type) && "completed".equals(source.get("contractStatus"))) throw new ApiException(409, "已结案合同不能继续登记到账");
        } else {
            Map<String, Object> original = original(row);
            for (Map<String, Object> d : others) if (Set.of("refund", "reversal").contains(text(d.get("recordType"))) && RESERVED.contains(text(d.get("status")))
                    && sameOriginal(row, d)) used = used.add(money(d.get("amount")));
            if (amount.add(used).compareTo(money(original.get("amount"))) > 0 || amount.compareTo(money(source.get("netReceivedAmount"))) > 0)
                throw new ApiException(409, "退款或冲销超过原到账可退金额");
            if (row.get("businessDate") != null && day(row.get("businessDate")).isBefore(day(original.get("businessDate")))) throw new ApiException(400, "退款或冲销日期不能早于原到账");
        }
    }

    public List<Map<String, Object>> receipts(Map<String, String> query) {
        Set<Long> customers = new HashSet<>();
        JdbcMaps.camelCase(jdbc.queryForList("SELECT id,owner_user_id FROM oa_merchant")).stream().filter(this::visible).forEach(c -> customers.add(id(c.get("id"))));
        List<Map<String, Object>> rows = new ArrayList<>(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT r.id AS original_receipt_id,r.contract_id,r.term_key,r.amount,r.receipt_date AS business_date,
                       c.customer_id,c.name AS contract_name,m.name AS customer_name FROM oa_crm_contract_receipt r
                JOIN oa_crm_contract c ON c.id=r.contract_id JOIN oa_merchant m ON m.id=c.customer_id WHERE r.amount>0 ORDER BY r.id DESC
                """)));
        for (Map<String, Object> doc : rawRecords()) if ("receipt".equals(doc.get("recordType")) && "confirmed".equals(doc.get("status")) && id(doc.get("contractId")) == 0) {
            Map<String, Object> r = new LinkedHashMap<>(doc); r.put("originalRecordId", r.get("id")); rows.add(r);
        }
        return rows.stream().filter(r -> customers.contains(id(r.get("customerId"))))
                .filter(r -> matches(r, query, "businessDate")).toList();
    }

    private Map<String, Object> original(Map<String, Object> row) {
        Map<String, Object> original;
        if (id(row.get("contractId")) > 0) original = one("SELECT contract_id,term_key,amount,receipt_date AS business_date FROM oa_crm_contract_receipt WHERE id=? AND amount>0", positiveId(row.get("originalReceiptId")));
        else {
            original = record(positiveId(row.get("originalRecordId")), false);
            expect(original, "confirmed");
            if (!"receipt".equals(original.get("recordType"))) throw new ApiException(400, "必须关联原到账记录");
        }
        if (!sameSource(row, original)) throw new ApiException(400, "原到账与应收节点不一致");
        return original;
    }

    private boolean sameOriginal(Map<String, Object> a, Map<String, Object> b) {
        return id(a.get("originalReceiptId")) > 0 ? id(a.get("originalReceiptId")) == id(b.get("originalReceiptId"))
                : id(a.get("originalRecordId")) > 0 && id(a.get("originalRecordId")) == id(b.get("originalRecordId"));
    }

    private Map<String, Object> source(Map<String, Object> row, boolean lock) {
        if (lock) lockSource(row);
        long contract = id(row.get("contractId")), manual = id(row.get("receivableId"));
        if ((contract > 0) == (manual > 0)) throw new ApiException(400, "请选择一个明确的应收节点");
        return ledgerRows(Map.of()).stream().filter(r -> sameSource(r, row)).findFirst().orElseThrow(() -> new ApiException(404, "应收节点不存在或不在数据范围内"));
    }

    private void lockSource(Map<String, Object> row) {
        if (id(row.get("contractId")) > 0) one("SELECT id FROM oa_crm_contract WHERE id=? FOR UPDATE", row.get("contractId"));
        else if (id(row.get("receivableId")) > 0) manual(id(row.get("receivableId")), true);
        else if (!"cost".equals(row.get("recordType"))) throw new ApiException(400, "请选择应收节点");
    }

    private void sync(Map<String, Object> row) {
        if (id(row.get("contractId")) > 0) {
            // A refund after close reopens the contract when its receivable is no longer settled.
            if (Set.of("refund", "reversal").contains(text(row.get("recordType"))))
                jdbc.update("UPDATE oa_crm_contract SET status='active',revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=? AND status='completed'", row.get("contractId"));
            contracts.syncCustomer(id(row.get("customerId")));
        }
        Map<String, Object> source = "cost".equals(row.get("recordType")) ? Map.of() : source(row, false);
        if (!source.isEmpty() && money(source.get("outstandingAmount")).signum() <= 0) {
            if (id(row.get("contractId")) > 0) {
                String task = "crm-contract-" + row.get("contractId") + "-fee-" + row.get("termKey");
                jdbc.update("UPDATE oa_todo SET status='done' WHERE business_type='crmRisk' AND task_id=?", task);
            }
            closeSettledCollections(source);
        }
    }

    private void closeSettledCollections(Map<String, Object> source) {
        String where = id(source.get("contractId")) > 0
                ? "contract_id=? AND term_key=?" : "receivable_id=?";
        Object[] args = id(source.get("contractId")) > 0
                ? new Object[] {source.get("contractId"), source.get("termKey")}
                : new Object[] {source.get("receivableId")};
        List<Map<String, Object>> open = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT id,content FROM oa_finance_collection WHERE status='open' AND " + where, args));
        for (Map<String, Object> collection : open) {
            long collectionId = id(collection.get("id"));
            jdbc.update("UPDATE oa_finance_collection SET status='closed',content='应收已结清，系统自动关闭催收任务' WHERE id=?", collectionId);
            history("collection", collectionId, "应收结清自动关闭", Map.of("previousContent", text(collection.get("content"))));
        }
    }

    public Map<String, Object> report(Map<String, String> query) {
        List<Map<String, Object>> ledger = ledgerRows(query).stream().filter(r -> "confirmed".equals(r.get("status"))).toList();
        Map<Long, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : ledger) {
            Map<String, Object> g = grouped.computeIfAbsent(id(row.get("customerId")), ignored -> {
                Map<String, Object> m = new LinkedHashMap<>(); m.put("customerId", row.get("customerId")); m.put("customerName", row.get("customerName")); return m;
            });
            for (String key : List.of("receivableAmount", "netReceivedAmount", "refundedAmount", "invoicedAmount", "outstandingAmount", "writeoffAmount")) add(g, key, money(row.get(key)));
        }
        for (Map<String, Object> doc : filteredRecords("cost", query)) if ("confirmed".equals(doc.get("status"))) {
            Map<String, Object> g = grouped.computeIfAbsent(id(doc.get("customerId")), ignored -> new LinkedHashMap<>(Map.of("customerId", doc.get("customerId"), "customerName", doc.get("customerName"))));
            add(g, "costAmount", money(doc.get("amount")));
        }
        List<Map<String, Object>> ages = new ArrayList<>();
        for (String bucket : List.of("未到期", "1–30天", "31–60天", "61–90天", "90天以上")) ages.add(new LinkedHashMap<>(Map.of("bucket", bucket, "amount", BigDecimal.ZERO)));
        for (Map<String, Object> row : ledger) if (money(row.get("outstandingAmount")).signum() > 0) {
            long days = id(row.get("overdueDays")); int index = days == 0 ? 0 : days <= 30 ? 1 : days <= 60 ? 2 : days <= 90 ? 3 : 4;
            add(ages.get(index), "amount", money(row.get("outstandingAmount")));
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        grouped.values().forEach(g -> {
            g.put("cashContribution", money(g.get("netReceivedAmount")).subtract(money(g.get("costAmount"))));
            for (String key : List.of("receivableAmount", "netReceivedAmount", "refundedAmount", "invoicedAmount", "outstandingAmount", "writeoffAmount", "costAmount", "cashContribution")) add(summary, key, money(g.get(key)));
        });
        return Map.of("summary", summary, "rows", grouped.values(), "aging", ages,
                "basis", "应收/账龄按到期日筛选，成本按发生日筛选；净回款含退款和到账冲销；核销不计现金。现金贡献=净回款-直接成本，非会计利润。期间资金明细请按实际办理日导出。");
    }

    public List<Map<String,Object>> exportRows(String type, Map<String,String> query) {
        List<Map<String,Object>> rows = switch (type) {
            case "ledger" -> ledgerRows(query);
            case "receivable" -> manualRows(query);
            case "collections" -> collections(query);
            case "receipts" -> receipts(query);
            case "reconcile" -> new ArrayList<>((List<Map<String,Object>>) report(query).get("rows"));
            case "invoice", "receipt", "refund", "cost", "adjustment", "all" -> filteredRecords(type,query);
            default -> throw new ApiException(400,"财务导出类型无效");
        };
        if (rows.size() > 5000) throw new ApiException(400,"每次最多导出5000条，请缩小筛选范围");
        return rows;
    }

    public List<Map<String, Object>> collections(Map<String, String> query) {
        List<Map<String,Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT l.*,m.name AS customer_name,m.owner_user_id AS customer_owner_user_id,u.nick_name AS owner_name
                FROM oa_finance_collection l JOIN oa_merchant m ON m.id=l.customer_id
                LEFT JOIN sys_user u ON u.user_id=l.owner_user_id ORDER BY l.id DESC
                """)).stream().filter(r -> access.canAccessOwner(AuthContext.userId(), r.get("customerOwnerUserId")))
                .filter(r -> matches(r, query, "nextDate")).toList();
        for (Map<String,Object> row : rows) {
            Map<String,Object> balance = ledgerRows(Map.of()).stream().filter(s -> sameSource(row,s)).findFirst().orElse(Map.of());
            row.put("outstandingAmount", balance.getOrDefault("outstandingAmount", BigDecimal.ZERO));
            row.put("history", JdbcMaps.camelCase(jdbc.queryForList("SELECT * FROM oa_finance_history WHERE entity_type='collection' AND entity_id=? ORDER BY id DESC", row.get("id"))));
        }
        return rows;
    }

    @Transactional
    public void collect(Map<String, Object> body) {
        Map<String, Object> source = source(body, true); checkCustomer(id(source.get("customerId")));
        long owner = positiveId(body.get("ownerUserId"));
        if (count("SELECT COUNT(*) FROM sys_user WHERE user_id=? AND status='0' AND del_flag='0'", owner) == 0) throw new ApiException(400, "请选择在职催收负责人");
        String content = required(body.get("content"), 2000, "催收内容");
        boolean close = Boolean.TRUE.equals(body.get("close"));
        if (close) required(body.get("reason"), 1000, "关闭原因");
        if (!close && !"confirmed".equals(source.get("status")))
            throw new ApiException(409, "请先确认应收，再登记催收计划");
        if (!close && money(source.get("outstandingAmount")).signum() <= 0)
            throw new ApiException(409, "当前应收已结清，不能新增催收任务");
        LocalDate next = close ? null : day(body.get("nextDate"));
        if (next != null && next.isBefore(today())) throw new ApiException(400, "下次催收日期不能早于今天");
        long existing = id(body.get("collectionId"));
        if (existing > 0) {
            Map<String,Object> old = one("SELECT * FROM oa_finance_collection WHERE id=? FOR UPDATE", existing);
            checkCustomer(id(old.get("customerId")));
            if (!sameSource(old, source)) throw new ApiException(409,"催收记录关联的应收已变更");
            if (!"open".equals(old.get("status"))) throw new ApiException(409,"该催收任务已关闭");
            if (close) jdbc.update("UPDATE oa_finance_collection SET status='closed',content=? WHERE id=?",content,existing);
            else jdbc.update("UPDATE oa_finance_collection SET owner_user_id=?,next_date=?,content=? WHERE id=?",owner,Date.valueOf(next),content,existing);
            history("collection",existing,close?"关闭催收":"更新催收计划",body);
            return;
        }
        if (close) throw new ApiException(400,"关闭任务时请关联现有催收任务");
        long id = insert("INSERT INTO oa_finance_collection(customer_id,contract_id,term_key,receivable_id,owner_user_id,next_date,content,status,create_by) VALUES(?,?,?,?,?,?,?,?,?)",
                source.get("customerId"), source.get("contractId"), source.get("termKey"), source.get("receivableId"), owner,
                Date.valueOf(next), content, "open", AuthContext.username());
        if (id(source.get("contractId")) > 0) {
            String task = "crm-contract-" + source.get("contractId") + "-fee-" + source.get("termKey");
            jdbc.update("INSERT INTO oa_crm_contract_reminder_log(contract_id,task_id,content,create_by) VALUES(?,?,?,?)", source.get("contractId"), task, content, AuthContext.username());
        }
        history("collection", id, "登记催收计划", body);
    }

    @Transactional
    public Map<String, Object> registerFromContract(long contractId, Map<String, Object> body) {
        Map<String, Object> contract = contracts.detail(contractId);
        checkCustomer(id(contract.get("customerId")));
        if ("completed".equals(contract.get("status"))) throw new ApiException(409, "已结案合同不能继续登记回款申请");
        String key = required(body.get("termKey"), 64, "收费节点");
        BigDecimal amount = positiveMoney(body.get("amount"));
        Object term = ((List<Map<String,Object>>) contract.get("effectiveFeeTerms")).stream()
                .filter(t -> key.equals(text(t.get("key")))).findFirst().orElseThrow(() -> new ApiException(400, "收费节点不存在"));
        Map<String,Object> selected = (Map<String,Object>) term;
        if (amount.compareTo(money(selected.get("outstandingAmount"))) > 0) throw new ApiException(409,"回款金额超过当前待收金额");
        long id = insert("""
                INSERT INTO oa_finance_record(record_type,customer_id,contract_id,term_key,amount,business_date,channel,
                reference_no,remark,status,create_token,create_by,create_user_id)
                VALUES('receipt',?,?,?,?,?,?,?,?,'draft',?,?,?)
                """, contract.get("customerId"), contractId, key, amount, Date.valueOf(actualDay(body.get("receiptDate"))),
                limited(body.get("channel"),100), limited(body.get("referenceNo"),100), limited(body.get("remark"),1000),
                UUID.randomUUID().toString(), AuthContext.username(), AuthContext.userId());
        history("record", id, "合同详情登记回款申请草稿", body);
        return detail(id);
    }

    private Map<String, Object> checkCustomer(long id) {
        Map<String, Object> c = one("SELECT id,owner_user_id,status,public_pool FROM oa_merchant WHERE id=?", id);
        if (!visible(c)) throw new ApiException(403, "无权访问该客户的财务数据");
        return c;
    }
    private boolean visible(Map<String, Object> row) { return access.canAccessOwner(AuthContext.userId(), row.get("ownerUserId")); }
    private Map<String, Object> manual(long id, boolean lock) { return one("SELECT * FROM oa_finance_receivable WHERE id=?" + (lock ? " FOR UPDATE" : ""), id); }
    private Map<String, Object> record(long id, boolean lock) { return one("SELECT * FROM oa_finance_record WHERE id=?" + (lock ? " FOR UPDATE" : ""), id); }
    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(sql, args));
        if (rows.isEmpty()) throw new ApiException(404, "财务关联记录不存在"); return rows.get(0);
    }
    private long insert(String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> { PreparedStatement s = c.prepareStatement(sql, new String[]{"id"}); for (int i=0;i<args.length;i++) s.setObject(i+1,args[i]); return s; }, key);
        if (key.getKey() == null) throw new ApiException(500, "财务记录保存失败"); return key.getKey().longValue();
    }
    private void history(String type, long id, String action, Object content) {
        try { jdbc.update("INSERT INTO oa_finance_history(entity_type,entity_id,action,content,create_by) VALUES(?,?,?,?,?)", type, id, action, mapper.writeValueAsString(content), AuthContext.username()); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new ApiException(400, "财务记录格式无效"); }
    }
    private List<Map<String, Object>> readTerms(Object snapshot) {
        try { Map<String, Object> m = mapper.readValue(text(snapshot), new TypeReference<>(){}); return mapper.convertValue(m.get("feeTerms"), new TypeReference<List<Map<String, Object>>>(){}); }
        catch (Exception e) { throw new ApiException(409, "生效合同收费快照损坏，请先修复合同"); }
    }
    private boolean sameSource(Map<String, Object> a, Map<String, Object> b) {
        return id(a.get("contractId")) > 0 ? id(a.get("contractId")) == id(b.get("contractId")) && text(a.get("termKey")).equals(text(b.get("termKey")))
                : id(a.get("receivableId")) > 0 && id(a.get("receivableId")) == id(b.get("receivableId"));
    }
    private boolean matches(Map<String, Object> r, Map<String, String> q, String dateKey) {
        if (!blank(q.get("customerId")) && id(q.get("customerId")) != id(r.get("customerId"))) return false;
        if (!blank(q.get("contractId")) && id(q.get("contractId")) != id(r.get("contractId"))) return false;
        if (!blank(q.get("status")) && !q.get("status").equals(r.get("status"))) return false;
        if (!blank(q.get("keyword")) && !(text(r.get("customerName")) + text(r.get("contractName")) + text(r.get("contractCode")) + text(r.get("title")) + text(r.get("invoiceNo")) + text(r.get("referenceNo")) + text(r.get("content")) + text(r.get("reason")) + text(r.get("remark"))).toLowerCase(Locale.ROOT).contains(q.get("keyword").trim().toLowerCase(Locale.ROOT))) return false;
        if (!blank(q.get("startDate")) && (r.get(dateKey) == null || day(r.get(dateKey)).isBefore(day(q.get("startDate"))))) return false;
        return blank(q.get("endDate")) || r.get(dateKey) != null && !day(r.get(dateKey)).isAfter(day(q.get("endDate")));
    }
    private Map<String, Object> page(List<Map<String, Object>> rows, Map<String, String> q) {
        long page = Math.max(1,id(q.get("pageNum"))); int size = (int)Math.max(1,Math.min(200,id(q.getOrDefault("pageSize","20")))) ;
        int from=(int)Math.min((page-1)*size,rows.size()); return new LinkedHashMap<>(Map.of("rows", rows.subList(from,Math.min(from+size,rows.size())),"total",rows.size()));
    }
    public static String actionFor(String type) { return switch(type) { case "invoice" -> "invoice_submit"; case "receipt" -> "receipt_confirm"; case "refund" -> "refund_submit"; case "cost" -> "cost_confirm"; case "writeoff", "reversal" -> "adjust_confirm"; default -> throw new ApiException(400,"财务类型无效"); }; }
    private void validateType(String type, boolean read) { if (!TYPES.contains(type) && !(read && Set.of("all","adjustment").contains(type))) throw new ApiException(400,"财务记录类型无效"); }
    private void revision(Map<String,Object> row, Map<String,Object> body) { if (!body.containsKey("revision") || id(body.get("revision")) != id(row.get("revision"))) throw new ApiException(409,"记录已变更，请刷新后重试"); }
    private void expect(Map<String,Object> row, String... states) { if (!Arrays.asList(states).contains(text(row.get("status")))) throw new ApiException(409,"当前状态不允许此操作，请刷新"); }
    private BigDecimal sum(String sql, Object... args) { BigDecimal v=jdbc.queryForObject(sql,BigDecimal.class,args);return v==null?BigDecimal.ZERO:v; }
    private long count(String sql, Object... args) { Long v=jdbc.queryForObject(sql,Long.class,args);return v==null?0:v; }
    private void add(Map<String,Object> row,String key,BigDecimal amount) { row.put(key,money(row.get(key)).add(amount)); }
    private BigDecimal money(Object v) { return v==null?BigDecimal.ZERO:new BigDecimal(text(v)); }
    private BigDecimal positiveMoney(Object v) { try { BigDecimal n=new BigDecimal(text(v)).setScale(2, java.math.RoundingMode.UNNECESSARY); if(n.signum()<=0 || n.precision()>18) throw new IllegalArgumentException(); return n; } catch(Exception e) { throw new ApiException(400,"金额必须大于0且最多两位小数"); } }
    private LocalDate today() { return LocalDate.now(ZONE); }
    private LocalDate actualDay(Object v) { LocalDate d=day(v); if(d.isAfter(today()))throw new ApiException(400,"实际办理日期不能晚于今天");return d; }
    private LocalDate day(Object v) { try {return v instanceof Date d?d.toLocalDate():LocalDate.parse(text(v));}catch(Exception e){throw new ApiException(400,"请填写有效日期（YYYY-MM-DD）");} }
    private long positiveId(Object v) {long n=id(v);if(n<=0)throw new ApiException(400,"关联编号无效");return n;}
    private long id(Object v) {try{return Long.parseLong(text(v));}catch(Exception e){return 0;}}
    private Long nullable(long v) {return v>0?v:null;}
    private String text(Object v) {return v==null?"":String.valueOf(v);}
    private boolean blank(String v) {return v==null||v.isBlank();}
    private String required(Object v,int max,String name) {String s=limited(v,max);if(s.isBlank())throw new ApiException(400,name+"不能为空");return s;}
    private String limited(Object v,int max) {String s=text(v).trim();if(s.length()>max)throw new ApiException(400,"字段长度超过"+max);return s;}
    private String attachments(Object v) {
        if(v==null||blank(text(v)))return "[]";
        try { List<Map<String,Object>> files=mapper.readValue(text(v), new TypeReference<>(){}); if(files.size()>20)throw new IllegalArgumentException();
            for(Map<String,Object> f:files) {String path=text(f.get("fileName"));if(!path.startsWith("/profile/")||path.contains(".."))throw new IllegalArgumentException();}
            return mapper.writeValueAsString(files);
        }catch(Exception e){throw new ApiException(400,"附件格式无效，请使用系统上传凭证");}
    }
}
