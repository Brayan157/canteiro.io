package com.renovar.canteiro.io.receivables.infrastructure.maintenance;

import com.renovar.canteiro.io.receivables.application.HistoricalReceiptLinkPlan;
import com.renovar.canteiro.io.receivables.application.HistoricalReceiptLinkPlan.Approval;
import com.renovar.canteiro.io.receivables.application.HistoricalReceiptLinkPlan.Entry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import static com.renovar.canteiro.io.receivables.application.HistoricalReceiptLinkPlan.reject;
import static com.renovar.canteiro.io.receivables.application.HistoricalReceiptLinkPlan.sha256;

/** No bean, route, scheduler, seed or automatic activation. Only the separate maintenance command invokes it. */
public final class HistoricalReceiptLinkMaintenance {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    public HistoricalReceiptLinkMaintenance(JdbcTemplate jdbc, PlatformTransactionManager transactions, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Result validate(HistoricalReceiptLinkPlan plan) { return run(plan, null); }

    public Result apply(HistoricalReceiptLinkPlan plan, Approval approval) {
        if (approval == null || !plan.fingerprint().equals(approval.planSha256())) reject("APPROVED_PLAN_CHANGED");
        return run(plan, approval);
    }

    private Result run(HistoricalReceiptLinkPlan plan, Approval approval) {
        var tx = new TransactionTemplate(transactions);
        tx.setReadOnly(approval == null);
        tx.setIsolationLevel(approval == null ? TransactionDefinition.ISOLATION_REPEATABLE_READ : TransactionDefinition.ISOLATION_SERIALIZABLE);
        tx.setTimeout(30);
        return tx.execute(status -> {
            jdbc.execute("set local statement_timeout='20s'");
            jdbc.execute("set local lock_timeout='5s'");
            if (approval != null) lock(plan);
            var replay = replay(plan, approval);
            if (replay != null) return replay;
            verify(plan);
            String original = originalData(plan), state = sha256(original + "|no-invoice-receipt-lines");
            if (approval == null) return new Result("VALIDATED_ONLY", plan.fingerprint(), state, false, 2);
            if (!state.equals(approval.stateSha256())) reject("APPROVED_STATE_CHANGED");
            List<Map<String, Object>> links = new ArrayList<>();
            for (Entry e : plan.entries()) {
                UUID lineId = UUID.randomUUID();
                jdbc.update("""
                        insert into invoice_settlement_line(id,company_id,contract_id,receivable_id,settlement_id,
                            invoice_id,invoice_item_id,billing_allocation_id,measurement_contract_composition_id,amount)
                        values(?,?,?,?,?,?,?,?,null,?)
                        """, lineId, plan.companyId(), plan.contractId(), e.receivableId(), e.settlementId(), e.invoiceId(), e.invoiceItemId(), e.allocationId(), e.amount());
                links.add(Map.of("lineId", lineId, "settlementId", e.settlementId(), "invoiceId", e.invoiceId(),
                        "invoiceItemId", e.invoiceItemId(), "billingAllocationId", e.allocationId()));
            }
            if (!original.equals(originalData(plan))) reject("ORIGINAL_FINANCIAL_DATA_CHANGED");
            var metadata = Map.of("origin", "system", "operationId", plan.operationId(), "planSha256", plan.fingerprint(),
                    "approvedStateSha256", state, "approvalReference", approval.humanAuthorizationReference());
            jdbc.update("""
                    insert into audit_event(id,company_id,actor_user_id,actor_type,module,action,entity_type,entity_id,
                        before_data,after_data,metadata,occurred_at,actor_display_name,entity_display_name,event_summary,origin,correlation_id)
                    values(?,?,null,'SYSTEM','RECEIVABLES','UPDATE','HistoricalReceiptReconciliation',?,
                        cast(? as jsonb),cast(? as jsonb),cast(? as jsonb),?,'Sistema','Vínculos de recebimentos históricos',
                        'Sistema registrou vínculos de recebimentos históricos autorizados.','system',?)
                    """, plan.operationId(), plan.companyId(), plan.operationId(), json.writeValueAsString(Map.of("invoiceReceiptLinks", 0)),
                    json.writeValueAsString(Map.of("links", links, "cashChanged", false)), json.writeValueAsString(metadata),
                    java.sql.Timestamp.from(clock.instant()), plan.operationId().toString());
            jdbc.execute("set constraints trg_invoice_settlement_line_total immediate");
            return new Result("APPLIED", plan.fingerprint(), state, false, 2);
        });
    }

    private void lock(HistoricalReceiptLinkPlan p) {
        // Same aggregate/input order as normal receipt/closure writers. No forced version increments.
        jdbc.queryForList("select id from contract where company_id=? and id=? for update", p.companyId(), p.contractId());
        for (String table : List.of("invoice", "billing_installment", "receivable", "receivable_settlement"))
            jdbc.queryForList("select id from " + table + " where company_id=? and contract_id=? order by id for update", p.companyId(), p.contractId());
    }

    private Result replay(HistoricalReceiptLinkPlan p, Approval approval) {
        var rows = jdbc.queryForList("""
                select metadata->>'planSha256' plan_hash,metadata->>'approvedStateSha256' state_hash,
                    metadata->>'approvalReference' approval_ref,actor_type,actor_user_id,entity_type
                from audit_event where company_id=? and id=?
                """, p.companyId(), p.operationId());
        if (rows.isEmpty()) return null;
        var a = rows.getFirst();
        if (!Objects.equals(a.get("plan_hash"), p.fingerprint()) || !"SYSTEM".equals(a.get("actor_type"))
                || a.get("actor_user_id") != null || !"HistoricalReceiptReconciliation".equals(a.get("entity_type"))) reject("OPERATION_CONFLICT");
        if (approval != null && (!approval.stateSha256().equals(a.get("state_hash"))
                || !approval.humanAuthorizationReference().equals(a.get("approval_ref")))) reject("APPROVAL_CHANGED");
        for (Entry e : p.entries()) {
            var lines = jdbc.queryForList("select invoice_id,invoice_item_id,billing_allocation_id,receivable_id,contract_id,amount,reversal_of_line_id from invoice_settlement_line where company_id=? and settlement_id=?", p.companyId(), e.settlementId());
            if (lines.size() != 1) reject("RECONCILIATION_HISTORY_CONFLICT");
            var l = lines.getFirst();
            if (!e.invoiceId().equals(l.get("invoice_id")) || !e.invoiceItemId().equals(l.get("invoice_item_id"))
                    || !e.allocationId().equals(l.get("billing_allocation_id")) || !e.receivableId().equals(l.get("receivable_id"))
                    || !p.contractId().equals(l.get("contract_id")) || l.get("reversal_of_line_id") != null
                    || e.amount().compareTo((BigDecimal) l.get("amount")) != 0) reject("RECONCILIATION_HISTORY_CONFLICT");
        }
        return new Result("ALREADY_APPLIED", p.fingerprint(), (String) a.get("state_hash"), true, 2);
    }

    private void verify(HistoricalReceiptLinkPlan p) {
        var contracts = jdbc.queryForList("select status,currency,pricing_mode from contract where company_id=? and id=?", p.companyId(), p.contractId());
        if (contracts.size() != 1 || !"OPEN".equals(contracts.getFirst().get("status"))
                || !p.currency().name().equals(contracts.getFirst().get("currency")) || !"FIXED".equals(contracts.getFirst().get("pricing_mode"))) reject("CONTRACT_SCOPE_CHANGED");
        var services = jdbc.queryForList("select id,status,measurement_composition_amount from contract_service where company_id=? and contract_id=?", p.companyId(), p.contractId());
        if (services.size() != 1 || !p.serviceId().equals(services.getFirst().get("id")) || !"COMPLETED".equals(services.getFirst().get("status"))
                || ((BigDecimal) services.getFirst().get("measurement_composition_amount")).signum() != 0) reject("SERVICE_SCOPE_CHANGED");
        for (String table : List.of("billing_installment", "billing_allocation", "receivable", "receivable_settlement", "invoice", "invoice_item"))
            if (count(table, p) != 2) reject("UNSUPPORTED_FINANCIAL_SCOPE");
        for (String table : List.of("tax_obligation", "receivable_credit", "measurement_contract_adjustment", "measurement_item_contract_service_conversion", "invoice_settlement_line", "invoice_settlement_operation"))
            if (count(table, p) != 0) reject("EXISTING_ADJUSTMENT_OR_LINK");
        for (Entry e : p.entries()) {
            var found = jdbc.queryForList("""
                    select s.amount cash,sa.amount attributed,r.original_amount title_amount,s.received_on::text received_on,i.number,i.issued_on::text issued_on,
                        a.allocated_amount allocation_amount,ii.allocated_amount item_amount,i.total_amount invoice_amount
                    from receivable_settlement s
                    join receivable r on r.id=s.receivable_id and r.company_id=s.company_id and r.contract_id=s.contract_id
                    join billing_installment b on b.id=r.billing_installment_id and b.company_id=r.company_id and b.contract_id=r.contract_id
                    join receivable_settlement_allocation sa on sa.settlement_id=s.id and sa.company_id=s.company_id
                    join billing_allocation a on a.id=sa.billing_allocation_id and a.company_id=s.company_id and a.contract_id=s.contract_id and a.billing_installment_id=b.id
                    join invoice_item ii on ii.billing_allocation_id=a.id and ii.company_id=a.company_id and ii.contract_id=a.contract_id and ii.contract_service_id=a.contract_service_id
                    join invoice i on i.id=ii.invoice_id and i.company_id=ii.company_id and i.contract_id=ii.contract_id
                    where s.company_id=? and s.contract_id=? and s.id=? and r.id=? and b.id=? and a.id=? and i.id=? and ii.id=? and a.contract_service_id=?
                        and s.status='CONFIRMED' and s.entry_type='SETTLEMENT' and s.reversal_of_settlement_id is null
                        and r.status='RECEIVED' and r.renegotiation_source_receivable_id is null and b.status='OPEN'
                        and a.status='ACTIVE' and a.allocation_type='SERVICE' and a.measurement_contract_composition_id is null
                        and i.status='REGISTERED' and ii.status='REGISTERED' and ii.allocated_amount>0
                    """, p.companyId(), p.contractId(), e.settlementId(), e.receivableId(), e.installmentId(), e.allocationId(), e.invoiceId(), e.invoiceItemId(), p.serviceId());
            if (found.size() != 1) reject("EXPLICIT_TARGET_NOT_VALID");
            var row = found.getFirst();
            for (String amount : List.of("cash", "attributed", "title_amount", "allocation_amount", "item_amount", "invoice_amount"))
                if (e.amount().compareTo((BigDecimal) row.get(amount)) != 0) reject("TARGET_AMOUNT_CHANGED");
            if (!e.receivedOn().equals(row.get("received_on")) || !e.invoiceNumber().equals(row.get("number"))
                    || !e.invoiceIssuedOn().equals(row.get("issued_on"))) reject("BUSINESS_REFERENCE_CHANGED");
            if (jdbc.queryForObject("select count(*) from receivable_settlement_allocation where company_id=? and settlement_id=?", Long.class, p.companyId(), e.settlementId()) != 1L)
                reject("AMBIGUOUS_LEGACY_ATTRIBUTION");
            if (jdbc.queryForObject("select count(*) from invoice_item where company_id=? and billing_allocation_id=?", Long.class, p.companyId(), e.allocationId()) != 1L)
                reject("AMBIGUOUS_INVOICE_TARGET");
        }
    }

    private long count(String table, HistoricalReceiptLinkPlan p) {
        return jdbc.queryForObject("select count(*) from " + table + " where company_id=? and contract_id=?", Long.class, p.companyId(), p.contractId());
    }

    private String originalData(HistoricalReceiptLinkPlan p) {
        StringBuilder data = new StringBuilder();
        for (String table : List.of("contract", "contract_service", "contract_discount", "billing_installment", "billing_allocation", "receivable", "receivable_settlement", "invoice", "invoice_item")) {
            var rows = jdbc.queryForList("select (to_jsonb(t)-array['description','note','access_key'])::text from " + table
                    + " t where company_id=? and " + (table.equals("contract") ? "id" : "contract_id") + "=? order by id", String.class, p.companyId(), p.contractId());
            data.append(table).append(':').append(rows).append('\n');
        }
        data.append(jdbc.queryForList("""
                select to_jsonb(sa)::text from receivable_settlement_allocation sa join receivable_settlement s on s.id=sa.settlement_id and s.company_id=sa.company_id
                where s.company_id=? and s.contract_id=? order by sa.settlement_id,sa.billing_allocation_id
                """, String.class, p.companyId(), p.contractId()));
        return data.toString();
    }

    public record Result(String status, String planSha256, String stateSha256, boolean alreadyApplied, int linkCount) { }
}
