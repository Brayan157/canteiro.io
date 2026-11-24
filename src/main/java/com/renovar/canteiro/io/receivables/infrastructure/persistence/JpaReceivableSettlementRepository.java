package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementStatus;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementEntryType;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementFilter;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementHistory;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Repository
@RequiredArgsConstructor
public class JpaReceivableSettlementRepository implements ReceivableSettlementRepository {

    private final ReceivableSettlementJpaRepository receivableSettlementJpaRepository;
    @PersistenceContext private EntityManager entityManager;

    @Override
    public Page<ReceivableSettlementHistory> findHistory(UUID companyId, UUID receivableId, UUID contractId,
            ReceivableSettlementFilter filter, Pageable pageable) {
        StringBuilder from = new StringBuilder("""
                from receivable_settlement s
                join receivable r on r.id=s.receivable_id and r.company_id=s.company_id and r.contract_id=s.contract_id
                join contract c on c.id=r.contract_id and c.company_id=r.company_id
                join billing_installment b on b.id=r.billing_installment_id and b.company_id=r.company_id and b.contract_id=r.contract_id
                left join receivable_settlement original on original.id=s.reversal_of_settlement_id
                    and original.company_id=s.company_id and original.receivable_id=s.receivable_id
                    and original.contract_id=s.contract_id and original.entry_type='SETTLEMENT'
                left join receivable_settlement reversal on reversal.reversal_of_settlement_id=s.id
                    and reversal.company_id=s.company_id and reversal.receivable_id=s.receivable_id
                    and reversal.contract_id=s.contract_id and reversal.entry_type='REVERSAL'
                where s.company_id=:company and s.receivable_id=:receivable and s.contract_id=:contract
                    and (s.reversal_of_settlement_id is null or original.id is not null)
                """);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("company", companyId); parameters.put("receivable", receivableId); parameters.put("contract", contractId);
        if (filter.entryType() != null) { from.append(" and s.entry_type=:entryType"); parameters.put("entryType", filter.entryType().name()); }
        if (filter.status() != null) { from.append(" and s.status=:status"); parameters.put("status", filter.status().name()); }
        if (filter.paymentMethod() != null) { from.append(" and s.payment_method=:method"); parameters.put("method", filter.paymentMethod().name()); }
        if (filter.receivedFrom() != null) { from.append(" and s.received_on>=:dateFrom"); parameters.put("dateFrom", filter.receivedFrom()); }
        if (filter.receivedTo() != null) { from.append(" and s.received_on<=:dateTo"); parameters.put("dateTo", filter.receivedTo()); }
        List<String> orders = new ArrayList<>();
        pageable.getSort().forEach(order -> {
            String column = switch (order.getProperty()) {
                case "receivedOn" -> "s.received_on";
                case "createdAt" -> "s.created_at";
                case "entryType" -> "s.entry_type";
                case "paymentMethod" -> "s.payment_method";
                case "status" -> "s.status";
                default -> throw new IllegalArgumentException("Sort field is not allowed");
            };
            orders.add(column + (order.isAscending() ? " asc" : " desc") + " nulls last");
        });
        if (orders.isEmpty()) orders.add("s.created_at desc");
        orders.add("s.id asc");
        var query = entityManager.createNativeQuery("""
                select s.id,s.receivable_id,r.reference,s.contract_id,s.received_on,s.amount,s.entry_type,
                    original.id,reversal.id,s.payment_method,s.status,c.currency,s.created_at,s.updated_at
                """ + from + " order by " + String.join(",", orders));
        var count = entityManager.createNativeQuery("select count(*) " + from);
        parameters.forEach((key, value) -> { query.setParameter(key, value); count.setParameter(key, value); });
        long total = ((Number) count.getSingleResult()).longValue();
        List<?> rows = query.setFirstResult(Math.toIntExact(pageable.getOffset())).setMaxResults(pageable.getPageSize()).getResultList();
        return new PageImpl<>(rows.stream().map(value -> {
            Object[] r = (Object[]) value;
            return new ReceivableSettlementHistory((UUID) r[0], (UUID) r[1], (String) r[2], (UUID) r[3], date(r[4]), (BigDecimal) r[5],
                    ReceivableSettlementEntryType.valueOf((String) r[6]), (UUID) r[7], (UUID) r[8],
                    ReceivableSettlementPaymentMethod.valueOf((String) r[9]), ReceivableSettlementStatus.valueOf((String) r[10]),
                    CurrencyCode.valueOf((String) r[11]), instant(r[12]), instant(r[13]));
        }).toList(), pageable, total);
    }

