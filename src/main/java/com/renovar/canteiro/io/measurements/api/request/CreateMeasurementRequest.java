package com.renovar.canteiro.io.measurements.api.request;

import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateMeasurementRequest(
        UUID workId,
        UUID contractId,
        @NotBlank @Size(min = 1, max = 200) String title,
        @Size(max = 100) String reference,
        @Size(max = 1000) String description,
        LocalDate measuredOn,
        @Size(max = 2000) String justification,
        @Schema(description = "Moeda ISO imutável. Padrão BRL; medição vinculada deriva a moeda do contrato quando omitida. Sem conversão.", example = "BRL") CurrencyCode currency
) {
}
