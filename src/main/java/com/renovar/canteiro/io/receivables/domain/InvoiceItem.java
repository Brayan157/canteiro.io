package com.renovar.canteiro.io.receivables.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Getter
public final class InvoiceItem {

    private final UUID id;
    private final UUID companyId;
    private final UUID invoiceId;
    private final UUID contractId;
    private final UUID billingAllocationId;
    private final UUID contractServiceId;
    private final String description;
    private final BigDecimal allocatedAmount;
    private final InvoiceItemStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private InvoiceItem(UUID id, UUID companyId, UUID invoiceId, UUID contractId, UUID billingAllocationId,
                        UUID contractServiceId, String description, BigDecimal allocatedAmount, InvoiceItemStatus status,
                        Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Invoice item company is required");
        this.invoiceId = require(invoiceId, "Invoice item invoice is required");
        this.contractId = require(contractId, "Invoice item contract is required");
        this.billingAllocationId = require(billingAllocationId, "Invoice item billing allocation is required");
        this.contractServiceId = contractServiceId;
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.allocatedAmount = nonZeroAmount(allocatedAmount);
        this.status = require(status, "Invoice item status is required");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static InvoiceItem create(UUID companyId, UUID invoiceId, UUID contractId, UUID billingAllocationId,
                                     UUID contractServiceId, String description, BigDecimal allocatedAmount) {
        return new InvoiceItem(null, companyId, invoiceId, contractId, billingAllocationId, contractServiceId,
                description, allocatedAmount, InvoiceItemStatus.REGISTERED, null, null);
    }

    public static InvoiceItem rehydrate(UUID id, UUID companyId, UUID invoiceId, UUID contractId,
                                        UUID billingAllocationId, UUID contractServiceId, String description,
                                        BigDecimal allocatedAmount, InvoiceItemStatus status, Instant createdAt, Instant updatedAt) {
        return new InvoiceItem(id, companyId, invoiceId, contractId, billingAllocationId, contractServiceId,
                description, allocatedAmount, status, createdAt, updatedAt);
    }

    public InvoiceItem cancel() {
        if (status == InvoiceItemStatus.CANCELLED) return this;
        return new InvoiceItem(id, companyId, invoiceId, contractId, billingAllocationId, contractServiceId,
                description, allocatedAmount, InvoiceItemStatus.CANCELLED, createdAt, updatedAt);
    }

    private static BigDecimal nonZeroAmount(BigDecimal value) {
        BigDecimal normalized = require(value, "Invoice item amount is required").setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() == 0) {
            throw new IllegalArgumentException("Invoice item amount must not be zero");
        }
        return normalized;
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
