package com.renovar.canteiro.io.payables.infrastructure;

import com.renovar.canteiro.io.payables.domain.PayablePaymentMethod;
import com.renovar.canteiro.io.payables.domain.PayableSettlement;
import com.renovar.canteiro.io.payables.domain.PayableSettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcPayableSettlementRepository implements PayableSettlementRepository {
    private static final String TITLE_SCOPE = " from payable_settlement where company_id = ? and payable_id = ?";
    private static final RowMapper<PayableSettlement> SETTLEMENT = (rs, row) -> new PayableSettlement(
            rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("payable_id", UUID.class),
            rs.getObject("operation_id", UUID.class), rs.getObject("reversal_of_id", UUID.class), rs.getBigDecimal("amount"),
            rs.getObject("effective_on", LocalDate.class), PayablePaymentMethod.valueOf(rs.getString("payment_method")),
            rs.getObject("recorded_by", UUID.class), rs.getObject("recorded_at", OffsetDateTime.class).toInstant(),
            rs.getString("justification"));

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    @Override
    public void lockOperation(UUID companyId, UUID operationId) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { },
                "PAYABLE_SETTLEMENT:" + companyId + ":" + operationId);
    }

    @Override
    public Optional<Operation> findOperation(UUID companyId, UUID operationId) {
        return jdbc.query("""
                select payable_id, fingerprint, change_request_id from payable_settlement_operation
                where company_id = ? and operation_id = ?
                """, (rs, row) -> new Operation(rs.getObject("payable_id", UUID.class), rs.getString("fingerprint"),
                rs.getObject("change_request_id", UUID.class)), companyId, operationId).stream().findFirst();
    }

    @Override
    public void saveOperation(UUID companyId, UUID operationId, Operation operation) {
        jdbc.update("""
                insert into payable_settlement_operation(company_id, operation_id, payable_id, fingerprint, change_request_id)
                values (?, ?, ?, ?, ?)
                """, companyId, operationId, operation.payableId(), operation.fingerprint(), operation.changeRequestId());
    }

    @Override
    public Optional<PayableSettlement> find(UUID companyId, UUID payableId, UUID settlementId) {
        return jdbc.query("select *" + TITLE_SCOPE + " and id = ?", SETTLEMENT, companyId, payableId, settlementId)
                .stream().findFirst();
    }

    @Override
    public Optional<PayableSettlement> findApplied(UUID companyId, UUID payableId, UUID operationId) {
        return jdbc.query("select *" + TITLE_SCOPE + " and operation_id = ?", SETTLEMENT, companyId, payableId, operationId)
                .stream().findFirst();
    }

    @Override
    public boolean hasReversal(UUID companyId, UUID payableId, UUID settlementId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1" + TITLE_SCOPE + " and reversal_of_id = ?)",
                Boolean.class, companyId, payableId, settlementId));
    }

    @Override
    public Map<UUID, BigDecimal> netPaid(UUID companyId, List<UUID> payableIds) {
        if (payableIds.isEmpty()) return Map.of();
        Map<UUID, BigDecimal> totals = new HashMap<>();
        namedJdbc.query("""
                select payable_id, sum(amount) as paid from payable_settlement
                where company_id = :companyId and payable_id in (:ids) group by payable_id
                """, Map.of("companyId", companyId, "ids", payableIds),
                rs -> { totals.put(rs.getObject("payable_id", UUID.class), rs.getBigDecimal("paid")); });
        return totals;
    }

    @Override
    public Page<PayableSettlement> history(UUID companyId, UUID payableId, Pageable page) {
        var rows = jdbc.query("select *" + TITLE_SCOPE
                        + " order by effective_on desc, recorded_at desc, id desc limit ? offset ?",
                SETTLEMENT, companyId, payableId, page.getPageSize(), page.getOffset());
        long count = jdbc.queryForObject("select count(*)" + TITLE_SCOPE, Long.class, companyId, payableId);
        return new PageImpl<>(rows, page, count);
    }

    @Override
    public void save(PayableSettlement row) {
        jdbc.update("""
                insert into payable_settlement(id, company_id, payable_id, operation_id, reversal_of_id, amount,
                    effective_on, payment_method, recorded_by, recorded_at, justification) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, row.id(), row.companyId(), row.payableId(), row.operationId(), row.reversalOfId(), row.amount(),
                row.effectiveOn(), row.paymentMethod().name(), row.recordedBy(), row.recordedAt().atOffset(ZoneOffset.UTC), row.justification());
    }
}
