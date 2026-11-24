package com.renovar.canteiro.io.contracts.api.request;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateContractStatusRequest(@NotNull ContractStatus status,
        @Schema(description = "Optional business date. Omitted/null preserves the date current at application, including approval; never invented automatically.") LocalDate completedOn,
        @Size(max = 1000) String justification) { }
