package com.renovar.canteiro.io.receivables.domain;

import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivableSettlementHistory(UUID id, UUID receivableId, String receivableReference, UUID contractId,
        LocalDate receivedOn, BigDecimal amount, ReceivableSettlementEntryType entryType,
        UUID reversalOfSettlementId, UUID reversedBySettlementId,
        ReceivableSettlementPaymentMethod paymentMethod, ReceivableSettlementStatus status,
        CurrencyCode currency, Instant createdAt, Instant updatedAt) { }
