package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentFilter;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentSummary;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentStatus;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaBillingInstallmentRepository implements BillingInstallmentRepository {

    private final BillingInstallmentJpaRepository billingInstallmentJpaRepository;
    @PersistenceContext private EntityManager entityManager;

    @Override
    public Page<BillingInstallmentSummary> findByCompanyId(UUID companyId, BillingInstallmentFilter filter, Pageable pageable) {
        String from = """
                from billing_installment b join contract c on c.id=b.contract_id and c.company_id=b.company_id
                join lateral (
                    select coalesce(sum(a.allocated_amount),0) as net_amount from billing_allocation a
                    where a.company_id=b.company_id and a.billing_installment_id=b.id and a.contract_id=b.contract_id
                ) totals on true
                where b.company_id=:company
                """;
        if (filter.contractId() != null) from += " and b.contract_id=:contract";
        if (filter.reference() != null) from += " and lower(b.reference) like :reference escape '!'";
        if (filter.status() != null) from += " and b.status=:status";
        if (filter.dueFrom() != null) from += " and b.due_on>=:dueFrom";
        if (filter.dueTo() != null) from += " and b.due_on<=:dueTo";
        List<String> orders = new java.util.ArrayList<>();
        pageable.getSort().forEach(order -> {
            String column = switch (order.getProperty()) {
                case "dueOn" -> "due_on";
                case "issuedOn" -> "issued_on";
                case "reference" -> "reference";
                case "status" -> "status";
                case "createdAt" -> "created_at";
                default -> throw new IllegalArgumentException("Sort field is not allowed");
            };
            orders.add("b." + column + (order.isAscending() ? " asc" : " desc") + " nulls last");
        });
        if (orders.isEmpty()) orders.add("b.due_on asc");
        orders.add("b.id asc");
        var query = entityManager.createNativeQuery("""
                select b.id,b.contract_id,b.reference,b.issued_on,b.due_on,b.status,c.currency,
                    totals.net_amount,b.created_at,b.updated_at
                """ + from + " order by " + String.join(",", orders));
        var count = entityManager.createNativeQuery("select count(*) " + from);
        for (var target : List.of(query, count)) {
            target.setParameter("company", companyId);
            if (filter.contractId() != null) target.setParameter("contract", filter.contractId());
            if (filter.reference() != null) target.setParameter("reference", "%" + filter.reference().toLowerCase(Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
            if (filter.status() != null) target.setParameter("status", filter.status().name());
            if (filter.dueFrom() != null) target.setParameter("dueFrom", filter.dueFrom());
            if (filter.dueTo() != null) target.setParameter("dueTo", filter.dueTo());
        }
        long total = ((Number) count.getSingleResult()).longValue();
        List<?> rows = query.setFirstResult(Math.toIntExact(pageable.getOffset())).setMaxResults(pageable.getPageSize()).getResultList();
        List<BillingInstallmentSummary> items = rows.stream().map(value -> {
            Object[] r = (Object[]) value;
            return new BillingInstallmentSummary((UUID) r[0], (UUID) r[1], (String) r[2], date(r[3]), date(r[4]),
                    BillingInstallmentStatus.valueOf((String) r[5]), CurrencyCode.valueOf((String) r[6]), (BigDecimal) r[7],
                    instant(r[8]), instant(r[9]));
        }).toList();
        return new PageImpl<>(items, pageable, total);
    }

    private LocalDate date(Object value) {
        if (value == null || value instanceof LocalDate) return (LocalDate) value;
        return ((java.sql.Date) value).toLocalDate();
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        return ((java.sql.Timestamp) value).toInstant();
    }

    @Override
    public BillingInstallment save(BillingInstallment billingInstallment) {
        return toDomain(billingInstallmentJpaRepository.save(new BillingInstallmentJpaEntity(
                billingInstallment.getCompanyId(), billingInstallment.getContractId(), billingInstallment.getReference(),
                billingInstallment.getDescription(), billingInstallment.getIssuedOn(), billingInstallment.getDueOn(),
                billingInstallment.getStatus()
        )));
    }

    @Override
    public Optional<BillingInstallment> findByIdAndCompanyId(UUID id, UUID companyId) {
        return billingInstallmentJpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public BillingInstallment updateStatus(BillingInstallment installment) {
        BillingInstallmentJpaEntity entity = billingInstallmentJpaRepository
                .findByIdAndCompanyId(installment.getId(), installment.getCompanyId()).orElseThrow();
        entity.setStatus(installment.getStatus());
        return toDomain(billingInstallmentJpaRepository.save(entity));
    }

    private BillingInstallment toDomain(BillingInstallmentJpaEntity entity) {
        return BillingInstallment.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getContractId(), entity.getReference(), entity.getDescription(),
                entity.getIssuedOn(), entity.getDueOn(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
