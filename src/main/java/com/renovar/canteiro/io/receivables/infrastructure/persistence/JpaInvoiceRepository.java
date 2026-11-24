package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceFilter;
import com.renovar.canteiro.io.receivables.domain.InvoiceSummary;
import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
import com.renovar.canteiro.io.receivables.domain.InvoiceContractSummary;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
@RequiredArgsConstructor
public class JpaInvoiceRepository implements InvoiceRepository {

    private final InvoiceJpaRepository invoiceJpaRepository;
    @PersistenceContext private EntityManager entityManager;

    private static final String CONTRACT_INDEX_FROM = """
            from contract c
            where c.company_id=:company and exists (
                select 1 from invoice i where i.company_id=c.company_id and i.contract_id=c.id
            )
            """;
    private static final String CONTRACT_INDEX_COLUMNS = "select c.id,c.name,c.reference,c.contract_identifier ";

    @Override
    public Page<InvoiceContractSummary> findContractsWithInvoices(UUID companyId, String search, Pageable pageable) {
        String from = CONTRACT_INDEX_FROM;
        if (search != null) from += """
                 and (lower(c.name) like :search escape '!'
                    or lower(c.reference) like :search escape '!'
                    or lower(c.contract_identifier) like :search escape '!')
                """;
        List<String> orders = new ArrayList<>();
        pageable.getSort().forEach(order -> {
            String column = switch (order.getProperty()) {
                case "contractName" -> "c.name";
                case "contractReference" -> "c.reference";
                case "contractIdentifier" -> "c.contract_identifier";
                default -> throw new IllegalArgumentException("Sort field is not allowed");
            };
            orders.add(column + (order.isAscending() ? " asc" : " desc") + " nulls last");
        });
        if (orders.isEmpty()) orders.add("c.name asc");
        orders.add("c.id asc");
        var query = entityManager.createNativeQuery(CONTRACT_INDEX_COLUMNS + from + " order by " + String.join(",", orders));
        var count = entityManager.createNativeQuery("select count(*) " + from);
        for (var target : List.of(query, count)) {
            target.setParameter("company", companyId);
            if (search != null) target.setParameter("search", literal(search));
        }
        long total = ((Number) count.getSingleResult()).longValue();
        List<?> rows = query.setFirstResult(Math.toIntExact(pageable.getOffset())).setMaxResults(pageable.getPageSize()).getResultList();
        return new PageImpl<>(rows.stream().map(this::contractRow).toList(), pageable, total);
    }

    @Override
    public Optional<InvoiceContractSummary> findContractWithInvoicesByIdAndCompanyId(UUID contractId, UUID companyId) {
        List<?> rows = entityManager.createNativeQuery(CONTRACT_INDEX_COLUMNS + CONTRACT_INDEX_FROM + " and c.id=:contract")
                .setParameter("company", companyId).setParameter("contract", contractId).getResultList();
        return rows.stream().findFirst().map(this::contractRow);
    }

    private InvoiceContractSummary contractRow(Object value) {
        Object[] row = (Object[]) value;
        return new InvoiceContractSummary((UUID) row[0], (String) row[1], (String) row[2], (String) row[3]);
    }

