package com.renovar.canteiro.io.measurements.application;

import java.util.List;
import java.util.UUID;

public record ReorderMeasurementItemsCommand(
        UUID measurementId,
        UUID measurementVersionId,
        List<ReorderedMeasurementItem> items,
        String justification
) {
    public record ReorderedMeasurementItem(UUID itemId, String serviceTitle, UUID continuousServiceId) {
        public ReorderedMeasurementItem(UUID itemId,String serviceTitle){this(itemId,serviceTitle,null);}
    }
}
