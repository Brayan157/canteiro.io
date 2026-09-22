package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;

import java.util.UUID;

public record UploadInvoiceAttachmentCommand(UUID invoiceId, InvoiceAttachmentType attachmentType, String filename,
                                             String contentType, byte[] content) {
}
