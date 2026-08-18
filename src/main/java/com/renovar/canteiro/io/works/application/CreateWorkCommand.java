package com.renovar.canteiro.io.works.application;

import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkStatus;

import java.time.LocalDate;
import java.util.UUID;

public record CreateWorkCommand(
        UUID finalCustomerId,
        String name,
        String reference,
        WorkExecutionLocationType executionLocationType,
        String executionAddress,
        WorkStatus status,
        LocalDate startedOn,
        LocalDate expectedCompletionOn,
        LocalDate completedOn,
        String justification
) {
}
