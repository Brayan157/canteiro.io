package com.renovar.canteiro.io.contracts.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Getter
public final class ContractRevision {

    private final UUID id;
    private final UUID companyId;
    private final UUID contractId;
    private final int revisionNumber;
    private final BigDecimal previousNetAmount;
    private final BigDecimal proposedNetAmount;
    private final BigDecimal approvedBilledAmount;
    private final String reason;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ContractRevision(UUID id, UUID companyId, UUID contractId, int revisionNumber,
                             BigDecimal previousNetAmount, BigDecimal proposedNetAmount,
                             BigDecimal approvedBilledAmount, String reason, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Contract revision company is required");
        this.contractId = require(contractId, "Contract revision contract is required");
        if (revisionNumber < 1) {
            throw new IllegalArgumentException("Contract revision number must be positive");
        }
        this.revisionNumber = revisionNumber;
        this.previousNetAmount = money(previousNetAmount, "Previous contract net amount is required");
        this.proposedNetAmount = money(proposedNetAmount, "Proposed contract net amount is required");
        this.approvedBilledAmount = money(approvedBilledAmount, "Approved billed amount is required");
        if (this.proposedNetAmount.compareTo(this.approvedBilledAmount) < 0) {
            throw new IllegalArgumentException("Contract net amount cannot be lower than approved billed amount");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Contract revision reason is required");
        }
        this.reason = reason.trim();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ContractRevision create(UUID companyId, UUID contractId, int revisionNumber,
                                          BigDecimal previousNetAmount, BigDecimal proposedNetAmount,
                                          BigDecimal approvedBilledAmount, String reason) {
        return new ContractRevision(null, companyId, contractId, revisionNumber, previousNetAmount, proposedNetAmount,
                approvedBilledAmount, reason, null, null);
    }

    public static ContractRevision rehydrate(UUID id, UUID companyId, UUID contractId, int revisionNumber,
                                             BigDecimal previousNetAmount, BigDecimal proposedNetAmount,
                                             BigDecimal approvedBilledAmount, String reason, Instant createdAt,
                                             Instant updatedAt) {
        return new ContractRevision(id, companyId, contractId, revisionNumber, previousNetAmount, proposedNetAmount,
                approvedBilledAmount, reason, createdAt, updatedAt);
    }

    private static BigDecimal money(BigDecimal value, String message) {
        BigDecimal normalized = require(value, message).setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException(message);
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
