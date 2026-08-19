package com.renovar.canteiro.io.measurements.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class MeasurementItemContractServiceConversion {

    private final UUID id;
    private final UUID companyId;
    private final UUID measurementVersionId;
    private final UUID measurementItemId;
    private final UUID contractId;
    private final UUID contractServiceId;
    private final Instant createdAt;

    private MeasurementItemContractServiceConversion(UUID id, UUID companyId, UUID measurementVersionId,
                                                     UUID measurementItemId, UUID contractId, UUID contractServiceId,
                                                     Instant createdAt) {
        this.id = id;
        this.companyId = require(companyId, "Measurement item conversion company is required");
        this.measurementVersionId = require(measurementVersionId, "Measurement item conversion version is required");
        this.measurementItemId = require(measurementItemId, "Measurement item conversion item is required");
        this.contractId = require(contractId, "Measurement item conversion contract is required");
        this.contractServiceId = require(contractServiceId, "Measurement item conversion contract service is required");
        this.createdAt = createdAt;
    }

    public static MeasurementItemContractServiceConversion create(UUID companyId, UUID measurementVersionId,
                                                                   UUID measurementItemId, UUID contractId,
                                                                   UUID contractServiceId) {
        return new MeasurementItemContractServiceConversion(null, companyId, measurementVersionId, measurementItemId,
                contractId, contractServiceId, null);
    }

    public static MeasurementItemContractServiceConversion rehydrate(UUID id, UUID companyId, UUID measurementVersionId,
                                                                      UUID measurementItemId, UUID contractId,
                                                                      UUID contractServiceId, Instant createdAt) {
        return new MeasurementItemContractServiceConversion(id, companyId, measurementVersionId, measurementItemId,
                contractId, contractServiceId, createdAt);
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
