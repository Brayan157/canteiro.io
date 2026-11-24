package com.renovar.canteiro.io.reporting.infrastructure;

import com.renovar.canteiro.io.reporting.domain.WorkManagerialResultRepository;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcWorkManagerialResultRepository implements WorkManagerialResultRepository {
    private static final String TOTALS = """
            SELECT contract_id, service_id, kind, sum(amount) AS amount
            FROM facts GROUP BY contract_id, service_id, kind
            """;
    private final JdbcClient jdbc;

    @Override
    public boolean containsTarget(UUID company, UUID work, UUID contract, UUID service) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM contract c
                    WHERE c.company_id = :company AND c.work_id = :work AND c.id = :contract
                      AND (CAST(:service AS UUID) IS NULL OR EXISTS (
                          SELECT 1 FROM contract_service s
                          WHERE s.company_id = c.company_id AND s.contract_id = c.id AND s.id = :service))
                )
                """).param("company", company).param("work", work).param("contract", contract)
                .param("service", service, Types.OTHER).query(Boolean.class).single();
    }

    @Override
    public List<Fact> projected(UUID company, UUID work, CurrencyCode currency, LocalDate from, LocalDate to) {
        return totals("""
                WITH facts AS (
                    SELECT a.contract_id, a.contract_service_id AS service_id, 'BILLED' AS kind, a.allocated_amount AS amount
                    FROM contract c
                    JOIN billing_installment i ON i.company_id = c.company_id AND i.contract_id = c.id
                    JOIN billing_allocation a ON a.company_id = i.company_id AND a.contract_id = i.contract_id
                        AND a.billing_installment_id = i.id
                    WHERE c.company_id = :company AND c.work_id = :work AND c.currency = :currency
                        AND a.status = 'ACTIVE' AND i.status NOT IN ('DRAFT', 'CANCELLED')
                        AND (i.created_at AT TIME ZONE 'UTC')::date BETWEEN :from AND :to
                    UNION ALL
                    SELECT p.contract_id, NULL::uuid,
                        CASE WHEN p.status = 'RENEGOTIATED' THEN 'UNCERTAIN_EXPENSE' ELSE 'WORK_EXPENSE' END,
                        p.original_amount
                    FROM payable p
                    WHERE p.company_id = :company AND p.work_id = :work AND p.financial_scope = 'WORK'
                        AND p.currency = :currency AND p.tax_obligation_id IS NULL
                        AND p.status NOT IN ('DRAFT', 'CANCELLED')
                        AND (p.created_at AT TIME ZONE 'UTC')::date BETWEEN :from AND :to
                    UNION ALL
                    SELECT t.contract_id, NULL::uuid, 'TAX', t.amount
                    FROM tax_obligation t JOIN contract c ON c.company_id = t.company_id AND c.id = t.contract_id
                    WHERE t.company_id = :company AND c.work_id = :work AND c.currency = :currency
                        AND t.status = 'REGISTERED' AND t.treatment = 'PAYABLE_BY_COMPANY'
                        AND (t.created_at AT TIME ZONE 'UTC')::date BETWEEN :from AND :to
                )
                """, company, work, currency, from, to);
    }

    @Override
    public List<Fact> recordedCash(UUID company, UUID work, CurrencyCode currency, LocalDate from, LocalDate to) {
        return totals("""
                WITH receipts AS (
                    SELECT s.id, s.company_id, s.contract_id, s.amount
                    FROM receivable_settlement s
                    JOIN receivable r ON r.id = s.receivable_id AND r.company_id = s.company_id AND r.contract_id = s.contract_id
                    JOIN billing_installment b ON b.id = r.billing_installment_id
                        AND b.company_id = r.company_id AND b.contract_id = r.contract_id
                    JOIN contract c ON c.company_id = r.company_id AND c.id = r.contract_id
                    WHERE s.company_id = :company AND c.work_id = :work AND c.currency = :currency
                        AND s.status = 'CONFIRMED' AND s.received_on BETWEEN :from AND :to
                ), exact_lines AS (
                    SELECT r.id AS settlement_id, r.contract_id, a.contract_service_id AS service_id, l.amount,
                        sum(l.amount) OVER (PARTITION BY r.id) = r.amount
                        AND bool_and(a.contract_service_id IS NOT NULL AND sign(l.amount) = sign(r.amount))
                            OVER (PARTITION BY r.id) AS fully_attributed
                    FROM receipts r
                    JOIN invoice_settlement_line l ON l.company_id = r.company_id AND l.contract_id = r.contract_id
                        AND l.settlement_id = r.id
                    JOIN billing_allocation a ON a.company_id = r.company_id AND a.contract_id = r.contract_id
                        AND a.id = l.billing_allocation_id
                ), facts AS (
                    SELECT contract_id, service_id, 'RECEIVED' AS kind, amount
                    FROM exact_lines WHERE fully_attributed
                    UNION ALL
                    SELECT r.contract_id, NULL::uuid, 'RECEIVED', r.amount
                    FROM receipts r WHERE NOT EXISTS (
                        SELECT 1 FROM exact_lines l WHERE l.settlement_id = r.id AND l.fully_attributed)
                    UNION ALL
                    SELECT p.contract_id, NULL::uuid, 'PAID', s.amount
                    FROM payable_settlement s
                    JOIN payable p ON p.id = s.payable_id AND p.company_id = s.company_id
                    WHERE s.company_id = :company AND p.work_id = :work AND p.financial_scope = 'WORK'
                        AND p.currency = :currency AND s.effective_on BETWEEN :from AND :to
                )
                """, company, work, currency, from, to);
    }

    private List<Fact> totals(String query, UUID company, UUID work, CurrencyCode currency, LocalDate from, LocalDate to) {
        return jdbc.sql(query + TOTALS).param("company", company).param("work", work).param("currency", currency.name())
                .param("from", from).param("to", to).query((row, index) -> new Fact(row.getObject("contract_id", UUID.class),
                        row.getObject("service_id", UUID.class), Kind.valueOf(row.getString("kind")),
                        row.getBigDecimal("amount"))).list();
    }
}
