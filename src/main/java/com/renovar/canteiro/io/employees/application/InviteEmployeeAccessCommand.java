package com.renovar.canteiro.io.employees.application;

import java.util.UUID;

public record InviteEmployeeAccessCommand(UUID employeeId, String email) {
}
