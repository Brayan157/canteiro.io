package com.renovar.canteiro.io.receivables.api.request;

import com.renovar.canteiro.io.receivables.application.CreateCalculatedTaxCommand;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import com.renovar.canteiro.io.shared.api.json.StrictDecimalStringDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateCalculatedInvoiceTaxRequest(@NotNull UUID operationId, @Schema(types = {"string", "null"}, format = "uuid") UUID receivableId,
        @NotBlank @Size(max = 100) String name, @Schema(types = {"string", "null"}) @Size(max = 1000) String description,
        @Schema(types = {"string", "null"}, format = "date") LocalDate dueOn,
        @NotNull TaxTreatment treatment, @NotNull @Valid CalculateInvoiceTaxRequest calculation,
        @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,16})\\.[0-9]{2}$") @JsonDeserialize(using = StrictDecimalStringDeserializer.class)
        @Schema(type = "string", format = "decimal", example = "21.23", description = "Exactly the official amount reviewed in preview. Divergence is 400; never silently replaced.") String confirmedAmount,
        @Schema(types = {"string", "null"}) @Size(max = 2000) String justification) {
    public CreateCalculatedTaxCommand toCommand(UUID invoice) { return new CreateCalculatedTaxCommand(operationId, invoice, receivableId, name, description, dueOn, treatment, justification, calculation.calculate(), new BigDecimal(confirmedAmount)); }
}
