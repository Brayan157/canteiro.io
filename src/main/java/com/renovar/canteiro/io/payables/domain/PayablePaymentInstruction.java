package com.renovar.canteiro.io.payables.domain;

import java.time.Instant;
import java.util.UUID;

/** Append-only official revisions; content and private object keys never reach ordinary API responses. */
public record PayablePaymentInstruction(UUID id,UUID companyId,UUID payableId,long version,
        PayablePaymentInstructionType type,String ciphertext,String pdfStorageKey,String pdfSha256,
        Long pdfSize,UUID createdBy,Instant createdAt) {
    @Override public String toString(){return "PayablePaymentInstruction[id="+id+", version="+version+"]";}
}
