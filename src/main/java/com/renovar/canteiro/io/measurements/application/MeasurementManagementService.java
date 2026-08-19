package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
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
import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscount;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmountCalculator;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeasurementManagementService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final WorkRepository workRepository;
    private final ContractRepository contractRepository;
    private final MeasurementRepository measurementRepository;
    private final MeasurementVersionRepository measurementVersionRepository;
    private final MeasurementItemRepository measurementItemRepository;
    private final MeasurementDiscountRepository measurementDiscountRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public MeasurementCreationResult create(CreateMeasurementCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        validateMeasurementContext(companyId, command);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.MEASUREMENTS, ChangeOperation.CREATE
        );
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest changeRequest = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.MEASUREMENTS,
                    ChangeRequestOperation.CREATE,
                    "Measurement",
                    null,
                    0,
                    new ChangeRequestSnapshot(null, new AuditPayload(measurementProposal(command))),
                    command.justification()
            ));
            return new MeasurementCreationResult(null, null, changeRequest, mode);
        }
        return persistMeasurement(companyId, command, "direct", null, mode);
    }

    @Transactional
    public MeasurementItemChangeResult addItem(CreateMeasurementItemCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        validateDraftVersion(companyId, command.measurementId(), command.measurementVersionId());
        createItem(companyId, command);
        rejectDuplicateItemNumber(companyId, command);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.MEASUREMENTS, ChangeOperation.CREATE
        );
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest changeRequest = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.MEASUREMENTS,
                    ChangeRequestOperation.CREATE,
                    "MeasurementItem",
                    command.measurementVersionId(),
                    0,
                    new ChangeRequestSnapshot(null, new AuditPayload(itemProposal(command))),
                    command.justification()
            ));
            return new MeasurementItemChangeResult(null, changeRequest, mode);
        }
        return persistItem(companyId, command, "direct", null, mode);
    }

    @Transactional(readOnly = true)
    public MeasurementDetails find(UUID measurementId) {
        accessAuthorizationService.requirePermission(AccessModule.MEASUREMENTS, AccessAction.READ);
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        Measurement measurement = requireMeasurement(companyId, measurementId);
        MeasurementVersion version = measurementVersionRepository
                .findLatestByMeasurementIdAndCompanyId(measurementId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement version"));
        List<MeasurementItem> items = measurementItemRepository
                .findByMeasurementVersionIdAndCompanyId(version.getId(), companyId);
        MeasurementDiscount discount = measurementDiscountRepository
                .findByMeasurementVersionIdAndCompanyId(version.getId(), companyId).orElse(null);
        return new MeasurementDetails(measurement, version, items, discount,
                MeasurementVersionAmountCalculator.calculate(items, discount));
    }

    @Transactional(readOnly = true)
    public void validateVersionLink(UUID measurementId, UUID measurementVersionId) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        Measurement measurement = requireMeasurement(companyId, measurementId);
        MeasurementVersion version = measurementVersionRepository
                .findByIdAndCompanyId(measurementVersionId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement version"));
        if (!measurement.getId().equals(version.getMeasurementId())) {
            throw new TenantResourceNotFoundException("Measurement version");
        }
    }

    @Transactional
    public void applyApprovedMeasurementCreation(ChangeRequest changeRequest) {
        CreateMeasurementCommand command = measurementFromProposal(changeRequest.getSnapshot().proposedData().values());
        persistMeasurement(changeRequest.getCompanyId(), command, "approved-change-request", changeRequest.getId(),
                ChangeAuthorizationMode.DIRECT);
    }

    @Transactional
    public void applyApprovedItemCreation(ChangeRequest changeRequest) {
        CreateMeasurementItemCommand command = itemFromProposal(changeRequest.getSnapshot().proposedData().values());
        persistItem(changeRequest.getCompanyId(), command, "approved-change-request", changeRequest.getId(),
                ChangeAuthorizationMode.DIRECT);
    }

    private MeasurementCreationResult persistMeasurement(UUID companyId, CreateMeasurementCommand command,
                                                          String origin, UUID changeRequestId,
                                                          ChangeAuthorizationMode mode) {
        validateMeasurementContext(companyId, command);
        Measurement measurement = measurementRepository.save(Measurement.create(companyId, command.workId(),
                command.contractId(), command.reference(), command.description(), command.measuredOn()));
        MeasurementVersion version = measurementVersionRepository.save(
                MeasurementVersion.create(companyId, measurement.getId(), 1));
        auditEventRecorder.recordDirectAction(AuditModule.MEASUREMENTS, AuditAction.CREATE, "Measurement",
                measurement.getId(), null, measurementAuditData(measurement, version), metadata(origin, changeRequestId));
        return new MeasurementCreationResult(measurement, version, null, mode);
    }

    private MeasurementItemChangeResult persistItem(UUID companyId, CreateMeasurementItemCommand command,
                                                     String origin, UUID changeRequestId,
                                                     ChangeAuthorizationMode mode) {
        validateDraftVersion(companyId, command.measurementId(), command.measurementVersionId());
        rejectDuplicateItemNumber(companyId, command);
        MeasurementItem item = measurementItemRepository.save(createItem(companyId, command));
        auditEventRecorder.recordDirectAction(AuditModule.MEASUREMENTS, AuditAction.CREATE, "MeasurementItem",
                item.getId(), null, itemAuditData(item), metadata(origin, changeRequestId));
        return new MeasurementItemChangeResult(item, null, mode);
    }

    private void validateMeasurementContext(UUID companyId, CreateMeasurementCommand command) {
        if (command == null || command.workId() == null
                || workRepository.findByIdAndCompanyId(command.workId(), companyId).isEmpty()) {
            throw new TenantResourceNotFoundException("Work");
        }
        if (command.contractId() == null) {
            return;
        }
        Contract contract = contractRepository.findByIdAndCompanyId(command.contractId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        if (!contract.getWorkId().equals(command.workId())) {
            throw new TenantResourceNotFoundException("Contract");
        }
    }

    private void validateDraftVersion(UUID companyId, UUID measurementId, UUID versionId) {
        Measurement measurement = requireMeasurement(companyId, measurementId);
        MeasurementVersion version = measurementVersionRepository.findByIdAndCompanyId(versionId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement version"));
        if (!version.getMeasurementId().equals(measurement.getId())) {
            throw new TenantResourceNotFoundException("Measurement version");
        }
        if (measurement.getStatus() != MeasurementStatus.DRAFT || version.getStatus() != MeasurementVersionStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Measurement items can only be changed while the current version is draft");
        }
    }

    private Measurement requireMeasurement(UUID companyId, UUID measurementId) {
        return measurementRepository.findByIdAndCompanyId(measurementId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement"));
    }

    private void rejectDuplicateItemNumber(UUID companyId, CreateMeasurementItemCommand command) {
        boolean duplicate = measurementItemRepository
                .findByMeasurementVersionIdAndCompanyId(command.measurementVersionId(), companyId).stream()
                .anyMatch(item -> item.getItemNumber() == command.itemNumber());
        if (duplicate) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Measurement item number is already in use in this version");
        }
    }

    private MeasurementItem createItem(UUID companyId, CreateMeasurementItemCommand command) {
        if (command == null || command.chargeType() == null) {
            throw new IllegalArgumentException("Measurement item charge type is required");
        }
        return switch (command.chargeType()) {
            case SQUARE_METER -> MeasurementItem.createSquareMeter(companyId, command.measurementVersionId(),
                    command.itemNumber(), command.activity(), command.description(), command.areaSquareMeters(),
                    command.unitPrice());
            case LINEAR_METER -> MeasurementItem.createLinearMeter(companyId, command.measurementVersionId(),
                    command.itemNumber(), command.activity(), command.description(), command.linearMeters(),
                    command.unitPrice());
            case KILOGRAM_PER_SQUARE_METER -> MeasurementItem.createKilogramPerSquareMeter(companyId,
                    command.measurementVersionId(), command.itemNumber(), command.activity(), command.description(),
                    command.kilogramsPerSquareMeter(), command.areaSquareMeters(), command.unitPrice());
            case KILOGRAM_PER_LINEAR_METER -> MeasurementItem.createKilogramPerLinearMeter(companyId,
                    command.measurementVersionId(), command.itemNumber(), command.activity(), command.description(),
                    command.kilogramsPerLinearMeter(), command.linearMeters(), command.unitPrice());
        };
    }

    private Map<String, Object> measurementProposal(CreateMeasurementCommand command) {
        Map<String, Object> values = new HashMap<>();
        values.put("workId", command.workId().toString());
        values.put("contractId", uuidValue(command.contractId()));
        values.put("reference", command.reference());
        values.put("description", command.description());
        values.put("measuredOn", dateValue(command.measuredOn()));
        return values;
    }

    private Map<String, Object> itemProposal(CreateMeasurementItemCommand command) {
        Map<String, Object> values = new HashMap<>();
        values.put("measurementId", command.measurementId().toString());
        values.put("measurementVersionId", command.measurementVersionId().toString());
        values.put("itemNumber", command.itemNumber());
        values.put("activity", command.activity());
        values.put("description", command.description());
        values.put("chargeType", command.chargeType().name());
        values.put("areaSquareMeters", decimalValue(command.areaSquareMeters()));
        values.put("linearMeters", decimalValue(command.linearMeters()));
        values.put("kilogramsPerSquareMeter", decimalValue(command.kilogramsPerSquareMeter()));
        values.put("kilogramsPerLinearMeter", decimalValue(command.kilogramsPerLinearMeter()));
        values.put("unitPrice", decimalValue(command.unitPrice()));
        return values;
    }

    private CreateMeasurementCommand measurementFromProposal(Map<String, Object> values) {
        return new CreateMeasurementCommand(UUID.fromString((String) values.get("workId")),
                uuid((String) values.get("contractId")), (String) values.get("reference"),
                (String) values.get("description"), date((String) values.get("measuredOn")), null);
    }

    private CreateMeasurementItemCommand itemFromProposal(Map<String, Object> values) {
        return new CreateMeasurementItemCommand(UUID.fromString((String) values.get("measurementId")),
                UUID.fromString((String) values.get("measurementVersionId")),
                ((Number) values.get("itemNumber")).intValue(), (String) values.get("activity"),
                (String) values.get("description"), MeasurementChargeType.valueOf((String) values.get("chargeType")),
                decimal((String) values.get("areaSquareMeters")), decimal((String) values.get("linearMeters")),
                decimal((String) values.get("kilogramsPerSquareMeter")),
                decimal((String) values.get("kilogramsPerLinearMeter")), decimal((String) values.get("unitPrice")), null);
    }

    private Map<String, Object> measurementAuditData(Measurement measurement, MeasurementVersion version) {
        Map<String, Object> values = new HashMap<>();
        values.put("workId", measurement.getWorkId().toString());
        values.put("contractId", uuidValue(measurement.getContractId()));
        values.put("reference", measurement.getReference());
        values.put("description", measurement.getDescription());
        values.put("measuredOn", dateValue(measurement.getMeasuredOn()));
        values.put("status", measurement.getStatus().name());
        values.put("measurementVersionId", version.getId().toString());
        values.put("versionNumber", version.getVersionNumber());
        return values;
    }

    private Map<String, Object> itemAuditData(MeasurementItem item) {
        Map<String, Object> values = new HashMap<>();
        values.put("measurementVersionId", item.getMeasurementVersionId().toString());
        values.put("itemNumber", item.getItemNumber());
        values.put("activity", item.getActivity());
        values.put("description", item.getDescription());
        values.put("chargeType", item.getChargeType().name());
        values.put("totalWeightKg", decimalValue(item.getTotalWeightKg()));
        values.put("totalAmount", decimalValue(item.getTotalAmount()));
        return values;
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

    private String dateValue(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private LocalDate date(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    private String decimalValue(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
