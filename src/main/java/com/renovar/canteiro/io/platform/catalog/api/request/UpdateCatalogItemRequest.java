package com.renovar.canteiro.io.platform.catalog.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCatalogItemRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description
) {
}
