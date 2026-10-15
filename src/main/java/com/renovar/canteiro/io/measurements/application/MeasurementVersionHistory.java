package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmounts;

/** Version timeline entry with totals calculated from that version's official active items. */
public record MeasurementVersionHistory(MeasurementVersion version, MeasurementVersionAmounts amounts,
        com.renovar.canteiro.io.shared.domain.CurrencyCode currency) {
    public MeasurementVersionHistory(MeasurementVersion version, MeasurementVersionAmounts amounts) {
        this(version, amounts, com.renovar.canteiro.io.shared.domain.CurrencyCode.BRL);
    }
}
