package com.renovar.canteiro.io.measurements.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * The auditable header discount of a fully converted measurement version.
 * It is deliberately not a ContractService and does not alter service prices.
 */
@Getter
public final class MeasurementContractAdjustment {

    private final UUID id;
    private final UUID companyId;
    private final UUID measurementVersionId;
    private final UUID contractId;
    private final BigDecimal amount;
    private final Instant createdAt;

    private MeasurementContractAdjustment(UUID id, UUID companyId, UUID measurementVersionId, UUID contractId,
                                          BigDecimal amount, Instant createdAt) {
        this.id = id;
        this.companyId = require(companyId, "Measurement adjustment company is required");
        this.measurementVersionId = require(measurementVersionId, "Measurement adjustment version is required");
        this.contractId = require(contractId, "Measurement adjustment contract is required");
        this.amount = require(amount, "Measurement adjustment amount is required").setScale(2, RoundingMode.HALF_UP);
        if (this.amount.signum() <= 0) {
            throw new IllegalArgumentException("Measurement adjustment amount must be positive");
        }
        this.createdAt = createdAt;
    }

    public static MeasurementContractAdjustment create(UUID companyId, UUID measurementVersionId, UUID contractId,
                                                        BigDecimal amount) {
        return new MeasurementContractAdjustment(null, companyId, measurementVersionId, contractId, amount, null);
    }

    public static MeasurementContractAdjustment rehydrate(UUID id, UUID companyId, UUID measurementVersionId,
                                                           UUID contractId, BigDecimal amount, Instant createdAt) {
        return new MeasurementContractAdjustment(id, companyId, measurementVersionId, contractId, amount, createdAt);
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
