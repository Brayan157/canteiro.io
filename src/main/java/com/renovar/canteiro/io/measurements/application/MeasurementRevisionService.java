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
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeasurementRevisionService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final MeasurementRepository measurementRepository;
    private final MeasurementVersionRepository measurementVersionRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public MeasurementRevisionChangeResult create(CreateMeasurementRevisionCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        Measurement measurement = requireMeasurement(command.measurementId(), companyId);
        MeasurementVersion latestVersion = requireAcceptedLatestVersion(measurement, companyId);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.MEASUREMENTS, ChangeOperation.UPDATE
        );
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest changeRequest = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.MEASUREMENTS,
                    ChangeRequestOperation.UPDATE,
                    "MeasurementRevision",
                    measurement.getId(),
                    measurement.getLockVersion(),
                    new ChangeRequestSnapshot(new AuditPayload(measurementAuditData(measurement, latestVersion)),
                            new AuditPayload(Map.of("measurementId", measurement.getId().toString(),
                                    "previousVersionId", latestVersion.getId().toString(),
                                    "revisionScope", "incremental-additions"))),
                    command.justification()
            ));
            return new MeasurementRevisionChangeResult(null, null, changeRequest, mode);
        }
        return persistAndAudit(companyId, command.measurementId(), "direct", null, mode);
    }

    @Transactional
    public void applyApprovedCreation(ChangeRequest changeRequest) {
        String measurementId = (String) changeRequest.getSnapshot().proposedData().values().get("measurementId");
        persistAndAudit(changeRequest.getCompanyId(), UUID.fromString(measurementId), "approved-change-request",
                changeRequest.getId(), ChangeAuthorizationMode.DIRECT);
    }

    private MeasurementRevisionChangeResult persistAndAudit(UUID companyId, UUID measurementId, String origin,
                                                             UUID changeRequestId,
                                                             ChangeAuthorizationMode authorizationMode) {
        Measurement measurement = requireMeasurement(measurementId, companyId);
        MeasurementVersion latestVersion = requireAcceptedLatestVersion(measurement, companyId);
        Map<String, Object> beforeData = measurementAuditData(measurement, latestVersion);
        measurement.startRevision();
        Measurement persistedMeasurement = measurementRepository.save(measurement);
        MeasurementVersion revision = measurementVersionRepository.save(MeasurementVersion.createRevision(
                companyId, persistedMeasurement.getId(), latestVersion.getVersionNumber() + 1, latestVersion.getId()
        ));
        auditEventRecorder.recordDirectAction(
                AuditModule.MEASUREMENTS, AuditAction.UPDATE, "MeasurementRevision", persistedMeasurement.getId(),
                beforeData, measurementAuditData(persistedMeasurement, revision), metadata(origin, changeRequestId)
        );
        return new MeasurementRevisionChangeResult(persistedMeasurement, revision, null, authorizationMode);
    }

    private Measurement requireMeasurement(UUID measurementId, UUID companyId) {
        return measurementRepository.findByIdAndCompanyId(measurementId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement"));
    }

    private MeasurementVersion requireAcceptedLatestVersion(Measurement measurement, UUID companyId) {
        if (measurement.getStatus() != MeasurementStatus.ACCEPTED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Only an accepted measurement can receive an incremental revision");
        }
        MeasurementVersion latestVersion = measurementVersionRepository
                .findLatestByMeasurementIdAndCompanyId(measurement.getId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement version"));
        if (latestVersion.getStatus() != MeasurementVersionStatus.ACCEPTED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Only an accepted measurement version can originate a revision");
        }
        return latestVersion;
    }

    private Map<String, Object> measurementAuditData(Measurement measurement, MeasurementVersion version) {
        Map<String, Object> data = new HashMap<>();
        data.put("measurementStatus", measurement.getStatus().name());
        data.put("versionId", version.getId().toString());
        data.put("versionNumber", version.getVersionNumber());
        data.put("versionStatus", version.getStatus().name());
        data.put("previousVersionId", version.getPreviousVersionId() == null ? null : version.getPreviousVersionId().toString());
        return data;
    }

    private Map<String, Object> metadata(String origin, UUID changeRequestId) {
        return changeRequestId == null
                ? Map.of("origin", origin, "revisionScope", "incremental-additions")
                : Map.of("origin", origin, "changeRequestId", changeRequestId.toString(),
                        "revisionScope", "incremental-additions");
    }
}
