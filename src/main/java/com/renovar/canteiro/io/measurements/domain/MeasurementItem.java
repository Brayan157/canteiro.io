package com.renovar.canteiro.io.measurements.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Getter
public final class MeasurementItem {

    private static final String SQUARE_METER_FORMULA = "area_square_meters × unit_price";
    private static final String LINEAR_METER_FORMULA = "linear_meters × unit_price";
    private static final String KILOGRAM_PER_SQUARE_METER_FORMULA =
            "kilograms_per_square_meter × area_square_meters = total_weight_kg; total_weight_kg × unit_price";
    private static final String KILOGRAM_PER_LINEAR_METER_FORMULA =
            "kilograms_per_linear_meter × linear_meters = total_weight_kg; total_weight_kg × unit_price";

    private final UUID id;
    private final UUID companyId;
    private final UUID measurementVersionId;
    private final int itemNumber;
    private final String activity;
    private final String description;
    private final MeasurementChargeType chargeType;
    private final BigDecimal areaSquareMeters;
    private final BigDecimal linearMeters;
    private final BigDecimal kilogramsPerSquareMeter;
    private final BigDecimal kilogramsPerLinearMeter;
    private final BigDecimal unitPrice;
    private final BigDecimal totalWeightKg;
    private final BigDecimal totalAmount;
    private final String calculationFormula;
    private final Instant createdAt;
    private final Instant updatedAt;

