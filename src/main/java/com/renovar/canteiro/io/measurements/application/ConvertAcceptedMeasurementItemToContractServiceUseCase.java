package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
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
import com.renovar.canteiro.io.measurements.domain.MeasurementContractAdjustment;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractAdjustmentRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemContractServiceConversion;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemContractServiceConversionRepository;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConvertAcceptedMeasurementItemToContractServiceUseCase {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final MeasurementRepository measurementRepository;
    private final MeasurementVersionRepository measurementVersionRepository;
    private final MeasurementItemRepository measurementItemRepository;
    private final ContractRepository contractRepository;
    private final ContractServiceRepository contractServiceRepository;
    private final MeasurementItemContractServiceConversionRepository conversionRepository;
    private final MeasurementDiscountRepository measurementDiscountRepository;
    private final MeasurementContractAdjustmentRepository adjustmentRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public MeasurementItemConversionResult convert(ConvertAcceptedMeasurementItemCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        ConversionContext context = requireAcceptedContext(companyId, command);
        ChangeAuthorizationMode authorizationMode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.SERVICES, ChangeOperation.CREATE
        );
        MeasurementItemContractServiceConversion existing = conversionRepository
                .findByMeasurementItemIdAndCompanyId(context.item().getId(), companyId)
                .orElse(null);
        if (existing != null) {
            return new MeasurementItemConversionResult(requireContractService(existing.getContractServiceId(), companyId), null,
                    authorizationMode, true);
        }
        if (authorizationMode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest changeRequest = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.SERVICES,
                    ChangeRequestOperation.CREATE,
                    "MeasurementItemContractServiceConversion",
                    context.item().getId(),
                    0,
                    new ChangeRequestSnapshot(new AuditPayload(auditData(context)), new AuditPayload(proposal(command))),
                    command.justification()
            ));
            return new MeasurementItemConversionResult(null, changeRequest, authorizationMode, false);
        }
        return persistAndAudit(companyId, command, "direct", null, authorizationMode);
    }

    @Transactional
    public void applyApprovedCreation(ChangeRequest changeRequest) {
        Map<String, Object> proposal = changeRequest.getSnapshot().proposedData().values();
        ConvertAcceptedMeasurementItemCommand command = new ConvertAcceptedMeasurementItemCommand(
                UUID.fromString((String) proposal.get("measurementId")),
                UUID.fromString((String) proposal.get("measurementVersionId")),
                UUID.fromString((String) proposal.get("measurementItemId")),
                null
        );
        persistAndAudit(changeRequest.getCompanyId(), command, "approved-change-request", changeRequest.getId(),
                ChangeAuthorizationMode.DIRECT);
    }

    private MeasurementItemConversionResult persistAndAudit(UUID companyId, ConvertAcceptedMeasurementItemCommand command,
                                                             String origin, UUID changeRequestId,
                                                             ChangeAuthorizationMode authorizationMode) {
        ConversionContext context = requireAcceptedContext(companyId, command);
        MeasurementItemContractServiceConversion existing = conversionRepository
                .findByMeasurementItemIdAndCompanyId(context.item().getId(), companyId)
                .orElse(null);
        if (existing != null) {
            return new MeasurementItemConversionResult(requireContractService(existing.getContractServiceId(), companyId), null,
                    authorizationMode, true);
        }
        ContractService contractService = contractServiceRepository.save(ContractService.create(
                companyId, context.contract().getId(), context.item().getActivity(), context.item().getDescription(),
                ContractServiceStatus.ACTIVE, serviceQuantity(context.item()), context.item().getUnitPrice()
        ));
        conversionRepository.save(MeasurementItemContractServiceConversion.create(
                companyId, context.version().getId(), context.item().getId(), context.contract().getId(), contractService.getId()
        ));
        createHeaderAdjustmentWhenVersionIsFullyConverted(companyId, context);
        auditEventRecorder.recordDirectAction(AuditModule.SERVICES, AuditAction.CREATE, "ContractService", contractService.getId(),
                null, Map.of("contractId", context.contract().getId().toString(),
                        "measurementId", context.measurement().getId().toString(),
                        "measurementVersionId", context.version().getId().toString(),
                        "measurementItemId", context.item().getId().toString(),
                        "netAmount", contractService.getNetAmount()),
                metadata(origin, changeRequestId));
        return new MeasurementItemConversionResult(contractService, null, authorizationMode, false);
    }

    private ConversionContext requireAcceptedContext(UUID companyId, ConvertAcceptedMeasurementItemCommand command) {
        if (command == null || command.measurementId() == null || command.measurementVersionId() == null
                || command.measurementItemId() == null) {
            throw new IllegalArgumentException("Measurement, version and item are required for conversion");
        }
        Measurement measurement = measurementRepository.findByIdAndCompanyId(command.measurementId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement"));
        MeasurementVersion version = measurementVersionRepository.findWithLockByIdAndCompanyId(command.measurementVersionId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement version"));
        MeasurementItem item = measurementItemRepository.findWithLockByIdAndCompanyId(command.measurementItemId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement item"));
        if (!measurement.getId().equals(version.getMeasurementId()) || !version.getId().equals(item.getMeasurementVersionId())) {
            throw new TenantResourceNotFoundException("Measurement item");
        }
        if (version.getStatus() != MeasurementVersionStatus.ACCEPTED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Only an accepted measurement item can be converted to a contract service");
        }
        if (measurement.getContractId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "An accepted measurement must be linked to a contract before converting its items");
        }
        Contract contract = contractRepository.findByIdAndCompanyId(measurement.getContractId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        if (!measurement.getWorkId().equals(contract.getWorkId())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Measurement contract must belong to the same work");
        }
        return new ConversionContext(measurement, version, item, contract);
    }

    private ContractService requireContractService(UUID contractServiceId, UUID companyId) {
        return contractServiceRepository.findByIdAndCompanyId(contractServiceId, companyId)
                .orElseThrow(() -> new IllegalStateException("Measurement conversion references a missing contract service"));
    }

    private BigDecimal serviceQuantity(MeasurementItem item) {
        return switch (item.getChargeType()) {
            case SQUARE_METER -> item.getAreaSquareMeters();
            case LINEAR_METER -> item.getLinearMeters();
            case KILOGRAM_PER_SQUARE_METER, KILOGRAM_PER_LINEAR_METER -> item.getTotalWeightKg();
        };
    }

    private void createHeaderAdjustmentWhenVersionIsFullyConverted(UUID companyId, ConversionContext context) {
        if (adjustmentRepository.findByMeasurementVersionIdAndCompanyId(context.version().getId(), companyId).isPresent()) {
            return;
        }
        var items = measurementItemRepository.findByMeasurementVersionIdAndCompanyId(context.version().getId(), companyId);
        int convertedItems = conversionRepository.findByMeasurementVersionIdAndCompanyId(context.version().getId(), companyId).size();
        if (convertedItems != items.size()) {
            return;
        }
        var discount = measurementDiscountRepository.findByMeasurementVersionIdAndCompanyId(context.version().getId(), companyId)
                .orElse(null);
        if (discount == null) {
            return;
        }
        BigDecimal amount = MeasurementVersionAmountCalculator.calculate(items, discount).discountAmount();
        if (amount.signum() > 0) {
            adjustmentRepository.save(MeasurementContractAdjustment.create(companyId, context.version().getId(),
                    context.contract().getId(), amount));
        }
    }

    private Map<String, Object> proposal(ConvertAcceptedMeasurementItemCommand command) {
        return Map.of("measurementId", command.measurementId().toString(),
                "measurementVersionId", command.measurementVersionId().toString(),
                "measurementItemId", command.measurementItemId().toString());
    }

    private Map<String, Object> auditData(ConversionContext context) {
        return Map.of("measurementId", context.measurement().getId().toString(),
                "measurementVersionId", context.version().getId().toString(),
                "measurementItemId", context.item().getId().toString(),
                "contractId", context.contract().getId().toString(),
                "measurementStatus", context.measurement().getStatus().name(),
                "measurementVersionStatus", context.version().getStatus().name());
    }

    private Map<String, Object> metadata(String origin, UUID changeRequestId) {
        return changeRequestId == null ? Map.of("origin", origin)
                : Map.of("origin", origin, "changeRequestId", changeRequestId.toString());
    }

    private record ConversionContext(Measurement measurement, MeasurementVersion version, MeasurementItem item,
                                     Contract contract) {
    }
}
