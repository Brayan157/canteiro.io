package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "measurement_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasurementItemJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false) private UUID companyId;
    @Column(name = "measurement_version_id", nullable = false, updatable = false) private UUID measurementVersionId;
    @Column(name = "item_number", nullable = false) private int itemNumber;
    @Column(name = "service_title", nullable = false, length = 255) private String serviceTitle;
    @Column(name = "activity", nullable = false, length = 255) private String activity;
    @Column(name = "description", length = 1000) private String description;
    @Column(name = "measured_on", nullable = false) private LocalDate measuredOn;
    @Enumerated(EnumType.STRING) @Column(name = "charge_type", nullable = false, length = 40) private MeasurementChargeType chargeType;
    @Column(name = "area_square_meters", precision = 18, scale = 4) private BigDecimal areaSquareMeters;
    @Column(name = "linear_meters", precision = 18, scale = 4) private BigDecimal linearMeters;
    @Column(name = "kilograms_per_square_meter", precision = 18, scale = 4) private BigDecimal kilogramsPerSquareMeter;
    @Column(name = "kilograms_per_linear_meter", precision = 18, scale = 4) private BigDecimal kilogramsPerLinearMeter;
    @Column(name = "unit_quantity", precision = 18, scale = 4) private BigDecimal unitQuantity;
    @Column(name = "unit_price", precision = 19, scale = 2) private BigDecimal unitPrice;
    @Column(name = "total_weight_kg", precision = 18, scale = 4) private BigDecimal totalWeightKg;
    @Column(name = "total_amount", precision = 19, scale = 2) private BigDecimal totalAmount;
    @Column(name = "calculation_formula", length = 1000) private String calculationFormula;
    @Column(name = "active", nullable = false) private boolean active;
    @Column(name = "source_item_id") private UUID sourceItemId;

    public MeasurementItemJpaEntity(UUID companyId, UUID measurementVersionId, int itemNumber, String serviceTitle,
                                    String activity, String description, LocalDate measuredOn,
                                    MeasurementChargeType chargeType, BigDecimal areaSquareMeters,
                                    BigDecimal linearMeters, BigDecimal kilogramsPerSquareMeter,
                                    BigDecimal kilogramsPerLinearMeter, BigDecimal unitQuantity, BigDecimal unitPrice, BigDecimal totalWeightKg,
                                    BigDecimal totalAmount, String calculationFormula, boolean active, UUID sourceItemId) {
        this.companyId = companyId;
        this.measurementVersionId = measurementVersionId;
        this.itemNumber = itemNumber;
        this.serviceTitle = serviceTitle;
        this.activity = activity;
        this.description = description;
        this.measuredOn = measuredOn;
        this.chargeType = chargeType;
        this.areaSquareMeters = areaSquareMeters;
        this.linearMeters = linearMeters;
        this.kilogramsPerSquareMeter = kilogramsPerSquareMeter;
        this.kilogramsPerLinearMeter = kilogramsPerLinearMeter;
        this.unitQuantity = unitQuantity;
        this.unitPrice = unitPrice;
        this.totalWeightKg = totalWeightKg;
        this.totalAmount = totalAmount;
        this.calculationFormula = calculationFormula;
        this.active = active;
        this.sourceItemId = sourceItemId;
    }

    void apply(MeasurementItem measurementItem) {
        this.itemNumber = measurementItem.getItemNumber();
        this.serviceTitle = measurementItem.getServiceTitle();
        this.activity = measurementItem.getActivity();
        this.description = measurementItem.getDescription();
        this.measuredOn = measurementItem.getMeasuredOn();
        this.chargeType = measurementItem.getChargeType();
        this.areaSquareMeters = measurementItem.getAreaSquareMeters();
        this.linearMeters = measurementItem.getLinearMeters();
        this.kilogramsPerSquareMeter = measurementItem.getKilogramsPerSquareMeter();
        this.kilogramsPerLinearMeter = measurementItem.getKilogramsPerLinearMeter();
        this.unitQuantity = measurementItem.getUnitQuantity();
        this.unitPrice = measurementItem.getUnitPrice();
        this.totalWeightKg = measurementItem.getTotalWeightKg();
        this.totalAmount = measurementItem.getTotalAmount();
        this.calculationFormula = measurementItem.getCalculationFormula();
        this.active = measurementItem.isActive();
        this.sourceItemId = measurementItem.getSourceItemId();
    }
}
