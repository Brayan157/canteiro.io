package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.payables.application.PayablePaymentInstructionService;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description="200: instruction metadata and DIRECT. 202: only changeRequestId and mode=REQUEST_APPROVAL; official instructions and payable remain unchanged.",requiredProperties={"mode"})
public record PayablePaymentInstructionChangeResponse(PayablePaymentInstructionResponse instruction,UUID changeRequestId,ChangeAuthorizationMode mode) {
    public static PayablePaymentInstructionChangeResponse from(PayablePaymentInstructionService.Result r){return new PayablePaymentInstructionChangeResponse(
            r.instruction()==null?null:PayablePaymentInstructionResponse.from(r.instruction()),r.changeRequestId(),r.mode());}
}
