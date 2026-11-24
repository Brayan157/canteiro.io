package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableFilter;
import com.renovar.canteiro.io.receivables.domain.ReceivableReadProjection;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.receivables.domain.ReceivableBalance;
import com.renovar.canteiro.io.receivables.domain.ReceivableFinancialPosition;
import com.renovar.canteiro.io.receivables.domain.ReceivableFinancialView;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Repository
@RequiredArgsConstructor
public class JpaReceivableRepository implements ReceivableRepository {

    private final ReceivableJpaRepository receivableJpaRepository;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    private static final String READ_FROM = """
            from receivable r
            join billing_installment b on b.id=r.billing_installment_id and b.company_id=r.company_id and b.contract_id=r.contract_id
            join contract c on c.id=r.contract_id and c.company_id=r.company_id
            join obra w on w.id=c.work_id and w.company_id=c.company_id
            join final_customer f on f.id=w.final_customer_id and f.company_id=w.company_id
            left join receivable src on src.id=r.renegotiation_source_receivable_id and src.company_id=r.company_id
                and src.contract_id=r.contract_id and src.billing_installment_id=r.billing_installment_id
            cross join lateral (
                select coalesce(sum(s.amount),0) amount from receivable_settlement s
                where s.company_id=r.company_id and s.receivable_id=r.id and s.contract_id=r.contract_id and s.status='CONFIRMED'
            ) cash
            cross join lateral (
                select coalesce(sum(t.amount),0) amount from tax_obligation t
                where t.company_id=r.company_id and t.receivable_id=r.id and t.contract_id=r.contract_id
                    and t.treatment='WITHHELD_BY_CUSTOMER' and t.status='REGISTERED'
            ) withholding
            cross join lateral (
                select coalesce(sum(case when cr.reversal_of_credit_id is null then cr.amount else -cr.amount end),0) amount
                from receivable_credit cr where cr.company_id=r.company_id and cr.receivable_id=r.id and cr.contract_id=r.contract_id
            ) credit
            where r.company_id=:company and (r.renegotiation_source_receivable_id is null or src.id is not null)
            """;
    private static final String READ_COLUMNS = """
            select r.id,r.reference,r.description,c.id,c.name,c.reference,c.contract_identifier,
                w.id,w.name,w.reference,f.id,f.name,b.id,b.reference,r.financial_group_id,src.id,src.reference,
                r.issued_on,r.due_on,r.status,c.currency,r.original_amount,r.created_at,r.updated_at,
                withholding.amount,cash.amount,credit.amount
            """;
    private static final String OUTSTANDING = "(r.original_amount-withholding.amount-credit.amount-cash.amount)";

    @Override
    public void scanCurrentDueBalances(UUID companyId, UUID workId, LocalDate dueThrough,
                                       java.util.function.Consumer<DueBalance> consumer) {
        String sql = """
                select r.id, c.currency, r.due_on, r.original_amount, withholding.amount, cash.amount, credit.amount, r.status
                """ + READ_FROM + " and r.due_on<=:dueThrough and r.status not in ('CANCELLED','RENEGOTIATED')"
                + (workId == null ? "" : " and c.work_id=:work");
        var query = entityManager.createNativeQuery(sql).unwrap(org.hibernate.query.NativeQuery.class)
                .setFetchSize(200).setReadOnly(true).setParameter("company", companyId)
                .setParameter("dueThrough", dueThrough);
        if (workId != null) query.setParameter("work", workId);
        try (var rows = query.scroll(org.hibernate.ScrollMode.FORWARD_ONLY)) {
            while (rows.next()) {
                Object[] row = (Object[]) rows.get();
                BigDecimal outstanding = null;
                try {
                    var balance = new ReceivableBalance((UUID) row[0], (BigDecimal) row[3], (BigDecimal) row[4],
                            (BigDecimal) row[5], (BigDecimal) row[6]);
                    var status = ReceivableStatus.valueOf((String) row[7]);
                    if ((status != ReceivableStatus.RECEIVED || balance.outstandingAmount().signum() == 0)
                            && (status != ReceivableStatus.PARTIALLY_RECEIVED || balance.confirmedSettlementAmount().signum() > 0)) {
                        outstanding = balance.outstandingAmount();
                    }
                } catch (IllegalArgumentException ignored) {
                    // An inconsistent legacy title cannot contribute a proven residual.
                }
                consumer.accept(new DueBalance(CurrencyCode.valueOf((String) row[1]), date(row[2]), outstanding));
            }
        }
    }

