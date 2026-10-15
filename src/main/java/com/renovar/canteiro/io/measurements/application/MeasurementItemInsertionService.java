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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inserts a draft item after an existing line and normalizes the active
 * sequence atomically. Two write passes (temporary numbers, then final
 * numbers) preserve the database's unique active item-number constraint.
 */
@Service
@RequiredArgsConstructor
public class MeasurementItemInsertionService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final MeasurementRepository measurementRepository;
    private final MeasurementVersionRepository measurementVersionRepository;
    private final MeasurementItemRepository measurementItemRepository;
    private final MeasurementDiscountRepository measurementDiscountRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;
    private final MeasurementContinuousServiceManagementService continuousServices;
    private final com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository continuity;

    @Transactional
    public MeasurementItemInsertionResult insert(InsertMeasurementItemCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        InsertionContext context = prepare(companyId, command);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.MEASUREMENTS, ChangeOperation.CREATE);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.MEASUREMENTS, ChangeRequestOperation.CREATE, "MeasurementItemInsertion",
                    context.anchor().getId(), context.version().getLockVersion(),
                    new ChangeRequestSnapshot(new AuditPayload(orderData(context.activeItems())),
                            new AuditPayload(proposal(command, context.version().getLockVersion()))),
                    command.justification()
            ));
            return new MeasurementItemInsertionResult(null, null, null, request, mode);
        }
        return persistPrepared(companyId, command, context, null, "direct", mode);
    }

    @Transactional
    public void applyApprovedInsertion(ChangeRequest request) {
        InsertMeasurementItemCommand command = commandFrom(request.getSnapshot().proposedData().values());
        long expectedVersion = request.getEntityVersion();
        InsertionContext context = prepare(request.getCompanyId(), command);
        if (context.version().getLockVersion() != expectedVersion) {
            conflict("Measurement version changed while the item insertion awaited approval");
        }
        persistPrepared(request.getCompanyId(), command, context, request.getId(), "approved-change-request",
                ChangeAuthorizationMode.DIRECT);
    }

    private MeasurementItemInsertionResult persistPrepared(UUID companyId, InsertMeasurementItemCommand command,
                                                            InsertionContext context, UUID changeRequestId,
                                                            String origin, ChangeAuthorizationMode mode) {
        List<MeasurementItem> before = context.activeItems();
        List<MeasurementItem> temporary = assignTemporaryNumbers(before);
        for (MeasurementItem item : temporary) {
            measurementItemRepository.save(item);
        }
        measurementItemRepository.flush();

        List<MeasurementItem> reordered = assignFinalNumbers(temporary, context.insertPosition());
        List<MeasurementItem> after = new ArrayList<>(reordered);
        MeasurementItem inserted = measurementItemRepository.save(context.newItem());
        continuousServices.bindNewItem(companyId,command.measurementId(),command.measurementVersionId(),inserted.getId(),command.continuousServiceId());
        after.add(inserted);
        after.sort(Comparator.comparingInt(MeasurementItem::getItemNumber));
        measurementItemRepository.flush();

        // Touching the version makes the ordered mutation participate in the
        // same optimistic version used by the remaining measurement workflow.
        MeasurementVersion persistedVersion = measurementVersionRepository.save(context.version());
        MeasurementDiscount discount = measurementDiscountRepository
                .findByMeasurementVersionIdAndCompanyId(context.version().getId(), companyId).orElse(null);
        var amounts = MeasurementVersionAmountCalculator.calculate(after, discount);
        auditEventRecorder.recordDirectAction(AuditModule.MEASUREMENTS, AuditAction.CREATE,
                "MeasurementItemInsertion", inserted.getId(), orderData(before), orderData(after),
                metadata(origin, changeRequestId, context.anchor().getId()));
        return new MeasurementItemInsertionResult(inserted, persistedVersion, amounts, null, mode);
    }

    private InsertionContext prepare(UUID companyId, InsertMeasurementItemCommand command) {
        continuity.lockMeasurement(companyId,command.measurementId());
        continuousServices.validateItemService(companyId,command.measurementId(),command.continuousServiceId());
        Measurement measurement = measurementRepository.findByIdAndCompanyId(command.measurementId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement"));
        MeasurementVersion version = measurementVersionRepository.findWithLockByIdAndCompanyId(command.measurementVersionId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement version"));
        if (!measurement.getId().equals(version.getMeasurementId())) {
            throw new TenantResourceNotFoundException("Measurement version");
        }
        if (measurement.getStatus() != MeasurementStatus.DRAFT || version.getStatus() != MeasurementVersionStatus.DRAFT) {
            conflict("Measurement items can only be inserted while the current version is draft");
        }
        if(!measurementVersionRepository.findLatestByMeasurementIdAndCompanyId(measurement.getId(),companyId).orElseThrow().getId().equals(version.getId()))
            conflict("Only the latest DRAFT version is editable");
        List<MeasurementItem> active = measurementItemRepository
                .findWithLockByMeasurementVersionIdAndCompanyId(version.getId(), companyId).stream()
                .filter(MeasurementItem::isActive)
                .sorted(Comparator.comparingInt(MeasurementItem::getItemNumber))
                .toList();
        MeasurementItem anchor = active.stream().filter(item -> item.getId().equals(command.insertAfterItemId()))
                .findFirst().orElseThrow(() -> new TenantResourceNotFoundException("Measurement item"));
        int insertionPosition = active.indexOf(anchor) + 1;
        MeasurementItem candidate = buildItem(companyId, version.getId(), insertionPosition + 1, command);
        return new InsertionContext(version, active, anchor, insertionPosition, candidate);
    }

    private List<MeasurementItem> assignTemporaryNumbers(List<MeasurementItem> activeItems) {
        int largest = activeItems.stream().mapToInt(MeasurementItem::getItemNumber).max().orElse(0);
        long base = (long) largest + activeItems.size() + 1L;
        if (base + activeItems.size() > Integer.MAX_VALUE) {
            conflict("Measurement item numbering capacity was reached");
        }
        List<MeasurementItem> temporary = new ArrayList<>();
        for (int index = 0; index < activeItems.size(); index++) {
            temporary.add(activeItems.get(index).withItemNumber((int) base + index));
        }
        return temporary;
    }

    private List<MeasurementItem> assignFinalNumbers(List<MeasurementItem> temporaryItems, int insertionPosition) {
        List<MeasurementItem> result = new ArrayList<>();
        for (int index = 0; index < temporaryItems.size(); index++) {
            int target = index < insertionPosition ? index + 1 : index + 2;
            result.add(measurementItemRepository.save(temporaryItems.get(index).withItemNumber(target)));
        }
        return result;
    }

    private MeasurementItem buildItem(UUID companyId, UUID versionId, int itemNumber, InsertMeasurementItemCommand command) {
        if (command.chargeType() == null) {
            throw new IllegalArgumentException("Measurement item charge type is required");
        }
        MeasurementItem item = switch (command.chargeType()) {
            case SQUARE_METER -> MeasurementItem.createSquareMeter(companyId, versionId, itemNumber, command.serviceTitle(),
                    command.activity(), command.description(), command.measuredOn(), command.areaSquareMeters(), command.unitPrice());
            case LINEAR_METER -> MeasurementItem.createLinearMeter(companyId, versionId, itemNumber, command.serviceTitle(),
                    command.activity(), command.description(), command.measuredOn(), command.linearMeters(), command.unitPrice());
            case KILOGRAM_PER_SQUARE_METER -> MeasurementItem.createKilogramPerSquareMeter(companyId, versionId, itemNumber,
                    command.serviceTitle(), command.activity(), command.description(), command.measuredOn(),
                    command.kilogramsPerSquareMeter(), command.areaSquareMeters(), command.unitPrice());
            case KILOGRAM_PER_LINEAR_METER -> MeasurementItem.createKilogramPerLinearMeter(companyId, versionId, itemNumber,
                    command.serviceTitle(), command.activity(), command.description(), command.measuredOn(),
                    command.kilogramsPerLinearMeter(), command.linearMeters(), command.unitPrice());
            case UNIT -> MeasurementItem.createUnit(companyId, versionId, itemNumber, command.serviceTitle(), command.activity(),
                    command.description(), command.measuredOn(), command.unitQuantity(), command.unitPrice());
        };
        if (command.chargeType() != MeasurementChargeType.UNIT) {
            item = item.withOperationalUnitQuantity(command.unitQuantity());
        }
        preview("previewTotalWeightKg", command.previewTotalWeightKg(),
                item.getTotalWeightKg() == null ? BigDecimal.ZERO : item.getTotalWeightKg(), 4);
        preview("previewTotalAmount", command.previewTotalAmount(), item.getTotalAmount(), 2);
        return item;
    }

    private Map<String, Object> proposal(InsertMeasurementItemCommand command, int versionLock) {
        Map<String, Object> values = new HashMap<>();
        values.put("continuousServiceId",command.continuousServiceId()==null?null:command.continuousServiceId().toString());
        values.put("measurementId", command.measurementId().toString());
        values.put("measurementVersionId", command.measurementVersionId().toString());
        values.put("insertAfterItemId", command.insertAfterItemId().toString());
        values.put("versionLock", versionLock);
        values.put("serviceTitle", command.serviceTitle());
        values.put("activity", command.activity());
        values.put("description", command.description());
        values.put("measuredOn", command.measuredOn() == null ? null : command.measuredOn().toString());
        values.put("chargeType", command.chargeType() == null ? null : command.chargeType().name());
        values.put("areaSquareMeters", decimal(command.areaSquareMeters()));
        values.put("linearMeters", decimal(command.linearMeters()));
        values.put("kilogramsPerSquareMeter", decimal(command.kilogramsPerSquareMeter()));
        values.put("kilogramsPerLinearMeter", decimal(command.kilogramsPerLinearMeter()));
        values.put("unitQuantity", decimal(command.unitQuantity()));
        values.put("unitPrice", decimal(command.unitPrice()));
        values.put("previewTotalWeightKg", decimal(command.previewTotalWeightKg()));
        values.put("previewTotalAmount", decimal(command.previewTotalAmount()));
        return values;
    }

    private InsertMeasurementItemCommand commandFrom(Map<String, Object> values) {
        return new InsertMeasurementItemCommand(uuid(values, "measurementId"), uuid(values, "measurementVersionId"),
                uuid(values, "insertAfterItemId"), (String) values.get("serviceTitle"), (String) values.get("activity"),
                (String) values.get("description"), date(values, "measuredOn"),
                MeasurementChargeType.valueOf((String) values.get("chargeType")), decimalValue(values, "areaSquareMeters"),
                decimalValue(values, "linearMeters"), decimalValue(values, "kilogramsPerSquareMeter"),
                decimalValue(values, "kilogramsPerLinearMeter"), decimalValue(values, "unitQuantity"),
                decimalValue(values, "unitPrice"), decimalValue(values, "previewTotalWeightKg"),
                decimalValue(values, "previewTotalAmount"), null,
                values.get("continuousServiceId")==null?null:UUID.fromString(values.get("continuousServiceId").toString()));
    }

    private Map<String, Object> orderData(List<MeasurementItem> items) {
        List<Map<String, Object>> order = items.stream().sorted(Comparator.comparingInt(MeasurementItem::getItemNumber))
                .map(item -> Map.<String, Object>of("itemId", item.getId().toString(), "itemNumber", item.getItemNumber(),
                        "serviceTitle", item.getServiceTitle(), "active", item.isActive()))
                .toList();
        return Map.of("activeOrder", order);
    }

    private void preview(String field, BigDecimal proposed, BigDecimal official, int scale) {
        if (proposed != null && proposed.setScale(scale).compareTo(official.setScale(scale)) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BUSINESS_RULE_VIOLATION,
                    field + " does not match the official backend calculation");
        }
    }

    private void conflict(String message) {
        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    private Map<String, Object> metadata(String origin, UUID changeRequestId, UUID anchorId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("origin", origin);
        metadata.put("insertAfterItemId", anchorId.toString());
        if (changeRequestId != null) metadata.put("changeRequestId", changeRequestId.toString());
        return metadata;
    }

    private UUID uuid(Map<String, Object> values, String name) { return UUID.fromString((String) values.get(name)); }
    private LocalDate date(Map<String, Object> values, String name) { Object value = values.get(name); return value == null ? null : LocalDate.parse((String) value); }
    private String decimal(BigDecimal value) { return value == null ? null : value.toPlainString(); }
    private BigDecimal decimalValue(Map<String, Object> values, String name) { Object value = values.get(name); return value == null ? null : new BigDecimal((String) value); }

    private record InsertionContext(MeasurementVersion version, List<MeasurementItem> activeItems,
                                    MeasurementItem anchor, int insertPosition, MeasurementItem newItem) { }
}
