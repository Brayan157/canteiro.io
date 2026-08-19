package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MeasurementResponse(
        UUID id,
        UUID workId,
        UUID contractId,
        String reference,
        String description,
        LocalDate measuredOn,
        MeasurementStatus status,
        int lockVersion,
        Instant createdAt,
        Instant updatedAt
) {
    public static MeasurementResponse from(Measurement measurement) {
        return measurement == null ? null : new MeasurementResponse(measurement.getId(), measurement.getWorkId(),
                measurement.getContractId(), measurement.getReference(), measurement.getDescription(),
                measurement.getMeasuredOn(), measurement.getStatus(), measurement.getLockVersion(),
                measurement.getCreatedAt(), measurement.getUpdatedAt());
    }
}
