package com.renovar.canteiro.io.receivables.api.request;

import com.renovar.canteiro.io.receivables.domain.TaxCalculation;
import com.renovar.canteiro.io.shared.api.json.StrictDecimalStringDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;

@Schema(description = "Arithmetic supplied by the user, not a fiscal engine. AMOUNT requires amount only; PERCENTAGE requires explicit baseAmount and ratePercent only. No inferred invoice base, rate, incidence, regime or treatment. Positive inputs; rate up to four decimal places; result HALF_UP to cents.")
public record CalculateInvoiceTaxRequest(@NotNull TaxCalculation.Mode mode,
        @JsonDeserialize(using = StrictDecimalStringDeserializer.class) @Pattern(regexp = "^(0|[1-9][0-9]{0,16})\\.[0-9]{2}$")
        @Schema(types = {"string", "null"}, format = "decimal", example = "25.00") String amount,
        @JsonDeserialize(using = StrictDecimalStringDeserializer.class) @Pattern(regexp = "^(0|[1-9][0-9]{0,16})\\.[0-9]{2}$")
        @Schema(types = {"string", "null"}, format = "decimal", example = "1000.00") String baseAmount,
        @JsonDeserialize(using = StrictDecimalStringDeserializer.class) @Pattern(regexp = "^(0|[1-9][0-9]{0,16})(\\.[0-9]{1,4})?$")
        @Schema(types = {"string", "null"}, format = "decimal", example = "2.1234", description = "User-supplied positive percentage up to four decimal places; never inferred or rounded on entry. No assumed fiscal 100% cap.") String ratePercent) {
    public TaxCalculation calculate() { return TaxCalculation.calculate(mode, decimal(amount), decimal(baseAmount), decimal(ratePercent)); }
    private BigDecimal decimal(String v) { return v == null ? null : new BigDecimal(v); }
}
