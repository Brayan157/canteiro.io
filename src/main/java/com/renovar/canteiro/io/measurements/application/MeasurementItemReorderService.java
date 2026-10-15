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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Atomically applies a complete active-item order for a draft version. A
 * temporary numbering pass keeps the partial unique item-number constraint
 * valid while rows are moved.
 */
@Service
@RequiredArgsConstructor
public class MeasurementItemReorderService {

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
    public MeasurementItemReorderResult reorder(ReorderMeasurementItemsCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        ReorderContext context = prepare(companyId, command);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.MEASUREMENTS, ChangeOperation.UPDATE);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.MEASUREMENTS, ChangeRequestOperation.UPDATE, "MeasurementItemReorder",
                    context.version().getId(), context.version().getLockVersion(),
                    new ChangeRequestSnapshot(new AuditPayload(orderData(context.activeItems())),
                            new AuditPayload(proposal(command))),
                    command.justification()));
            return new MeasurementItemReorderResult(null, null, null, request, mode);
        }
        return persistPrepared(companyId, context, null, "direct", mode);
    }

    @Transactional
    public void applyApprovedReorder(ChangeRequest request) {
        ReorderMeasurementItemsCommand command = commandFrom(request.getSnapshot().proposedData().values());
        ReorderContext context = prepare(request.getCompanyId(), command);
        if (context.version().getLockVersion() != request.getEntityVersion()) {
            conflict("Measurement version changed while the item reordering awaited approval");
        }
        persistPrepared(request.getCompanyId(), context, request.getId(), "approved-change-request",
                ChangeAuthorizationMode.DIRECT);
    }

    private MeasurementItemReorderResult persistPrepared(UUID companyId, ReorderContext context,
                                                          UUID changeRequestId, String origin,
                                                          ChangeAuthorizationMode mode) {
        List<MeasurementItem> before = context.activeItems();
        Map<String,Object> beforeData=orderData(before);
        List<MeasurementItem> temporary = assignTemporaryNumbers(context.requestedOrder());
        List<MeasurementItem> persistedTemporary = new ArrayList<>();
        for (MeasurementItem item : temporary) {
            persistedTemporary.add(measurementItemRepository.save(item));
        }
        measurementItemRepository.flush();

        List<MeasurementItem> reordered = new ArrayList<>();
        for (int index = 0; index < persistedTemporary.size(); index++) {
            reordered.add(measurementItemRepository.save(persistedTemporary.get(index).withItemNumber(index + 1)));
        }
        measurementItemRepository.flush();

        MeasurementVersion version = measurementVersionRepository.save(context.version());
        for(var entry:context.bindings().entrySet())continuity.bindItem(companyId,version.getMeasurementId(),version.getId(),entry.getKey(),entry.getValue());
        MeasurementDiscount discount = measurementDiscountRepository
                .findByMeasurementVersionIdAndCompanyId(version.getId(), companyId).orElse(null);
        var amounts = MeasurementVersionAmountCalculator.calculate(reordered, discount);
        auditEventRecorder.recordDirectAction(AuditModule.MEASUREMENTS, AuditAction.UPDATE, "MeasurementItemReorder",
                version.getId(), beforeData, orderData(reordered), metadata(origin, changeRequestId));
        return new MeasurementItemReorderResult(reordered, version, amounts, null, mode);
    }

    private ReorderContext prepare(UUID companyId, ReorderMeasurementItemsCommand command) {
        if (command == null || command.items() == null) {
            throw new IllegalArgumentException("Measurement item reorder request is required");
        }
        continuity.lockMeasurement(companyId,command.measurementId());
        Measurement measurement = measurementRepository.findByIdAndCompanyId(command.measurementId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement"));
        MeasurementVersion version = measurementVersionRepository
                .findWithLockByIdAndCompanyId(command.measurementVersionId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement version"));
        if (!measurement.getId().equals(version.getMeasurementId())) {
            throw new TenantResourceNotFoundException("Measurement version");
        }
        if (measurement.getStatus() != MeasurementStatus.DRAFT || version.getStatus() != MeasurementVersionStatus.DRAFT) {
            conflict("Measurement items can only be reordered while the current version is draft");
        }
        if(!measurementVersionRepository.findLatestByMeasurementIdAndCompanyId(measurement.getId(),companyId).orElseThrow().getId().equals(version.getId()))
            conflict("Only the latest DRAFT version is editable");

        List<MeasurementItem> active = measurementItemRepository
                .findWithLockByMeasurementVersionIdAndCompanyId(version.getId(), companyId).stream()
                .filter(MeasurementItem::isActive)
                .sorted(Comparator.comparingInt(MeasurementItem::getItemNumber))
                .toList();
        Map<UUID, MeasurementItem> activeById = new HashMap<>();
        for (MeasurementItem item : active) {
            activeById.put(item.getId(), item);
        }

        Set<UUID> receivedIds = new HashSet<>();
        List<MeasurementItem> requestedOrder = new ArrayList<>();
        Map<UUID,UUID> bindings=new LinkedHashMap<>();
        for (ReorderMeasurementItemsCommand.ReorderedMeasurementItem requested : command.items()) {
            if (requested == null || requested.itemId() == null) {
                throw new IllegalArgumentException("Each reordered measurement item must identify an itemId");
            }
            if (!receivedIds.add(requested.itemId())) {
                throw new IllegalArgumentException("Measurement item ids must not be repeated in a reorder request");
            }
            MeasurementItem item = activeById.get(requested.itemId());
            if (item == null) {
                throw new TenantResourceNotFoundException("Measurement item");
            }
            if(requested.continuousServiceId()!=null){
                continuousServices.validateItemService(companyId,command.measurementId(),requested.continuousServiceId());
                if(item.getSourceItemId()!=null&&continuity.recognitionForItem(companyId,item.getSourceItemId()).isPresent()
                        &&!continuity.serviceForItem(companyId,item.getSourceItemId()).orElseThrow().equals(requested.continuousServiceId()))
                    conflict("Accepted origins cannot be moved to a different service by ordinary reordering");
                bindings.put(item.getId(),requested.continuousServiceId());
            }
            requestedOrder.add(item.withServiceTitle(requested.serviceTitle()));
        }
        if (receivedIds.size() != active.size() || !receivedIds.equals(activeById.keySet())) {
            throw new IllegalArgumentException("A reorder request must contain every active measurement item exactly once");
        }
        return new ReorderContext(version, active, requestedOrder, bindings);
    }

    private List<MeasurementItem> assignTemporaryNumbers(List<MeasurementItem> requestedOrder) {
        int largest = requestedOrder.stream().mapToInt(MeasurementItem::getItemNumber).max().orElse(0);
        long base = (long) largest + requestedOrder.size() + 1L;
        if (base + requestedOrder.size() > Integer.MAX_VALUE) {
            conflict("Measurement item numbering capacity was reached");
        }
        List<MeasurementItem> result = new ArrayList<>();
        for (int index = 0; index < requestedOrder.size(); index++) {
            result.add(requestedOrder.get(index).withItemNumber((int) base + index));
        }
        return result;
    }

    private Map<String, Object> proposal(ReorderMeasurementItemsCommand command) {
        List<Map<String, Object>> items = command.items().stream()
                .map(item -> {
                    Map<String,Object> value=new LinkedHashMap<>();value.put("itemId",item.itemId().toString());value.put("serviceTitle",item.serviceTitle());
                    value.put("continuousServiceId",item.continuousServiceId()==null?null:item.continuousServiceId().toString());return value;
                })
                .toList();
        return Map.of("measurementId", command.measurementId().toString(),
                "measurementVersionId", command.measurementVersionId().toString(), "items", items);
    }

    @SuppressWarnings("unchecked")
    private ReorderMeasurementItemsCommand commandFrom(Map<String, Object> values) {
        Object rawItems = values.get("items");
        if (!(rawItems instanceof List<?> itemList)) {
            throw new IllegalArgumentException("Measurement item reorder proposal is invalid");
        }
        List<ReorderMeasurementItemsCommand.ReorderedMeasurementItem> items = itemList.stream()
                .map(value -> (Map<String, Object>) value)
                .map(value -> new ReorderMeasurementItemsCommand.ReorderedMeasurementItem(
                        UUID.fromString((String) value.get("itemId")), (String) value.get("serviceTitle"),
                        value.get("continuousServiceId")==null?null:UUID.fromString(value.get("continuousServiceId").toString())))
                .toList();
        return new ReorderMeasurementItemsCommand(UUID.fromString((String) values.get("measurementId")),
                UUID.fromString((String) values.get("measurementVersionId")), items, null);
    }

    private Map<String, Object> orderData(List<MeasurementItem> items) {
        List<Map<String, Object>> order = items.stream().sorted(Comparator.comparingInt(MeasurementItem::getItemNumber))
                .map(item -> {
                    Map<String,Object> value=new LinkedHashMap<>();value.put("itemId",item.getId().toString());value.put("itemNumber",item.getItemNumber());
                    value.put("serviceTitle",item.getServiceTitle());value.put("active",item.isActive());
                    value.put("continuousServiceId",continuity.serviceForItem(item.getCompanyId(),item.getId()).map(UUID::toString).orElse(null));return value;
                })
                .toList();
        return Map.of("activeOrder", order);
    }

    private Map<String, Object> metadata(String origin, UUID changeRequestId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("origin", origin);
        metadata.put("operation", "complete-reorder");
        if (changeRequestId != null) {
            metadata.put("changeRequestId", changeRequestId.toString());
        }
        return metadata;
    }

    private void conflict(String message) {
        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    private record ReorderContext(MeasurementVersion version, List<MeasurementItem> activeItems,
                                  List<MeasurementItem> requestedOrder, Map<UUID,UUID> bindings) {
    }
}
