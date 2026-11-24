package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.InvoiceAttachment;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaInvoiceAttachmentRepository implements InvoiceAttachmentRepository {
    private final InvoiceAttachmentJpaRepository repository;

    @Override
    public org.springframework.data.domain.Page<InvoiceAttachment> findByInvoiceIdAndCompanyIdAndContractId(
            UUID invoiceId, UUID companyId, UUID contractId, org.springframework.data.domain.Pageable pageable) {
        return repository.findByInvoiceIdAndCompanyIdAndContractId(invoiceId, companyId, contractId, pageable).map(this::map);
    }

    @Override
    public InvoiceAttachment save(InvoiceAttachment attachment) {
        // Generate persistence timestamps before copying to the immutable response projection.
        return map(repository.saveAndFlush(new InvoiceAttachmentJpaEntity(attachment)));
    }

    @Override
    public boolean existsByInvoiceIdAndCompanyIdAndContractIdAndAttachmentType(
            UUID invoiceId, UUID companyId, UUID contractId, InvoiceAttachmentType attachmentType) {
        return repository.existsByInvoiceIdAndCompanyIdAndContractIdAndAttachmentType(
                invoiceId, companyId, contractId, attachmentType);
    }

    @Override
    public Optional<InvoiceAttachment> findByIdAndCompanyId(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyId(id, companyId).map(this::map);
    }

    private InvoiceAttachment map(InvoiceAttachmentJpaEntity entity) {
        return InvoiceAttachment.rehydrate(entity.getId(), entity.getCompanyId(), entity.getInvoiceId(), entity.getContractId(),
                entity.getAttachmentType(), entity.getOriginalFilename(), entity.getContentType(), entity.getContentSize(),
                entity.getSha256(), entity.getStorageKey(), entity.getUploadedByUserId(), entity.getCreatedAt());
    }
}
