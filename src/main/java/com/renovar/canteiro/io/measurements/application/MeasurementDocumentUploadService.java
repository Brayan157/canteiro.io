package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.measurements.domain.MeasurementDocument;
import com.renovar.canteiro.io.measurements.domain.MeasurementDocumentRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class MeasurementDocumentUploadService {
    private static final int MAX_SIZE_BYTES = 10 * 1024 * 1024;
    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService authorizationService;
    private final MeasurementVersionRepository versionRepository;
    private final MeasurementDocumentRepository documentRepository;
    private final StorageProvider storageProvider;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public MeasurementDocument upload(UploadMeasurementDocumentCommand command) {
        var tenant = tenantContextHolder.requireCurrentTenant();
        if (authorizationService.requireChangeAuthorization(AccessModule.MEASUREMENTS, ChangeOperation.CREATE)
                != ChangeAuthorizationMode.DIRECT) {
            throw new AccessDeniedException("Uploading a measurement document requires direct authority");
        }
        MeasurementVersion version = versionRepository.findByIdAndCompanyId(command.measurementVersionId(), tenant.companyId())
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement version"));
        if (!version.getMeasurementId().equals(command.measurementId())) throw new TenantResourceNotFoundException("Measurement version");
        ValidatedFile file = validate(command);
        String storageKey = "measurements/" + tenant.companyId() + "/" + command.measurementId() + "/"
                + command.measurementVersionId() + "/" + UUID.randomUUID() + "-" + file.filename();
        storageProvider.store(storageKey, command.content());
        try {
            MeasurementDocument document = documentRepository.save(MeasurementDocument.create(tenant.companyId(),
                    command.measurementId(), command.measurementVersionId(), command.documentType(), file.filename(),
                    file.contentType(), command.content().length, file.sha256(), storageKey, tenant.userId()));
            auditEventRecorder.recordDirectAction(AuditModule.MEASUREMENTS, AuditAction.CREATE, "MeasurementDocument",
                    document.getId(), null, Map.of("measurementVersionId", version.getId().toString(), "type", command.documentType().name(),
                            "filename", file.filename(), "sha256", file.sha256()), Map.of("origin", "upload"));
            return document;
        } catch (RuntimeException exception) {
            storageProvider.delete(storageKey);
            throw exception;
        }
    }

    private ValidatedFile validate(UploadMeasurementDocumentCommand command) {
        if (command == null || command.content() == null || command.content().length == 0 || command.content().length > MAX_SIZE_BYTES)
            throw new IllegalArgumentException("Measurement document content must be between 1 byte and 10 MB");
        String filename = sanitizeFilename(command.filename());
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String contentType = command.contentType() == null ? "" : command.contentType().toLowerCase(Locale.ROOT);
        if (!allowed(extension, contentType)) throw new IllegalArgumentException("Unsupported measurement document type");
        return new ValidatedFile(filename, contentType, sha256(command.content()));
    }

    private boolean allowed(String extension, String contentType) {
        return switch (extension) {
            case "pdf" -> contentType.equals("application/pdf");
            case "xlsx" -> contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "csv" -> contentType.equals("text/csv") || contentType.equals("application/csv");
            case "docx" -> contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "xml" -> contentType.equals("application/xml") || contentType.equals("text/xml");
            default -> false;
        };
    }
    private String sanitizeFilename(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Measurement document filename is required");
        String filename = value.replace('\\', '/'); filename = filename.substring(filename.lastIndexOf('/') + 1).trim();
        if (filename.isEmpty() || !filename.contains(".") || filename.length() > 255) throw new IllegalArgumentException("Invalid measurement document filename");
        return filename;
    }
    private String sha256(byte[] content) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    private record ValidatedFile(String filename, String contentType, String sha256) { }
}
