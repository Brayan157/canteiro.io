package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.domain.MeasurementFinancialStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementOriginatedServiceFinancialPosition;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MeasurementFinancialStatusResponse(UUID measurementId, UUID measurementVersionId,
                                                 BigDecimal originatedServiceAmount, BigDecimal headerDiscountAmount,
                                                 BigDecimal netMeasurementAmount, BigDecimal billedAmount,
                                                 BigDecimal balanceAmount,
                                                 List<MeasurementOriginatedServiceFinancialPosition> services) {
    public static MeasurementFinancialStatusResponse from(MeasurementFinancialStatus status) {
        return new MeasurementFinancialStatusResponse(status.measurementId(), status.measurementVersionId(),
                status.originatedServiceAmount(), status.headerDiscountAmount(), status.netMeasurementAmount(),
                status.billedAmount(), status.balanceAmount(), status.services());
    }
}
