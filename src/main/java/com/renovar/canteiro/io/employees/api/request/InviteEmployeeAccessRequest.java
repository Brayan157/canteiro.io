package com.renovar.canteiro.io.employees.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteEmployeeAccessRequest(@NotBlank @Email @Size(max = 255) String email) {
}
