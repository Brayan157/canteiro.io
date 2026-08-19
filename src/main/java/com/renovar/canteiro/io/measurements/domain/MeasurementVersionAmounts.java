package com.renovar.canteiro.io.measurements.domain;

import java.math.BigDecimal;

public record MeasurementVersionAmounts(BigDecimal grossAmount, BigDecimal discountAmount, BigDecimal netAmount) {
}
