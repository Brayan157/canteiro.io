package com.renovar.canteiro.io.employees.api.response;

import java.util.UUID;

public record EmployeeAccessInvitationResponse(UUID employeeId, UUID userId, String email) {
}
