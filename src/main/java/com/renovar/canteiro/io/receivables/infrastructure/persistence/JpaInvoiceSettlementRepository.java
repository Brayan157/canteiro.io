package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.InvoiceSettlementRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaInvoiceSettlementRepository implements InvoiceSettlementRepository {
    @PersistenceContext private EntityManager em;

    private static final String FROM = """
            from receivable r
            join billing_allocation a on a.billing_installment_id=r.billing_installment_id and a.company_id=r.company_id and a.contract_id=r.contract_id
            join invoice_item ii on ii.billing_allocation_id=a.id and ii.company_id=a.company_id and ii.contract_id=a.contract_id and ii.contract_service_id=a.contract_service_id
            join invoice i on i.id=ii.invoice_id and i.company_id=ii.company_id and i.contract_id=ii.contract_id
            join contract_service cs on cs.id=a.contract_service_id and cs.company_id=a.company_id and cs.contract_id=a.contract_id
            where r.company_id=:company and r.id=:receivable and a.status='ACTIVE' and a.allocation_type='SERVICE'
                and ii.status='REGISTERED' and ii.allocated_amount>0 and i.status='REGISTERED'
            """;
    private static final String COLUMNS = """
            select i.id,i.number,ii.id,a.id,cs.id,cs.name,a.measurement_contract_composition_id,ii.allocated_amount,
                (select coalesce(sum(l.amount),0) from invoice_settlement_line l join receivable_settlement s on s.id=l.settlement_id and s.company_id=l.company_id
                    where l.company_id=i.company_id and l.invoice_item_id=ii.id and s.status='CONFIRMED'),
                i.total_amount-(select coalesce(sum(t.amount),0) from tax_obligation t where t.company_id=i.company_id and t.invoice_id=i.id
                    and t.treatment='WITHHELD_BY_CUSTOMER' and t.status='REGISTERED'),
                (select coalesce(sum(l.amount),0) from invoice_settlement_line l join receivable_settlement s on s.id=l.settlement_id and s.company_id=l.company_id
                    where l.company_id=i.company_id and l.invoice_id=i.id and s.status='CONFIRMED')
            """;

    // Unknown historical ownership remains unknown. Only an exact, wholly unassigned reversal pair has no remaining cash to attribute.
    private static final String UNPROVEN_HISTORY = """
            (exists (select 1 from receivable_settlement s where s.company_id=r.company_id and s.receivable_id=r.id
                and (s.status<>'CONFIRMED' or (
                    s.amount<>(select coalesce(sum(l.amount),0) from invoice_settlement_line l where l.company_id=s.company_id and l.settlement_id=s.id)
                    and not exists (
                        select 1 from receivable_settlement paired where paired.company_id=s.company_id and paired.receivable_id=s.receivable_id
                            and paired.contract_id=s.contract_id and paired.status='CONFIRMED' and paired.amount=-s.amount
                            and ((s.entry_type='SETTLEMENT' and paired.entry_type='REVERSAL' and paired.reversal_of_settlement_id=s.id)
                                or (s.entry_type='REVERSAL' and paired.entry_type='SETTLEMENT' and s.reversal_of_settlement_id=paired.id))
                            and not exists(select 1 from invoice_settlement_line l where l.company_id=s.company_id and l.settlement_id in(s.id,paired.id))
                    )
                )))
            or (r.status in ('RECEIVED','PARTIALLY_RECEIVED')
                and not exists(select 1 from receivable_settlement s where s.company_id=r.company_id and s.receivable_id=r.id)
                and r.original_amount>(select coalesce(sum(t.amount),0) from tax_obligation t
                    where t.company_id=r.company_id and t.receivable_id=r.id and t.status='REGISTERED' and t.treatment='WITHHELD_BY_CUSTOMER')))
            """;

    @Override public void lockOperation(UUID companyId, UUID operationId) {
        em.createNativeQuery("select 1 from pg_advisory_xact_lock(hashtextextended(:key,0))")
                .setParameter("key", "invoice-settlement/" + companyId + "/" + operationId).getResultList();
    }

    @Override public Optional<Operation> findOperation(UUID companyId, UUID operationId) {
        List<?> rows = em.createNativeQuery("select operation_id,receivable_id,fingerprint,settlement_id,change_request_id from invoice_settlement_operation where company_id=:company and operation_id=:operation")
                .setParameter("company", companyId).setParameter("operation", operationId).getResultList();
        return rows.stream().findFirst().map(value -> { Object[] row = (Object[]) value;
            return new Operation((UUID) row[0], (UUID) row[1], (String) row[2], (UUID) row[3], (UUID) row[4]); });
    }

    @Override public void remember(UUID companyId, UUID operationId, UUID receivableId, UUID contractId, String fingerprint, UUID settlementId, UUID requestId) {
        em.flush();
        em.createNativeQuery("insert into invoice_settlement_operation(company_id,operation_id,receivable_id,contract_id,fingerprint,settlement_id,change_request_id) values(:company,:operation,:receivable,:contract,:hash,:settlement,:request)")
                .setParameter("company", companyId).setParameter("operation", operationId).setParameter("receivable", receivableId)
                .setParameter("contract", contractId).setParameter("hash", fingerprint).setParameter("settlement", settlementId).setParameter("request", requestId).executeUpdate();
    }

    @Override public void complete(UUID companyId, UUID operationId, UUID requestId, UUID settlementId) {
        em.flush();
        int changed = em.createNativeQuery("update invoice_settlement_operation set settlement_id=:settlement where company_id=:company and operation_id=:operation and change_request_id=:request and settlement_id is null")
                .setParameter("company", companyId).setParameter("operation", operationId).setParameter("request", requestId).setParameter("settlement", settlementId).executeUpdate();
        if (changed != 1) throw new IllegalStateException("Invoice receipt intent no longer matches its pending approval");
    }

    @Override public Page<Candidate> candidates(UUID companyId, UUID receivableId, Pageable pageable) {
        long count = ((Number) em.createNativeQuery("select count(*) " + FROM).setParameter("company", companyId).setParameter("receivable", receivableId).getSingleResult()).longValue();
        List<?> rows = em.createNativeQuery(COLUMNS + FROM + " order by i.issued_on,i.id,ii.id")
                .setParameter("company", companyId).setParameter("receivable", receivableId)
                .setFirstResult(Math.toIntExact(pageable.getOffset())).setMaxResults(pageable.getPageSize()).getResultList();
        return new PageImpl<>(rows.stream().map(this::candidate).toList(), pageable, count);
    }

    @Override public Optional<Candidate> candidate(UUID companyId, UUID receivableId, UUID invoiceItemId) {
        List<?> rows = em.createNativeQuery(COLUMNS + FROM + " and ii.id=:item")
                .setParameter("company", companyId).setParameter("receivable", receivableId).setParameter("item", invoiceItemId).getResultList();
        return rows.stream().findFirst().map(this::candidate);
    }

    private Candidate candidate(Object value) {
        Object[] row = (Object[]) value;
        return new Candidate((UUID) row[0], (String) row[1], (UUID) row[2], (UUID) row[3], (UUID) row[4], (String) row[5],
                (UUID) row[6], (BigDecimal) row[7], (BigDecimal) row[8], (BigDecimal) row[9], (BigDecimal) row[10]);
    }

    @Override public List<WithholdingBasis> withholdingBasis(UUID company, UUID receivable, List<UUID> invoices) {
        if (invoices.isEmpty()) return List.of();
        List<?> rows = em.createNativeQuery("""
                with basis as (
                    select i.id invoice_id,ii.id item_id,i.total_amount,
                        (select coalesce(sum(t.amount),0) from tax_obligation t where t.company_id=i.company_id
                            and t.contract_id=i.contract_id and t.invoice_id=i.id and t.status='REGISTERED' and t.treatment='WITHHELD_BY_CUSTOMER') withheld,
                        not exists(select 1 from tax_obligation t where t.company_id=i.company_id and t.contract_id=i.contract_id
                            and t.invoice_id=i.id and t.status='REGISTERED' and t.treatment='WITHHELD_BY_CUSTOMER'
                            and t.receivable_id is distinct from r.id) tax_scope,
                        (ii.status='REGISTERED' and ii.allocated_amount>0 and a.status='ACTIVE' and a.allocation_type='SERVICE'
                            and a.billing_installment_id=r.billing_installment_id and cs.id is not null
                            and r.renegotiation_source_receivable_id is null) proven,
                        ii.allocated_amount,
                        (select coalesce(sum(l.amount),0) from invoice_settlement_line l join receivable_settlement s
                            on s.id=l.settlement_id and s.company_id=l.company_id
                            where l.company_id=i.company_id and l.invoice_id=i.id and l.invoice_item_id=ii.id and s.status='CONFIRMED') received,
                        count(*) over(partition by i.id) item_count,row_number() over(partition by i.id order by ii.id) item_row
                    from invoice i join receivable r on r.id=:receivable and r.company_id=i.company_id and r.contract_id=i.contract_id
                    left join invoice_item ii on ii.invoice_id=i.id and ii.company_id=i.company_id and ii.contract_id=i.contract_id
                    left join billing_allocation a on a.id=ii.billing_allocation_id and a.company_id=i.company_id and a.contract_id=i.contract_id
                        and a.contract_service_id=ii.contract_service_id
                    left join contract_service cs on cs.id=ii.contract_service_id and cs.company_id=i.company_id and cs.contract_id=i.contract_id
                    where i.company_id=:company and i.id in (:invoices) and i.status='REGISTERED'
                ) select invoice_id,item_id,total_amount,withheld,tax_scope,proven,allocated_amount,received,item_count
                    from basis where item_row<=101 order by invoice_id,item_id
                """).setParameter("company",company).setParameter("receivable",receivable).setParameter("invoices",invoices).getResultList();
        return rows.stream().map(value -> {
            Object[] row=(Object[])value;
            return new WithholdingBasis((UUID)row[0],(UUID)row[1],(BigDecimal)row[2],(BigDecimal)row[3],
                    Boolean.TRUE.equals(row[4]),Boolean.TRUE.equals(row[5]),(BigDecimal)row[6],(BigDecimal)row[7],((Number)row[8]).longValue());
        }).toList();
    }

    @Override public List<Line> lines(UUID companyId, UUID settlementId) {
        List<?> rows = em.createNativeQuery("select id,company_id,contract_id,receivable_id,settlement_id,invoice_id,invoice_item_id,billing_allocation_id,measurement_contract_composition_id,amount,reversal_of_line_id,created_at from invoice_settlement_line where company_id=:company and settlement_id=:settlement order by invoice_id,invoice_item_id")
                .setParameter("company", companyId).setParameter("settlement", settlementId).getResultList();
        return rows.stream().map(value -> { Object[] row = (Object[]) value;
            return new Line((UUID) row[0], (UUID) row[1], (UUID) row[2], (UUID) row[3], (UUID) row[4], (UUID) row[5], (UUID) row[6],
                    (UUID) row[7], (UUID) row[8], (BigDecimal) row[9], (UUID) row[10],
                    row[11] instanceof Instant instant ? instant : ((OffsetDateTime) row[11]).toInstant()); }).toList();
    }

    @Override public void append(Line line) {
        em.flush();
        em.createNativeQuery("insert into invoice_settlement_line(id,company_id,contract_id,receivable_id,settlement_id,invoice_id,invoice_item_id,billing_allocation_id,measurement_contract_composition_id,amount,reversal_of_line_id) values(:id,:company,:contract,:receivable,:settlement,:invoice,:item,:allocation,:composition,:amount,:reversal)")
                .setParameter("id", line.id()).setParameter("company", line.companyId()).setParameter("contract", line.contractId())
                .setParameter("receivable", line.receivableId()).setParameter("settlement", line.settlementId()).setParameter("invoice", line.invoiceId())
                .setParameter("item", line.invoiceItemId()).setParameter("allocation", line.billingAllocationId()).setParameter("composition", line.measurementContractCompositionId())
                .setParameter("amount", line.amount()).setParameter("reversal", line.reversalOfLineId()).executeUpdate();
    }

    @Override public boolean hasUnprovenHistory(UUID companyId, UUID receivableId) {
        return ((Number) em.createNativeQuery("select count(*) from receivable r where r.company_id=:company and r.id=:receivable and (" + UNPROVEN_HISTORY + ")")
                .setParameter("company", companyId).setParameter("receivable", receivableId).getSingleResult()).longValue() > 0;
    }

    @Override public boolean hasUnprovenInvoiceHistory(UUID companyId, UUID invoiceId) {
        return ((Number) em.createNativeQuery("""
                select count(*) from receivable r where r.company_id=:company
                and exists(select 1 from invoice_item ii join billing_allocation a on a.id=ii.billing_allocation_id and a.company_id=ii.company_id
                    where ii.company_id=r.company_id and ii.invoice_id=:invoice and a.billing_installment_id=r.billing_installment_id)
                and (
                """ + UNPROVEN_HISTORY + """
                or r.renegotiation_source_receivable_id is not null or r.status in ('RENEGOTIATED','CANCELLED')
                or exists(select 1 from receivable_credit cr where cr.company_id=r.company_id and cr.receivable_id=r.id))
                """).setParameter("company", companyId).setParameter("invoice", invoiceId).getSingleResult()).longValue() > 0;
    }

    @Override public BigDecimal receivedByInvoice(UUID companyId, UUID invoiceId) {
        return (BigDecimal) em.createNativeQuery("select coalesce(sum(l.amount),0) from invoice_settlement_line l join receivable_settlement s on s.id=l.settlement_id and s.company_id=l.company_id where l.company_id=:company and l.invoice_id=:invoice and s.status='CONFIRMED'")
                .setParameter("company", companyId).setParameter("invoice", invoiceId).getSingleResult();
    }
}
