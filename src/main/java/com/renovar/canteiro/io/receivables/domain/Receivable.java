package com.renovar.canteiro.io.receivables.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class Receivable {

    private final UUID id;
    private final UUID companyId;
    private final UUID contractId;
    private final UUID billingInstallmentId;
    private final UUID financialGroupId;
    private final String reference;
    private final String description;
    private final LocalDate issuedOn;
    private final LocalDate dueOn;
    private final BigDecimal originalAmount;
    private final ReceivableStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Receivable(UUID id, UUID companyId, UUID contractId, UUID billingInstallmentId, UUID financialGroupId,
                       String reference, String description, LocalDate issuedOn, LocalDate dueOn,
                       BigDecimal originalAmount, ReceivableStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Receivable company is required");
        this.contractId = require(contractId, "Receivable contract is required");
        this.billingInstallmentId = require(billingInstallmentId, "Receivable billing installment is required");
        this.financialGroupId = require(financialGroupId, "Receivable financial group is required");
        this.reference = normalize(reference);
        this.description = normalize(description);
        this.issuedOn = issuedOn;
        this.dueOn = require(dueOn, "Receivable due date is required");
        if (issuedOn != null && dueOn.isBefore(issuedOn)) {
            throw new IllegalArgumentException("Receivable due date must not precede its issue date");
        }
        this.originalAmount = positiveAmount(originalAmount);
        this.status = require(status, "Receivable status is required");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Receivable create(UUID companyId, UUID contractId, UUID billingInstallmentId, UUID financialGroupId,
                                    String reference, String description, LocalDate issuedOn, LocalDate dueOn,
                                    BigDecimal originalAmount) {
        return new Receivable(null, companyId, contractId, billingInstallmentId, financialGroupId, reference,
                description, issuedOn, dueOn, originalAmount, ReceivableStatus.OPEN, null, null);
    }

    public static Receivable rehydrate(UUID id, UUID companyId, UUID contractId, UUID billingInstallmentId,
                                       UUID financialGroupId, String reference, String description, LocalDate issuedOn,
                                       LocalDate dueOn, BigDecimal originalAmount, ReceivableStatus status,
                                       Instant createdAt, Instant updatedAt) {
        return new Receivable(id, companyId, contractId, billingInstallmentId, financialGroupId, reference,
                description, issuedOn, dueOn, originalAmount, status, createdAt, updatedAt);
    }

    public Receivable withStatus(ReceivableStatus updatedStatus) {
        return new Receivable(id, companyId, contractId, billingInstallmentId, financialGroupId, reference, description,
                issuedOn, dueOn, originalAmount, updatedStatus, createdAt, updatedAt);
    }

    public Receivable cancel() {
        if (status == ReceivableStatus.CANCELLED) throw new IllegalStateException("Receivable is already cancelled");
        return withStatus(ReceivableStatus.CANCELLED);
    }

    private static BigDecimal positiveAmount(BigDecimal value) {
        BigDecimal normalized = require(value, "Receivable original amount is required").setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("Receivable original amount must be positive");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
