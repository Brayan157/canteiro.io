package com.renovar.canteiro.io.receivables.domain;

import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class BillingInstallment {

    private final UUID id;
    private final UUID companyId;
    private final UUID contractId;
    private final String reference;
    private final String description;
    private final LocalDate issuedOn;
    private final LocalDate dueOn;
    private final BillingInstallmentStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private BillingInstallment(UUID id, UUID companyId, UUID contractId, String reference, String description,
                               LocalDate issuedOn, LocalDate dueOn, BillingInstallmentStatus status,
                               Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Billing installment company is required");
        this.contractId = require(contractId, "Billing installment contract is required");
        this.reference = normalize(reference);
        this.description = normalize(description);
        this.issuedOn = issuedOn;
        this.dueOn = require(dueOn, "Billing installment due date is required");
        if (issuedOn != null && dueOn.isBefore(issuedOn)) {
            throw new IllegalArgumentException("Billing installment due date must not precede its issue date");
        }
        this.status = require(status, "Billing installment status is required");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static BillingInstallment create(UUID companyId, UUID contractId, String reference, String description,
                                            LocalDate issuedOn, LocalDate dueOn) {
        return new BillingInstallment(null, companyId, contractId, reference, description, issuedOn, dueOn,
                BillingInstallmentStatus.DRAFT, null, null);
    }

    public static BillingInstallment createApproved(UUID companyId, UUID contractId, String reference,
                                                    String description, LocalDate issuedOn, LocalDate dueOn) {
        return new BillingInstallment(null, companyId, contractId, reference, description, issuedOn, dueOn,
                BillingInstallmentStatus.OPEN, null, null);
    }

    public static BillingInstallment rehydrate(UUID id, UUID companyId, UUID contractId, String reference,
                                               String description, LocalDate issuedOn, LocalDate dueOn,
                                               BillingInstallmentStatus status, Instant createdAt, Instant updatedAt) {
        return new BillingInstallment(id, companyId, contractId, reference, description, issuedOn, dueOn, status,
                createdAt, updatedAt);
    }

    public BillingInstallment cancel() {
        if (status == BillingInstallmentStatus.CANCELLED) throw new IllegalStateException("Billing installment is already cancelled");
        if (status == BillingInstallmentStatus.RECEIVED || status == BillingInstallmentStatus.PARTIALLY_RECEIVED) {
            throw new IllegalStateException("A received billing installment cannot be cancelled before reversing its settlements");
        }
        return new BillingInstallment(id, companyId, contractId, reference, description, issuedOn, dueOn,
                BillingInstallmentStatus.CANCELLED, createdAt, updatedAt);
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
