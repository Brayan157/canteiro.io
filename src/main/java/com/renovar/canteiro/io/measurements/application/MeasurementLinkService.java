package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
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
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeasurementLinkService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final MeasurementRepository measurementRepository;
    private final WorkRepository workRepository;
    private final ContractRepository contractRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;
    private final com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository continuity;
    private final MeasurementCompositionCoordinator coordinator;

    @Transactional
    public MeasurementLinkResult link(LinkMeasurementCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        continuity.lockMeasurement(companyId, command.measurementId());
        Measurement measurement = requireMeasurement(command.measurementId(), companyId);
        validateTarget(command, companyId);
        validateCurrentLink(measurement, command);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.MEASUREMENTS, ChangeOperation.UPDATE);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = changeRequestService.createIdempotent(new CreateChangeRequestCommand(
                    AuditModule.MEASUREMENTS,
                    ChangeRequestOperation.UPDATE,
                    "MeasurementWorkLink",
                    measurement.getId(),
                    measurement.getLockVersion(),
                    new ChangeRequestSnapshot(new AuditPayload(auditData(measurement)),
                            new AuditPayload(proposal(command))),
                    command.justification()
            ));
            return new MeasurementLinkResult(null, request, mode);
        }
        return applyAndAudit(companyId, command, "direct", null, mode);
    }

    @Transactional
    public void applyApprovedUpdate(ChangeRequest request) {
        Map<String, Object> values = request.getSnapshot().proposedData().values();
        LinkMeasurementCommand command = new LinkMeasurementCommand(
                UUID.fromString((String) values.get("measurementId")),
                UUID.fromString((String) values.get("workId")),
                uuid((String) values.get("contractId")),
                null
        );
        applyAndAudit(request.getCompanyId(), command, "approved-change-request", request.getId(),
                ChangeAuthorizationMode.DIRECT);
    }

    private MeasurementLinkResult applyAndAudit(UUID companyId, LinkMeasurementCommand command, String origin,
                                                 UUID changeRequestId, ChangeAuthorizationMode mode) {
        continuity.lockMeasurement(companyId, command.measurementId());
        Measurement measurement = requireMeasurement(command.measurementId(), companyId);
        validateTarget(command, companyId);
        validateCurrentLink(measurement, command);
        Map<String, Object> before = auditData(measurement);
        measurement.linkToWork(command.workId(), command.contractId());
        Measurement persisted = measurementRepository.save(measurement);
        auditEventRecorder.recordDirectAction(AuditModule.MEASUREMENTS, AuditAction.UPDATE, "Measurement",
                persisted.getId(), before, auditData(persisted), metadata(origin, changeRequestId));
        coordinator.coordinate(persisted.getId());
        return new MeasurementLinkResult(persisted, null, mode);
    }

    private void validateTarget(LinkMeasurementCommand command, UUID companyId) {
        if (command == null || command.measurementId() == null || command.workId() == null) {
            throw new IllegalArgumentException("Measurement and work are required for linking");
        }
        if (workRepository.findByIdAndCompanyId(command.workId(), companyId).isEmpty()) {
            throw new TenantResourceNotFoundException("Work");
        }
        if (command.contractId() == null) {
            return;
        }
        Contract contract = contractRepository.findByIdAndCompanyId(command.contractId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        requireMeasurement(command.measurementId(), companyId).getCurrency().requireSame(contract.getCurrency());
        if (!command.workId().equals(contract.getWorkId())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Measurement contract must belong to the selected work");
        }
    }

    private void validateCurrentLink(Measurement measurement, LinkMeasurementCommand command) {
        if (measurement.getWorkId() != null && !measurement.getWorkId().equals(command.workId())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Measurement work link cannot be changed");
        }
        if (measurement.getContractId() != null && command.contractId() != null
                && !measurement.getContractId().equals(command.contractId())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Measurement contract link cannot be changed");
        }
        if (measurement.getStatus() == com.renovar.canteiro.io.measurements.domain.MeasurementStatus.FINALIZED
                || measurement.getStatus() == com.renovar.canteiro.io.measurements.domain.MeasurementStatus.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Finalized or cancelled measurement cannot change its work link");
        }
    }

    private Measurement requireMeasurement(UUID measurementId, UUID companyId) {
        return measurementRepository.findByIdAndCompanyId(measurementId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement"));
    }

    private Map<String, Object> proposal(LinkMeasurementCommand command) {
        Map<String, Object> data = new HashMap<>();
        data.put("measurementId", command.measurementId().toString());
        data.put("workId", command.workId().toString());
        data.put("contractId", uuidValue(command.contractId()));
        return data;
    }

    private Map<String, Object> auditData(Measurement measurement) {
        Map<String, Object> data = new HashMap<>();
        data.put("workId", uuidValue(measurement.getWorkId()));
        data.put("contractId", uuidValue(measurement.getContractId()));
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

    private UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
