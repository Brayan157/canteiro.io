package com.renovar.canteiro.io.receivables.api.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonDeserialize;
import com.renovar.canteiro.io.shared.api.json.StrictDecimalStringDeserializer;
public record CreateReceivableCreditRequest(@NotBlank @Pattern(regexp="^(?!0\\.00$)(?:0|[1-9]\\d*)\\.\\d{2}$")
        @JsonDeserialize(using=StrictDecimalStringDeserializer.class) @Schema(type="string",format="decimal",example="100.00") String amount,
        @NotBlank @Size(max=1000) String reason,@Size(max=1000) String justification){}

