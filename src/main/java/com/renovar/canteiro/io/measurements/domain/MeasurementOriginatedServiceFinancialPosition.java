package com.renovar.canteiro.io.measurements.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record MeasurementOriginatedServiceFinancialPosition(UUID measurementItemId, UUID contractServiceId,
                                                            BigDecimal serviceAmount, BigDecimal billedAmount,
                                                            BigDecimal balanceAmount) {
}
