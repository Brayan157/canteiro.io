package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface InvoiceAttachmentJpaRepository extends JpaRepository<InvoiceAttachmentJpaEntity, UUID> {
    org.springframework.data.domain.Page<InvoiceAttachmentJpaEntity> findByInvoiceIdAndCompanyIdAndContractId(
            UUID invoiceId, UUID companyId, UUID contractId, org.springframework.data.domain.Pageable pageable);
    Optional<InvoiceAttachmentJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByInvoiceIdAndCompanyIdAndContractIdAndAttachmentType(
            UUID invoiceId, UUID companyId, UUID contractId, InvoiceAttachmentType attachmentType);
}
