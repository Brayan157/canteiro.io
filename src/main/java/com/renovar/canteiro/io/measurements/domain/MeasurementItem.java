package com.renovar.canteiro.io.measurements.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class MeasurementItem {

    private static final String SQUARE_METER_FORMULA = "area_square_meters × unit_price";
    private static final String LINEAR_METER_FORMULA = "linear_meters × unit_price";
    private static final String KILOGRAM_PER_SQUARE_METER_FORMULA =
            "kilograms_per_square_meter × area_square_meters = total_weight_kg; total_weight_kg × unit_price";
    private static final String KILOGRAM_PER_LINEAR_METER_FORMULA =
            "kilograms_per_linear_meter × linear_meters = total_weight_kg; total_weight_kg × unit_price";
    private static final String UNIT_FORMULA = "unit_quantity × unit_price";

    private final UUID id;
    private final UUID companyId;
    private final UUID measurementVersionId;
    private final int itemNumber;
    private final String serviceTitle;
    private final String activity;
    private final String description;
    private final LocalDate measuredOn;
    private final MeasurementChargeType chargeType;
    private final BigDecimal areaSquareMeters;
    private final BigDecimal linearMeters;
    private final BigDecimal kilogramsPerSquareMeter;
    private final BigDecimal kilogramsPerLinearMeter;
    private final BigDecimal unitQuantity;
    private final BigDecimal unitPrice;
    private final BigDecimal totalWeightKg;
    private final BigDecimal totalAmount;
    private final String calculationFormula;
    /**
     * A removed draft line stays in the timeline and in audit, but it must not
     * participate in the official totals of the version.
     */
    private final boolean active;
    /** The accepted line from which a corrective-revision line originated. */
    private final UUID sourceItemId;
    private final Instant createdAt;
    private final Instant updatedAt;

    private MeasurementItem(UUID id, UUID companyId, UUID measurementVersionId, int itemNumber, String serviceTitle,
                            String activity, String description, LocalDate measuredOn, MeasurementChargeType chargeType,
                            BigDecimal areaSquareMeters,
                            BigDecimal linearMeters, BigDecimal kilogramsPerSquareMeter,
                            BigDecimal kilogramsPerLinearMeter, BigDecimal unitPrice, BigDecimal totalWeightKg,
                            BigDecimal totalAmount, String calculationFormula, boolean active, UUID sourceItemId,
                            Instant createdAt, Instant updatedAt) {
        this(id, companyId, measurementVersionId, itemNumber, serviceTitle, activity, description, measuredOn,
                chargeType, areaSquareMeters, linearMeters, kilogramsPerSquareMeter, kilogramsPerLinearMeter, null,
                unitPrice, totalWeightKg, totalAmount, calculationFormula, active, sourceItemId, createdAt, updatedAt);
    }

    private MeasurementItem(UUID id, UUID companyId, UUID measurementVersionId, int itemNumber, String serviceTitle,
                            String activity, String description, LocalDate measuredOn, MeasurementChargeType chargeType,
                            BigDecimal areaSquareMeters, BigDecimal linearMeters, BigDecimal kilogramsPerSquareMeter,
                            BigDecimal kilogramsPerLinearMeter, BigDecimal unitQuantity, BigDecimal unitPrice,
                            BigDecimal totalWeightKg, BigDecimal totalAmount, String calculationFormula, boolean active,
                            UUID sourceItemId, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Measurement item company is required");
        this.measurementVersionId = require(measurementVersionId, "Measurement item version is required");
        if (itemNumber < 1) {
            throw new IllegalArgumentException("Measurement item number must be positive");
        }
        this.itemNumber = itemNumber;
        this.serviceTitle = requireText(serviceTitle, "Measurement item service title is required");
        this.activity = requireText(activity, "Measurement item activity is required");
        this.description = normalize(description);
        this.measuredOn = require(measuredOn, "Measurement item measured date is required");
        this.chargeType = require(chargeType, "Measurement item charge type is required");
        this.areaSquareMeters = areaSquareMeters;
        this.linearMeters = linearMeters;
        this.kilogramsPerSquareMeter = kilogramsPerSquareMeter;
        this.kilogramsPerLinearMeter = kilogramsPerLinearMeter;
        this.unitQuantity = normalizeUnitQuantity(chargeType, unitQuantity);
        this.unitPrice = unitPrice;
        this.totalWeightKg = totalWeightKg;
        this.totalAmount = totalAmount;
        this.calculationFormula = normalize(calculationFormula);
        this.active = active;
        this.sourceItemId = sourceItemId;
        validateSquareMeterCalculation();
        validateLinearMeterCalculation();
        validateKilogramPerSquareMeterCalculation();
        validateKilogramPerLinearMeterCalculation();
        validateUnitCalculation();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static MeasurementItem createSquareMeter(UUID companyId, UUID measurementVersionId, int itemNumber,
                                                    String activity, String description, BigDecimal areaSquareMeters,
                                                    BigDecimal pricePerSquareMeter) {
        return createSquareMeter(companyId, measurementVersionId, itemNumber, activity, activity, description,
                LocalDate.of(1970, 1, 1), areaSquareMeters, pricePerSquareMeter);
    }

    public static MeasurementItem createSquareMeter(UUID companyId, UUID measurementVersionId, int itemNumber,
                                                     String serviceTitle, String activity, String description,
                                                     LocalDate measuredOn, BigDecimal areaSquareMeters,
                                                     BigDecimal pricePerSquareMeter) {
        BigDecimal normalizedArea = requirePositive(areaSquareMeters, "Measurement item area must be positive", 4);
        BigDecimal normalizedPrice = requireNonNegative(pricePerSquareMeter,
                "Measurement item price per square meter must not be negative", 2);
        BigDecimal calculatedTotal = normalizedArea.multiply(normalizedPrice).setScale(2, RoundingMode.HALF_UP);
        return new MeasurementItem(null, companyId, measurementVersionId, itemNumber, serviceTitle, activity,
                description, measuredOn, MeasurementChargeType.SQUARE_METER, normalizedArea, null, null, null, normalizedPrice, null,
                calculatedTotal, SQUARE_METER_FORMULA, true, null, null, null);
    }

    public static MeasurementItem createLinearMeter(UUID companyId, UUID measurementVersionId, int itemNumber,
                                                    String activity, String description, BigDecimal linearMeters,
                                                    BigDecimal pricePerLinearMeter) {
        return createLinearMeter(companyId, measurementVersionId, itemNumber, activity, activity, description,
                LocalDate.of(1970, 1, 1), linearMeters, pricePerLinearMeter);
    }

    public static MeasurementItem createLinearMeter(UUID companyId, UUID measurementVersionId, int itemNumber,
                                                     String serviceTitle, String activity, String description,
                                                     LocalDate measuredOn, BigDecimal linearMeters,
                                                     BigDecimal pricePerLinearMeter) {
        BigDecimal normalizedMeters = requirePositive(linearMeters, "Measurement item linear meters must be positive", 4);
        BigDecimal normalizedPrice = requireNonNegative(pricePerLinearMeter,
                "Measurement item price per linear meter must not be negative", 2);
        BigDecimal calculatedTotal = normalizedMeters.multiply(normalizedPrice).setScale(2, RoundingMode.HALF_UP);
        return new MeasurementItem(null, companyId, measurementVersionId, itemNumber, serviceTitle, activity,
                description, measuredOn, MeasurementChargeType.LINEAR_METER, null, normalizedMeters, null, null, normalizedPrice, null,
                calculatedTotal, LINEAR_METER_FORMULA, true, null, null, null);
    }

    public static MeasurementItem createKilogramPerSquareMeter(UUID companyId, UUID measurementVersionId, int itemNumber,
                                                                String activity, String description,
                                                                BigDecimal kilogramsPerSquareMeter,
                                                                BigDecimal areaSquareMeters, BigDecimal pricePerKilogram) {
        return createKilogramPerSquareMeter(companyId, measurementVersionId, itemNumber, activity, activity,
                description, LocalDate.of(1970, 1, 1), kilogramsPerSquareMeter, areaSquareMeters, pricePerKilogram);
    }

    public static MeasurementItem createKilogramPerSquareMeter(UUID companyId, UUID measurementVersionId, int itemNumber,
                                                                String serviceTitle, String activity, String description,
                                                                LocalDate measuredOn, BigDecimal kilogramsPerSquareMeter,
                                                                BigDecimal areaSquareMeters, BigDecimal pricePerKilogram) {
        BigDecimal normalizedKilogramsPerSquareMeter = requirePositive(kilogramsPerSquareMeter,
                "Measurement item kilograms per square meter must be positive", 4);
        BigDecimal normalizedArea = requirePositive(areaSquareMeters, "Measurement item area must be positive", 4);
        BigDecimal normalizedPrice = requireNonNegative(pricePerKilogram,
                "Measurement item price per kilogram must not be negative", 2);
        BigDecimal calculatedWeight = normalizedKilogramsPerSquareMeter.multiply(normalizedArea)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal calculatedTotal = calculatedWeight.multiply(normalizedPrice).setScale(2, RoundingMode.HALF_UP);
        return new MeasurementItem(null, companyId, measurementVersionId, itemNumber, serviceTitle, activity,
                description, measuredOn, MeasurementChargeType.KILOGRAM_PER_SQUARE_METER, normalizedArea, null, normalizedKilogramsPerSquareMeter,
                null, normalizedPrice, calculatedWeight, calculatedTotal, KILOGRAM_PER_SQUARE_METER_FORMULA,
                true, null, null, null);
    }

    public static MeasurementItem createKilogramPerLinearMeter(UUID companyId, UUID measurementVersionId, int itemNumber,
                                                                String activity, String description,
                                                                BigDecimal kilogramsPerLinearMeter,
                                                                BigDecimal linearMeters, BigDecimal pricePerKilogram) {
        return createKilogramPerLinearMeter(companyId, measurementVersionId, itemNumber, activity, activity,
                description, LocalDate.of(1970, 1, 1), kilogramsPerLinearMeter, linearMeters, pricePerKilogram);
    }

    public static MeasurementItem createKilogramPerLinearMeter(UUID companyId, UUID measurementVersionId, int itemNumber,
                                                                String serviceTitle, String activity, String description,
                                                                LocalDate measuredOn, BigDecimal kilogramsPerLinearMeter,
                                                                BigDecimal linearMeters, BigDecimal pricePerKilogram) {
        BigDecimal normalizedKilogramsPerLinearMeter = requirePositive(kilogramsPerLinearMeter,
                "Measurement item kilograms per linear meter must be positive", 4);
        BigDecimal normalizedMeters = requirePositive(linearMeters, "Measurement item linear meters must be positive", 4);
        BigDecimal normalizedPrice = requireNonNegative(pricePerKilogram,
                "Measurement item price per kilogram must not be negative", 2);
        BigDecimal calculatedWeight = normalizedKilogramsPerLinearMeter.multiply(normalizedMeters)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal calculatedTotal = calculatedWeight.multiply(normalizedPrice).setScale(2, RoundingMode.HALF_UP);
        return new MeasurementItem(null, companyId, measurementVersionId, itemNumber, serviceTitle, activity,
                description, measuredOn, MeasurementChargeType.KILOGRAM_PER_LINEAR_METER, null, normalizedMeters, null,
                normalizedKilogramsPerLinearMeter, normalizedPrice, calculatedWeight, calculatedTotal,
                KILOGRAM_PER_LINEAR_METER_FORMULA, true, null, null, null);
    }

    public static MeasurementItem createUnit(UUID companyId, UUID measurementVersionId, int itemNumber,
                                             String serviceTitle, String activity, String description,
                                             LocalDate measuredOn, BigDecimal unitQuantity, BigDecimal unitPrice) {
        BigDecimal normalizedQuantity = requirePositive(unitQuantity, "Measurement item unit quantity must be positive", 4);
        BigDecimal normalizedPrice = requireNonNegative(unitPrice,
                "Measurement item price per unit must not be negative", 2);
        BigDecimal calculatedTotal = normalizedQuantity.multiply(normalizedPrice).setScale(2, RoundingMode.HALF_UP);
        return new MeasurementItem(null, companyId, measurementVersionId, itemNumber, serviceTitle, activity,
                description, measuredOn, MeasurementChargeType.UNIT, null, null, null, null, normalizedQuantity,
                normalizedPrice, null, calculatedTotal, UNIT_FORMULA, true, null, null, null);
    }

    public static MeasurementItem rehydrate(UUID id, UUID companyId, UUID measurementVersionId, int itemNumber,
                                            String serviceTitle, String activity, String description, LocalDate measuredOn,
                                            MeasurementChargeType chargeType,
                                            BigDecimal areaSquareMeters, BigDecimal linearMeters,
                                            BigDecimal kilogramsPerSquareMeter, BigDecimal kilogramsPerLinearMeter,
                                            BigDecimal unitPrice, BigDecimal totalWeightKg, BigDecimal totalAmount,
                                            String calculationFormula, Instant createdAt, Instant updatedAt) {
        return new MeasurementItem(id, companyId, measurementVersionId, itemNumber, serviceTitle, activity, description,
                measuredOn, chargeType, areaSquareMeters, linearMeters, kilogramsPerSquareMeter,
                kilogramsPerLinearMeter, unitPrice, totalWeightKg, totalAmount, calculationFormula,
                true, null, createdAt, updatedAt);
    }

    public static MeasurementItem rehydrate(UUID id, UUID companyId, UUID measurementVersionId, int itemNumber,
                                            String serviceTitle, String activity, String description, LocalDate measuredOn,
                                            MeasurementChargeType chargeType,
                                            BigDecimal areaSquareMeters, BigDecimal linearMeters,
                                            BigDecimal kilogramsPerSquareMeter, BigDecimal kilogramsPerLinearMeter,
                                            BigDecimal unitQuantity, BigDecimal unitPrice, BigDecimal totalWeightKg,
                                            BigDecimal totalAmount, String calculationFormula, boolean active,
                                            UUID sourceItemId, Instant createdAt, Instant updatedAt) {
        return new MeasurementItem(id, companyId, measurementVersionId, itemNumber, serviceTitle, activity, description,
                measuredOn, chargeType, areaSquareMeters, linearMeters, kilogramsPerSquareMeter,
                kilogramsPerLinearMeter, unitQuantity, unitPrice, totalWeightKg, totalAmount, calculationFormula,
                active, sourceItemId, createdAt, updatedAt);
    }

    public static MeasurementItem rehydrate(UUID id, UUID companyId, UUID measurementVersionId, int itemNumber,
                                            String serviceTitle, String activity, String description, LocalDate measuredOn,
                                            MeasurementChargeType chargeType,
                                            BigDecimal areaSquareMeters, BigDecimal linearMeters,
                                            BigDecimal kilogramsPerSquareMeter, BigDecimal kilogramsPerLinearMeter,
                                            BigDecimal unitPrice, BigDecimal totalWeightKg, BigDecimal totalAmount,
                                            String calculationFormula, boolean active, UUID sourceItemId,
                                            Instant createdAt, Instant updatedAt) {
        return new MeasurementItem(id, companyId, measurementVersionId, itemNumber, serviceTitle, activity, description,
                measuredOn, chargeType, areaSquareMeters, linearMeters, kilogramsPerSquareMeter,
                kilogramsPerLinearMeter, unitPrice, totalWeightKg, totalAmount, calculationFormula,
                active, sourceItemId, createdAt, updatedAt);
    }

    public static MeasurementItem rehydrate(UUID id, UUID companyId, UUID measurementVersionId, int itemNumber,
                                            String activity, String description, MeasurementChargeType chargeType,
                                            BigDecimal areaSquareMeters, BigDecimal linearMeters,
                                            BigDecimal kilogramsPerSquareMeter, BigDecimal kilogramsPerLinearMeter,
                                            BigDecimal unitPrice, BigDecimal totalWeightKg, BigDecimal totalAmount,
                                            String calculationFormula, Instant createdAt, Instant updatedAt) {
        return new MeasurementItem(id, companyId, measurementVersionId, itemNumber, activity, activity, description,
                LocalDate.of(1970, 1, 1), chargeType, areaSquareMeters, linearMeters, kilogramsPerSquareMeter,
                kilogramsPerLinearMeter, unitPrice,
                totalWeightKg, totalAmount, calculationFormula, true, null, createdAt, updatedAt);
    }

    public MeasurementItem replaceWith(MeasurementItem replacement) {
        require(replacement, "Measurement item replacement is required");
        if (!companyId.equals(replacement.companyId) || !measurementVersionId.equals(replacement.measurementVersionId)
                || id == null) {
            throw new IllegalArgumentException("Measurement item replacement must belong to the same draft version");
        }
        return new MeasurementItem(id, companyId, measurementVersionId, replacement.itemNumber, replacement.serviceTitle,
                replacement.activity, replacement.description, replacement.measuredOn, replacement.chargeType,
                replacement.areaSquareMeters, replacement.linearMeters, replacement.kilogramsPerSquareMeter,
                replacement.kilogramsPerLinearMeter, replacement.unitQuantity, replacement.unitPrice, replacement.totalWeightKg,
                replacement.totalAmount, replacement.calculationFormula, true, sourceItemId, createdAt, updatedAt);
    }

    public MeasurementItem deactivate() {
        if (id == null) {
            throw new IllegalStateException("Only persisted measurement items can be deactivated");
        }
        return new MeasurementItem(id, companyId, measurementVersionId, itemNumber, serviceTitle, activity, description,
                measuredOn, chargeType, areaSquareMeters, linearMeters, kilogramsPerSquareMeter,
                kilogramsPerLinearMeter, unitQuantity, unitPrice, totalWeightKg, totalAmount, calculationFormula,
                false, sourceItemId, createdAt, updatedAt);
    }

    public MeasurementItem copyToRevision(UUID targetVersionId, UUID originItemId) {
        return new MeasurementItem(null, companyId, require(targetVersionId, "Measurement revision version is required"),
                itemNumber, serviceTitle, activity, description, measuredOn, chargeType, areaSquareMeters, linearMeters,
                kilogramsPerSquareMeter, kilogramsPerLinearMeter, unitQuantity, unitPrice, totalWeightKg, totalAmount,
                calculationFormula, active, require(originItemId, "Measurement item origin is required"), null, null);
    }

    public MeasurementItem withSourceItemId(UUID originItemId) {
        return new MeasurementItem(id, companyId, measurementVersionId, itemNumber, serviceTitle, activity, description,
                measuredOn, chargeType, areaSquareMeters, linearMeters, kilogramsPerSquareMeter,
                kilogramsPerLinearMeter, unitQuantity, unitPrice, totalWeightKg, totalAmount, calculationFormula,
                active, require(originItemId, "Measurement item origin is required"), createdAt, updatedAt);
    }

    /**
     * Reassigns only the display/order number of an active draft line. The
     * caller must perform a batch reorder in one transaction so the database
     * uniqueness constraint is never observed in an intermediate state.
     */
    public MeasurementItem withItemNumber(int replacementItemNumber) {
        if (replacementItemNumber < 1) {
            throw new IllegalArgumentException("Measurement item number must be positive");
        }
        return new MeasurementItem(id, companyId, measurementVersionId, replacementItemNumber, serviceTitle, activity,
                description, measuredOn, chargeType, areaSquareMeters, linearMeters, kilogramsPerSquareMeter,
                kilogramsPerLinearMeter, unitQuantity, unitPrice, totalWeightKg, totalAmount, calculationFormula,
                active, sourceItemId, createdAt, updatedAt);
    }

    /**
     * Reassigns the service grouping of a draft item without touching its
     * measurement formula or calculated values.
     */
    public MeasurementItem withServiceTitle(String replacementServiceTitle) {
        return new MeasurementItem(id, companyId, measurementVersionId, itemNumber, replacementServiceTitle, activity,
                description, measuredOn, chargeType, areaSquareMeters, linearMeters, kilogramsPerSquareMeter,
                kilogramsPerLinearMeter, unitQuantity, unitPrice, totalWeightKg, totalAmount, calculationFormula,
                active, sourceItemId, createdAt, updatedAt);
    }

    /**
     * Stores the number of pieces executed for a non-UNIT charge. This is an
     * operational annotation only: it never changes the formula, weight or
     * monetary amount of the line.
     */
    public MeasurementItem withOperationalUnitQuantity(BigDecimal operationalUnitQuantity) {
        if (chargeType == MeasurementChargeType.UNIT) {
            throw new IllegalArgumentException("Operational unit quantity is only applicable to non-UNIT measurement items");
        }
        return new MeasurementItem(id, companyId, measurementVersionId, itemNumber, serviceTitle, activity,
                description, measuredOn, chargeType, areaSquareMeters, linearMeters, kilogramsPerSquareMeter,
                kilogramsPerLinearMeter, operationalUnitQuantity, unitPrice, totalWeightKg, totalAmount,
                calculationFormula, active, sourceItemId, createdAt, updatedAt);
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateSquareMeterCalculation() {
        if (chargeType != MeasurementChargeType.SQUARE_METER) {
            return;
        }
        BigDecimal normalizedArea = requirePositive(areaSquareMeters, "Measurement item area must be positive", 4);
        BigDecimal normalizedPrice = requireNonNegative(unitPrice,
                "Measurement item price per square meter must not be negative", 2);
        BigDecimal calculatedTotal = normalizedArea.multiply(normalizedPrice).setScale(2, RoundingMode.HALF_UP);
        if (linearMeters != null || kilogramsPerSquareMeter != null || kilogramsPerLinearMeter != null
                || totalWeightKg != null) {
            throw new IllegalArgumentException("Square meter measurement item cannot contain weight or linear inputs");
        }
        if (totalAmount == null || calculatedTotal.compareTo(totalAmount.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new IllegalArgumentException("Measurement item total amount is inconsistent with square meter calculation");
        }
        if (!SQUARE_METER_FORMULA.equals(calculationFormula)) {
            throw new IllegalArgumentException("Measurement item square meter formula is inconsistent");
        }
    }

    private void validateLinearMeterCalculation() {
        if (chargeType != MeasurementChargeType.LINEAR_METER) {
            return;
        }
        BigDecimal normalizedMeters = requirePositive(linearMeters, "Measurement item linear meters must be positive", 4);
        BigDecimal normalizedPrice = requireNonNegative(unitPrice,
                "Measurement item price per linear meter must not be negative", 2);
        BigDecimal calculatedTotal = normalizedMeters.multiply(normalizedPrice).setScale(2, RoundingMode.HALF_UP);
        if (areaSquareMeters != null || kilogramsPerSquareMeter != null || kilogramsPerLinearMeter != null
                || totalWeightKg != null) {
            throw new IllegalArgumentException("Linear meter measurement item cannot contain area or weight inputs");
        }
        if (totalAmount == null || calculatedTotal.compareTo(totalAmount.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new IllegalArgumentException("Measurement item total amount is inconsistent with linear meter calculation");
        }
        if (!LINEAR_METER_FORMULA.equals(calculationFormula)) {
            throw new IllegalArgumentException("Measurement item linear meter formula is inconsistent");
        }
    }

    private void validateKilogramPerSquareMeterCalculation() {
        if (chargeType != MeasurementChargeType.KILOGRAM_PER_SQUARE_METER) {
            return;
        }
        BigDecimal normalizedKilogramsPerSquareMeter = requirePositive(kilogramsPerSquareMeter,
                "Measurement item kilograms per square meter must be positive", 4);
        BigDecimal normalizedArea = requirePositive(areaSquareMeters, "Measurement item area must be positive", 4);
        BigDecimal normalizedPrice = requireNonNegative(unitPrice,
                "Measurement item price per kilogram must not be negative", 2);
        BigDecimal calculatedWeight = normalizedKilogramsPerSquareMeter.multiply(normalizedArea)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal calculatedTotal = calculatedWeight.multiply(normalizedPrice).setScale(2, RoundingMode.HALF_UP);
        if (linearMeters != null || kilogramsPerLinearMeter != null) {
            throw new IllegalArgumentException("Kilogram per square meter item cannot contain linear inputs");
        }
        if (totalWeightKg == null || calculatedWeight.compareTo(totalWeightKg.setScale(4, RoundingMode.HALF_UP)) != 0) {
            throw new IllegalArgumentException("Measurement item total weight is inconsistent with kilogram per square meter calculation");
        }
        if (totalAmount == null || calculatedTotal.compareTo(totalAmount.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new IllegalArgumentException("Measurement item total amount is inconsistent with kilogram per square meter calculation");
        }
        if (!KILOGRAM_PER_SQUARE_METER_FORMULA.equals(calculationFormula)) {
            throw new IllegalArgumentException("Measurement item kilogram per square meter formula is inconsistent");
        }
    }

    private void validateKilogramPerLinearMeterCalculation() {
        if (chargeType != MeasurementChargeType.KILOGRAM_PER_LINEAR_METER) {
            return;
        }
        BigDecimal normalizedKilogramsPerLinearMeter = requirePositive(kilogramsPerLinearMeter,
                "Measurement item kilograms per linear meter must be positive", 4);
        BigDecimal normalizedMeters = requirePositive(linearMeters, "Measurement item linear meters must be positive", 4);
        BigDecimal normalizedPrice = requireNonNegative(unitPrice,
                "Measurement item price per kilogram must not be negative", 2);
        BigDecimal calculatedWeight = normalizedKilogramsPerLinearMeter.multiply(normalizedMeters)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal calculatedTotal = calculatedWeight.multiply(normalizedPrice).setScale(2, RoundingMode.HALF_UP);
        if (areaSquareMeters != null || kilogramsPerSquareMeter != null) {
            throw new IllegalArgumentException("Kilogram per linear meter item cannot contain area inputs");
        }
        if (totalWeightKg == null || calculatedWeight.compareTo(totalWeightKg.setScale(4, RoundingMode.HALF_UP)) != 0) {
            throw new IllegalArgumentException("Measurement item total weight is inconsistent with kilogram per linear meter calculation");
        }
        if (totalAmount == null || calculatedTotal.compareTo(totalAmount.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new IllegalArgumentException("Measurement item total amount is inconsistent with kilogram per linear meter calculation");
        }
        if (!KILOGRAM_PER_LINEAR_METER_FORMULA.equals(calculationFormula)) {
            throw new IllegalArgumentException("Measurement item kilogram per linear meter formula is inconsistent");
        }
    }

    private void validateUnitCalculation() {
        if (chargeType != MeasurementChargeType.UNIT) {
            return;
        }
        BigDecimal normalizedQuantity = requirePositive(unitQuantity,
                "Measurement item unit quantity must be positive", 4);
        BigDecimal normalizedPrice = requireNonNegative(unitPrice,
                "Measurement item price per unit must not be negative", 2);
        BigDecimal calculatedTotal = normalizedQuantity.multiply(normalizedPrice).setScale(2, RoundingMode.HALF_UP);
        if (areaSquareMeters != null || linearMeters != null || kilogramsPerSquareMeter != null
                || kilogramsPerLinearMeter != null || totalWeightKg != null) {
            throw new IllegalArgumentException("Unit measurement item cannot contain area, linear or weight inputs");
        }
        if (totalAmount == null || calculatedTotal.compareTo(totalAmount.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new IllegalArgumentException("Measurement item total amount is inconsistent with unit calculation");
        }
        if (!UNIT_FORMULA.equals(calculationFormula)) {
            throw new IllegalArgumentException("Measurement item unit formula is inconsistent");
        }
    }

    private static BigDecimal requirePositive(BigDecimal value, String message, int scale) {
        BigDecimal normalized = require(value, message).setScale(scale, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static BigDecimal normalizeUnitQuantity(MeasurementChargeType chargeType, BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.scale() > 4) {
            throw new IllegalArgumentException("Measurement item unit quantity must have at most four decimal places");
        }
        BigDecimal normalized = value.setScale(4, RoundingMode.UNNECESSARY);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("Measurement item unit quantity must be positive");
        }
        return normalized;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String message, int scale) {
        BigDecimal normalized = require(value, message).setScale(scale, RoundingMode.HALF_UP);
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
