package com.renovar.canteiro.io.measurements.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Inserts a new item immediately after an existing active draft item. */
public record InsertMeasurementItemRequest(
        @NotNull UUID insertAfterItemId,
        @NotNull @Valid MeasurementItemDataRequest item
) {
}
