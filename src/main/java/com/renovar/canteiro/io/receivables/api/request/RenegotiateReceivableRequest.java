package com.renovar.canteiro.io.receivables.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record RenegotiateReceivableRequest(
    @NotEmpty @Size(max=100) List<@Valid Installment> installments,
    @NotBlank @Size(max=1000) String reason,
    @Size(max=2000) String justification
) {
    public record Installment(
        @NotNull LocalDate dueOn,
        @NotBlank @Pattern(regexp="(?:0|[1-9][0-9]{0,16})\\.[0-9]{2}")
        @tools.jackson.databind.annotation.JsonDeserialize(using=com.renovar.canteiro.io.shared.api.json.StrictDecimalStringDeserializer.class)
        @Schema(type="string",format="decimal",example="500.00",description="Positive canonical decimal. The new titles must total exactly the current unpaid balance.") String amount
    ) { }
}
