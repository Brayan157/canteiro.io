package com.renovar.canteiro.io.measurements.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MeasurementFinancialStatus(UUID measurementId, UUID measurementVersionId,
                                         BigDecimal originatedServiceAmount, BigDecimal headerDiscountAmount,
                                         BigDecimal netMeasurementAmount, BigDecimal billedAmount,
                                         BigDecimal balanceAmount,
                                         List<MeasurementOriginatedServiceFinancialPosition> services) {
    public MeasurementFinancialStatus {
        services = List.copyOf(services);
    }
}