    private LocalDate date(Object value) {
        return value instanceof LocalDate local ? local : ((java.sql.Date) value).toLocalDate();
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offset) return offset.toInstant();
        return ((java.sql.Timestamp) value).toInstant();
    }

    @Override
    public ReceivableSettlement save(ReceivableSettlement settlement) {
        return toDomain(receivableSettlementJpaRepository.saveAndFlush(new ReceivableSettlementJpaEntity(
                settlement.getCompanyId(), settlement.getReceivableId(), settlement.getContractId(), settlement.getReceivedOn(),
                settlement.getAmount(), settlement.getEntryType(), settlement.getReversalOfSettlementId(),
                settlement.getPaymentMethod(), settlement.getNote(), settlement.getStatus()
        )));
    }

    @Override
    public Map<UUID, BigDecimal> confirmedAmountsByContractIdsAndCompanyId(List<UUID> contractIds, UUID companyId) {
        if (contractIds.isEmpty()) return Map.of();
        List<?> rows = entityManager.createNativeQuery("""
                select s.contract_id, sum(s.amount)
                from receivable_settlement s
                join receivable r on r.id=s.receivable_id and r.company_id=s.company_id and r.contract_id=s.contract_id
                join billing_installment b on b.id=r.billing_installment_id and b.company_id=r.company_id and b.contract_id=r.contract_id
                join contract c on c.id=r.contract_id and c.company_id=r.company_id
                where s.company_id=:company and s.contract_id in (:contracts) and s.status='CONFIRMED'
                group by s.contract_id
                """).setParameter("company", companyId).setParameter("contracts", contractIds).getResultList();
        Map<UUID, BigDecimal> totals = new LinkedHashMap<>();
        for (Object value : rows) {
            Object[] row = (Object[]) value;
            totals.put((UUID) row[0], ((BigDecimal) row[1]).setScale(2));
        }
        return Map.copyOf(totals);
    }

    @Override
    public List<ReceivableSettlement> findByReceivableIdAndCompanyId(UUID receivableId, UUID companyId) {
        return receivableSettlementJpaRepository.findByReceivableIdAndCompanyId(receivableId, companyId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public BigDecimal confirmedAmountByReceivableIdAndCompanyId(UUID receivableId, UUID companyId) {
        return receivableSettlementJpaRepository.sumAmountByReceivableIdAndCompanyIdAndStatus(
                receivableId, companyId, ReceivableSettlementStatus.CONFIRMED
        );
    }

    @Override
    public Optional<ReceivableSettlement> findByIdAndCompanyId(UUID id, UUID companyId) {
        return receivableSettlementJpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public boolean existsReversalOfSettlementIdAndCompanyId(UUID settlementId, UUID companyId) {
        return receivableSettlementJpaRepository.existsByReversalOfSettlementIdAndCompanyId(settlementId, companyId);
    }

    private ReceivableSettlement toDomain(ReceivableSettlementJpaEntity entity) {
        return ReceivableSettlement.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getReceivableId(), entity.getContractId(), entity.getReceivedOn(),
                entity.getAmount(), entity.getEntryType(), entity.getReversalOfSettlementId(), entity.getPaymentMethod(),
                entity.getNote(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
