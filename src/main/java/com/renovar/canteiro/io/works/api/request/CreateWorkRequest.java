package com.renovar.canteiro.io.works.api.request;

import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateWorkRequest(
        @NotNull UUID finalCustomerId,
        @NotBlank String name,
        String reference,
        @NotNull WorkExecutionLocationType executionLocationType,
        String executionAddress,
        @NotNull WorkStatus status,
        LocalDate startedOn,
        LocalDate expectedCompletionOn,
        LocalDate completedOn,
        String justification
) {
}
