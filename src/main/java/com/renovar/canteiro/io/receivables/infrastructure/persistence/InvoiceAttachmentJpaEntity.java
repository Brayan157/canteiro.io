package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.InvoiceAttachment;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
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

@Getter
@Entity
@Table(name = "invoice_attachment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class InvoiceAttachmentJpaEntity extends BaseJpaEntity {
    @Column(name = "company_id", nullable = false, updatable = false) private UUID companyId;
    @Column(name = "invoice_id", nullable = false, updatable = false) private UUID invoiceId;
    @Column(name = "contract_id", nullable = false, updatable = false) private UUID contractId;
    @Enumerated(EnumType.STRING) @Column(name = "attachment_type", nullable = false, length = 10) private InvoiceAttachmentType attachmentType;
    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "content_size", nullable = false) private long contentSize;
    @Column(name = "sha256", nullable = false, length = 64) private String sha256;
    @Column(name = "storage_key", nullable = false, length = 500) private String storageKey;
    @Column(name = "uploaded_by_user_id", nullable = false, updatable = false) private UUID uploadedByUserId;

    InvoiceAttachmentJpaEntity(InvoiceAttachment attachment) {
        this.companyId = attachment.getCompanyId(); this.invoiceId = attachment.getInvoiceId();
        this.contractId = attachment.getContractId(); this.attachmentType = attachment.getAttachmentType();
        this.originalFilename = attachment.getOriginalFilename(); this.contentType = attachment.getContentType();
        this.contentSize = attachment.getContentSize(); this.sha256 = attachment.getSha256();
        this.storageKey = attachment.getStorageKey(); this.uploadedByUserId = attachment.getUploadedByUserId();
    }
}
