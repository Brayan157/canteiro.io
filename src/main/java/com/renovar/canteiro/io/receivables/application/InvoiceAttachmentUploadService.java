package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachment;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceAttachmentUploadService {
    private static final int MAX_SIZE_BYTES = 10 * 1024 * 1024;
    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService authorizationService;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceAttachmentRepository attachmentRepository;
    private final StorageProvider storageProvider;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public InvoiceAttachment upload(UploadInvoiceAttachmentCommand command) {
        var tenant = tenantContextHolder.requireCurrentTenant();
        if (authorizationService.requireChangeAuthorization(AccessModule.INVOICES, ChangeOperation.CREATE)
                != ChangeAuthorizationMode.DIRECT) {
            throw new AccessDeniedException("Uploading an invoice attachment requires direct authority");
        }
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(command.invoiceId(), tenant.companyId())
                .orElseThrow(() -> new TenantResourceNotFoundException("Invoice"));
        ValidatedFile file = validate(command);
        String storageKey = "invoices/" + tenant.companyId() + "/" + invoice.getId() + "/"
                + UUID.randomUUID() + "-" + file.filename();
        storageProvider.store(storageKey, command.content());
        try {
            InvoiceAttachment attachment = attachmentRepository.save(InvoiceAttachment.create(tenant.companyId(), invoice.getId(),
                    invoice.getContractId(), command.attachmentType(), file.filename(), file.contentType(), command.content().length,
                    file.sha256(), storageKey, tenant.userId()));
            auditEventRecorder.recordDirectAction(AuditModule.INVOICES, AuditAction.CREATE, "InvoiceAttachment",
                    attachment.getId(), null, Map.of("invoiceId", invoice.getId().toString(),
                            "type", command.attachmentType().name(), "filename", file.filename(), "sha256", file.sha256()),
                    Map.of("origin", "upload"));
            return attachment;
        } catch (RuntimeException exception) {
            storageProvider.delete(storageKey);
            throw exception;
        }
    }

    private ValidatedFile validate(UploadInvoiceAttachmentCommand command) {
        if (command == null || command.attachmentType() == null || command.content() == null || command.content().length == 0
                || command.content().length > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Invoice attachment content must be between 1 byte and 10 MB");
        }
        String filename = sanitizeFilename(command.filename());
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String contentType = command.contentType() == null ? "" : command.contentType().toLowerCase(Locale.ROOT);
        if (!allowed(command.attachmentType(), extension, contentType)) {
            throw new IllegalArgumentException("Invoice attachment type does not match the file format");
        }
        return new ValidatedFile(filename, contentType, sha256(command.content()));
    }

    private boolean allowed(InvoiceAttachmentType type, String extension, String contentType) {
        return switch (type) {
            case PDF -> extension.equals("pdf") && contentType.equals("application/pdf");
            case XML -> extension.equals("xml") && (contentType.equals("application/xml") || contentType.equals("text/xml"));
        };
    }

    private String sanitizeFilename(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Invoice attachment filename is required");
        String filename = value.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).trim();
        if (filename.isEmpty() || !filename.contains(".") || filename.length() > 255) {
            throw new IllegalArgumentException("Invalid invoice attachment filename");
        }
        return filename;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ValidatedFile(String filename, String contentType, String sha256) { }
}
