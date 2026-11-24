package com.renovar.canteiro.io.receivables.domain;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceAttachmentRepository {
    org.springframework.data.domain.Page<InvoiceAttachment> findByInvoiceIdAndCompanyIdAndContractId(
            UUID invoiceId, UUID companyId, UUID contractId, org.springframework.data.domain.Pageable pageable);
    InvoiceAttachment save(InvoiceAttachment attachment);
    boolean existsByInvoiceIdAndCompanyIdAndContractIdAndAttachmentType(
            UUID invoiceId, UUID companyId, UUID contractId, InvoiceAttachmentType attachmentType);
    Optional<InvoiceAttachment> findByIdAndCompanyId(UUID id, UUID companyId);
}