    @Override
    public Page<ReceivableReadProjection> findByCompanyId(UUID companyId, ReceivableFilter filter, Pageable pageable) {
        StringBuilder from = new StringBuilder(READ_FROM);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("company", companyId);
        if (filter.financialView() != ReceivableFinancialView.ALL) {
            from.append(" and r.status not in ('CANCELLED','RENEGOTIATED')");
            from.append(filter.financialView() == ReceivableFinancialView.OUTSTANDING
                    ? " and " + OUTSTANDING + ">0" : " and " + OUTSTANDING + "=0 and cash.amount>0");
        }
        exact(from, parameters, "contract", "c.id", filter.contractId());
        exact(from, parameters, "work", "w.id", filter.workId());
        exact(from, parameters, "customer", "f.id", filter.finalCustomerId());
        exact(from, parameters, "status", "r.status", filter.status() == null ? null : filter.status().name());
        exact(from, parameters, "currency", "c.currency", filter.currency() == null ? null : filter.currency().name());
        contains(from, parameters, "reference", filter.reference(), "r.reference");
        contains(from, parameters, "contractSearch", filter.contractSearch(), "c.name", "c.reference", "c.contract_identifier");
        contains(from, parameters, "workSearch", filter.workSearch(), "w.name", "w.reference");
        contains(from, parameters, "customerName", filter.customerName(), "f.name");
        contains(from, parameters, "billingReference", filter.billingReference(), "b.reference");
        contains(from, parameters, "search", filter.search(), "r.reference", "b.reference", "c.name", "c.reference",
                "c.contract_identifier", "w.name", "w.reference", "f.name");
        if (filter.dueFrom() != null) { from.append(" and r.due_on>=:dueFrom"); parameters.put("dueFrom", filter.dueFrom()); }
        if (filter.dueTo() != null) { from.append(" and r.due_on<=:dueTo"); parameters.put("dueTo", filter.dueTo()); }
        List<String> orders = new ArrayList<>();
        pageable.getSort().forEach(order -> {
            String column = switch (order.getProperty()) {
                case "dueOn" -> "r.due_on";
                case "issuedOn" -> "r.issued_on";
                case "reference" -> "r.reference";
                case "status" -> "r.status";
                case "createdAt" -> "r.created_at";
                case "contractName" -> "c.name";
                case "workName" -> "w.name";
                case "finalCustomerName" -> "f.name";
                default -> throw new IllegalArgumentException("Sort field is not allowed");
            };
            orders.add(column + (order.isAscending() ? " asc" : " desc") + " nulls last");
        });
        if (orders.isEmpty()) orders.add("r.due_on asc");
        orders.add("r.id asc");
        var query = entityManager.createNativeQuery(READ_COLUMNS + from + " order by " + String.join(",", orders));
        var count = entityManager.createNativeQuery("select count(*) " + from);
        parameters.forEach((key, value) -> { query.setParameter(key, value); count.setParameter(key, value); });
        long total = ((Number) count.getSingleResult()).longValue();
        List<?> rows = query.setFirstResult(Math.toIntExact(pageable.getOffset())).setMaxResults(pageable.getPageSize()).getResultList();
        return new PageImpl<>(rows.stream().map(value -> readRow((Object[]) value)).toList(), pageable, total);
    }

    @Override
    public Optional<ReceivableReadProjection> findReadByIdAndCompanyId(UUID id, UUID companyId) {
        List<?> rows = entityManager.createNativeQuery(READ_COLUMNS + READ_FROM + " and r.id=:id")
                .setParameter("company", companyId).setParameter("id", id).getResultList();
        return rows.stream().findFirst().map(value -> readRow((Object[]) value));
    }

    @Override
    public Map<UUID, ReceivableFinancialPosition> findOriginalPositionsByBillingInstallmentIdsAndCompanyId(
            List<UUID> installmentIds, UUID companyId) {
        if (installmentIds.isEmpty()) return Map.of();
        List<?> rows = entityManager.createNativeQuery(READ_COLUMNS + READ_FROM
                        + " and r.billing_installment_id in (:installments) and r.renegotiation_source_receivable_id is null")
                .setParameter("company", companyId).setParameter("installments", installmentIds).getResultList();
        return rows.stream().map(value -> readRow((Object[]) value)).collect(java.util.stream.Collectors.toMap(
                ReceivableReadProjection::billingInstallmentId, ReceivableReadProjection::financialPosition));
    }

    private void exact(StringBuilder sql, Map<String, Object> parameters, String key, String column, Object value) {
        if (value == null) return;
        sql.append(" and ").append(column).append("=:").append(key);
        parameters.put(key, value);
    }

