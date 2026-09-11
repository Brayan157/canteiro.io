package com.renovar.canteiro.io.receivables.domain;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceAttachmentRepository {
    InvoiceAttachment save(InvoiceAttachment attachment);
    Optional<InvoiceAttachment> findByIdAndCompanyId(UUID id, UUID companyId);
}
