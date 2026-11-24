package com.renovar.canteiro.io.receivables.domain;

import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Registration-time facts, never reconstructed from a fiscal issue date. */
public interface InvoiceFinancialSnapshotRepository {
    void insert(UUID company, UUID contract, UUID invoice, CurrencyCode currency, Instant observedAt,
            BigDecimal contractNet, BigDecimal received);
}
