package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementDocumentType;
import com.renovar.canteiro.io.measurements.domain.MeasurementDocument;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter @Entity @Table(name = "measurement_document") @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasurementDocumentJpaEntity extends BaseJpaEntity {
    @Column(name = "company_id", nullable = false, updatable = false) private UUID companyId;
    @Column(name = "measurement_id", nullable = false, updatable = false) private UUID measurementId;
    @Column(name = "measurement_version_id", nullable = false, updatable = false) private UUID measurementVersionId;
    @Enumerated(EnumType.STRING) @Column(name = "document_type", nullable = false, length = 20) private MeasurementDocumentType documentType;
    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "content_size", nullable = false) private long contentSize;
    @Column(name = "sha256", nullable = false, length = 64) private String sha256;
    @Column(name = "storage_key", nullable = false, length = 500) private String storageKey;
    @Column(name = "uploaded_by_user_id", nullable = false, updatable = false) private UUID uploadedByUserId;

    public MeasurementDocumentJpaEntity(MeasurementDocument document) {
        this.companyId = document.getCompanyId(); this.measurementId = document.getMeasurementId();
        this.measurementVersionId = document.getMeasurementVersionId(); this.documentType = document.getDocumentType();
        this.originalFilename = document.getOriginalFilename(); this.contentType = document.getContentType();
        this.contentSize = document.getContentSize(); this.sha256 = document.getSha256(); this.storageKey = document.getStorageKey();
        this.uploadedByUserId = document.getUploadedByUserId();
    }
}
