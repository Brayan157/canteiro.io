package com.renovar.canteiro.io.measurements.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable, idempotent financial origin for a measurement service applied to a contract service. */
@Getter
public final class MeasurementContractComposition {
    private final UUID id;
    private final UUID companyId;
    private final UUID measurementId;
    private final UUID measurementVersionId;
    private final UUID measurementServiceId;
    private final UUID contractId;
    private final UUID contractServiceId;
    private final MeasurementCompositionOriginType originType;
    private final BigDecimal grossAmount;
    private final BigDecimal netAmount;
    private final BigDecimal totalWeightKg;
    private final int itemCount;
    private final List<UUID> measurementItemIds;
    private final String justification;
    private final UUID createdByUserId;
    private final Instant createdAt;

    private MeasurementContractComposition(UUID id, UUID companyId, UUID measurementId, UUID measurementVersionId,
                                            UUID measurementServiceId, UUID contractId, UUID contractServiceId,
                                            MeasurementCompositionOriginType originType, BigDecimal grossAmount,
                                            BigDecimal netAmount, BigDecimal totalWeightKg, int itemCount,
                                            List<UUID> measurementItemIds, String justification, UUID createdByUserId,
                                            Instant createdAt) {
        this.id = id;
        this.companyId = required(companyId, "Measurement composition company is required");
        this.measurementId = required(measurementId, "Measurement composition measurement is required");
        this.measurementVersionId = required(measurementVersionId, "Measurement composition version is required");
        this.measurementServiceId = required(measurementServiceId, "Measurement composition service group is required");
        this.contractId = required(contractId, "Measurement composition contract is required");
        this.contractServiceId = required(contractServiceId, "Measurement composition contract service is required");
        this.originType = required(originType, "Measurement composition origin type is required");
        this.grossAmount = money(grossAmount, "Measurement composition gross amount is required");
        this.netAmount = money(netAmount, "Measurement composition net amount is required");
        this.totalWeightKg = totalWeightKg == null ? null : totalWeightKg.setScale(4, RoundingMode.HALF_UP);
        if (itemCount < 1) throw new IllegalArgumentException("Measurement composition must contain items");
        this.itemCount = itemCount;
        this.measurementItemIds = List.copyOf(measurementItemIds == null ? List.of() : measurementItemIds);
        if (this.measurementItemIds.size() != itemCount) throw new IllegalArgumentException("Measurement composition items are inconsistent");
        this.justification = justification == null || justification.isBlank() ? null : justification.trim();
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
    }

    public static MeasurementContractComposition create(UUID companyId, UUID measurementId, UUID versionId, UUID serviceId,
                                                          UUID contractId, UUID contractServiceId, MeasurementCompositionOriginType originType,
                                                          BigDecimal grossAmount, BigDecimal netAmount, BigDecimal totalWeightKg,
                                                          List<UUID> itemIds, String justification, UUID createdByUserId) {
        return new MeasurementContractComposition(null, companyId, measurementId, versionId, serviceId, contractId,
                contractServiceId, originType, grossAmount, netAmount, totalWeightKg, itemIds.size(), itemIds,
                justification, createdByUserId, null);
    }

    public static MeasurementContractComposition rehydrate(UUID id, UUID companyId, UUID measurementId, UUID versionId,
                                                            UUID serviceId, UUID contractId, UUID contractServiceId,
                                                            MeasurementCompositionOriginType originType, BigDecimal grossAmount,
                                                            BigDecimal netAmount, BigDecimal totalWeightKg, int itemCount,
                                                            List<UUID> itemIds, String justification, UUID createdByUserId,
                                                            Instant createdAt) {
        return new MeasurementContractComposition(id, companyId, measurementId, versionId, serviceId, contractId,
                contractServiceId, originType, grossAmount, netAmount, totalWeightKg, itemCount, itemIds,
                justification, createdByUserId, createdAt);
    }

    private static BigDecimal money(BigDecimal value, String message) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(message);
        return value.setScale(2, RoundingMode.HALF_UP);
    }
    private static <T> T required(T value, String message) { if (value == null) throw new IllegalArgumentException(message); return value; }
}