    @Override
    public Page<InvoiceSummary> findByCompanyId(UUID companyId, InvoiceFilter filter, Pageable pageable) {
        String from = """
                from invoice i join contract c on c.id=i.contract_id and c.company_id=i.company_id
                where i.company_id=:company
                """;
        if (filter.contractId() != null) from += " and i.contract_id=:contract";
        if (filter.number() != null) from += " and lower(i.number) like :number escape '!'";
        if (filter.series() != null) from += " and lower(i.series) like :series escape '!'";
        if (filter.contractSearch() != null) from += """
                 and (lower(c.name) like :contractSearch escape '!'
                   or lower(c.reference) like :contractSearch escape '!'
                   or lower(c.contract_identifier) like :contractSearch escape '!')
                """;
        if (filter.installmentReference() != null) from += """
                 and exists (select 1 from invoice_item item
                   join billing_allocation a on a.id=item.billing_allocation_id
                       and a.company_id=item.company_id and a.contract_id=item.contract_id
                   join billing_installment b on b.id=a.billing_installment_id
                       and b.company_id=a.company_id and b.contract_id=a.contract_id
                   where item.invoice_id=i.id and item.company_id=i.company_id and item.contract_id=i.contract_id
                     and lower(b.reference) like :installmentReference escape '!')
                """;
        if (filter.status() != null) from += " and i.status=:status";
        if (filter.issuedFrom() != null) from += " and i.issued_on>=:issuedFrom";
        if (filter.issuedTo() != null) from += " and i.issued_on<=:issuedTo";
        List<String> orders = new ArrayList<>();
        pageable.getSort().forEach(order -> {
            String column = switch (order.getProperty()) {
                case "number" -> "number";
                case "series" -> "series";
                case "issuedOn" -> "issued_on";
                case "status" -> "status";
                case "createdAt" -> "created_at";
                default -> throw new IllegalArgumentException("Sort field is not allowed");
            };
            orders.add("i." + column + (order.isAscending() ? " asc" : " desc") + " nulls last");
        });
        if (orders.isEmpty()) orders.add("i.issued_on desc");
        orders.add("i.id asc");
        var query = entityManager.createNativeQuery("""
                select i.id,i.contract_id,c.name,c.reference,c.contract_identifier,i.number,i.series,i.issued_on,
                  i.status,c.currency,i.total_amount,i.created_at,i.updated_at
                """ + from + " order by " + String.join(",", orders));
        var count = entityManager.createNativeQuery("select count(*) " + from);
        for (var target : List.of(query, count)) {
            target.setParameter("company", companyId);
            if (filter.contractId() != null) target.setParameter("contract", filter.contractId());
            if (filter.number() != null) target.setParameter("number", literal(filter.number()));
            if (filter.series() != null) target.setParameter("series", literal(filter.series()));
            if (filter.contractSearch() != null) target.setParameter("contractSearch", literal(filter.contractSearch()));
            if (filter.installmentReference() != null) target.setParameter("installmentReference", literal(filter.installmentReference()));
            if (filter.status() != null) target.setParameter("status", filter.status().name());
            if (filter.issuedFrom() != null) target.setParameter("issuedFrom", filter.issuedFrom());
            if (filter.issuedTo() != null) target.setParameter("issuedTo", filter.issuedTo());
        }
        long total = ((Number) count.getSingleResult()).longValue();
        List<?> rows = query.setFirstResult(Math.toIntExact(pageable.getOffset())).setMaxResults(pageable.getPageSize()).getResultList();
        List<InvoiceSummary> summaries = rows.stream().map(value -> {
            Object[] row = (Object[]) value;
            LocalDate date = row[7] instanceof LocalDate local ? local : ((java.sql.Date) row[7]).toLocalDate();
            return new InvoiceSummary((UUID) row[0], (UUID) row[1], (String) row[2], (String) row[3], (String) row[4],
                    (String) row[5], (String) row[6], date, InvoiceStatus.valueOf((String) row[8]),
                    CurrencyCode.valueOf((String) row[9]), (BigDecimal) row[10], instant(row[11]), instant(row[12]));
        }).toList();
        return new PageImpl<>(summaries, pageable, total);
    }

    private String literal(String value) {
        return "%" + value.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offset) return offset.toInstant();
        return ((java.sql.Timestamp) value).toInstant();
    }

    @Override
    public Invoice save(Invoice invoice) {
        try { return toDomain(invoiceJpaRepository.saveAndFlush(new InvoiceJpaEntity(
                invoice.getCompanyId(), invoice.getContractId(), invoice.getNumber(), invoice.getSeries(),
                invoice.getAccessKey(), invoice.getIssuedOn(), invoice.getTotalAmount(), invoice.getStatus()
        )));
        } catch(org.springframework.dao.DataIntegrityViolationException exception){
            String detail=exception.getMostSpecificCause().getMessage();
            if(detail!=null&&(detail.contains("ux_invoice_company_number_series")||detail.contains("ux_invoice_company_access_key")))
                throw new com.renovar.canteiro.io.receivables.domain.InvoiceFiscalIdentityConflict();
            throw exception;
        }
    }

    @Override
    public Optional<Invoice> findByIdAndCompanyId(UUID id, UUID companyId) {
        return invoiceJpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public Optional<Invoice> findByIdAndCompanyIdWithLock(UUID id, UUID companyId) {
        return invoiceJpaRepository.findByIdAndCompanyIdWithLock(id, companyId).map(this::toDomain);
    }

    @Override
    public Invoice updateStatus(Invoice invoice) {
        InvoiceJpaEntity entity = invoiceJpaRepository.findByIdAndCompanyId(invoice.getId(), invoice.getCompanyId()).orElseThrow();
        entity.setStatus(invoice.getStatus());
        return toDomain(invoiceJpaRepository.save(entity));
    }

    @Override
    public boolean existsRegisteredByBillingInstallmentIdAndCompanyId(UUID billingInstallmentId, UUID companyId) {
        return invoiceJpaRepository.existsRegisteredByBillingInstallmentIdAndCompanyId(billingInstallmentId, companyId);
    }

    private Invoice toDomain(InvoiceJpaEntity entity) {
        return Invoice.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getContractId(), entity.getNumber(), entity.getSeries(),
                entity.getAccessKey(), entity.getIssuedOn(), entity.getTotalAmount(), entity.getStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
