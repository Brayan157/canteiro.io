package com.renovar.canteiro.io.receivables.api.request;

import com.renovar.canteiro.io.receivables.application.CreateNetBillingCommand;
import com.renovar.canteiro.io.shared.api.json.StrictDecimalStringDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Bill services without selecting technical origins. Negotiated base first, then official acceptance date ascending; ties measurementId/versionId/compositionId lexicographically. Existing commitments never change. ALL_AVAILABLE consumes only usable capacity of the quoted version.")
public record CreateServiceNetBillingRequest(@NotNull UUID operationId, @NotNull UUID contractId,
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String capacityVersion,
        @Schema(types = {"string", "null"}) @Size(max = 100) String reference,
        @Schema(types = {"string", "null"}) @Size(max = 1000) String description,
        @Schema(types = {"string", "null"}, format = "date") LocalDate issuedOn,
        @NotNull LocalDate dueOn, @Schema(types = {"string", "null"}, format = "uuid") UUID financialGroupId,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid Line> services,
        @Schema(types = {"string", "null"}) @Size(max = 1000) String justification) {
    @Schema(name = "ServiceNetBillingLineRequest")
    public record Line(@NotNull UUID contractServiceId, @NotNull CreateNetBillingCommand.ServiceAmountMode mode,
            @JsonDeserialize(using = StrictDecimalStringDeserializer.class)
            @Pattern(regexp = "^(0|[1-9][0-9]{0,16})\\.[0-9]{2}$")
            @Schema(types = {"string", "null"}, format = "decimal", example = "50.00", description = "Required positive amount for PARTIAL; must be absent/null for ALL_AVAILABLE.") String netAmount) { }
    public CreateNetBillingCommand toCommand() {
        return new CreateNetBillingCommand(operationId, contractId, capacityVersion, reference, description, issuedOn, dueOn,
                financialGroupId, List.of(), justification, services.stream().map(line -> new CreateNetBillingCommand.ServiceLine(
                        line.contractServiceId(), line.mode(), line.netAmount() == null ? null : new BigDecimal(line.netAmount()))).toList());
    }
}
