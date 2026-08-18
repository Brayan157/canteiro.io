package com.renovar.canteiro.io.works.api.response;

import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkStatus;

import java.time.LocalDate;
import java.util.UUID;

public record WorkResponse(
        UUID id,
        UUID finalCustomerId,
        String name,
        String reference,
        WorkExecutionLocationType executionLocationType,
        String executionAddress,
        WorkStatus status,
        LocalDate startedOn,
        LocalDate expectedCompletionOn,
        LocalDate completedOn
) {
}
