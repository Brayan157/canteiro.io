package com.renovar.canteiro.io.works.api.request;

import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateWorkRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 100) String reference,
        @NotNull WorkExecutionLocationType executionLocationType,
        @Size(max = 500) String executionAddress,
        @NotNull WorkStatus status,
        LocalDate startedOn,
        LocalDate expectedCompletionOn,
        LocalDate completedOn,
        @Size(max = 1000) String justification
) {
}
