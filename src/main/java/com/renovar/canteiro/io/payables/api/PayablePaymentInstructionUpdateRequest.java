package com.renovar.canteiro.io.payables.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PayablePaymentInstructionUpdateRequest(
        @NotNull @Min(0) @Schema(description="Version of payment instructions, independent of Payable.version. Zero when absent; stale state returns 409.") Long expectedVersion,
        @NotNull @Valid PayablePaymentDetails details,@Size(max=1000) String justification) {
    @Override public String toString(){return "PayablePaymentInstructionUpdateRequest[redacted]";}
}
