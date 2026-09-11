package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface InvoiceAttachmentJpaRepository extends JpaRepository<InvoiceAttachmentJpaEntity, UUID> {
    Optional<InvoiceAttachmentJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);
}
