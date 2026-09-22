package com.renovar.canteiro.io.receivables.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class InvoiceAttachment {
    private final UUID id;
    private final UUID companyId;
    private final UUID invoiceId;
    private final UUID contractId;
    private final InvoiceAttachmentType attachmentType;
    private final String originalFilename;
    private final String contentType;
    private final long contentSize;
    private final String sha256;
    private final String storageKey;
    private final UUID uploadedByUserId;
    private final Instant createdAt;

    private InvoiceAttachment(UUID id, UUID companyId, UUID invoiceId, UUID contractId, InvoiceAttachmentType attachmentType,
                              String originalFilename, String contentType, long contentSize, String sha256,
                              String storageKey, UUID uploadedByUserId, Instant createdAt) {
        this.id = id;
        this.companyId = require(companyId, "Invoice attachment company is required");
        this.invoiceId = require(invoiceId, "Invoice attachment invoice is required");
        this.contractId = require(contractId, "Invoice attachment contract is required");
        this.attachmentType = require(attachmentType, "Invoice attachment type is required");
        this.originalFilename = text(originalFilename, "Invoice attachment filename is required");
        this.contentType = text(contentType, "Invoice attachment content type is required");
        if (contentSize <= 0) throw new IllegalArgumentException("Invoice attachment content size must be positive");
        this.contentSize = contentSize;
        this.sha256 = text(sha256, "Invoice attachment hash is required");
        this.storageKey = text(storageKey, "Invoice attachment storage key is required");
        this.uploadedByUserId = require(uploadedByUserId, "Invoice attachment author is required");
        this.createdAt = createdAt;
    }

    public static InvoiceAttachment create(UUID companyId, UUID invoiceId, UUID contractId, InvoiceAttachmentType attachmentType,
                                           String originalFilename, String contentType, long contentSize, String sha256,
                                           String storageKey, UUID uploadedByUserId) {
        return new InvoiceAttachment(null, companyId, invoiceId, contractId, attachmentType, originalFilename, contentType,
                contentSize, sha256, storageKey, uploadedByUserId, null);
    }

    public static InvoiceAttachment rehydrate(UUID id, UUID companyId, UUID invoiceId, UUID contractId,
                                              InvoiceAttachmentType attachmentType, String originalFilename, String contentType,
                                              long contentSize, String sha256, String storageKey, UUID uploadedByUserId,
                                              Instant createdAt) {
        return new InvoiceAttachment(id, companyId, invoiceId, contractId, attachmentType, originalFilename, contentType,
                contentSize, sha256, storageKey, uploadedByUserId, createdAt);
    }

    private static String text(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
    private static <T> T require(T value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }
}
