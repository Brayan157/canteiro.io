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
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscount;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmountCalculator;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmounts;
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
public class MeasurementDiscountService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final MeasurementVersionRepository measurementVersionRepository;
    private final MeasurementItemRepository measurementItemRepository;
    private final MeasurementDiscountRepository measurementDiscountRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public MeasurementDiscountChangeResult create(CreateMeasurementDiscountCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        requireDraftMeasurementVersion(command.measurementVersionId(), companyId);
        MeasurementDiscount.create(companyId, command.measurementVersionId(), command.discountType(), command.discountValue());
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.MEASUREMENTS, ChangeOperation.CREATE
        );
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest changeRequest = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.MEASUREMENTS,
                    ChangeRequestOperation.CREATE,
                    "MeasurementDiscount",
                    command.measurementVersionId(),
                    0,
                    new ChangeRequestSnapshot(null, new AuditPayload(proposal(command))),
                    command.justification()
            ));
            return new MeasurementDiscountChangeResult(null, null, changeRequest, mode);
        }
        return persistAndAudit(companyId, command, "direct", null, mode);
    }

    @Transactional
    public void applyApprovedCreation(ChangeRequest changeRequest) {
        CreateMeasurementDiscountCommand command = fromProposal(changeRequest.getSnapshot().proposedData().values());
        persistAndAudit(changeRequest.getCompanyId(), command, "approved-change-request", changeRequest.getId(),
                ChangeAuthorizationMode.DIRECT);
    }

    private MeasurementDiscountChangeResult persistAndAudit(UUID companyId, CreateMeasurementDiscountCommand command,
                                                             String origin, UUID changeRequestId,
                                                             ChangeAuthorizationMode authorizationMode) {
        requireDraftMeasurementVersion(command.measurementVersionId(), companyId);
        if (measurementDiscountRepository.findByMeasurementVersionIdAndCompanyId(command.measurementVersionId(), companyId)
                .isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "A measurement version can have only one header discount");
        }
        MeasurementDiscount discount = MeasurementDiscount.create(companyId, command.measurementVersionId(),
                command.discountType(), command.discountValue());
        MeasurementVersionAmounts amounts = MeasurementVersionAmountCalculator.calculate(
                measurementItemRepository.findByMeasurementVersionIdAndCompanyId(command.measurementVersionId(), companyId),
                discount
        );
        MeasurementDiscount persistedDiscount = measurementDiscountRepository.save(discount);
        auditEventRecorder.recordDirectAction(
                AuditModule.MEASUREMENTS, AuditAction.CREATE, "MeasurementDiscount", persistedDiscount.getId(), null,
                auditData(persistedDiscount, amounts), metadata(origin, changeRequestId)
        );
        return new MeasurementDiscountChangeResult(persistedDiscount, amounts, null, authorizationMode);
    }

    private void requireDraftMeasurementVersion(UUID measurementVersionId, UUID companyId) {
        var version = measurementVersionId == null ? null : measurementVersionRepository
                .findWithLockByIdAndCompanyId(measurementVersionId, companyId).orElse(null);
        if (version == null) {
            throw new TenantResourceNotFoundException("Measurement version");
        }
        if (version.getStatus() != MeasurementVersionStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "A measurement header discount can only be changed while the version is draft");
        }
    }

    private Map<String, Object> proposal(CreateMeasurementDiscountCommand command) {
        return Map.of(
                "measurementVersionId", command.measurementVersionId().toString(),
                "discountType", command.discountType().name(),
                "discountValue", command.discountValue().toPlainString()
        );
    }

    private CreateMeasurementDiscountCommand fromProposal(Map<String, Object> proposal) {
        return new CreateMeasurementDiscountCommand(
                UUID.fromString((String) proposal.get("measurementVersionId")),
                com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType.valueOf(
                        (String) proposal.get("discountType")),
                new BigDecimal((String) proposal.get("discountValue")),
                null
        );
    }

    private Map<String, Object> auditData(MeasurementDiscount discount, MeasurementVersionAmounts amounts) {
        return Map.of(
                "measurementVersionId", discount.getMeasurementVersionId().toString(),
                "discountType", discount.getDiscountType().name(),
                "discountValue", discount.getDiscountValue().toPlainString(),
                "grossAmount", amounts.grossAmount().toPlainString(),
                "discountAmount", amounts.discountAmount().toPlainString(),
                "netAmount", amounts.netAmount().toPlainString()
        );
    }

    private Map<String, Object> metadata(String origin, UUID changeRequestId) {
        return changeRequestId == null
                ? Map.of("origin", origin)
                : Map.of("origin", origin, "changeRequestId", changeRequestId.toString());
    }
}
