package com.renovar.canteiro.io.employees.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;

import java.util.UUID;

public record EmployeeChangeResponse(EmployeeResponse employee, UUID changeRequestId, ChangeAuthorizationMode mode) {
}
