package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscount;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementServiceSummary;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmounts;

import java.util.List;

/** Immutable, tenant-scoped view of one specific measurement version. */
public record MeasurementVersionDetails(
        Measurement measurement,
        MeasurementVersion version,
        MeasurementVersion correctionRequestSource,
        List<MeasurementItem> items,
        MeasurementDiscount discount,
        MeasurementVersionAmounts amounts,
        List<MeasurementServiceSummary> services
) {
}
