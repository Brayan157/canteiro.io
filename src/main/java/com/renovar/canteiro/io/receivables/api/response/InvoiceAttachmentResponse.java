package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;

import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Official attachment metadata only. No storage key, path, public URL or fiscal contents. All fields are non-null.",
        requiredProperties = {"id", "invoiceId", "attachmentType", "originalFilename", "contentType", "contentSize", "sha256", "createdAt"})
public record InvoiceAttachmentResponse(UUID id, UUID invoiceId, InvoiceAttachmentType attachmentType,
                                        String originalFilename, String contentType, long contentSize,
                                        String sha256, Instant createdAt) {
}
