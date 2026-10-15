package com.renovar.canteiro.io.measurements.api.request;

import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemCorrectionAction;
import com.renovar.canteiro.io.shared.api.json.StrictDecimalStringDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;

/** REPLACE requires all line fields; VOID only requires action and justification. */
public record CorrectAcceptedMeasurementItemRequest(
        @NotNull MeasurementItemCorrectionAction action,
        Integer itemNumber,
        @Size(max = 255) String serviceTitle,
        @Size(max = 255) String activity,
        @Size(max = 1000) String description,
        LocalDate measuredOn,
        MeasurementChargeType chargeType,
        BigDecimal areaSquareMeters,
        BigDecimal linearMeters,
        BigDecimal kilogramsPerSquareMeter,
        BigDecimal kilogramsPerLinearMeter,
        @Positive @Digits(integer = 14, fraction = 4)
        @Schema(example = "12.5000", description = "Required and positive for a UNIT replacement, where it drives unitQuantity × unitPrice. Optional for other charge types as an operational count only; it does not affect their formula, weight or amount.")
        BigDecimal unitQuantity,
        @Pattern(regexp = "^(?!0\\.00$)(?:0|[1-9]\\d*)\\.\\d{2}$")
        @JsonDeserialize(using = StrictDecimalStringDeserializer.class)
        @Schema(type = "string", format = "decimal", example = "25.00") String unitPrice,
        @Pattern(regexp = "^(?:0|[1-9]\\d*)\\.\\d{4}$")
        @JsonDeserialize(using = StrictDecimalStringDeserializer.class)
        @Schema(type = "string", format = "decimal", example = "1536.0000") String previewTotalWeightKg,
        @Pattern(regexp = "^(?:0|[1-9]\\d*)\\.\\d{2}$")
        @JsonDeserialize(using = StrictDecimalStringDeserializer.class)
        @Schema(type = "string", format = "decimal", example = "4608.00") String previewTotalAmount,
        @NotNull @Size(min = 3, max = 2000) String justification
) {
}
