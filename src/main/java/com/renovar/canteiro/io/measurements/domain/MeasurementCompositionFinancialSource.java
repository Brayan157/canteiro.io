package com.renovar.canteiro.io.measurements.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Header-only financial projection: does not load an accepted composition's entire item collection. */
public record MeasurementCompositionFinancialSource(UUID id,UUID measurementVersionId,UUID measurementServiceId,BigDecimal netAmount) {
    public UUID getId(){return id;}
    public UUID getMeasurementVersionId(){return measurementVersionId;}
    public UUID getMeasurementServiceId(){return measurementServiceId;}
    public BigDecimal getNetAmount(){return netAmount;}
}
