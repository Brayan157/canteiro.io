package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachment;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
import com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceAttachmentUploadServiceTest {
    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private AccessAuthorizationService authorizationService;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceAttachmentRepository attachmentRepository;
    @Mock private StorageProvider storageProvider;
    @Mock private AuditEventRecorder auditEventRecorder;

    @Test
    void storesPdfMetadataAndAuditsItsUpload() {
        UUID companyId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(userId, companyId));
        when(authorizationService.requireChangeAuthorization(any(), any())).thenReturn(ChangeAuthorizationMode.DIRECT);
        when(invoiceRepository.findByIdAndCompanyId(invoiceId, companyId)).thenReturn(Optional.of(Invoice.rehydrate(
                invoiceId, companyId, contractId, "NF-1", null, null, LocalDate.now(), new BigDecimal("1.00"),
                InvoiceStatus.REGISTERED, Instant.now(), Instant.now())));
        when(attachmentRepository.save(any())).thenAnswer(invocation -> {
            InvoiceAttachment value = invocation.getArgument(0);
            return InvoiceAttachment.rehydrate(UUID.randomUUID(), value.getCompanyId(), value.getInvoiceId(),
                    value.getContractId(), value.getAttachmentType(), value.getOriginalFilename(), value.getContentType(),
                    value.getContentSize(), value.getSha256(), value.getStorageKey(), value.getUploadedByUserId(), Instant.now());
        });

        InvoiceAttachment attachment = service().upload(new UploadInvoiceAttachmentCommand(invoiceId,
                InvoiceAttachmentType.PDF, "../nota.pdf", "application/pdf", new byte[]{1, 2, 3}));

        assertEquals("nota.pdf", attachment.getOriginalFilename());
        assertEquals(InvoiceAttachmentType.PDF, attachment.getAttachmentType());
        ArgumentCaptor<String> storageKey = ArgumentCaptor.forClass(String.class);
        verify(storageProvider).store(storageKey.capture(), any());
        assertTrue(storageKey.getValue().startsWith("invoices/" + companyId + "/" + invoiceId + "/"));
        verify(auditEventRecorder).recordDirectAction(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsMismatchedFileTypeBeforeStoring() {
        UUID companyId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), companyId));
        when(authorizationService.requireChangeAuthorization(any(), any())).thenReturn(ChangeAuthorizationMode.DIRECT);
        when(invoiceRepository.findByIdAndCompanyId(invoiceId, companyId)).thenReturn(Optional.of(Invoice.rehydrate(
                invoiceId, companyId, UUID.randomUUID(), "NF-2", null, null, LocalDate.now(), new BigDecimal("1.00"),
                InvoiceStatus.REGISTERED, Instant.now(), Instant.now())));
        assertThrows(IllegalArgumentException.class, () -> service().upload(new UploadInvoiceAttachmentCommand(invoiceId,
                InvoiceAttachmentType.XML, "nota.pdf", "application/pdf", new byte[]{1})));
    }

    private InvoiceAttachmentUploadService service() {
        return new InvoiceAttachmentUploadService(tenantContextHolder, authorizationService, invoiceRepository,
                attachmentRepository, storageProvider, auditEventRecorder);
    }
}
