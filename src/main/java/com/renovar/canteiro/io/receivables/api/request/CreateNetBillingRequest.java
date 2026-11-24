package com.renovar.canteiro.io.receivables.api.request;

import com.renovar.canteiro.io.receivables.application.CreateNetBillingCommand;
import com.renovar.canteiro.io.shared.api.json.StrictDecimalStringDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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

@Schema(description = "Positive net billing only. The backend allocates contract discount over uncommitted capacity; never send CONTRACT_DISCOUNT or a calculated gross amount.")
public record CreateNetBillingRequest(@NotNull UUID operationId, @NotNull UUID contractId,
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") @Schema(description = "Version from GET net-capacity. Stale capacity produces 409; a pending proposal does not reserve it.") String capacityVersion,
        @Size(max = 100) String reference, @Size(max = 1000) String description,
        LocalDate issuedOn, @NotNull LocalDate dueOn, UUID financialGroupId,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid Line> allocations, @Size(max = 1000) String justification) {
    @Schema(name = "NetBillingAllocationRequest")
    public record Line(@NotNull UUID contractServiceId,
            @Schema(types = {"string", "null"}, format = "uuid", description = "Exact immutable measurement composition; omit only for negotiated base capacity.") UUID measurementContractCompositionId,
            @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]*)\\.[0-9]{2}$") @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 17, fraction = 2)
            @JsonDeserialize(using = StrictDecimalStringDeserializer.class)
            @Schema(type = "string", format = "decimal", pattern = "^(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "90.00") String netAmount) { }
    public CreateNetBillingCommand toCommand() {
        return new CreateNetBillingCommand(operationId, contractId, capacityVersion, reference, description, issuedOn, dueOn, financialGroupId,
                allocations.stream().map(line -> new CreateNetBillingCommand.Line(line.contractServiceId(), line.measurementContractCompositionId(), new BigDecimal(line.netAmount()))).toList(), justification);
    }
}
