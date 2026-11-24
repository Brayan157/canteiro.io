package com.renovar.canteiro.io.workforce.domain;

import java.time.LocalDate;
import java.util.UUID;

public record WorkAssignment(UUID id, UUID workId, UUID employeeId, UUID employmentId,
                             LocalDate startedOn, LocalDate endedOn, long version) {
}
