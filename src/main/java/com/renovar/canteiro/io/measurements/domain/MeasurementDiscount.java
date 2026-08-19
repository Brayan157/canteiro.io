package com.renovar.canteiro.io.measurements.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Getter
public final class MeasurementDiscount {

    private final UUID id;
    private final UUID companyId;
    private final UUID measurementVersionId;
    private final MeasurementDiscountType discountType;
    private final BigDecimal discountValue;
    private final Instant createdAt;
    private final Instant updatedAt;

    private MeasurementDiscount(UUID id, UUID companyId, UUID measurementVersionId, MeasurementDiscountType discountType,
                                BigDecimal discountValue, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Measurement discount company is required");
        this.measurementVersionId = require(measurementVersionId, "Measurement discount version is required");
        this.discountType = require(discountType, "Measurement discount type is required");
        this.discountValue = requireDiscountValue(discountType, discountValue);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static MeasurementDiscount create(UUID companyId, UUID measurementVersionId,
                                             MeasurementDiscountType discountType, BigDecimal discountValue) {
        return new MeasurementDiscount(null, companyId, measurementVersionId, discountType, discountValue, null, null);
    }

    public static MeasurementDiscount rehydrate(UUID id, UUID companyId, UUID measurementVersionId,
                                                MeasurementDiscountType discountType, BigDecimal discountValue,
                                                Instant createdAt, Instant updatedAt) {
        return new MeasurementDiscount(id, companyId, measurementVersionId, discountType, discountValue, createdAt, updatedAt);
    }

    private static BigDecimal requireDiscountValue(MeasurementDiscountType type, BigDecimal value) {
        int scale = type == MeasurementDiscountType.FIXED ? 2 : 4;
        BigDecimal normalized = require(value, "Measurement discount value is required").setScale(scale, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("Measurement discount value must be positive");
        }
        if (type == MeasurementDiscountType.PERCENTAGE && normalized.compareTo(new BigDecimal("100.0000")) > 0) {
            throw new IllegalArgumentException("Measurement percentage discount must not exceed 100");
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
