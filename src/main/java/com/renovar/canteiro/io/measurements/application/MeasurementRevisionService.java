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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeasurementRevisionService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final MeasurementRepository measurementRepository;
    private final MeasurementVersionRepository measurementVersionRepository;
    private final MeasurementItemRepository measurementItemRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public MeasurementRevisionChangeResult create(CreateMeasurementRevisionCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        Measurement measurement = requireMeasurement(command.measurementId(), companyId);
        MeasurementVersion latestVersion = requireRevisableLatestVersion(measurement, companyId, null);
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
                                    "revisionScope", revisionScope(latestVersion)))),
                    command.justification()
            ));
            return new MeasurementRevisionChangeResult(null, null, List.of(), changeRequest, mode);
        }
        return persistAndAudit(companyId, command.measurementId(), latestVersion.getId(), "direct", null, mode);
    }

    @Transactional
    public void applyApprovedCreation(ChangeRequest changeRequest) {
        String measurementId = (String) changeRequest.getSnapshot().proposedData().values().get("measurementId");
        String previousVersionId = (String) changeRequest.getSnapshot().proposedData().values().get("previousVersionId");
        persistAndAudit(changeRequest.getCompanyId(), UUID.fromString(measurementId), UUID.fromString(previousVersionId),
                "approved-change-request",
                changeRequest.getId(), ChangeAuthorizationMode.DIRECT);
    }

    private MeasurementRevisionChangeResult persistAndAudit(UUID companyId, UUID measurementId, UUID expectedPreviousVersionId,
                                                             String origin,
                                                             UUID changeRequestId,
                                                             ChangeAuthorizationMode authorizationMode) {
        Measurement measurement = requireMeasurement(measurementId, companyId);
        MeasurementVersion latestVersion = requireRevisableLatestVersion(measurement, companyId, expectedPreviousVersionId);
        Map<String, Object> beforeData = measurementAuditData(measurement, latestVersion);
        measurement.startRevision();
        Measurement persistedMeasurement = measurementRepository.save(measurement);
        MeasurementVersion revision = measurementVersionRepository.save(MeasurementVersion.createRevision(
                companyId, persistedMeasurement.getId(), latestVersion.getVersionNumber() + 1, latestVersion.getId()
        ));
        List<MeasurementItem> revisionItems = latestVersion.getStatus() == MeasurementVersionStatus.REJECTED
                ? copyActiveRejectedItems(companyId, latestVersion, revision)
                : List.of();
        auditEventRecorder.recordDirectAction(
                AuditModule.MEASUREMENTS, AuditAction.UPDATE, "MeasurementRevision", persistedMeasurement.getId(),
                beforeData, measurementAuditData(persistedMeasurement, revision),
                metadata(origin, changeRequestId, revisionScope(latestVersion), revisionItems.size())
        );
        return new MeasurementRevisionChangeResult(persistedMeasurement, revision, revisionItems, null, authorizationMode);
    }

    private Measurement requireMeasurement(UUID measurementId, UUID companyId) {
        return measurementRepository.findByIdAndCompanyId(measurementId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement"));
    }

    private MeasurementVersion requireRevisableLatestVersion(Measurement measurement, UUID companyId,
                                                              UUID expectedPreviousVersionId) {
        if (measurement.getStatus() != MeasurementStatus.ACCEPTED && measurement.getStatus() != MeasurementStatus.REJECTED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Only an accepted or rejected measurement can receive a revision");
        }
        MeasurementVersion latestVersion = measurementVersionRepository
                .findLatestByMeasurementIdAndCompanyId(measurement.getId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement version"));
        if (latestVersion.getStatus() != MeasurementVersionStatus.ACCEPTED
                && latestVersion.getStatus() != MeasurementVersionStatus.REJECTED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Only an accepted or rejected measurement version can originate a revision");
        }
        if (expectedPreviousVersionId != null && !expectedPreviousVersionId.equals(latestVersion.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Measurement version changed before the revision could be applied");
        }
        return latestVersion;
    }

    private List<MeasurementItem> copyActiveRejectedItems(UUID companyId, MeasurementVersion rejectedVersion,
                                                           MeasurementVersion revision) {
        return measurementItemRepository.findByMeasurementVersionIdAndCompanyId(rejectedVersion.getId(), companyId).stream()
                .filter(MeasurementItem::isActive)
                .sorted(Comparator.comparingInt(MeasurementItem::getItemNumber))
                .map(item -> measurementItemRepository.save(item.copyToRevision(revision.getId(), item.getId())))
                .toList();
    }

    private Map<String, Object> measurementAuditData(Measurement measurement, MeasurementVersion version) {
        Map<String, Object> data = new HashMap<>();
        data.put("measurementStatus", measurement.getStatus().name());
        data.put("versionId", version.getId().toString());
        data.put("versionNumber", version.getVersionNumber());
        data.put("versionStatus", version.getStatus().name());
        data.put("previousVersionId", version.getPreviousVersionId() == null ? null : version.getPreviousVersionId().toString());
        data.put("externalAcceptanceNotes", version.getExternalAcceptanceNotes());
        data.put("correctionItemIds", version.getCorrectionItemIds().stream().map(UUID::toString).toList());
        return data;
    }

    private Map<String, Object> metadata(String origin, UUID changeRequestId, String revisionScope, int copiedItemCount) {
        Map<String, Object> values = new HashMap<>();
        values.put("origin", origin);
        values.put("revisionScope", revisionScope);
        values.put("copiedItemCount", copiedItemCount);
        if (changeRequestId != null) values.put("changeRequestId", changeRequestId.toString());
        return values;
    }

    private String revisionScope(MeasurementVersion previousVersion) {
        return previousVersion.getStatus() == MeasurementVersionStatus.REJECTED
                ? "external-rejection-corrections"
                : "incremental-additions";
    }

}
