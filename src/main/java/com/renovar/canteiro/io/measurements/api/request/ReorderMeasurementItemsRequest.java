package com.renovar.canteiro.io.measurements.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Contains every active item of a draft version exactly once, in final order. */
public record ReorderMeasurementItemsRequest(
        @NotNull List<@Valid ReorderMeasurementItemRequest> items,
        @Size(max = 2000) String justification
) {
}
