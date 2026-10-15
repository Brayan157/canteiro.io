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
public class MeasurementCancellationService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final MeasurementRepository measurementRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public MeasurementCancellationResult cancel(UUID measurementId, String justification) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        Measurement measurement = requireMeasurement(measurementId, companyId);
        validateCanCancel(measurement);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.MEASUREMENTS, ChangeOperation.CANCEL);
        if (measurement.getStatus() == MeasurementStatus.CANCELLED) {
            return new MeasurementCancellationResult(null, mode);
        }
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.MEASUREMENTS,
                    ChangeRequestOperation.CANCEL,
                    "Measurement",
                    measurement.getId(),
                    measurement.getLockVersion(),
                    new ChangeRequestSnapshot(new AuditPayload(auditData(measurement)),
                            new AuditPayload(Map.of("status", MeasurementStatus.CANCELLED.name()))),
                    justification
            ));
            return new MeasurementCancellationResult(request, mode);
        }
        applyAndAudit(companyId, measurementId, "direct", null);
        return new MeasurementCancellationResult(null, mode);
    }

    @Transactional
    public void applyApprovedCancellation(ChangeRequest request) {
        applyAndAudit(request.getCompanyId(), request.getEntityId(), "approved-change-request", request.getId());
    }

    private void applyAndAudit(UUID companyId, UUID measurementId, String origin, UUID changeRequestId) {
        Measurement measurement = requireMeasurement(measurementId, companyId);
        validateCanCancel(measurement);
        if (measurement.getStatus() == MeasurementStatus.CANCELLED) {
            return;
        }
        Map<String, Object> before = auditData(measurement);
        measurement.cancel();
        Measurement persisted = measurementRepository.save(measurement);
        auditEventRecorder.recordDirectAction(AuditModule.MEASUREMENTS, AuditAction.CANCEL, "Measurement",
                persisted.getId(), before, auditData(persisted), metadata(origin, changeRequestId));
    }

    private Measurement requireMeasurement(UUID measurementId, UUID companyId) {
        return measurementRepository.findByIdAndCompanyId(measurementId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement"));
    }

    private void validateCanCancel(Measurement measurement) {
        if (measurement.getContractId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Measurement linked to a contract cannot be cancelled");
        }
    }

    private Map<String, Object> auditData(Measurement measurement) {
        Map<String, Object> data = new HashMap<>();
        data.put("workId", uuidValue(measurement.getWorkId()));
        data.put("contractId", uuidValue(measurement.getContractId()));
        data.put("title", measurement.getTitle());
        data.put("status", measurement.getStatus().name());
        return data;
    }

    private Map<String, Object> metadata(String origin, UUID changeRequestId) {
        return changeRequestId == null ? Map.of("origin", origin)
                : Map.of("origin", origin, "changeRequestId", changeRequestId.toString());
    }

    private String uuidValue(UUID value) {
        return value == null ? null : value.toString();
    }
}
