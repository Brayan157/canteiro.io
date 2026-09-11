package com.renovar.canteiro.io.receivables.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class TaxObligation {

    private final UUID id;
    private final UUID companyId;
    private final UUID invoiceId;
    private final UUID contractId;
    private final UUID receivableId;
    private final String name;
    private final String description;
    private final LocalDate dueOn;
    private final BigDecimal amount;
    private final TaxTreatment treatment;
    private final TaxObligationStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private TaxObligation(UUID id, UUID companyId, UUID invoiceId, UUID contractId, UUID receivableId, String name,
                          String description, LocalDate dueOn, BigDecimal amount, TaxTreatment treatment,
                          TaxObligationStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Tax obligation company is required");
        this.invoiceId = require(invoiceId, "Tax obligation invoice is required");
        this.contractId = require(contractId, "Tax obligation contract is required");
        this.receivableId = receivableId;
        this.name = requiredText(name, "Tax obligation name is required");
        this.description = optionalText(description);
        this.dueOn = dueOn;
        this.amount = positiveAmount(amount);
        this.treatment = require(treatment, "Tax obligation treatment is required");
        validateTreatmentLinks();
        this.status = require(status, "Tax obligation status is required");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static TaxObligation register(UUID companyId, UUID invoiceId, UUID contractId, UUID receivableId,
                                         String name, String description, LocalDate dueOn, BigDecimal amount,
                                         TaxTreatment treatment) {
        return new TaxObligation(null, companyId, invoiceId, contractId, receivableId, name, description, dueOn, amount,
                treatment, TaxObligationStatus.REGISTERED, null, null);
    }

    public static TaxObligation rehydrate(UUID id, UUID companyId, UUID invoiceId, UUID contractId, UUID receivableId,
                                          String name, String description, LocalDate dueOn, BigDecimal amount,
                                          TaxTreatment treatment, TaxObligationStatus status, Instant createdAt,
                                          Instant updatedAt) {
        return new TaxObligation(id, companyId, invoiceId, contractId, receivableId, name, description, dueOn, amount,
                treatment, status, createdAt, updatedAt);
    }

    public TaxObligation cancel() {
        if (status == TaxObligationStatus.CANCELLED) return this;
        return new TaxObligation(id, companyId, invoiceId, contractId, receivableId, name, description, dueOn, amount,
                treatment, TaxObligationStatus.CANCELLED, createdAt, updatedAt);
    }

    private void validateTreatmentLinks() {
        if (treatment == TaxTreatment.PAYABLE_BY_COMPANY && (receivableId != null || dueOn == null)) {
            throw new IllegalArgumentException("Company-payable tax requires a due date and no receivable");
        }
        if (treatment == TaxTreatment.WITHHELD_BY_CUSTOMER && receivableId == null) {
            throw new IllegalArgumentException("Withheld tax requires the receivable it reduces");
        }
        if (treatment == TaxTreatment.INFORMATIONAL && receivableId != null) {
            throw new IllegalArgumentException("Informational tax must not reference a receivable");
        }
    }

    private static BigDecimal positiveAmount(BigDecimal value) {
        BigDecimal normalized = require(value, "Tax obligation amount is required").setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("Tax obligation amount must be positive");
        }
        return normalized;
    }

    private static String requiredText(String value, String message) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
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
