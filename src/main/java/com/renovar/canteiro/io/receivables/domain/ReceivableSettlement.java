package com.renovar.canteiro.io.receivables.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class ReceivableSettlement {

    private final UUID id;
    private final UUID companyId;
    private final UUID receivableId;
    private final UUID contractId;
    private final LocalDate receivedOn;
    private final BigDecimal amount;
    private final ReceivableSettlementEntryType entryType;
    private final UUID reversalOfSettlementId;
    private final ReceivableSettlementPaymentMethod paymentMethod;
    private final String note;
    private final ReceivableSettlementStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ReceivableSettlement(UUID id, UUID companyId, UUID receivableId, UUID contractId, LocalDate receivedOn,
                                BigDecimal amount, ReceivableSettlementEntryType entryType, UUID reversalOfSettlementId,
                                ReceivableSettlementPaymentMethod paymentMethod, String note,
                                ReceivableSettlementStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Receivable settlement company is required");
        this.receivableId = require(receivableId, "Receivable settlement receivable is required");
        this.contractId = require(contractId, "Receivable settlement contract is required");
        this.receivedOn = require(receivedOn, "Receivable settlement received date is required");
        this.entryType = require(entryType, "Receivable settlement entry type is required");
        this.reversalOfSettlementId = reversalOfSettlementId;
        this.amount = validateAmount(amount);
        this.paymentMethod = require(paymentMethod, "Receivable settlement payment method is required");
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.status = require(status, "Receivable settlement status is required");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        validateReversalLink();
    }

    public static ReceivableSettlement confirm(UUID companyId, UUID receivableId, UUID contractId, LocalDate receivedOn,
                                               BigDecimal amount, ReceivableSettlementPaymentMethod paymentMethod,
                                               String note) {
        return new ReceivableSettlement(null, companyId, receivableId, contractId, receivedOn, amount,
                ReceivableSettlementEntryType.SETTLEMENT, null, paymentMethod,
                note, ReceivableSettlementStatus.CONFIRMED, null, null);
    }

    public static ReceivableSettlement reverse(ReceivableSettlement original, LocalDate reversedOn, String note) {
        require(original, "Original receivable settlement is required");
        if (original.entryType != ReceivableSettlementEntryType.SETTLEMENT
                || original.status != ReceivableSettlementStatus.CONFIRMED) {
            throw new IllegalStateException("Only a confirmed settlement can be reversed");
        }
        return new ReceivableSettlement(null, original.companyId, original.receivableId, original.contractId, reversedOn,
                original.amount.negate(), ReceivableSettlementEntryType.REVERSAL, original.id, original.paymentMethod,
                note, ReceivableSettlementStatus.CONFIRMED, null, null);
    }

    public static ReceivableSettlement rehydrate(UUID id, UUID companyId, UUID receivableId, UUID contractId,
                                                 LocalDate receivedOn, BigDecimal amount, ReceivableSettlementEntryType entryType,
                                                 UUID reversalOfSettlementId,
                                                 ReceivableSettlementPaymentMethod paymentMethod, String note,
                                                 ReceivableSettlementStatus status, Instant createdAt, Instant updatedAt) {
        return new ReceivableSettlement(id, companyId, receivableId, contractId, receivedOn, amount, entryType,
                reversalOfSettlementId, paymentMethod, note, status, createdAt, updatedAt);
    }

    private BigDecimal validateAmount(BigDecimal value) {
        BigDecimal normalized = require(value, "Receivable settlement amount is required").setScale(2, RoundingMode.HALF_UP);
        if ((entryType == ReceivableSettlementEntryType.SETTLEMENT && normalized.signum() <= 0)
                || (entryType == ReceivableSettlementEntryType.REVERSAL && normalized.signum() >= 0)) {
            throw new IllegalArgumentException("Receivable settlement amount has an invalid sign for its entry type");
        }
        return normalized;
    }

    private void validateReversalLink() {
        if ((entryType == ReceivableSettlementEntryType.SETTLEMENT && reversalOfSettlementId != null)
                || (entryType == ReceivableSettlementEntryType.REVERSAL && reversalOfSettlementId == null)) {
            throw new IllegalArgumentException("Receivable settlement reversal link is inconsistent");
        }
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
