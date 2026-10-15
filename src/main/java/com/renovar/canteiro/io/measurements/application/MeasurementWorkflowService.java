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
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeasurementWorkflowService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final MeasurementRepository measurementRepository;
    private final MeasurementVersionRepository measurementVersionRepository;
    private final MeasurementItemRepository measurementItemRepository;
    private final com.renovar.canteiro.io.measurements.domain.MeasurementServiceRepository measurementServiceRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;
    private final com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository continuity;
    private final MeasurementOriginRecognitionService origins;
    private final MeasurementCompositionCoordinator coordinator;
    private final com.renovar.canteiro.io.contracts.domain.ContractRepository contractRepository;

    @Transactional
    public MeasurementWorkflowChangeResult advance(AdvanceMeasurementWorkflowCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        continuity.lockMeasurement(companyId, command.measurementId());
        MeasurementVersion version = requireVersion(command.measurementVersionId(), companyId);
        requireMeasurementVersionLink(command.measurementId(), version);
        validateCommand(companyId, version, command);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.MEASUREMENTS, ChangeOperation.UPDATE
        );
        if (version.getStatus() == com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus.ACCEPTED
                && command.action() == MeasurementWorkflowAction.RECORD_EXTERNAL_ACCEPTANCE
                && Boolean.TRUE.equals(command.externallyAccepted())) {
            if (!java.util.Objects.equals(version.getExternalAcceptanceOn(), command.externalAcceptanceOn())
                    || !java.util.Objects.equals(version.getExternalAcceptanceNotes(), command.externalAcceptanceNotes())) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, "The acceptance was already recorded with different data");
            }
            coordinator.coordinate(command.measurementId());
            return new MeasurementWorkflowChangeResult(requireMeasurement(command.measurementId(), companyId), version, null, mode);
        }
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest changeRequest = changeRequestService.createIdempotent(new CreateChangeRequestCommand(
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
        continuity.lockMeasurement(companyId, measurement.getId());
        MeasurementVersion version = requireVersion(command.measurementVersionId(), companyId);
        requireMeasurementVersionLink(measurement.getId(), version);
        validateCommand(companyId, version, command);
        if(version.getStatus()==com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus.ACCEPTED
            &&command.action()==MeasurementWorkflowAction.RECORD_EXTERNAL_ACCEPTANCE&&Boolean.TRUE.equals(command.externallyAccepted())) {
            if(!java.util.Objects.equals(version.getExternalAcceptanceOn(),command.externalAcceptanceOn())
                ||!java.util.Objects.equals(version.getExternalAcceptanceNotes(),command.externalAcceptanceNotes()))
                throw new ApiException(HttpStatus.CONFLICT,ErrorCode.BUSINESS_RULE_VIOLATION,"Acceptance already recorded with different data");
            coordinator.coordinate(measurement.getId());
            return new MeasurementWorkflowChangeResult(measurement,version,null,authorizationMode);
        }
        Map<String, Object> beforeData = auditData(measurement, version);
        if (command.action() == MeasurementWorkflowAction.RECORD_EXTERNAL_ACCEPTANCE && Boolean.TRUE.equals(command.externallyAccepted())) {
            if (measurement.getContractId()!=null) {
                contractRepository.findByIdAndCompanyIdWithBillingLock(measurement.getContractId(),companyId)
                        .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
            }
            origins.ensureBindings(companyId, version);
            origins.validateCorrections(companyId, version);
        }
        applyTransition(measurement, version, command);
        Measurement persistedMeasurement = measurementRepository.save(measurement);
        MeasurementVersion persistedVersion = measurementVersionRepository.save(version);
        if (command.action() == MeasurementWorkflowAction.RECORD_EXTERNAL_ACCEPTANCE
                && Boolean.TRUE.equals(command.externallyAccepted())) {
            origins.recognize(companyId, persistedVersion);
            coordinator.coordinate(measurement.getId());
        }
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
                        command.externalAcceptanceNotes(), command.correctionItemIds());
            }
            case FINALIZE -> {
                if (measurement.getWorkId() == null || measurement.getContractId() == null) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                            "Measurement must be linked to a work and contract before finalization");
                }
                measurement.finalizeMeasurement();
            }
        }
    }

    private void validateCommand(UUID companyId, MeasurementVersion version, AdvanceMeasurementWorkflowCommand command) {
        if (command == null || command.action() == null) {
            throw new IllegalArgumentException("Measurement workflow action is required");
        }
        if (command.action() == MeasurementWorkflowAction.RECORD_EXTERNAL_ACCEPTANCE) {
            if (command.externallyAccepted() == null || command.externalAcceptanceOn() == null) {
                throw new IllegalArgumentException("External acceptance result and date are required");
            }
            if (Boolean.FALSE.equals(command.externallyAccepted())
                    && (command.externalAcceptanceNotes() == null || command.externalAcceptanceNotes().isBlank())) {
                throw new IllegalArgumentException("External acceptance notes are required when requesting corrections");
            }
            if (Boolean.TRUE.equals(command.externallyAccepted()) && !correctionItemIds(command).isEmpty()) {
                throw new IllegalArgumentException("Correction items can only be supplied for an external rejection");
            }
            validateCorrectionItems(companyId, version, correctionItemIds(command));
        } else if (command.externallyAccepted() != null || command.externalAcceptanceOn() != null
                || command.externalAcceptanceNotes() != null || !correctionItemIds(command).isEmpty()) {
            throw new IllegalArgumentException("External acceptance data is only allowed when recording an external acceptance");
        }
    }

    private void materializeServiceGroups(UUID companyId, UUID versionId) {
        Set<String> existingTitles = measurementServiceRepository
                .findByMeasurementVersionIdAndCompanyId(versionId, companyId).stream()
                .map(com.renovar.canteiro.io.measurements.domain.MeasurementService::getServiceTitle)
                .collect(java.util.stream.Collectors.toSet());
        measurementItemRepository.findByMeasurementVersionIdAndCompanyId(versionId, companyId).stream()
                .filter(MeasurementItem::isActive)
                .collect(java.util.stream.Collectors.groupingBy(MeasurementItem::getServiceTitle, java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.minBy(java.util.Comparator.comparingInt(MeasurementItem::getItemNumber))))
                .forEach((title, first) -> {
                    if (!existingTitles.contains(title)) {
                        measurementServiceRepository.save(
                                com.renovar.canteiro.io.measurements.domain.MeasurementService.create(companyId, versionId, title,
                                        first.orElseThrow().getItemNumber()));
                    }
                });
    }

    private void validateCorrectionItems(UUID companyId, MeasurementVersion version, List<UUID> correctionItemIds) {
        if (correctionItemIds.isEmpty()) return;
        if (correctionItemIds.size() != Set.copyOf(correctionItemIds).size()) {
            throw new IllegalArgumentException("Correction item identifiers must not be repeated");
        }
        Map<UUID, MeasurementItem> activeItems = measurementItemRepository
                .findByMeasurementVersionIdAndCompanyId(version.getId(), companyId).stream()
                .filter(MeasurementItem::isActive)
                .collect(java.util.stream.Collectors.toMap(MeasurementItem::getId, item -> item));
        for (UUID itemId : correctionItemIds) {
            if (itemId == null || !activeItems.containsKey(itemId)) {
                throw new TenantResourceNotFoundException("Active measurement correction item");
            }
        }
    }

    private List<UUID> correctionItemIds(AdvanceMeasurementWorkflowCommand command) {
        return command.correctionItemIds() == null ? List.of() : List.copyOf(command.correctionItemIds());
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
        proposal.put("correctionItemIds", correctionItemIds(command).stream().map(UUID::toString).toList());
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
                uuidList(proposal.get("correctionItemIds")),
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
        data.put("correctionItemIds", version.getCorrectionItemIds().stream().map(UUID::toString).toList());
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

    private List<UUID> uuidList(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().map(String::valueOf).map(UUID::fromString).toList();
    }
}
