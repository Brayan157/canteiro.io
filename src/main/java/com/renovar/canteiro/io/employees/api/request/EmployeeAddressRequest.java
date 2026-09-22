package com.renovar.canteiro.io.employees.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record EmployeeAddressRequest(
        @Size(max = 100) String label, @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 255) String street, @Size(max = 40) String number,
        @Size(max = 255) String complement, @Size(max = 150) String district,
        @NotBlank @Size(max = 150) String city, @Size(max = 100) String state,
        @NotBlank @Size(max = 100) String country,
        @Schema(description = "Marks this address as the employee's primary address") boolean primaryAddress
) { }
