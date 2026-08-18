package com.renovar.canteiro.io.employees.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEmployeeRequest(
        @NotBlank @Size(max = 255) String fullName,
        @Size(max = 150) String jobTitle,
        @Size(max = 40) String phone,
        @Size(max = 1000) String justification
) {
}
