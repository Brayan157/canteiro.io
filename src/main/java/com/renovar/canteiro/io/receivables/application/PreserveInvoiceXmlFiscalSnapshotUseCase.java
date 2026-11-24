package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PreserveInvoiceXmlFiscalSnapshotUseCase {
    private static final String PROFILE = "NFSE_NACIONAL_1_01";
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final InvoiceRepository invoices;
    private final ReadInvoiceAttachmentsUseCase attachments;
    private final PreviewInvoiceXmlUseCase xml;
    private final InvoiceXmlFiscalSnapshotRepository snapshots;
    private final AuditEventRecorder audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public View read(UUID invoiceId) {
        authorization.requirePermission(AccessModule.INVOICES, AccessAction.READ);
        UUID companyId = tenants.requireCurrentTenant().companyId();
        Invoice invoice = invoices.findByIdAndCompanyId(invoiceId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Invoice"));
        var snapshot = snapshots.find(companyId, invoiceId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Invoice XML fiscal snapshot"));
        return new View(invoice, snapshot, false);
    }

    @Transactional
    public View preserve(UUID invoiceId, UUID attachmentId) {
        authorization.requirePermission(AccessModule.INVOICES, AccessAction.READ);
        if (authorization.requireChangeAuthorization(AccessModule.INVOICES, ChangeOperation.CREATE)
                != ChangeAuthorizationMode.DIRECT) {
            throw new AccessDeniedException("Fiscal evidence confirmation requires direct invoice creation authority");
        }
        var tenant = tenants.requireCurrentTenant();
        UUID companyId = tenant.companyId();
        Invoice invoice = invoices.findByIdAndCompanyIdWithLock(invoiceId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Invoice"));
        var attachment = attachments.requireAttachment(invoice, attachmentId);
        var existing = snapshots.find(companyId, invoiceId);
        if (existing.isPresent()) {
            var snapshot = existing.get();
            if (!attachmentId.equals(snapshot.attachmentId())) {
                throw conflict("This invoice already has different immutable fiscal evidence.");
            }
            return new View(invoice, snapshot, false);
        }
        if (invoice.getStatus() != InvoiceStatus.REGISTERED) {
            throw conflict("Fiscal evidence can only be confirmed on a registered invoice.");
        }
        if (attachment.getAttachmentType() != InvoiceAttachmentType.XML) {
            throw new IllegalArgumentException("Fiscal evidence requires an XML attachment.");
        }

        var inspection = xml.inspectFiscal(companyId, invoice.getContractId(), attachments.verifiedContent(attachment));
        if (inspection.issues().stream().anyMatch(NfseXmlPreviewParser.Issue::blocking)) {
            throw conflict("The XML fiscal data requires reconciliation against the registered invoice.");
        }
        var parsed = inspection.parsed();
        String key = requireMatchingIdentity(invoice, parsed);
        var facts = facts(parsed);
        var snapshot = new InvoiceXmlFiscalSnapshot(invoiceId, companyId, invoice.getContractId(), attachmentId,
                PROFILE, "BRL", facts, tenant.userId(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        if (!snapshots.insert(snapshot, digest(key))) {
            throw conflict("This fiscal identity already has immutable evidence.");
        }
        audit.recordDirectAction(AuditModule.INVOICES, AuditAction.CREATE, "InvoiceXmlFiscalSnapshot",
                invoiceId, null, null, Map.of("origin", "direct", "operation", "confirm-invoice-xml"));
        return new View(invoice, snapshot, true);
    }

    private String requireMatchingIdentity(Invoice invoice, NfseXmlPreviewParser.Parsed parsed) {
        var header = parsed.header();
        if (header.accessKey() == null || !header.number().value().matches("[0-9]+")
                || !invoice.getNumber().matches("[0-9]+")
                || !new BigInteger(header.number().value()).equals(new BigInteger(invoice.getNumber()))
                || new BigDecimal(parsed.sourceTotals().commercialAmountCandidate().value()).compareTo(invoice.getTotalAmount()) != 0) {
            throw conflict("The XML identity or commercial total differs from the registered invoice.");
        }
        String key = header.accessKey().value();
        if (invoice.getAccessKey() != null && !invoice.getAccessKey().equals(key)) {
            throw conflict("The XML identity differs from the registered invoice.");
        }
        return key;
    }

    private InvoiceXmlFiscalSnapshot.Facts facts(NfseXmlPreviewParser.Parsed parsed) {
        var dps = parsed.dps();
        validateDates(parsed.header().processedAt(), dps);
        if (!digitsOrMissing(dps.number()) || !digitsOrMissing(dps.series())
                || parsed.taxCandidates().stream().anyMatch(tax -> !digitsOrMissing(tax.withholdingIndicator()))) {
            throw new IllegalArgumentException("Invalid XML fiscal identity or withholding code.");
        }
        var fields = new LinkedHashMap<>(parsed.additionalTaxFields());
        fields.putAll(parsed.fiscalFacts());
        parsed.taxCandidates().stream().filter(tax -> "ISSQN".equals(tax.tax())).findFirst().ifPresent(tax -> {
            var indicator = tax.withholdingIndicator();
            if (indicator != null && "2".equals(indicator.value())) {
                fields.put("issWithholdingResponsible", new NfseXmlPreviewParser.Field("CUSTOMER", indicator.sourcePath()));
            } else if (indicator != null && "3".equals(indicator.value())) {
                fields.put("issWithholdingResponsible", new NfseXmlPreviewParser.Field("INTERMEDIARY", indicator.sourcePath()));
            }
        });
        if (parsed.substitutedAccessKey() != null) {
            var source = parsed.substitutedAccessKey();
            fields.put("substitutedAccessKeySha256",
                    new NfseXmlPreviewParser.Field(digest(source.value()), source.sourcePath()));
        }
        return new InvoiceXmlFiscalSnapshot.Facts(parsed.header().number(), parsed.header().processedAt(), dps,
                parsed.sourceTotals(), parsed.taxCandidates(), Map.copyOf(fields), parsed.signaturePresent(),
                parsed.substitutionIndicated(), parsed.issues().stream().map(NfseXmlPreviewParser.Issue::code).toList());
    }

    private void validateDates(NfseXmlPreviewParser.Field processedAt, NfseXmlPreviewParser.Dps dps) {
        try {
            if (processedAt != null) OffsetDateTime.parse(processedAt.value());
            if (dps.issuedAt() != null) OffsetDateTime.parse(dps.issuedAt().value());
            if (dps.competenceOn() != null) LocalDate.parse(dps.competenceOn().value());
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("Invalid XML fiscal date or timestamp.");
        }
    }

    private boolean digitsOrMissing(NfseXmlPreviewParser.Field value) {
        return value == null || value.value().matches("[0-9]+");
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    public record View(Invoice invoice, InvoiceXmlFiscalSnapshot snapshot, boolean created) { }
}
