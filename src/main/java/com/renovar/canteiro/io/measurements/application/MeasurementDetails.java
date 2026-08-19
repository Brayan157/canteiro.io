package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscount;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmounts;

import java.util.List;

public record MeasurementDetails(
        Measurement measurement,
        MeasurementVersion latestVersion,
        List<MeasurementItem> items,
        MeasurementDiscount discount,
        MeasurementVersionAmounts amounts
) {
}
