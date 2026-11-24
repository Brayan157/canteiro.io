package com.renovar.canteiro.io.receivables.infrastructure.reporting;

import com.renovar.canteiro.io.contracts.domain.ContractDiscount;
import com.renovar.canteiro.io.contracts.domain.ContractNetAmountCalculator;
import com.renovar.canteiro.io.contracts.domain.DiscountType;
import com.renovar.canteiro.io.receivables.domain.WorkBillingReportRepository;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.function.Consumer;

@Repository
@RequiredArgsConstructor
public class JdbcWorkBillingReportRepository implements WorkBillingReportRepository {
    private static final String POSITIONS = """
            WITH scoped AS (
                SELECT id, company_id, contract_identifier, reference, name, status, currency
                FROM contract WHERE company_id = ? AND work_id = ?
            ), services AS (
                SELECT s.contract_id, sum(s.net_amount + s.measurement_composition_amount) subtotal
                FROM contract_service s JOIN scoped c ON c.id = s.contract_id AND c.company_id = s.company_id
                WHERE s.status <> 'CANCELLED' GROUP BY s.contract_id
            ), invoices AS (
                SELECT i.contract_id, sum(i.total_amount) invoiced
                FROM invoice i JOIN scoped c ON c.id = i.contract_id AND c.company_id = i.company_id
                WHERE i.status = 'REGISTERED' GROUP BY i.contract_id
            ), adjustments AS (
                SELECT a.contract_id, sum(a.adjustment_amount) adjustment
                FROM measurement_contract_adjustment a
                JOIN scoped c ON c.id = a.contract_id AND c.company_id = a.company_id
                GROUP BY a.contract_id
            )
            SELECT c.*, coalesce(s.subtotal, 0) subtotal, coalesce(i.invoiced, 0) invoiced,
                   coalesce(a.adjustment, 0) adjustment, d.discount_type, d.discount_value
            FROM scoped c
            LEFT JOIN services s ON s.contract_id = c.id
            LEFT JOIN invoices i ON i.contract_id = c.id
            LEFT JOIN adjustments a ON a.contract_id = c.id
            LEFT JOIN contract_discount d ON d.contract_id = c.id AND d.company_id = c.company_id AND d.active
            ORDER BY c.id
            """;

    private final JdbcTemplate jdbc;

    @Override
    public void validateInvoiceOrigins(UUID companyId, UUID workId) {
        Boolean invalid = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM invoice i JOIN contract c ON c.id = i.contract_id AND c.company_id = i.company_id
                    WHERE c.company_id = ? AND c.work_id = ? AND i.status = 'REGISTERED' AND
                """ + JdbcContractBillingReportRepository.INVALID_INVOICE_ORIGINS + ")",
                Boolean.class, companyId, workId);
        JdbcContractBillingReportRepository.requireReconciled(Boolean.TRUE.equals(invalid));
    }

    @Override
    public void scan(UUID companyId, UUID workId, Consumer<ContractPosition> consumer) {
        jdbc.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(POSITIONS);
            statement.setObject(1, companyId);
            statement.setObject(2, workId);
            statement.setFetchSize(100);
            return statement;
        }, row -> {
            UUID id = row.getObject("id", UUID.class);
            String discountType = row.getString("discount_type");
            var discount = discountType == null ? null : ContractDiscount.create(companyId, id,
                    DiscountType.valueOf(discountType), row.getBigDecimal("discount_value"));
            var commercial = ContractNetAmountCalculator.calculateSubtotal(row.getBigDecimal("subtotal"),
                    discount, row.getBigDecimal("adjustment"));
            consumer.accept(new ContractPosition(id, row.getString("contract_identifier"), row.getString("reference"), row.getString("name"),
                    row.getString("status"), CurrencyCode.valueOf(row.getString("currency")),
                    commercial, row.getBigDecimal("invoiced").setScale(2)));
        });
    }
}
