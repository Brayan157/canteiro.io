package com.renovar.canteiro.io.measurements.api.request;

import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;
import com.renovar.canteiro.io.shared.api.json.StrictDecimalStringDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateMeasurementItemRequest(
        @Positive int itemNumber,
        @NotBlank @Size(max = 255) String serviceTitle,
        @NotBlank @Size(max = 255) String activity,
        @Size(max = 1000) String description,
        @NotNull LocalDate measuredOn,
        @NotNull @Schema(description = "UNIT requires unitQuantity and uses unitQuantity × unitPrice; it has no totalWeightKg.") MeasurementChargeType chargeType,
        BigDecimal areaSquareMeters,
        BigDecimal linearMeters,
        BigDecimal kilogramsPerSquareMeter,
        BigDecimal kilogramsPerLinearMeter,
        @Positive @Digits(integer = 14, fraction = 4)
        @Schema(example = "12.5000", description = "Required and positive for UNIT, where it drives unitQuantity × unitPrice. Optional for all other charge types as an operational count of executed pieces; in those types it does not affect the formula, weight or amount.")
        BigDecimal unitQuantity,
        @NotBlank
        @Pattern(regexp = "^(?!0\\.00$)(?:0|[1-9]\\d*)\\.\\d{2}$")
        @JsonDeserialize(using = StrictDecimalStringDeserializer.class)
        @Schema(type = "string", format = "decimal", example = "25.00", pattern = "^(?!0\\.00$)(?:0|[1-9]\\d*)\\.\\d{2}$")
        String unitPrice,
        @Pattern(regexp = "^(?:0|[1-9]\\d*)\\.\\d{4}$")
        @JsonDeserialize(using = StrictDecimalStringDeserializer.class)
        @Schema(type = "string", format = "decimal", example = "1536.0000", pattern = "^(?:0|[1-9]\\d*)\\.\\d{4}$",
                description = "Optional frontend preview. The backend recalculates and rejects an inconsistent value.")
        String previewTotalWeightKg,
        @Pattern(regexp = "^(?:0|[1-9]\\d*)\\.\\d{2}$")
        @JsonDeserialize(using = StrictDecimalStringDeserializer.class)
        @Schema(type = "string", format = "decimal", example = "4608.00", pattern = "^(?:0|[1-9]\\d*)\\.\\d{2}$",
                description = "Optional frontend preview. The backend recalculates and rejects an inconsistent value.")
        String previewTotalAmount,
        @Size(max = 2000) String justification,
        @Schema(description = "Stable continuous service of this measurement. Select the persisted ID to continue a service in later advances.")
        java.util.UUID continuousServiceId
) {
}
