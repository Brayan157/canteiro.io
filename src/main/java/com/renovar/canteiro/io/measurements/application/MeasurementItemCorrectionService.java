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
import com.renovar.canteiro.io.measurements.domain.MeasurementItemCorrectionAction;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps accepted lines immutable. Draft lines may be replaced or deactivated;
 * an accepted line is represented by a new corrective draft that retains its
 * source-item link.
 */
@Service
@RequiredArgsConstructor
public class MeasurementItemCorrectionService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final MeasurementRepository measurementRepository;
    private final MeasurementVersionRepository measurementVersionRepository;
    private final MeasurementItemRepository measurementItemRepository;
    private final MeasurementDiscountRepository measurementDiscountRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public MeasurementItemCorrectionResult updateDraft(UpdateMeasurementItemCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        MeasurementItem existing = requireDraftItem(companyId, command.measurementId(), command.measurementVersionId(), command.itemId());
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(AccessModule.MEASUREMENTS, ChangeOperation.UPDATE);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            return pending("MeasurementItemDraftUpdate", ChangeRequestOperation.UPDATE, existing, proposal(command), command.justification(), mode);
        }
        return persistDraftUpdate(companyId, command, "direct", null, mode);
    }

    @Transactional
    public MeasurementItemCorrectionResult removeDraft(UUID measurementId, UUID versionId, UUID itemId, String justification) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        MeasurementItem existing = requireDraftItem(companyId, measurementId, versionId, itemId);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(AccessModule.MEASUREMENTS, ChangeOperation.CANCEL);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            Map<String, Object> values = baseProposal(measurementId, versionId, itemId);
            values.put("kind", "draft-remove");
            return pending("MeasurementItemDraftCancellation", ChangeRequestOperation.CANCEL, existing, values, justification, mode);
        }
        return persistDraftRemoval(companyId, measurementId, versionId, itemId, "direct", null, mode);
    }

    @Transactional
    public MeasurementItemCorrectionResult correctAccepted(CorrectAcceptedMeasurementItemCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        MeasurementItem source = requireAcceptedSource(companyId, command.measurementId(), command.measurementVersionId(), command.itemId());
        validateCorrection(command);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(AccessModule.MEASUREMENTS, ChangeOperation.UPDATE);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            return pending("MeasurementItemAcceptedCorrection", ChangeRequestOperation.UPDATE, source, proposal(command), command.justification(), mode);
        }
        return persistAcceptedCorrection(companyId, command, "direct", null, mode);
    }

    @Transactional
    public void applyApprovedDraftUpdate(ChangeRequest request) {
        persistDraftUpdate(request.getCompanyId(), updateFrom(request.getSnapshot().proposedData().values()),
                "approved-change-request", request.getId(), ChangeAuthorizationMode.DIRECT);
    }

    @Transactional
    public void applyApprovedDraftRemoval(ChangeRequest request) {
        Map<String, Object> values = request.getSnapshot().proposedData().values();
        persistDraftRemoval(request.getCompanyId(), uuid(values, "measurementId"), uuid(values, "measurementVersionId"),
                uuid(values, "itemId"), "approved-change-request", request.getId(), ChangeAuthorizationMode.DIRECT);
    }

    @Transactional
    public void applyApprovedAcceptedCorrection(ChangeRequest request) {
        persistAcceptedCorrection(request.getCompanyId(), correctionFrom(request.getSnapshot().proposedData().values()),
                "approved-change-request", request.getId(), ChangeAuthorizationMode.DIRECT);
    }

    private MeasurementItemCorrectionResult persistDraftUpdate(UUID companyId, UpdateMeasurementItemCommand command,
                                                                String origin, UUID changeRequestId,
                                                                ChangeAuthorizationMode mode) {
        MeasurementItem existing = requireDraftItem(companyId, command.measurementId(), command.measurementVersionId(), command.itemId());
        MeasurementItem replacement = buildItem(companyId, command.measurementVersionId(), command.itemNumber(), command.serviceTitle(),
                command.activity(), command.description(), command.measuredOn(), command.chargeType(), command.areaSquareMeters(),
                command.linearMeters(), command.kilogramsPerSquareMeter(), command.kilogramsPerLinearMeter(), command.unitQuantity(), command.unitPrice(),
                command.previewTotalWeightKg(), command.previewTotalAmount());
        replacement = existing.replaceWith(replacement);
        rejectDuplicateActiveNumber(companyId, command.measurementVersionId(), replacement.getItemNumber(), existing.getId());
        MeasurementItem saved = measurementItemRepository.save(replacement);
        MeasurementVersion version = requireVersion(companyId, command.measurementVersionId());
        auditEventRecorder.recordDirectAction(AuditModule.MEASUREMENTS, AuditAction.UPDATE, "MeasurementItem", saved.getId(),
                itemAuditData(existing), itemAuditData(saved), metadata(origin, changeRequestId));
        return directResult(companyId, saved, version, mode);
    }

    private MeasurementItemCorrectionResult persistDraftRemoval(UUID companyId, UUID measurementId, UUID versionId, UUID itemId,
                                                                 String origin, UUID changeRequestId, ChangeAuthorizationMode mode) {
        MeasurementItem existing = requireDraftItem(companyId, measurementId, versionId, itemId);
        List<MeasurementItem> activeBeforeRemoval = measurementItemRepository
                .findWithLockByMeasurementVersionIdAndCompanyId(versionId, companyId).stream()
                .filter(MeasurementItem::isActive)
                .sorted(Comparator.comparingInt(MeasurementItem::getItemNumber))
                .toList();
        MeasurementItem saved = measurementItemRepository.save(existing.deactivate());
        // Flush the deactivation before assigning a number that was historically
        // used by the removed line. The inactive line keeps its original number.
        measurementItemRepository.flush();
        List<MeasurementItem> remaining = activeBeforeRemoval.stream()
                .filter(item -> !item.getId().equals(existing.getId()))
                .toList();
        List<MeasurementItem> temporarilyRenumbered = assignTemporaryNumbers(remaining);
        List<MeasurementItem> persistedTemporary = new ArrayList<>();
        for (MeasurementItem item : temporarilyRenumbered) {
            persistedTemporary.add(measurementItemRepository.save(item));
        }
        measurementItemRepository.flush();
        List<MeasurementItem> renumbered = new ArrayList<>();
        for (int index = 0; index < persistedTemporary.size(); index++) {
            renumbered.add(measurementItemRepository.save(persistedTemporary.get(index).withItemNumber(index + 1)));
        }
        measurementItemRepository.flush();
        MeasurementVersion version = requireVersion(companyId, versionId);
        auditEventRecorder.recordDirectAction(AuditModule.MEASUREMENTS, AuditAction.CANCEL, "MeasurementItem", saved.getId(),
                removalAuditData(existing, activeBeforeRemoval), removalAuditData(saved, renumbered),
                removalMetadata(origin, changeRequestId));
        MeasurementDiscount discount = measurementDiscountRepository
                .findByMeasurementVersionIdAndCompanyId(version.getId(), companyId).orElse(null);
        return new MeasurementItemCorrectionResult(saved, version,
                MeasurementVersionAmountCalculator.calculate(renumbered, discount), null, mode);
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

    private MeasurementItemCorrectionResult persistAcceptedCorrection(UUID companyId, CorrectAcceptedMeasurementItemCommand command,
                                                                       String origin, UUID changeRequestId,
                                                                       ChangeAuthorizationMode mode) {
        Measurement measurement = requireMeasurement(companyId, command.measurementId());
        MeasurementItem source = requireAcceptedSource(companyId, command.measurementId(), command.measurementVersionId(), command.itemId());
        validateCorrection(command);
        MeasurementVersion sourceVersion = requireVersion(companyId, command.measurementVersionId());
        measurement.startRevision();
        Measurement persistedMeasurement = measurementRepository.save(measurement);
        MeasurementVersion revision = measurementVersionRepository.save(MeasurementVersion.createRevision(companyId,
                measurement.getId(), sourceVersion.getVersionNumber() + 1, sourceVersion.getId()));
        List<MeasurementItem> originals = measurementItemRepository.findByMeasurementVersionIdAndCompanyId(sourceVersion.getId(), companyId);
        MeasurementItem corrected = null;
        for (MeasurementItem original : originals) {
            MeasurementItem copy;
            if (original.getId().equals(source.getId()) && command.action() == MeasurementItemCorrectionAction.REPLACE) {
                copy = buildItem(companyId, revision.getId(), requiredItemNumber(command), command.serviceTitle(), command.activity(),
                        command.description(), command.measuredOn(), command.chargeType(), command.areaSquareMeters(), command.linearMeters(),
                        command.kilogramsPerSquareMeter(), command.kilogramsPerLinearMeter(), command.unitQuantity(), command.unitPrice(),
                        command.previewTotalWeightKg(), command.previewTotalAmount()).withSourceItemId(source.getId());
            } else {
                copy = original.copyToRevision(revision.getId(), original.getId());
                if (original.getId().equals(source.getId()) && command.action() == MeasurementItemCorrectionAction.VOID) {
                    copy = original.deactivate().copyToRevision(revision.getId(), original.getId());
                }
            }
            MeasurementItem saved = measurementItemRepository.save(copy);
            if (original.getId().equals(source.getId())) corrected = saved;
        }
        MeasurementDiscount discount = measurementDiscountRepository.findByMeasurementVersionIdAndCompanyId(sourceVersion.getId(), companyId)
                .map(value -> measurementDiscountRepository.save(MeasurementDiscount.create(companyId, revision.getId(),
                        value.getDiscountType(), value.getDiscountValue())))
                .orElse(null);
        auditEventRecorder.recordDirectAction(AuditModule.MEASUREMENTS, AuditAction.UPDATE, "MeasurementItemCorrectiveRevision",
                revision.getId(), itemAuditData(source), itemAuditData(corrected), Map.of("origin", origin,
                        "sourceItemId", source.getId().toString(), "revisionScope", "corrective-item",
                        "action", command.action().name(), "changeRequestId", changeRequestId == null ? "" : changeRequestId.toString()));
        List<MeasurementItem> revisionItems = measurementItemRepository.findByMeasurementVersionIdAndCompanyId(revision.getId(), companyId);
        return new MeasurementItemCorrectionResult(corrected, revision,
                MeasurementVersionAmountCalculator.calculate(revisionItems, discount), null, mode);
    }

    private MeasurementItemCorrectionResult directResult(UUID companyId, MeasurementItem item, MeasurementVersion version,
                                                          ChangeAuthorizationMode mode) {
        version=measurementVersionRepository.save(version);
        MeasurementDiscount discount = measurementDiscountRepository.findByMeasurementVersionIdAndCompanyId(version.getId(), companyId).orElse(null);
        return new MeasurementItemCorrectionResult(item, version,
                MeasurementVersionAmountCalculator.calculate(measurementItemRepository
                        .findByMeasurementVersionIdAndCompanyId(version.getId(), companyId), discount), null, mode);
    }

    private MeasurementItemCorrectionResult pending(String entityType, ChangeRequestOperation operation, MeasurementItem existing,
                                                    Map<String, Object> proposed, String justification, ChangeAuthorizationMode mode) {
        ChangeRequest request = changeRequestService.create(new CreateChangeRequestCommand(AuditModule.MEASUREMENTS, operation,
                entityType, existing.getId(), 0, new ChangeRequestSnapshot(new AuditPayload(itemAuditData(existing)),
                new AuditPayload(proposed)), justification));
        return new MeasurementItemCorrectionResult(null, null, null, request, mode);
    }

    private MeasurementItem requireDraftItem(UUID companyId, UUID measurementId, UUID versionId, UUID itemId) {
        Measurement measurement = requireMeasurement(companyId, measurementId);
        MeasurementVersion version = requireVersion(companyId, versionId);
        if (!measurement.getId().equals(version.getMeasurementId())) throw new TenantResourceNotFoundException("Measurement version");
        if (measurement.getStatus() != MeasurementStatus.DRAFT || version.getStatus() != MeasurementVersionStatus.DRAFT) conflict("Measurement items can only be changed while the current version is draft");
        if(!measurementVersionRepository.findLatestByMeasurementIdAndCompanyId(measurementId,companyId).orElseThrow().getId().equals(versionId))
            conflict("Only the latest DRAFT version is editable");
        MeasurementItem item = measurementItemRepository.findWithLockByIdAndCompanyId(itemId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement item"));
        if (!versionId.equals(item.getMeasurementVersionId())) throw new TenantResourceNotFoundException("Measurement item");
        if (!item.isActive()) conflict("Measurement item is already logically removed");
        return item;
    }

    private MeasurementItem requireAcceptedSource(UUID companyId, UUID measurementId, UUID versionId, UUID itemId) {
        Measurement measurement = requireMeasurement(companyId, measurementId);
        MeasurementVersion version = requireVersion(companyId, versionId);
        if (!measurement.getId().equals(version.getMeasurementId())) throw new TenantResourceNotFoundException("Measurement version");
        MeasurementVersion latest = measurementVersionRepository.findLatestByMeasurementIdAndCompanyId(measurementId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement version"));
        if (measurement.getStatus() != MeasurementStatus.ACCEPTED || version.getStatus() != MeasurementVersionStatus.ACCEPTED
                || !latest.getId().equals(versionId)) conflict("Only the latest accepted measurement version can receive a corrective revision");
        MeasurementItem item = measurementItemRepository.findWithLockByIdAndCompanyId(itemId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement item"));
        if (!versionId.equals(item.getMeasurementVersionId()) || !item.isActive()) throw new TenantResourceNotFoundException("Measurement item");
        return item;
    }

    private Measurement requireMeasurement(UUID companyId, UUID measurementId) {
        return measurementRepository.findByIdAndCompanyId(measurementId, companyId).orElseThrow(() -> new TenantResourceNotFoundException("Measurement"));
    }
    private MeasurementVersion requireVersion(UUID companyId, UUID versionId) {
        return measurementVersionRepository.findByIdAndCompanyId(versionId, companyId).orElseThrow(() -> new TenantResourceNotFoundException("Measurement version"));
    }
    private void rejectDuplicateActiveNumber(UUID companyId, UUID versionId, int number, UUID except) {
        boolean duplicate = measurementItemRepository.findByMeasurementVersionIdAndCompanyId(versionId, companyId).stream()
                .anyMatch(item -> item.isActive() && item.getItemNumber() == number && !item.getId().equals(except));
        if (duplicate) conflict("Measurement item number is already in use in this version");
    }
    private void validateCorrection(CorrectAcceptedMeasurementItemCommand command) {
        if (command.action() == null) throw new IllegalArgumentException("Measurement item correction action is required");
        if (command.action() == MeasurementItemCorrectionAction.REPLACE) {
            buildItem(UUID.randomUUID(), UUID.randomUUID(), requiredItemNumber(command), command.serviceTitle(), command.activity(),
                    command.description(), command.measuredOn(), command.chargeType(), command.areaSquareMeters(), command.linearMeters(),
                    command.kilogramsPerSquareMeter(), command.kilogramsPerLinearMeter(), command.unitQuantity(), command.unitPrice(),
                    command.previewTotalWeightKg(), command.previewTotalAmount());
        }
    }
    private int requiredItemNumber(CorrectAcceptedMeasurementItemCommand command) {
        if (command.itemNumber() == null || command.itemNumber() < 1) throw new IllegalArgumentException("Measurement item number must be positive");
        return command.itemNumber();
    }
    private MeasurementItem buildItem(UUID companyId, UUID versionId, int itemNumber, String serviceTitle, String activity,
                                      String description, LocalDate measuredOn, MeasurementChargeType chargeType,
                                      BigDecimal area, BigDecimal linear, BigDecimal kgPerArea, BigDecimal kgPerLinear,
                                      BigDecimal unitQuantity, BigDecimal unitPrice, BigDecimal previewWeight, BigDecimal previewAmount) {
        if (chargeType == null) throw new IllegalArgumentException("Measurement item charge type is required");
        MeasurementItem item = switch (chargeType) {
            case SQUARE_METER -> MeasurementItem.createSquareMeter(companyId, versionId, itemNumber, serviceTitle, activity, description, measuredOn, area, unitPrice);
            case LINEAR_METER -> MeasurementItem.createLinearMeter(companyId, versionId, itemNumber, serviceTitle, activity, description, measuredOn, linear, unitPrice);
            case KILOGRAM_PER_SQUARE_METER -> MeasurementItem.createKilogramPerSquareMeter(companyId, versionId, itemNumber, serviceTitle, activity, description, measuredOn, kgPerArea, area, unitPrice);
            case KILOGRAM_PER_LINEAR_METER -> MeasurementItem.createKilogramPerLinearMeter(companyId, versionId, itemNumber, serviceTitle, activity, description, measuredOn, kgPerLinear, linear, unitPrice);
            case UNIT -> MeasurementItem.createUnit(companyId, versionId, itemNumber, serviceTitle, activity, description, measuredOn, unitQuantity, unitPrice);
        };
        if (chargeType != MeasurementChargeType.UNIT) {
            item = item.withOperationalUnitQuantity(unitQuantity);
        }
        preview("previewTotalWeightKg", previewWeight, item.getTotalWeightKg() == null ? BigDecimal.ZERO : item.getTotalWeightKg(), 4);
        preview("previewTotalAmount", previewAmount, item.getTotalAmount(), 2);
        return item;
    }
    private void preview(String field, BigDecimal proposed, BigDecimal official, int scale) {
        if (proposed != null && proposed.setScale(scale).compareTo(official.setScale(scale)) != 0)
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BUSINESS_RULE_VIOLATION, field + " does not match the official backend calculation");
    }
    private void conflict(String message) { throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, message); }
    private Map<String, Object> baseProposal(UUID measurementId, UUID versionId, UUID itemId) {
        Map<String, Object> values = new HashMap<>(); values.put("measurementId", measurementId.toString()); values.put("measurementVersionId", versionId.toString()); values.put("itemId", itemId.toString()); return values;
    }
    private Map<String, Object> proposal(UpdateMeasurementItemCommand command) { return proposal(command.measurementId(), command.measurementVersionId(), command.itemId(), "draft-update", null, command.itemNumber(), command.serviceTitle(), command.activity(), command.description(), command.measuredOn(), command.chargeType(), command.areaSquareMeters(), command.linearMeters(), command.kilogramsPerSquareMeter(), command.kilogramsPerLinearMeter(), command.unitQuantity(), command.unitPrice(), command.previewTotalWeightKg(), command.previewTotalAmount()); }
    private Map<String, Object> proposal(CorrectAcceptedMeasurementItemCommand command) { return proposal(command.measurementId(), command.measurementVersionId(), command.itemId(), "accepted-correction", command.action(), command.itemNumber(), command.serviceTitle(), command.activity(), command.description(), command.measuredOn(), command.chargeType(), command.areaSquareMeters(), command.linearMeters(), command.kilogramsPerSquareMeter(), command.kilogramsPerLinearMeter(), command.unitQuantity(), command.unitPrice(), command.previewTotalWeightKg(), command.previewTotalAmount()); }
    private Map<String, Object> proposal(UUID measurementId, UUID versionId, UUID itemId, String kind, MeasurementItemCorrectionAction action, Integer itemNumber, String serviceTitle, String activity, String description, LocalDate measuredOn, MeasurementChargeType chargeType, BigDecimal area, BigDecimal linear, BigDecimal kgPerArea, BigDecimal kgPerLinear, BigDecimal unitQuantity, BigDecimal unitPrice, BigDecimal previewWeight, BigDecimal previewAmount) {
        Map<String, Object> v = baseProposal(measurementId, versionId, itemId); v.put("kind", kind); v.put("action", action == null ? null : action.name()); v.put("itemNumber", itemNumber); v.put("serviceTitle", serviceTitle); v.put("activity", activity); v.put("description", description); v.put("measuredOn", measuredOn == null ? null : measuredOn.toString()); v.put("chargeType", chargeType == null ? null : chargeType.name()); v.put("areaSquareMeters", decimal(area)); v.put("linearMeters", decimal(linear)); v.put("kilogramsPerSquareMeter", decimal(kgPerArea)); v.put("kilogramsPerLinearMeter", decimal(kgPerLinear)); v.put("unitQuantity", decimal(unitQuantity)); v.put("unitPrice", decimal(unitPrice)); v.put("previewTotalWeightKg", decimal(previewWeight)); v.put("previewTotalAmount", decimal(previewAmount)); return v;
    }
    private UpdateMeasurementItemCommand updateFrom(Map<String, Object> v) { return new UpdateMeasurementItemCommand(uuid(v,"measurementId"),uuid(v,"measurementVersionId"),uuid(v,"itemId"),((Number)v.get("itemNumber")).intValue(),(String)v.get("serviceTitle"),(String)v.get("activity"),(String)v.get("description"),date(v,"measuredOn"),MeasurementChargeType.valueOf((String)v.get("chargeType")),decimalValue(v,"areaSquareMeters"),decimalValue(v,"linearMeters"),decimalValue(v,"kilogramsPerSquareMeter"),decimalValue(v,"kilogramsPerLinearMeter"),decimalValue(v,"unitQuantity"),decimalValue(v,"unitPrice"),decimalValue(v,"previewTotalWeightKg"),decimalValue(v,"previewTotalAmount"),null); }
    private CorrectAcceptedMeasurementItemCommand correctionFrom(Map<String, Object> v) { return new CorrectAcceptedMeasurementItemCommand(uuid(v,"measurementId"),uuid(v,"measurementVersionId"),uuid(v,"itemId"),MeasurementItemCorrectionAction.valueOf((String)v.get("action")),number(v,"itemNumber"),(String)v.get("serviceTitle"),(String)v.get("activity"),(String)v.get("description"),date(v,"measuredOn"),v.get("chargeType") == null ? null : MeasurementChargeType.valueOf((String)v.get("chargeType")),decimalValue(v,"areaSquareMeters"),decimalValue(v,"linearMeters"),decimalValue(v,"kilogramsPerSquareMeter"),decimalValue(v,"kilogramsPerLinearMeter"),decimalValue(v,"unitQuantity"),decimalValue(v,"unitPrice"),decimalValue(v,"previewTotalWeightKg"),decimalValue(v,"previewTotalAmount"),null); }
    private Map<String,Object> itemAuditData(MeasurementItem item) { Map<String,Object> d=new HashMap<>(); d.put("measurementVersionId", item.getMeasurementVersionId().toString()); d.put("itemNumber",item.getItemNumber()); d.put("serviceTitle",item.getServiceTitle()); d.put("activity",item.getActivity()); d.put("chargeType",item.getChargeType().name()); d.put("unitQuantity",decimal(item.getUnitQuantity())); d.put("active",item.isActive()); d.put("sourceItemId",item.getSourceItemId()==null?null:item.getSourceItemId().toString()); d.put("totalWeightKg",decimal(item.getTotalWeightKg())); d.put("totalAmount",decimal(item.getTotalAmount())); return d; }
    private Map<String, Object> removalAuditData(MeasurementItem item, List<MeasurementItem> activeItems) {
        Map<String, Object> data = itemAuditData(item);
        data.put("activeOrder", activeItems.stream()
                .sorted(Comparator.comparingInt(MeasurementItem::getItemNumber))
                .map(active -> Map.<String, Object>of("itemId", active.getId().toString(), "itemNumber", active.getItemNumber()))
                .toList());
        return data;
    }
    private Map<String, Object> removalMetadata(String origin, UUID changeRequestId) {
        Map<String, Object> values = new HashMap<>(metadata(origin, changeRequestId));
        values.put("operation", "logical-removal-and-renumber");
        return values;
    }
    private Map<String,Object> metadata(String origin, UUID changeRequestId) { return changeRequestId == null ? Map.of("origin", origin) : Map.of("origin",origin,"changeRequestId",changeRequestId.toString()); }
    private UUID uuid(Map<String,Object> v,String key) { return UUID.fromString((String)v.get(key)); }
    private Integer number(Map<String,Object> v,String key) { Object value=v.get(key); return value==null?null:((Number)value).intValue(); }
    private LocalDate date(Map<String,Object> v,String key) { Object value=v.get(key); return value==null?null:LocalDate.parse((String)value); }
    private String decimal(BigDecimal value) { return value==null?null:value.toPlainString(); }
    private BigDecimal decimalValue(Map<String,Object> v,String key) { Object value=v.get(key); return value==null?null:new BigDecimal((String)value); }
}
