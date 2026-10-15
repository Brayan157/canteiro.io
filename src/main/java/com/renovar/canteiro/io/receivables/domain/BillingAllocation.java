package com.renovar.canteiro.io.receivables.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Getter
public final class BillingAllocation {

    private final UUID id;
    private final UUID companyId;
    private final UUID billingInstallmentId;
    private final UUID contractId;
    private final UUID contractServiceId;
    private final UUID measurementContractCompositionId;
    private final BillingAllocationType type;
    private final BigDecimal allocatedAmount;
    private final BillingAllocationStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private BillingAllocation(UUID id, UUID companyId, UUID billingInstallmentId, UUID contractId,
                              UUID contractServiceId, UUID measurementContractCompositionId, BillingAllocationType type, BigDecimal allocatedAmount,
                              BillingAllocationStatus status,
                              Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Billing allocation company is required");
        this.billingInstallmentId = require(billingInstallmentId, "Billing allocation installment is required");
        this.contractId = require(contractId, "Billing allocation contract is required");
        this.type = require(type, "Billing allocation type is required");
        this.contractServiceId = validateService(type, contractServiceId);
        this.measurementContractCompositionId = validateComposition(type, measurementContractCompositionId);
        this.allocatedAmount = validateAmount(type, allocatedAmount);
        this.status = require(status, "Billing allocation status is required");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static BillingAllocation create(UUID companyId, UUID billingInstallmentId, UUID contractId,
                                           UUID contractServiceId, BigDecimal allocatedAmount) {
        return createService(companyId, billingInstallmentId, contractId, contractServiceId, allocatedAmount);
    }

    public static BillingAllocation createService(UUID companyId, UUID billingInstallmentId, UUID contractId,
                                                  UUID contractServiceId, BigDecimal allocatedAmount) {
        return createService(companyId, billingInstallmentId, contractId, contractServiceId, null, allocatedAmount);
    }

    public static BillingAllocation createService(UUID companyId, UUID billingInstallmentId, UUID contractId,
                                                  UUID contractServiceId, UUID measurementContractCompositionId,
                                                  BigDecimal allocatedAmount) {
        return new BillingAllocation(null, companyId, billingInstallmentId, contractId, contractServiceId,
                measurementContractCompositionId, BillingAllocationType.SERVICE, allocatedAmount, BillingAllocationStatus.DRAFT, null, null);
    }

    public static BillingAllocation createContractDiscount(UUID companyId, UUID billingInstallmentId, UUID contractId,
                                                           BigDecimal allocatedAmount) {
        return new BillingAllocation(null, companyId, billingInstallmentId, contractId, null,
                null, BillingAllocationType.CONTRACT_DISCOUNT, allocatedAmount, BillingAllocationStatus.DRAFT, null, null);
    }

    public static BillingAllocation rehydrate(UUID id, UUID companyId, UUID billingInstallmentId, UUID contractId,
                                              UUID contractServiceId, UUID measurementContractCompositionId, BillingAllocationType type, BigDecimal allocatedAmount,
                                              BillingAllocationStatus status, Instant createdAt, Instant updatedAt) {
        return new BillingAllocation(id, companyId, billingInstallmentId, contractId, contractServiceId, measurementContractCompositionId, type,
                allocatedAmount, status, createdAt, updatedAt);
    }

    /** Compatibility overload for allocations persisted before measurement composition tracing. */
    public static BillingAllocation rehydrate(UUID id, UUID companyId, UUID billingInstallmentId, UUID contractId,
                                              UUID contractServiceId, BillingAllocationType type, BigDecimal allocatedAmount,
                                              BillingAllocationStatus status, Instant createdAt, Instant updatedAt) {
        return rehydrate(id, companyId, billingInstallmentId, contractId, contractServiceId, null, type,
                allocatedAmount, status, createdAt, updatedAt);
    }

    public BillingAllocation activate() {
        if (status == BillingAllocationStatus.ACTIVE) {
            return this;
        }
        if (status != BillingAllocationStatus.DRAFT) {
            throw new IllegalStateException("Only draft billing allocations can be activated");
        }
        return new BillingAllocation(id, companyId, billingInstallmentId, contractId, contractServiceId, measurementContractCompositionId, type,
                allocatedAmount, BillingAllocationStatus.ACTIVE, createdAt, updatedAt);
    }

    public BillingAllocation cancel() {
        if (status == BillingAllocationStatus.CANCELLED) return this;
        return new BillingAllocation(id, companyId, billingInstallmentId, contractId, contractServiceId, measurementContractCompositionId, type,
                allocatedAmount, BillingAllocationStatus.CANCELLED, createdAt, updatedAt);
    }

    private static UUID validateService(BillingAllocationType type, UUID contractServiceId) {
        if (type == BillingAllocationType.SERVICE) {
            return require(contractServiceId, "Service billing allocation requires a contract service");
        }
        if (contractServiceId != null) {
            throw new IllegalArgumentException("Contract discount billing allocation must not reference a contract service");
        }
        return null;
    }

    private static BigDecimal validateAmount(BillingAllocationType type, BigDecimal value) {
        BigDecimal normalized = require(value, "Billing allocation amount is required").setScale(2, RoundingMode.HALF_UP);
        if (type == BillingAllocationType.SERVICE && normalized.signum() <= 0) {
            throw new IllegalArgumentException("Service billing allocation amount must be positive");
        }
        if (type == BillingAllocationType.CONTRACT_DISCOUNT && normalized.signum() >= 0) {
            throw new IllegalArgumentException("Contract discount billing allocation amount must be negative");
        }
        return normalized;
    }

    private static UUID validateComposition(BillingAllocationType type, UUID measurementContractCompositionId) {
        if (type == BillingAllocationType.CONTRACT_DISCOUNT && measurementContractCompositionId != null) {
            throw new IllegalArgumentException("Contract discount billing allocation must not reference a measurement composition");
        }
        return measurementContractCompositionId;
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
