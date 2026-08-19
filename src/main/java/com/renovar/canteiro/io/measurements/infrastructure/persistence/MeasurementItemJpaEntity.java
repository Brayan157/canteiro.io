package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;
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
import java.util.UUID;

@Getter
@Entity
@Table(name = "measurement_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasurementItemJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false) private UUID companyId;
    @Column(name = "measurement_version_id", nullable = false, updatable = false) private UUID measurementVersionId;
    @Column(name = "item_number", nullable = false, updatable = false) private int itemNumber;
    @Column(name = "activity", nullable = false, length = 255) private String activity;
    @Column(name = "description", length = 1000) private String description;
    @Enumerated(EnumType.STRING) @Column(name = "charge_type", nullable = false, length = 40) private MeasurementChargeType chargeType;
    @Column(name = "area_square_meters", precision = 18, scale = 4) private BigDecimal areaSquareMeters;
    @Column(name = "linear_meters", precision = 18, scale = 4) private BigDecimal linearMeters;
    @Column(name = "kilograms_per_square_meter", precision = 18, scale = 4) private BigDecimal kilogramsPerSquareMeter;
    @Column(name = "kilograms_per_linear_meter", precision = 18, scale = 4) private BigDecimal kilogramsPerLinearMeter;
    @Column(name = "unit_price", precision = 19, scale = 2) private BigDecimal unitPrice;
    @Column(name = "total_weight_kg", precision = 18, scale = 4) private BigDecimal totalWeightKg;
    @Column(name = "total_amount", precision = 19, scale = 2) private BigDecimal totalAmount;
    @Column(name = "calculation_formula", length = 1000) private String calculationFormula;

    public MeasurementItemJpaEntity(UUID companyId, UUID measurementVersionId, int itemNumber, String activity,
                                    String description, MeasurementChargeType chargeType, BigDecimal areaSquareMeters,
                                    BigDecimal linearMeters, BigDecimal kilogramsPerSquareMeter,
                                    BigDecimal kilogramsPerLinearMeter, BigDecimal unitPrice, BigDecimal totalWeightKg,
                                    BigDecimal totalAmount, String calculationFormula) {
        this.companyId = companyId;
        this.measurementVersionId = measurementVersionId;
        this.itemNumber = itemNumber;
        this.activity = activity;
        this.description = description;
        this.chargeType = chargeType;
        this.areaSquareMeters = areaSquareMeters;
        this.linearMeters = linearMeters;
        this.kilogramsPerSquareMeter = kilogramsPerSquareMeter;
        this.kilogramsPerLinearMeter = kilogramsPerLinearMeter;
        this.unitPrice = unitPrice;
        this.totalWeightKg = totalWeightKg;
        this.totalAmount = totalAmount;
        this.calculationFormula = calculationFormula;
    }
}
