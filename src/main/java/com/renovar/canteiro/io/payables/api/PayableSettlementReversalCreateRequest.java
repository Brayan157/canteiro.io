package com.renovar.canteiro.io.payables.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record PayableSettlementReversalCreateRequest(@NotNull UUID operationId,
        @NotNull @Schema(description = "Effective reversal date, from the original payment date through today UTC.") LocalDate reversedOn,
        @NotBlank @Size(max = 1000) String justification) { }
