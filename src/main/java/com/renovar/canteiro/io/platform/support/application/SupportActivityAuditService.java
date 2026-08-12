package com.renovar.canteiro.io.platform.support.application;

import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditEvent;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.platform.support.domain.SupportOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupportActivityAuditService {

    private final SupportAuthorizationService supportAuthorizationService;
    private final SupportTargetContextHolder supportTargetContextHolder;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public AuditEvent recordOperationalAction(
            SupportOperation operation,
            AuditModule module,
            String entityType,
            UUID entityId,
            Map<String, Object> beforeData,
            Map<String, Object> afterData
    ) {
        supportAuthorizationService.requireAllowed(operation);
        requireOperationalAction(operation);
        return auditEventRecorder.recordDirectAction(
                module,
                AuditAction.SUPPORT_ACTION,
                entityType,
                entityId,
                beforeData,
                afterData,
                metadata(operation)
        );
    }

    @Transactional
    public AuditEvent recordReportSent(RecordSupportReportDeliveryCommand command) {
        supportAuthorizationService.requireAllowed(SupportOperation.SEND_REPORT);
        Map<String, Object> metadata = metadata(SupportOperation.SEND_REPORT);
        metadata.put("reportType", command.reportType());
        metadata.put("recipientEmail", command.recipientEmail());
        metadata.put("artifactReference", command.artifactReference());
        return auditEventRecorder.recordDirectAction(
                AuditModule.REPORTING,
                AuditAction.SEND_REPORT,
                "ReportDelivery",
                null,
                null,
                null,
                metadata
        );
    }

    private void requireOperationalAction(SupportOperation operation) {
        if (operation != SupportOperation.READ_OPERATIONAL_DATA
                && operation != SupportOperation.CREATE_OPERATIONAL_DATA
                && operation != SupportOperation.UPDATE_OPERATIONAL_DATA
                && operation != SupportOperation.GENERATE_REPORT) {
            throw new IllegalArgumentException("The support operation must be operational or report generation");
        }
    }

    private Map<String, Object> metadata(SupportOperation operation) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("supportOperation", operation.name());
        metadata.put("targetCompanyId", supportTargetContextHolder.requireCurrentTarget().targetCompanyId().toString());
        return metadata;
    }
}
