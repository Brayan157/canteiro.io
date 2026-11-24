package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.payables.domain.PayablePaymentMethod;
import com.renovar.canteiro.io.shared.api.json.StrictDecimalStringDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDate;
import java.util.UUID;

public record PayableSettlementCreateRequest(@NotNull UUID operationId,
        @NotNull @Pattern(regexp = "^(0|[1-9][0-9]{0,16})\\.[0-9]{2}$")
        @jakarta.validation.constraints.DecimalMin(value = "0.00", inclusive = false)
        @JsonDeserialize(using = StrictDecimalStringDeserializer.class)
        @Schema(type = "string", format = "decimal", example = "50.00") String amount,
        @NotNull @Schema(description = "Date the payment was made. Cannot be after the current UTC date.") LocalDate paidOn,
        @NotNull PayablePaymentMethod paymentMethod, @Size(max = 1000) String justification) { }
