package com.renovar.canteiro.io.measurements.api.response;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
public record MeasurementAccumulatedTotalsResponse(long itemCount,
        @Schema(type="string",format="decimal",example="1250.50") String grossAmount,
        @Schema(type="string",format="decimal",example="50.00") String discountAmount,
        @Schema(type="string",format="decimal",example="1200.50") String netAmount,
        @Schema(type="string",format="decimal",example="1536.0000") String totalWeightKg) {
    public static MeasurementAccumulatedTotalsResponse of(long count,BigDecimal gross,BigDecimal net,BigDecimal weight) {
        return new MeasurementAccumulatedTotalsResponse(count,gross.setScale(2).toPlainString(),gross.subtract(net).setScale(2).toPlainString(),
            net.setScale(2).toPlainString(),weight==null?null:weight.setScale(4).toPlainString());
    }
}

