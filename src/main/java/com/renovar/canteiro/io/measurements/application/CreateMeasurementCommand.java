package com.renovar.canteiro.io.measurements.application;

import java.time.LocalDate;
import java.util.UUID;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;

public record CreateMeasurementCommand(
        UUID workId,
        UUID contractId,
        String title,
        String reference,
        String description,
        LocalDate measuredOn,
        String justification,
        CurrencyCode currency
) {
    public CreateMeasurementCommand(UUID workId, UUID contractId, String title, String reference, String description,
                                    LocalDate measuredOn, String justification) {
        this(workId, contractId, title, reference, description, measuredOn, justification, null);
    }

    public CreateMeasurementCommand withCurrency(CurrencyCode resolved) {
        return new CreateMeasurementCommand(workId, contractId, title, reference, description, measuredOn, justification, resolved);
    }
}
