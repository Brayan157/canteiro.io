package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.InvoiceAttachment;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaInvoiceAttachmentRepository implements InvoiceAttachmentRepository {
    private final InvoiceAttachmentJpaRepository repository;

    @Override
    public InvoiceAttachment save(InvoiceAttachment attachment) {
        return map(repository.save(new InvoiceAttachmentJpaEntity(attachment)));
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
