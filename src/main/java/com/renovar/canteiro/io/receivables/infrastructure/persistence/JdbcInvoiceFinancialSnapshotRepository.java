package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.InvoiceFinancialSnapshotRepository;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
public class JdbcInvoiceFinancialSnapshotRepository implements InvoiceFinancialSnapshotRepository {
    @PersistenceContext private EntityManager entityManager;

    @Override public void insert(UUID company, UUID contract, UUID invoice, CurrencyCode currency,
            Instant observedAt, BigDecimal contractNet, BigDecimal received) {
        entityManager.flush();
        entityManager.createNativeQuery("""
                insert into invoice_financial_snapshot(invoice_id,company_id,contract_id,currency,observed_at,
                    basis,contract_net_amount,confirmed_received_amount,unreceived_cash_amount)
                values (:invoice,:company,:contract,:currency,:observed,
                    'CONTRACT_NET_MINUS_CONFIRMED_CASH_V1',:net,:received,:unreceived)
                """).setParameter("invoice", invoice).setParameter("company", company)
                .setParameter("contract", contract).setParameter("currency", currency.name())
                .setParameter("observed", OffsetDateTime.ofInstant(observedAt, ZoneOffset.UTC))
                .setParameter("net", contractNet).setParameter("received", received)
                .setParameter("unreceived", contractNet.subtract(received)).executeUpdate();
    }
}
