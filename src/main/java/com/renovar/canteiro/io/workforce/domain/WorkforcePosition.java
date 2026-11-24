package com.renovar.canteiro.io.workforce.domain;

import java.util.UUID;

public record WorkforcePosition(UUID employeeId, UUID employmentId, UUID assignmentId, boolean assignedToOtherWork) {
    public boolean isOccasional() {
        return assignmentId == null;
    }
}
