package com.renovar.canteiro.io.dto.request.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyCreateRequest (
    @NotBlank(message = "Corporate name is required")
            @Size(max = 255, message = "Corporate name must be less than 255 characters")
    String corporateName,

    @Size(max = 255, message = "Trade name must be less than 255 characters")
    String tradeName,

    @NotBlank(message = "Document is required")
            @Size(max = 20, message = "Document must be less than 20 characters")
    String document,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,

    @Size(max = 20, message = "Phone must be less than 20 characters")
    String phone,

    @Size (max = 255, message = "Address must be less than 255 characters")
    String address,
    @Size(max = 255, message = "Logo must be less than 255 characters")
    String logo
){}
