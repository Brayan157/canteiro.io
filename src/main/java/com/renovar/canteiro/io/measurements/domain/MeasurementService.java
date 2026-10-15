package com.renovar.canteiro.io.measurements.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** A stable service group in one frozen measurement version. */
@Getter
public final class MeasurementService {
    private final UUID id;
    private final UUID companyId;
    private final UUID measurementVersionId;
    private final String serviceTitle;
    private final int sortOrder;
    private final Instant createdAt;

    private MeasurementService(UUID id, UUID companyId, UUID measurementVersionId, String serviceTitle, int sortOrder,
                               Instant createdAt) {
        this.id = id;
        this.companyId = require(companyId, "Measurement service company is required");
        this.measurementVersionId = require(measurementVersionId, "Measurement service version is required");
        this.serviceTitle = requireTitle(serviceTitle);
        if (sortOrder < 1) throw new IllegalArgumentException("Measurement service sort order must be positive");
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    public static MeasurementService create(UUID companyId, UUID measurementVersionId, String serviceTitle, int sortOrder) {
        return new MeasurementService(null, companyId, measurementVersionId, serviceTitle, sortOrder, null);
    }

    public static MeasurementService rehydrate(UUID id, UUID companyId, UUID measurementVersionId, String serviceTitle,
                                               int sortOrder, Instant createdAt) {
        return new MeasurementService(id, companyId, measurementVersionId, serviceTitle, sortOrder, createdAt);
    }

    private static String requireTitle(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Measurement service title is required");
        return value.trim();
    }

    private static <T> T require(T value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }
}
