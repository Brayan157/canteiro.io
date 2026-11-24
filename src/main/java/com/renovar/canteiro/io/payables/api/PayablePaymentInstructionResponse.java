package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.payables.domain.PayablePaymentInstruction;
import com.renovar.canteiro.io.payables.domain.PayablePaymentInstructionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties={"exists","version","hasPdf"},description="Metadata only, no banking/Pix/boleto values, storage keys, hash or public URLs. Missing instructions return exists=false and version=0 for an existing tenant payable.")
public record PayablePaymentInstructionResponse(boolean exists,@Schema(types={"string","null"},format="uuid") UUID id,long version,
        @Schema(types={"string","null"},allowableValues={"BOLETO","PIX","BANK_TRANSFER"}) PayablePaymentInstructionType type,boolean hasPdf,
        @Schema(types={"string","null"},format="date-time") Instant updatedAt) {
    public static PayablePaymentInstructionResponse from(PayablePaymentInstruction p){return p==null?
            new PayablePaymentInstructionResponse(false,null,0,null,false,null):
            new PayablePaymentInstructionResponse(true,p.id(),p.version(),p.type(),p.pdfStorageKey()!=null,p.createdAt());}
}
