package com.renovar.canteiro.io.measurements.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** One active item in the final order chosen for a draft measurement version. */
public record ReorderMeasurementItemRequest(
        @NotNull UUID itemId,
        @NotBlank @Size(max = 255) String serviceTitle,
        UUID continuousServiceId
) {
}
