package com.renovar.canteiro.io.reporting.infrastructure;

import com.renovar.canteiro.io.payables.domain.PayableFinancialScope;
import com.renovar.canteiro.io.reporting.domain.RealizedCashRepository;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcRealizedCashRepository implements RealizedCashRepository {
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<MovementTotal> receipts(UUID companyId, UUID workId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                select c.currency, 'WORK' as scope, sum(s.amount) as amount, count(*) as entries,
                    min(s.received_on) as first_effective_on
                from receivable_settlement s
                join receivable r on r.id = s.receivable_id and r.company_id = s.company_id and r.contract_id = s.contract_id
                join billing_installment b on b.id = r.billing_installment_id
                    and b.company_id = r.company_id and b.contract_id = r.contract_id
                join contract c on c.id = r.contract_id and c.company_id = r.company_id
                where s.company_id = :companyId and s.status = 'CONFIRMED'
                    and (cast(:from as date) is null or s.received_on >= :from) and s.received_on <= :to
                    and (cast(:workId as uuid) is null or c.work_id = :workId)
                group by c.currency
                """, params(companyId, workId, from, to), this::total);
    }

    @Override
    public List<MovementTotal> payments(UUID companyId, UUID workId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                select p.currency, p.financial_scope as scope, sum(s.amount) as amount, count(*) as entries,
                    min(s.effective_on) as first_effective_on
                from payable_settlement s
                join payable p on p.id = s.payable_id and p.company_id = s.company_id
                where s.company_id = :companyId
                    and (cast(:from as date) is null or s.effective_on >= :from) and s.effective_on <= :to
                    and (cast(:workId as uuid) is null or (p.financial_scope = 'WORK' and p.work_id = :workId))
                group by p.currency, p.financial_scope
                """, params(companyId, workId, from, to), this::total);
    }

    @Override
    public List<CategoryMovementTotal> paymentsByCategory(UUID companyId, UUID workId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                select p.currency, p.financial_scope as scope, sum(s.amount) as amount, count(*) as entries,
                    c.id as category_id, c.code, c.name, c.active, min(s.effective_on) as first_effective_on
                from payable_settlement s
                join payable p on p.id = s.payable_id and p.company_id = s.company_id
                left join financial_category c on c.id = p.financial_category_id and c.company_id = p.company_id
                where s.company_id = :companyId and s.effective_on between :from and :to
                    and (cast(:workId as uuid) is null or (p.financial_scope = 'WORK' and p.work_id = :workId))
                group by p.currency, p.financial_scope, c.id, c.code, c.name, c.active
                """, params(companyId, workId, from, to), (row, index) -> new CategoryMovementTotal(
                new Category(row.getObject("category_id", UUID.class), row.getString("code"),
                        row.getString("name"), row.getObject("active", Boolean.class)), total(row, index)));
    }

    private MovementTotal total(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        return new MovementTotal(CurrencyCode.valueOf(row.getString("currency")),
                PayableFinancialScope.valueOf(row.getString("scope")), row.getBigDecimal("amount"),
                row.getLong("entries"), row.getObject("first_effective_on", LocalDate.class));
    }

    private MapSqlParameterSource params(UUID companyId, UUID workId, LocalDate from, LocalDate to) {
        return new MapSqlParameterSource("companyId", companyId).addValue("workId", workId, Types.OTHER, "uuid")
                .addValue("from", from, Types.DATE).addValue("to", to, Types.DATE);
    }
}
