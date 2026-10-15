package com.renovar.canteiro.io.measurements.api.request;

import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;
import com.renovar.canteiro.io.shared.api.json.StrictDecimalStringDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Item data without an item number; the server assigns its insertion position. */
public record MeasurementItemDataRequest(
        @NotBlank @Size(max = 255) String serviceTitle,
        @NotBlank @Size(max = 255) String activity,
        @Size(max = 1000) String description,
        @NotNull LocalDate measuredOn,
        @NotNull @Schema(description = "UNIT requires unitQuantity and uses unitQuantity × unitPrice; it has no totalWeightKg.")
        MeasurementChargeType chargeType,
        BigDecimal areaSquareMeters,
        BigDecimal linearMeters,
        BigDecimal kilogramsPerSquareMeter,
        BigDecimal kilogramsPerLinearMeter,
        @Positive @Digits(integer = 14, fraction = 4)
        @Schema(example = "12.5000", description = "Required and positive for UNIT, where it drives unitQuantity × unitPrice. Optional for all other charge types as an operational count of executed pieces; in those types it does not affect the formula, weight or amount.")
        BigDecimal unitQuantity,
        @NotBlank @Pattern(regexp = "^(?!0\\.00$)(?:0|[1-9]\\d*)\\.\\d{2}$")
        @JsonDeserialize(using = StrictDecimalStringDeserializer.class)
        @Schema(type = "string", format = "decimal", example = "25.00") String unitPrice,
        @Pattern(regexp = "^(?:0|[1-9]\\d*)\\.\\d{4}$")
        @JsonDeserialize(using = StrictDecimalStringDeserializer.class)
        @Schema(type = "string", format = "decimal", example = "0.0000",
                description = "Optional frontend preview. UNIT may omit it or send 0.0000.") String previewTotalWeightKg,
        @Pattern(regexp = "^(?:0|[1-9]\\d*)\\.\\d{2}$")
        @JsonDeserialize(using = StrictDecimalStringDeserializer.class)
        @Schema(type = "string", format = "decimal", example = "250.00",
                description = "Optional frontend preview. The backend recalculates and rejects an inconsistent value.") String previewTotalAmount,
        @Size(max = 2000) String justification,
        java.util.UUID continuousServiceId
) {
}
