package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementContractAdjustment;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@Table(name = "measurement_contract_adjustment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MeasurementContractAdjustmentJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "measurement_version_id", nullable = false, updatable = false)
    private UUID measurementVersionId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "adjustment_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal adjustmentAmount;

    MeasurementContractAdjustmentJpaEntity(MeasurementContractAdjustment adjustment) {
        this.companyId = adjustment.getCompanyId();
        this.measurementVersionId = adjustment.getMeasurementVersionId();
        this.contractId = adjustment.getContractId();
        this.adjustmentAmount = adjustment.getAmount();
    }
}
