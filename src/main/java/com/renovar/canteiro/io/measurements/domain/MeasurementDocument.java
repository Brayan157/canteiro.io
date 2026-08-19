package com.renovar.canteiro.io.measurements.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class MeasurementDocument {

    private final UUID id;
    private final UUID companyId;
    private final UUID measurementId;
    private final UUID measurementVersionId;
    private final MeasurementDocumentType documentType;
    private final String originalFilename;
    private final String contentType;
    private final long contentSize;
    private final String sha256;
    private final String storageKey;
    private final UUID uploadedByUserId;
    private final Instant createdAt;

    private MeasurementDocument(UUID id, UUID companyId, UUID measurementId, UUID measurementVersionId,
                                MeasurementDocumentType documentType, String originalFilename, String contentType,
                                long contentSize, String sha256, String storageKey, UUID uploadedByUserId,
                                Instant createdAt) {
        this.id = id;
        this.companyId = require(companyId, "Measurement document company is required");
        this.measurementId = require(measurementId, "Measurement document measurement is required");
        this.measurementVersionId = require(measurementVersionId, "Measurement document version is required");
        this.documentType = require(documentType, "Measurement document type is required");
        this.originalFilename = requireText(originalFilename, "Measurement document filename is required");
        this.contentType = requireText(contentType, "Measurement document content type is required");
        if (contentSize <= 0) throw new IllegalArgumentException("Measurement document content size must be positive");
        this.contentSize = contentSize;
        this.sha256 = requireText(sha256, "Measurement document hash is required");
        this.storageKey = requireText(storageKey, "Measurement document storage key is required");
        this.uploadedByUserId = require(uploadedByUserId, "Measurement document author is required");
        this.createdAt = createdAt;
    }

    public static MeasurementDocument create(UUID companyId, UUID measurementId, UUID measurementVersionId,
                                             MeasurementDocumentType documentType, String originalFilename,
                                             String contentType, long contentSize, String sha256, String storageKey,
                                             UUID uploadedByUserId) {
        return new MeasurementDocument(null, companyId, measurementId, measurementVersionId, documentType,
                originalFilename, contentType, contentSize, sha256, storageKey, uploadedByUserId, null);
    }

    public static MeasurementDocument rehydrate(UUID id, UUID companyId, UUID measurementId, UUID measurementVersionId,
                                                MeasurementDocumentType documentType, String originalFilename,
                                                String contentType, long contentSize, String sha256, String storageKey,
                                                UUID uploadedByUserId, Instant createdAt) {
        return new MeasurementDocument(id, companyId, measurementId, measurementVersionId, documentType,
                originalFilename, contentType, contentSize, sha256, storageKey, uploadedByUserId, createdAt);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
    private static <T> T require(T value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }
}
