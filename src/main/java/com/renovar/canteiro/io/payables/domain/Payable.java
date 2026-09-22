package com.renovar.canteiro.io.payables.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class Payable {

    private final UUID id;
    private final UUID companyId;
    private final PayableFinancialScope financialScope;
    private final UUID workId;
    private final UUID contractId;
    private final UUID taxObligationId;
    private final String reference;
    private final String description;
    private final LocalDate dueOn;
    private final BigDecimal originalAmount;
    private final PayableStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Payable(UUID id, UUID companyId, PayableFinancialScope financialScope, UUID workId, UUID contractId,
                    UUID taxObligationId, String reference, String description, LocalDate dueOn, BigDecimal originalAmount,
                    PayableStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Payable company is required");
        this.financialScope = require(financialScope, "Payable financial scope is required");
        this.workId = require(workId, "Work-scoped payable requires a work");
        this.contractId = contractId;
        this.taxObligationId = taxObligationId;
        this.reference = optionalText(reference);
        this.description = optionalText(description);
        this.dueOn = require(dueOn, "Payable due date is required");
        this.originalAmount = positiveAmount(originalAmount);
        this.status = require(status, "Payable status is required");
        if (financialScope != PayableFinancialScope.WORK) {
            throw new IllegalArgumentException("Tax payable must have WORK financial scope");
        }
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Payable createTaxPayable(UUID companyId, UUID workId, UUID contractId, UUID taxObligationId,
                                           String reference, String description, LocalDate dueOn, BigDecimal originalAmount) {
        return new Payable(null, companyId, PayableFinancialScope.WORK, workId, contractId, taxObligationId, reference,
                description, dueOn, originalAmount, PayableStatus.OPEN, null, null);
    }

    public static Payable rehydrate(UUID id, UUID companyId, PayableFinancialScope financialScope, UUID workId,
                                    UUID contractId, UUID taxObligationId, String reference, String description,
                                    LocalDate dueOn, BigDecimal originalAmount, PayableStatus status, Instant createdAt,
                                    Instant updatedAt) {
        return new Payable(id, companyId, financialScope, workId, contractId, taxObligationId, reference, description,
                dueOn, originalAmount, status, createdAt, updatedAt);
    }

    public Payable cancel() {
        if (status == PayableStatus.PAID || status == PayableStatus.PARTIALLY_PAID) {
            throw new IllegalStateException("Tax payable must have its payments reversed before cancellation");
        }
        if (status == PayableStatus.CANCELLED) return this;
        return new Payable(id, companyId, financialScope, workId, contractId, taxObligationId, reference, description,
                dueOn, originalAmount, PayableStatus.CANCELLED, createdAt, updatedAt);
    }

    private static BigDecimal positiveAmount(BigDecimal value) {
        BigDecimal normalized = require(value, "Payable original amount is required").setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("Payable original amount must be positive");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
