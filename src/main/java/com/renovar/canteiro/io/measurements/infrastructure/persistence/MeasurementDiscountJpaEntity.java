package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType;
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
@Table(name = "measurement_discount")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasurementDiscountJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "measurement_version_id", nullable = false, updatable = false)
    private UUID measurementVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private MeasurementDiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountValue;

    public MeasurementDiscountJpaEntity(UUID companyId, UUID measurementVersionId,
                                        MeasurementDiscountType discountType, BigDecimal discountValue) {
        this.companyId = companyId;
        this.measurementVersionId = measurementVersionId;
        this.discountType = discountType;
        this.discountValue = discountValue;
    }
}