    private void contains(StringBuilder sql, Map<String, Object> parameters, String key, String value, String... columns) {
        if (value == null) return;
        sql.append(" and (");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) sql.append(" or ");
            sql.append("lower(").append(columns[i]).append(") like :").append(key).append(" escape '!'");
        }
        sql.append(")");
        parameters.put(key, "%" + value.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
    }

    private ReceivableReadProjection readRow(Object[] r) {
        ReceivableStatus status = ReceivableStatus.valueOf((String) r[19]);
        ReceivableBalance balance = new ReceivableBalance((UUID) r[0], (BigDecimal) r[21], (BigDecimal) r[24],
                (BigDecimal) r[25], (BigDecimal) r[26]);
        return new ReceivableReadProjection((UUID) r[0], (String) r[1], (String) r[2], (UUID) r[3], (String) r[4],
                (String) r[5], (String) r[6], (UUID) r[7], (String) r[8], (String) r[9], (UUID) r[10], (String) r[11],
                (UUID) r[12], (String) r[13], (UUID) r[14], (UUID) r[15], (String) r[16], date(r[17]), date(r[18]),
                status, CurrencyCode.valueOf((String) r[20]), (BigDecimal) r[21], instant(r[22]), instant(r[23]),
                ReceivableFinancialPosition.from(status, balance));
    }

    private LocalDate date(Object value) {
        if (value == null) return null;
        return value instanceof LocalDate local ? local : ((java.sql.Date) value).toLocalDate();
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offset) return offset.toInstant();
        return ((java.sql.Timestamp) value).toInstant();
    }

    @Override
    public Receivable save(Receivable receivable) {
        return toDomain(receivableJpaRepository.save(new ReceivableJpaEntity(
                receivable.getCompanyId(), receivable.getContractId(), receivable.getBillingInstallmentId(),
                receivable.getFinancialGroupId(), receivable.getReference(), receivable.getDescription(),
                receivable.getIssuedOn(), receivable.getDueOn(), receivable.getOriginalAmount(), receivable.getStatus()
        )));
    }

    @Override
    public Optional<Receivable> findByIdAndCompanyId(UUID id, UUID companyId) {
        return receivableJpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public Optional<Receivable> findByIdAndCompanyIdWithSettlementLock(UUID id, UUID companyId) {
        return receivableJpaRepository.findByIdAndCompanyIdWithSettlementLock(id, companyId).map(entity->{
            entityManager.refresh(entity,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            return toDomain(entity);
        });
    }

    @Override
    public Optional<Receivable> findByIdAndCompanyIdWithCancellationCheckLock(UUID id, UUID companyId) {
        return receivableJpaRepository.findByIdAndCompanyIdWithCancellationCheckLock(id, companyId).map(entity -> {
            entityManager.refresh(entity, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            return toDomain(entity);
        });
    }

    @Override
    public Receivable updateStatus(Receivable receivable) {
        ReceivableJpaEntity entity = receivableJpaRepository.findByIdAndCompanyId(receivable.getId(), receivable.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Receivable was not found in its company"));
        entity.setStatus(receivable.getStatus());
        return toDomain(receivableJpaRepository.save(entity));
    }

    @Override
    public Optional<Receivable> findByBillingInstallmentIdAndCompanyId(UUID billingInstallmentId, UUID companyId) {
        return receivableJpaRepository.findByBillingInstallmentIdAndCompanyIdAndRenegotiationSourceReceivableIdIsNull(billingInstallmentId, companyId)
                .map(this::toDomain);
    }

    @Override public Receivable saveRenegotiated(Receivable r,UUID sourceId) {
        ReceivableJpaEntity e=new ReceivableJpaEntity(r.getCompanyId(),r.getContractId(),r.getBillingInstallmentId(),r.getFinancialGroupId(),
            r.getReference(),r.getDescription(),r.getIssuedOn(),r.getDueOn(),r.getOriginalAmount(),r.getStatus());
        e.linkRenegotiationOrigin(sourceId);
        return toDomain(receivableJpaRepository.saveAndFlush(e));
    }
    @Override public java.util.List<Receivable> findAllByBillingInstallmentIdAndCompanyId(UUID installment,UUID company) {
        return receivableJpaRepository.findAllByBillingInstallmentIdAndCompanyId(installment,company).stream().map(this::toDomain).toList();
    }
    @Override public java.util.List<Receivable> findRenegotiatedChildren(UUID source,UUID company) {
        return receivableJpaRepository.findByRenegotiationSourceReceivableIdAndCompanyId(source,company).stream().map(this::toDomain).toList();
    }

    private Receivable toDomain(ReceivableJpaEntity entity) {
        return Receivable.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getContractId(), entity.getBillingInstallmentId(),
                entity.getFinancialGroupId(), entity.getReference(), entity.getDescription(), entity.getIssuedOn(),
                entity.getDueOn(), entity.getOriginalAmount(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
