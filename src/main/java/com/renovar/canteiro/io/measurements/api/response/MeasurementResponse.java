package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MeasurementResponse(
        UUID id,
        UUID workId,
        UUID contractId,
        String title,
        String reference,
        String description,
        LocalDate measuredOn,
        MeasurementStatus status,
        int lockVersion,
        Instant createdAt,
        Instant updatedAt,
        CurrencyCode currency
) {
    public static MeasurementResponse from(Measurement measurement) {
        return measurement == null ? null : new MeasurementResponse(measurement.getId(), measurement.getWorkId(),
                measurement.getContractId(), measurement.getTitle(), measurement.getReference(), measurement.getDescription(),
                measurement.getMeasuredOn(), measurement.getStatus(), measurement.getLockVersion(),
                measurement.getCreatedAt(), measurement.getUpdatedAt(), measurement.getCurrency());
    }
}
