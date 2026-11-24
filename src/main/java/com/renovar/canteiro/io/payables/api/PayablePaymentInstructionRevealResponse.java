package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.payables.application.PayablePaymentInstructionService;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="Sensitive explicit reveal. Never store in client persistence, application logs, telemetry, exports or public URLs.",requiredProperties={"instruction","details"})
public record PayablePaymentInstructionRevealResponse(PayablePaymentInstructionResponse instruction,PayablePaymentDetails details) {
    public static PayablePaymentInstructionRevealResponse from(PayablePaymentInstructionService.Reveal r){return new PayablePaymentInstructionRevealResponse(
            PayablePaymentInstructionResponse.from(r.instruction()),PayablePaymentDetails.from(r.details()));}
    @Override public String toString(){return "PayablePaymentInstructionRevealResponse[redacted]";}
}
