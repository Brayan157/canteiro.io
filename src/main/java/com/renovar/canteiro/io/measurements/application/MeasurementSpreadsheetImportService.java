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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Synchronizes an editable official XLSX only after a client has reviewed its
 * hash-bound preview. Item identifiers from the export are the sole matching
 * key; visual labels and item numbers are never used for identity.
 */
@Service
@RequiredArgsConstructor
public class MeasurementSpreadsheetImportService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService authorizationService;
    private final MeasurementRepository measurementRepository;
    private final MeasurementVersionRepository measurementVersionRepository;
    private final MeasurementItemRepository measurementItemRepository;
    private final MeasurementDiscountRepository measurementDiscountRepository;
    private final MeasurementSpreadsheetImportParser parser;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;
    private final com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository continuity;

    @Transactional(readOnly = true)
    public MeasurementSpreadsheetImportPreview preview(UUID measurementId, UUID versionId, String originalFilename,
                                                        byte[] spreadsheet) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        authorizationService.requireChangeAuthorization(AccessModule.MEASUREMENTS, ChangeOperation.UPDATE);
        return preview(companyId, measurementId, versionId, originalFilename, spreadsheet);
    }

    @Transactional
    public MeasurementSpreadsheetImportResult confirm(UUID measurementId, UUID versionId, String originalFilename,
                                                       byte[] spreadsheet, String expectedSpreadsheetHash,
                                                       String justification) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        ImportContext context = prepare(companyId, measurementId, versionId, originalFilename, spreadsheet, true);
        if (!context.hash().equalsIgnoreCase(expectedSpreadsheetHash)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Spreadsheet content changed after its preview; generate a new preview before confirming");
        }
        requireValid(context);
        ChangeAuthorizationMode mode = authorizationService.requireChangeAuthorization(
                AccessModule.MEASUREMENTS, ChangeOperation.UPDATE);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.MEASUREMENTS, ChangeRequestOperation.UPDATE, "MeasurementSpreadsheetImport",
                    context.version().getId(), context.version().getLockVersion(),
                    new ChangeRequestSnapshot(new AuditPayload(auditItems(context.currentActiveItems())),
                            new AuditPayload(proposal(context))), justification));
            return new MeasurementSpreadsheetImportResult(null, null, null, request, mode);
        }
        return apply(companyId, context, null, "direct", mode);
    }

    @Transactional
    public void applyApprovedImport(ChangeRequest request) {
        ImportCommand command = commandFrom(request.getSnapshot().proposedData().values());
        ImportContext context = prepare(request.getCompanyId(), command.measurementId(), command.versionId(),
                command.originalFilename(), command.rows());
        if (context.version().getLockVersion() != request.getEntityVersion()) {
            conflict("Measurement version changed while the spreadsheet import awaited approval");
        }
        apply(request.getCompanyId(), context, request.getId(), "approved-change-request", ChangeAuthorizationMode.DIRECT);
    }

    private MeasurementSpreadsheetImportPreview preview(UUID companyId, UUID measurementId, UUID versionId,
                                                         String originalFilename, byte[] spreadsheet) {
        ImportContext context = prepare(companyId, measurementId, versionId, originalFilename, spreadsheet, false);
        return new MeasurementSpreadsheetImportPreview(measurementId, versionId, context.filename(), context.hash(),
                context.lineErrors().isEmpty(), context.differences(), context.lineErrors(), context.amounts());
    }

    private ImportContext prepare(UUID companyId, UUID measurementId, UUID versionId, String originalFilename,
                                  byte[] spreadsheet, boolean lock) {
        MeasurementSpreadsheetImportParser.ParsedMeasurementSpreadsheet parsed = parser.parse(spreadsheet);
        Measurement measurement = measurementRepository.findByIdAndCompanyId(measurementId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement"));
        if (parsed.identity() == null) {
            measurement.getCurrency().requireSame(com.renovar.canteiro.io.shared.domain.CurrencyCode.BRL);
        } else {
            measurement.getCurrency().requireSame(parsed.identity().currency());
            if (!measurementId.equals(parsed.identity().measurementId()) || !versionId.equals(parsed.identity().versionId()))
                conflict("Spreadsheet origin differs from the selected measurement/version");
        }
        return prepare(companyId, measurementId, versionId, safeFilename(originalFilename), hash(spreadsheet), parsed.rows(),
                new ArrayList<>(parsed.lineErrors()), lock);
    }

    private ImportContext prepare(UUID companyId, UUID measurementId, UUID versionId, String originalFilename,
                                  List<MeasurementSpreadsheetImportRow> rows) {
        return prepare(companyId, measurementId, versionId, safeFilename(originalFilename), null, rows,
                new ArrayList<>(), true);
    }

    private ImportContext prepare(UUID companyId, UUID measurementId, UUID versionId, String originalFilename, String hash,
                                  List<MeasurementSpreadsheetImportRow> rows,
                                  List<MeasurementSpreadsheetImportLineError> errors, boolean lock) {
        Measurement measurement = measurementRepository.findByIdAndCompanyId(measurementId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement"));
        if(lock)continuity.lockMeasurement(companyId,measurementId);
        MeasurementVersion version = (lock ? measurementVersionRepository.findWithLockByIdAndCompanyId(versionId, companyId)
                : measurementVersionRepository.findByIdAndCompanyId(versionId, companyId))
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement version"));
        if (!measurement.getId().equals(version.getMeasurementId())) {
            throw new TenantResourceNotFoundException("Measurement version");
        }
        if (measurement.getStatus() != MeasurementStatus.DRAFT || version.getStatus() != MeasurementVersionStatus.DRAFT
            || !measurementVersionRepository.findLatestByMeasurementIdAndCompanyId(measurementId,companyId).orElseThrow().getId().equals(versionId)) {
            conflict("Spreadsheet import is allowed only for a DRAFT version; create a new revision for accepted or finalized versions");
        }
        List<MeasurementItem> existing = (lock
                ? measurementItemRepository.findWithLockByMeasurementVersionIdAndCompanyId(versionId, companyId)
                : measurementItemRepository.findByMeasurementVersionIdAndCompanyId(versionId, companyId));
        List<MeasurementItem> active = existing.stream().filter(MeasurementItem::isActive)
                .sorted(Comparator.comparingInt(MeasurementItem::getItemNumber)).toList();
        Map<UUID, MeasurementItem> activeById = new HashMap<>();
        active.forEach(item -> activeById.put(item.getId(), item));
        Set<UUID> receivedIds = new HashSet<>();
        List<PlannedLine> planned = new ArrayList<>();

        for (MeasurementSpreadsheetImportRow row : rows) {
            if (row.itemId() != null && !receivedIds.add(row.itemId())) {
                errors.add(new MeasurementSpreadsheetImportLineError(row.spreadsheetRow(), "Item ID",
                        "Item ID appears more than once in the spreadsheet"));
                continue;
            }
            MeasurementItem current = row.itemId() == null ? null : activeById.get(row.itemId());
            if (row.itemId() != null && current == null) {
                errors.add(new MeasurementSpreadsheetImportLineError(row.spreadsheetRow(), "Item ID",
                        "Item ID does not belong to an active item in this measurement version"));
                continue;
            }
            try {
                MeasurementItem candidate = createItem(companyId, versionId, planned.size() + 1, row);
                planned.add(new PlannedLine(row, current, candidate));
            } catch (IllegalArgumentException ex) {
                errors.add(new MeasurementSpreadsheetImportLineError(row.spreadsheetRow(), "row", ex.getMessage()));
            }
        }
        List<MeasurementItem> inactive = active.stream().filter(item -> !receivedIds.contains(item.getId())).toList();
        List<MeasurementSpreadsheetImportDifference> differences = errors.isEmpty()
                ? differences(planned, inactive) : List.of();
        MeasurementDiscount discount = measurementDiscountRepository.findByMeasurementVersionIdAndCompanyId(versionId, companyId)
                .orElse(null);
        List<MeasurementItem> anticipated = errors.isEmpty()
                ? planned.stream().map(PlannedLine::candidate).toList() : active;
        return new ImportContext(measurement, version, active, planned, inactive, differences, errors, discount,
                MeasurementVersionAmountCalculator.calculate(anticipated, discount), originalFilename, hash);
    }

    private MeasurementSpreadsheetImportResult apply(UUID companyId, ImportContext context, UUID changeRequestId,
                                                     String origin, ChangeAuthorizationMode mode) {
        Map<UUID, MeasurementItem> temporary = new HashMap<>();
        for (MeasurementItem item : context.toInactivate()) {
            measurementItemRepository.save(item.deactivate());
        }
        int base = temporaryNumberBase(context.currentActiveItems());
        for (int index = 0; index < context.plannedLines().size(); index++) {
            PlannedLine line = context.plannedLines().get(index);
            if (line.current() != null) {
                MeasurementItem staged = line.current().replaceWith(line.candidate().withItemNumber(base + index));
                temporary.put(line.current().getId(), measurementItemRepository.save(staged));
            }
        }
        measurementItemRepository.flush();

        List<MeasurementItem> persisted = new ArrayList<>();
        for (int index = 0; index < context.plannedLines().size(); index++) {
            PlannedLine line = context.plannedLines().get(index);
            MeasurementItem finalItem = line.current() == null
                    ? line.candidate().withItemNumber(index + 1)
                    : temporary.get(line.current().getId()).withItemNumber(index + 1);
            persisted.add(measurementItemRepository.save(finalItem));
        }
        measurementItemRepository.flush();
        MeasurementVersion version = measurementVersionRepository.save(context.version());
        MeasurementVersionAmounts amounts = MeasurementVersionAmountCalculator.calculate(persisted, context.discount());
        auditEventRecorder.recordDirectAction(AuditModule.MEASUREMENTS, AuditAction.UPDATE,
                "MeasurementSpreadsheetImport", version.getId(), auditItems(context.currentActiveItems()), auditItems(persisted),
                auditMetadata(context, origin, changeRequestId));
        return new MeasurementSpreadsheetImportResult(persisted, version, amounts, null, mode);
    }

    private List<MeasurementSpreadsheetImportDifference> differences(List<PlannedLine> planned,
                                                                       List<MeasurementItem> inactive) {
        List<MeasurementSpreadsheetImportDifference> values = new ArrayList<>();
        for (int index = 0; index < planned.size(); index++) {
            PlannedLine line = planned.get(index);
            if (line.current() == null) {
                values.add(diff(line.row(), null, MeasurementSpreadsheetImportDifferenceType.CREATE));
                continue;
            }
            boolean serviceMoved = !Objects.equals(line.current().getServiceTitle(), line.candidate().getServiceTitle());
            if (serviceMoved) {
                values.add(diff(line.row(), line.current().getId(), MeasurementSpreadsheetImportDifferenceType.MOVE_SERVICE));
            }
            if (!sameExceptService(line)) {
                values.add(diff(line.row(), line.current().getId(), MeasurementSpreadsheetImportDifferenceType.UPDATE));
            }
            if (line.current().getItemNumber() != index + 1) {
                values.add(diff(line.row(), line.current().getId(), MeasurementSpreadsheetImportDifferenceType.REORDER));
            }
        }
        for (MeasurementItem item : inactive) {
            values.add(new MeasurementSpreadsheetImportDifference(0, item.getId(),
                    MeasurementSpreadsheetImportDifferenceType.INACTIVATE, item.getServiceTitle(), item.getActivity()));
        }
        return List.copyOf(values);
    }

    private MeasurementSpreadsheetImportDifference diff(MeasurementSpreadsheetImportRow row, UUID itemId,
                                                         MeasurementSpreadsheetImportDifferenceType type) {
        return new MeasurementSpreadsheetImportDifference(row.spreadsheetRow(), itemId, type,
                row.serviceTitle(), row.activity());
    }

    private boolean sameExceptService(PlannedLine line) {
        MeasurementItem current = line.current();
        MeasurementItem candidate = line.candidate();
        return Objects.equals(current.getActivity(), candidate.getActivity())
                && Objects.equals(current.getDescription(), candidate.getDescription())
                && Objects.equals(current.getMeasuredOn(), candidate.getMeasuredOn())
                && current.getChargeType() == candidate.getChargeType()
                && decimalEquals(current.getAreaSquareMeters(), candidate.getAreaSquareMeters())
                && decimalEquals(current.getLinearMeters(), candidate.getLinearMeters())
                && decimalEquals(current.getKilogramsPerSquareMeter(), candidate.getKilogramsPerSquareMeter())
                && decimalEquals(current.getKilogramsPerLinearMeter(), candidate.getKilogramsPerLinearMeter())
                && decimalEquals(current.getUnitQuantity(), candidate.getUnitQuantity())
                && decimalEquals(current.getUnitPrice(), candidate.getUnitPrice());
    }

    private boolean same(MeasurementItem first, MeasurementItem second) {
        return Objects.equals(first.getServiceTitle(), second.getServiceTitle()) && sameExceptService(new PlannedLine(null, first, second));
    }

    private boolean decimalEquals(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }

    private MeasurementItem createItem(UUID companyId, UUID versionId, int itemNumber,
                                       MeasurementSpreadsheetImportRow row) {
        MeasurementItem item = switch (row.chargeType()) {
            case SQUARE_METER -> MeasurementItem.createSquareMeter(companyId, versionId, itemNumber, row.serviceTitle(),
                    row.activity(), row.description(), row.measuredOn(), row.areaSquareMeters(), row.unitPrice());
            case LINEAR_METER -> MeasurementItem.createLinearMeter(companyId, versionId, itemNumber, row.serviceTitle(),
                    row.activity(), row.description(), row.measuredOn(), row.linearMeters(), row.unitPrice());
            case KILOGRAM_PER_SQUARE_METER -> MeasurementItem.createKilogramPerSquareMeter(companyId, versionId, itemNumber,
                    row.serviceTitle(), row.activity(), row.description(), row.measuredOn(),
                    row.kilogramsPerSquareMeter(), row.areaSquareMeters(), row.unitPrice());
            case KILOGRAM_PER_LINEAR_METER -> MeasurementItem.createKilogramPerLinearMeter(companyId, versionId, itemNumber,
                    row.serviceTitle(), row.activity(), row.description(), row.measuredOn(),
                    row.kilogramsPerLinearMeter(), row.linearMeters(), row.unitPrice());
            case UNIT -> MeasurementItem.createUnit(companyId, versionId, itemNumber, row.serviceTitle(), row.activity(),
                    row.description(), row.measuredOn(), row.unitQuantity(), row.unitPrice());
        };
        return row.chargeType() == MeasurementChargeType.UNIT ? item : item.withOperationalUnitQuantity(row.unitQuantity());
    }

    private int temporaryNumberBase(List<MeasurementItem> active) {
        int largest = active.stream().mapToInt(MeasurementItem::getItemNumber).max().orElse(0);
        long base = (long) largest + active.size() + 1L;
        if (base + active.size() > Integer.MAX_VALUE) {
            conflict("Measurement item numbering capacity was reached");
        }
        return (int) base;
    }

    private Map<String, Object> proposal(ImportContext context) {
        List<Map<String, Object>> rows = context.plannedLines().stream().map(line -> rowData(line.row())).toList();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("measurementId", context.measurement().getId().toString());
        values.put("measurementVersionId", context.version().getId().toString());
        values.put("originalFilename", context.filename());
        values.put("spreadsheetHash", context.hash());
        values.put("rows", rows);
        return values;
    }

    private ImportCommand commandFrom(Map<String, Object> values) {
        Object rawRows = values.get("rows");
        if (!(rawRows instanceof List<?> list)) throw new IllegalArgumentException("Spreadsheet import proposal is invalid");
        @SuppressWarnings("unchecked")
        List<MeasurementSpreadsheetImportRow> rows = list.stream().map(value -> rowFrom((Map<String, Object>) value)).toList();
        return new ImportCommand(UUID.fromString((String) values.get("measurementId")),
                UUID.fromString((String) values.get("measurementVersionId")), (String) values.get("originalFilename"), rows);
    }

    private Map<String, Object> rowData(MeasurementSpreadsheetImportRow row) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("spreadsheetRow", row.spreadsheetRow());
        values.put("itemId", row.itemId() == null ? null : row.itemId().toString());
        values.put("serviceTitle", row.serviceTitle()); values.put("activity", row.activity()); values.put("description", row.description());
        values.put("measuredOn", row.measuredOn().toString()); values.put("chargeType", row.chargeType().name());
        values.put("areaSquareMeters", decimal(row.areaSquareMeters())); values.put("linearMeters", decimal(row.linearMeters()));
        values.put("kilogramsPerSquareMeter", decimal(row.kilogramsPerSquareMeter()));
        values.put("kilogramsPerLinearMeter", decimal(row.kilogramsPerLinearMeter()));
        values.put("unitQuantity", decimal(row.unitQuantity())); values.put("unitPrice", decimal(row.unitPrice()));
        return values;
    }

    private MeasurementSpreadsheetImportRow rowFrom(Map<String, Object> values) {
        return new MeasurementSpreadsheetImportRow(((Number) values.get("spreadsheetRow")).intValue(),
                uuid(values.get("itemId")), (String) values.get("serviceTitle"), (String) values.get("activity"),
                (String) values.get("description"), LocalDate.parse((String) values.get("measuredOn")),
                MeasurementChargeType.valueOf((String) values.get("chargeType")), decimalValue(values.get("areaSquareMeters")),
                decimalValue(values.get("linearMeters")), decimalValue(values.get("kilogramsPerSquareMeter")),
                decimalValue(values.get("kilogramsPerLinearMeter")), decimalValue(values.get("unitQuantity")),
                decimalValue(values.get("unitPrice")));
    }

    private Map<String, Object> auditItems(List<MeasurementItem> items) {
        List<Map<String, Object>> values = items.stream().sorted(Comparator.comparingInt(MeasurementItem::getItemNumber))
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("itemId", item.getId().toString()); row.put("itemNumber", item.getItemNumber());
                    row.put("serviceTitle", item.getServiceTitle()); row.put("activity", item.getActivity());
                    row.put("active", item.isActive()); row.put("totalWeightKg", decimal(item.getTotalWeightKg()));
                    row.put("totalAmount", decimal(item.getTotalAmount()));
                    return row;
                }).toList();
        return Map.of("items", values);
    }

    private Map<String, Object> auditMetadata(ImportContext context, String origin, UUID changeRequestId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("origin", origin); metadata.put("format", "XLSX"); metadata.put("filename", context.filename());
        metadata.put("spreadsheetHash", context.hash()); metadata.put("measurementVersionNumber", context.version().getVersionNumber());
        metadata.put("previousVersionLock", context.version().getLockVersion()); metadata.put("differenceCount", context.differences().size());
        if (changeRequestId != null) metadata.put("changeRequestId", changeRequestId.toString());
        return metadata;
    }

    private void requireValid(ImportContext context) {
        if (!context.lineErrors().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "Spreadsheet has invalid rows; request a preview and correct its line errors");
        }
    }

    private String safeFilename(String value) {
        if (value == null || value.isBlank()) return "measurement-import.xlsx";
        String filename = value.replaceAll("[\\r\\n\\\\/]", "_").trim();
        return filename.length() <= 255 ? filename : filename.substring(0, 255);
    }

    private String hash(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private UUID uuid(Object value) { return value == null ? null : UUID.fromString((String) value); }
    private String decimal(BigDecimal value) { return value == null ? null : value.toPlainString(); }
    private BigDecimal decimalValue(Object value) { return value == null ? null : new BigDecimal((String) value); }
    private void conflict(String message) { throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, message); }

    private record PlannedLine(MeasurementSpreadsheetImportRow row, MeasurementItem current, MeasurementItem candidate) { }
    private record ImportContext(Measurement measurement, MeasurementVersion version, List<MeasurementItem> currentActiveItems,
                                 List<PlannedLine> plannedLines, List<MeasurementItem> toInactivate,
                                 List<MeasurementSpreadsheetImportDifference> differences,
                                 List<MeasurementSpreadsheetImportLineError> lineErrors, MeasurementDiscount discount,
                                 MeasurementVersionAmounts amounts, String filename, String hash) { }
    private record ImportCommand(UUID measurementId, UUID versionId, String originalFilename,
                                 List<MeasurementSpreadsheetImportRow> rows) { }
}
