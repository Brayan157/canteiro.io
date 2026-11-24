package com.renovar.canteiro.io.workforce.domain;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeEmployment(UUID id, UUID employeeId, LocalDate startedOn, LocalDate endedOn, long version) {
    public boolean covers(LocalDate date) {
        return !date.isBefore(startedOn) && (endedOn == null || !date.isAfter(endedOn));
    }

    public boolean covers(LocalDate from, LocalDate through) {
        return covers(from) && (through == null ? endedOn == null : covers(through));
    }
}
