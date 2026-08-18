package com.renovar.canteiro.io.employees.application;

import java.util.UUID;

public record EmployeeAccessInvitationResult(UUID employeeId, UUID userId, String email) {
}
