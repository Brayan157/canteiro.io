package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;

import java.time.Instant;
import java.util.UUID;

public record InvoiceAttachmentResponse(UUID id, UUID invoiceId, InvoiceAttachmentType attachmentType,
                                        String originalFilename, String contentType, long contentSize,
                                        String sha256, Instant createdAt) {
}
