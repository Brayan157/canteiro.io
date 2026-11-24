package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachment;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReadInvoiceAttachmentsUseCase {
    private static final long MAX_CONTENT_SIZE = 10L * 1024 * 1024;
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final InvoiceRepository invoices;
    private final InvoiceAttachmentRepository attachments;
    private final StorageProvider storage;
    private final AuditEventRecorder audits;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<Metadata> find(UUID invoiceId, Pageable pageable) {
        var tenant = tenants.requireCurrentTenant();
        authorization.requirePermission(AccessModule.INVOICES, AccessAction.READ);
        var invoice = invoices.findByIdAndCompanyId(invoiceId, tenant.companyId())
                .orElseThrow(() -> new TenantResourceNotFoundException("Invoice"));
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PAGINATION, "Page offset is too large.");
        }
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Order.desc("createdAt"));
        Pageable ordered = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(Sort.by("id")));
        return attachments.findByInvoiceIdAndCompanyIdAndContractId(invoiceId, tenant.companyId(), invoice.getContractId(), ordered)
                .map(value -> new Metadata(value.getId(), value.getInvoiceId(), value.getAttachmentType(),
                        InvoiceAttachmentFilename.display(value.getOriginalFilename(), value.getAttachmentType()), value.getContentType(),
                        value.getContentSize(), value.getSha256(), value.getCreatedAt()));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Download download(UUID invoiceId, UUID attachmentId) {
        var tenant = tenants.requireCurrentTenant();
        authorization.requirePermission(AccessModule.INVOICES, AccessAction.READ);
        authorization.requirePermission(AccessModule.INVOICES, AccessAction.EXPORT);
        var invoice = invoices.findByIdAndCompanyId(invoiceId, tenant.companyId())
                .orElseThrow(() -> new TenantResourceNotFoundException("Invoice"));
        var attachment = requireAttachment(invoice, attachmentId);
        byte[] bytes = verifiedContent(attachment);
        String filename = InvoiceAttachmentFilename.download(attachment.getOriginalFilename(), attachment.getAttachmentType());
        String contentType = attachment.getAttachmentType() == InvoiceAttachmentType.PDF ? "application/pdf" : "application/xml";
        audits.recordDirectAction(AuditModule.INVOICES, AuditAction.EXPORT, "InvoiceAttachment", attachmentId, null, null,
                Map.of("origin", "invoice_attachment_download", "invoiceId", invoiceId.toString(), "format", attachment.getAttachmentType().name()));
        return new Download(filename, contentType, bytes);
    }

    InvoiceAttachment requireAttachment(Invoice invoice, UUID attachmentId) {
        return attachments.findByIdAndCompanyId(attachmentId, invoice.getCompanyId())
                .filter(value -> invoice.getId().equals(value.getInvoiceId())
                        && invoice.getContractId().equals(value.getContractId()))
                .orElseThrow(() -> new TenantResourceNotFoundException("Invoice attachment"));
    }

    byte[] verifiedContent(InvoiceAttachment attachment) {
        if (attachment.getContentSize() > MAX_CONTENT_SIZE) throw unavailable();
        byte[] bytes;
        try {
            bytes = storage.load(attachment.getStorageKey());
        } catch (RuntimeException exception) {
            throw unavailable();
        }
        if (bytes == null || bytes.length != attachment.getContentSize() || !sha256(bytes).equals(attachment.getSha256())) {
            throw unavailable();
        }
        return bytes;
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.INTERNAL_ERROR, "Invoice attachment content is unavailable.");
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    public record Download(String filename, String contentType, byte[] content) { }
    public record Metadata(UUID id, UUID invoiceId, InvoiceAttachmentType attachmentType, String originalFilename,
                           String contentType, long contentSize, String sha256, Instant createdAt) { }
}
