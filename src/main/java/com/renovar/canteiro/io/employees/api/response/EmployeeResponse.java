package com.renovar.canteiro.io.employees.api.response;

import java.util.UUID;

public record EmployeeResponse(UUID id, String fullName, String jobTitle, String phone, boolean active, UUID userId) {
}
