package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.UUID;

/** Explicit, tenant-scoped criteria accepted by the measurement list. */
public record MeasurementFilter(
        UUID workId,
        UUID contractId,
        String reference,
        MeasurementStatus status,
        LocalDate measuredOnFrom,
        LocalDate measuredOnTo
) {
    public MeasurementFilter {
        if (measuredOnFrom != null && measuredOnTo != null && measuredOnFrom.isAfter(measuredOnTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_FILTER,
                    "measuredOnFrom must not be after measuredOnTo");
        }
    }

    public String normalizedReference() {
        return reference == null || reference.isBlank() ? null : reference.trim();
    }
}
