package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeasurementWorkflowService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final MeasurementRepository measurementRepository;
    private final MeasurementVersionRepository measurementVersionRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public MeasurementWorkflowChangeResult advance(AdvanceMeasurementWorkflowCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        MeasurementVersion version = requireVersion(command.measurementVersionId(), companyId);
        requireMeasurementVersionLink(command.measurementId(), version);
        validateCommand(command);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.MEASUREMENTS, ChangeOperation.UPDATE
        );
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest changeRequest = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.MEASUREMENTS,
                    ChangeRequestOperation.UPDATE,
                    "MeasurementWorkflow",
                    command.measurementId(),
                    version.getLockVersion(),
                    new ChangeRequestSnapshot(new AuditPayload(auditData(requireMeasurement(command.measurementId(), companyId), version)),
                            new AuditPayload(proposal(command))),
                    command.justification()
            ));
            return new MeasurementWorkflowChangeResult(null, null, changeRequest, mode);
        }
        return applyAndAudit(companyId, command, "direct", null, mode);
    }

    @Transactional
    public void applyApprovedUpdate(ChangeRequest changeRequest) {
        AdvanceMeasurementWorkflowCommand command = fromProposal(changeRequest.getSnapshot().proposedData().values());
        applyAndAudit(changeRequest.getCompanyId(), command, "approved-change-request", changeRequest.getId(),
                ChangeAuthorizationMode.DIRECT);
    }

    private MeasurementWorkflowChangeResult applyAndAudit(UUID companyId, AdvanceMeasurementWorkflowCommand command,
                                                           String origin, UUID changeRequestId,
                                                           ChangeAuthorizationMode authorizationMode) {
        Measurement measurement = requireMeasurement(command.measurementId(), companyId);
        MeasurementVersion version = requireVersion(command.measurementVersionId(), companyId);
        requireMeasurementVersionLink(measurement.getId(), version);
        validateCommand(command);
        Map<String, Object> beforeData = auditData(measurement, version);
        applyTransition(measurement, version, command);
        Measurement persistedMeasurement = measurementRepository.save(measurement);
        MeasurementVersion persistedVersion = measurementVersionRepository.save(version);
        auditEventRecorder.recordDirectAction(
                AuditModule.MEASUREMENTS, AuditAction.UPDATE, "MeasurementWorkflow", persistedMeasurement.getId(),
                beforeData, auditData(persistedMeasurement, persistedVersion), metadata(origin, changeRequestId)
        );
        return new MeasurementWorkflowChangeResult(persistedMeasurement, persistedVersion, null, authorizationMode);
    }

    private void applyTransition(Measurement measurement, MeasurementVersion version, AdvanceMeasurementWorkflowCommand command) {
        switch (command.action()) {
            case SEND -> {
                measurement.markSent();
                version.markSent();
            }
            case AWAIT_EXTERNAL_ACCEPTANCE -> {
                measurement.markPendingAcceptance();
                version.markPendingAcceptance();
            }
            case RECORD_EXTERNAL_ACCEPTANCE -> {
                measurement.recordExternalAcceptance(command.externallyAccepted());
                version.recordExternalAcceptance(command.externallyAccepted(), command.externalAcceptanceOn(),
                        command.externalAcceptanceNotes());
            }
            case FINALIZE -> measurement.finalizeMeasurement();
        }
    }

    private void validateCommand(AdvanceMeasurementWorkflowCommand command) {
        if (command == null || command.action() == null) {
            throw new IllegalArgumentException("Measurement workflow action is required");
        }
        if (command.action() == MeasurementWorkflowAction.RECORD_EXTERNAL_ACCEPTANCE) {
            if (command.externallyAccepted() == null || command.externalAcceptanceOn() == null) {
                throw new IllegalArgumentException("External acceptance result and date are required");
            }
        } else if (command.externallyAccepted() != null || command.externalAcceptanceOn() != null
                || command.externalAcceptanceNotes() != null) {
            throw new IllegalArgumentException("External acceptance data is only allowed when recording an external acceptance");
        }
    }

    private Measurement requireMeasurement(UUID measurementId, UUID companyId) {
        return measurementRepository.findByIdAndCompanyId(measurementId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement"));
    }

    private MeasurementVersion requireVersion(UUID versionId, UUID companyId) {
        return measurementVersionRepository.findByIdAndCompanyId(versionId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement version"));
    }

    private void requireMeasurementVersionLink(UUID measurementId, MeasurementVersion version) {
        if (!measurementId.equals(version.getMeasurementId())) {
            throw new TenantResourceNotFoundException("Measurement version");
        }
    }

    private Map<String, Object> proposal(AdvanceMeasurementWorkflowCommand command) {
        Map<String, Object> proposal = new HashMap<>();
        proposal.put("measurementId", command.measurementId().toString());
        proposal.put("measurementVersionId", command.measurementVersionId().toString());
        proposal.put("action", command.action().name());
        proposal.put("externallyAccepted", command.externallyAccepted());
        proposal.put("externalAcceptanceOn", dateValue(command.externalAcceptanceOn()));
        proposal.put("externalAcceptanceNotes", command.externalAcceptanceNotes());
        return proposal;
    }

    private AdvanceMeasurementWorkflowCommand fromProposal(Map<String, Object> proposal) {
        return new AdvanceMeasurementWorkflowCommand(
                UUID.fromString((String) proposal.get("measurementId")),
                UUID.fromString((String) proposal.get("measurementVersionId")),
                MeasurementWorkflowAction.valueOf((String) proposal.get("action")),
                (Boolean) proposal.get("externallyAccepted"),
                date((String) proposal.get("externalAcceptanceOn")),
                (String) proposal.get("externalAcceptanceNotes"),
                null
        );
    }

    private Map<String, Object> auditData(Measurement measurement, MeasurementVersion version) {
        Map<String, Object> data = new HashMap<>();
        data.put("measurementStatus", measurement.getStatus().name());
        data.put("measurementVersionId", version.getId().toString());
        data.put("measurementVersionStatus", version.getStatus().name());
        data.put("externalAcceptanceOn", dateValue(version.getExternalAcceptanceOn()));
        data.put("externalAcceptanceNotes", version.getExternalAcceptanceNotes());
        return data;
    }

    private Map<String, Object> metadata(String origin, UUID changeRequestId) {
        return changeRequestId == null
                ? Map.of("origin", origin)
                : Map.of("origin", origin, "changeRequestId", changeRequestId.toString());
    }

    private String dateValue(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private LocalDate date(String value) {
        return value == null ? null : LocalDate.parse(value);
    }
}