    private MeasurementItem(UUID id, UUID companyId, UUID measurementVersionId, int itemNumber, String activity,
                            String description, MeasurementChargeType chargeType, BigDecimal areaSquareMeters,
                            BigDecimal linearMeters, BigDecimal kilogramsPerSquareMeter,
                            BigDecimal kilogramsPerLinearMeter, BigDecimal unitPrice, BigDecimal totalWeightKg,
                            BigDecimal totalAmount, String calculationFormula, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Measurement item company is required");
        this.measurementVersionId = require(measurementVersionId, "Measurement item version is required");
        if (itemNumber < 1) {
            throw new IllegalArgumentException("Measurement item number must be positive");
        }
        this.itemNumber = itemNumber;
        this.activity = requireText(activity, "Measurement item activity is required");
        this.description = normalize(description);
        this.chargeType = require(chargeType, "Measurement item charge type is required");
        this.areaSquareMeters = areaSquareMeters;
        this.linearMeters = linearMeters;
        this.kilogramsPerSquareMeter = kilogramsPerSquareMeter;
        this.kilogramsPerLinearMeter = kilogramsPerLinearMeter;
        this.unitPrice = unitPrice;
        this.totalWeightKg = totalWeightKg;
        this.totalAmount = totalAmount;
        this.calculationFormula = normalize(calculationFormula);
        validateSquareMeterCalculation();
        validateLinearMeterCalculation();
        validateKilogramPerSquareMeterCalculation();
        validateKilogramPerLinearMeterCalculation();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static MeasurementItem createSquareMeter(UUID companyId, UUID measurementVersionId, int itemNumber,
                                                    String activity, String description, BigDecimal areaSquareMeters,
                                                    BigDecimal pricePerSquareMeter) {
        BigDecimal normalizedArea = requirePositive(areaSquareMeters, "Measurement item area must be positive", 4);
        BigDecimal normalizedPrice = requireNonNegative(pricePerSquareMeter,
                "Measurement item price per square meter must not be negative", 2);
        BigDecimal calculatedTotal = normalizedArea.multiply(normalizedPrice).setScale(2, RoundingMode.HALF_UP);
        return new MeasurementItem(null, companyId, measurementVersionId, itemNumber, activity, description,
                MeasurementChargeType.SQUARE_METER, normalizedArea, null, null, null, normalizedPrice, null,
                calculatedTotal, SQUARE_METER_FORMULA, null, null);
    }

    public static MeasurementItem createLinearMeter(UUID companyId, UUID measurementVersionId, int itemNumber,
                                                    String activity, String description, BigDecimal linearMeters,
                                                    BigDecimal pricePerLinearMeter) {
        BigDecimal normalizedMeters = requirePositive(linearMeters, "Measurement item linear meters must be positive", 4);
        BigDecimal normalizedPrice = requireNonNegative(pricePerLinearMeter,
                "Measurement item price per linear meter must not be negative", 2);
        BigDecimal calculatedTotal = normalizedMeters.multiply(normalizedPrice).setScale(2, RoundingMode.HALF_UP);
        return new MeasurementItem(null, companyId, measurementVersionId, itemNumber, activity, description,
                MeasurementChargeType.LINEAR_METER, null, normalizedMeters, null, null, normalizedPrice, null,
                calculatedTotal, LINEAR_METER_FORMULA, null, null);
    }

    public static MeasurementItem createKilogramPerSquareMeter(UUID companyId, UUID measurementVersionId, int itemNumber,
                                                                String activity, String description,
                                                                BigDecimal kilogramsPerSquareMeter,
                                                                BigDecimal areaSquareMeters, BigDecimal pricePerKilogram) {
        BigDecimal normalizedKilogramsPerSquareMeter = requirePositive(kilogramsPerSquareMeter,
                "Measurement item kilograms per square meter must be positive", 4);
        BigDecimal normalizedArea = requirePositive(areaSquareMeters, "Measurement item area must be positive", 4);
        BigDecimal normalizedPrice = requireNonNegative(pricePerKilogram,
                "Measurement item price per kilogram must not be negative", 2);
        BigDecimal calculatedWeight = normalizedKilogramsPerSquareMeter.multiply(normalizedArea)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal calculatedTotal = calculatedWeight.multiply(normalizedPrice).setScale(2, RoundingMode.HALF_UP);
        return new MeasurementItem(null, companyId, measurementVersionId, itemNumber, activity, description,
                MeasurementChargeType.KILOGRAM_PER_SQUARE_METER, normalizedArea, null, normalizedKilogramsPerSquareMeter,
                null, normalizedPrice, calculatedWeight, calculatedTotal, KILOGRAM_PER_SQUARE_METER_FORMULA, null, null);
    }

    public static MeasurementItem createKilogramPerLinearMeter(UUID companyId, UUID measurementVersionId, int itemNumber,
                                                                String activity, String description,
                                                                BigDecimal kilogramsPerLinearMeter,
                                                                BigDecimal linearMeters, BigDecimal pricePerKilogram) {
        BigDecimal normalizedKilogramsPerLinearMeter = requirePositive(kilogramsPerLinearMeter,
                "Measurement item kilograms per linear meter must be positive", 4);
        BigDecimal normalizedMeters = requirePositive(linearMeters, "Measurement item linear meters must be positive", 4);
        BigDecimal normalizedPrice = requireNonNegative(pricePerKilogram,
                "Measurement item price per kilogram must not be negative", 2);
        BigDecimal calculatedWeight = normalizedKilogramsPerLinearMeter.multiply(normalizedMeters)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal calculatedTotal = calculatedWeight.multiply(normalizedPrice).setScale(2, RoundingMode.HALF_UP);
        return new MeasurementItem(null, companyId, measurementVersionId, itemNumber, activity, description,
                MeasurementChargeType.KILOGRAM_PER_LINEAR_METER, null, normalizedMeters, null,
                normalizedKilogramsPerLinearMeter, normalizedPrice, calculatedWeight, calculatedTotal,
                KILOGRAM_PER_LINEAR_METER_FORMULA, null, null);
    }

    public static MeasurementItem rehydrate(UUID id, UUID companyId, UUID measurementVersionId, int itemNumber,
                                            String activity, String description, MeasurementChargeType chargeType,
                                            BigDecimal areaSquareMeters, BigDecimal linearMeters,
                                            BigDecimal kilogramsPerSquareMeter, BigDecimal kilogramsPerLinearMeter,
                                            BigDecimal unitPrice, BigDecimal totalWeightKg, BigDecimal totalAmount,
                                            String calculationFormula, Instant createdAt, Instant updatedAt) {
        return new MeasurementItem(id, companyId, measurementVersionId, itemNumber, activity, description, chargeType,
                areaSquareMeters, linearMeters, kilogramsPerSquareMeter, kilogramsPerLinearMeter, unitPrice,
                totalWeightKg, totalAmount, calculationFormula, createdAt, updatedAt);
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
        if (linearMeters != null || kilogramsPerSquareMeter != null || kilogramsPerLinearMeter != null || totalWeightKg != null) {
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
        if (areaSquareMeters != null || kilogramsPerSquareMeter != null || kilogramsPerLinearMeter != null || totalWeightKg != null) {
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

    private static BigDecimal requirePositive(BigDecimal value, String message, int scale) {
        BigDecimal normalized = require(value, message).setScale(scale, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException(message);
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
