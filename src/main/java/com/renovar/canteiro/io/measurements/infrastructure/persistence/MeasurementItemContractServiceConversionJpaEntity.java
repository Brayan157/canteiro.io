package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementItemContractServiceConversion;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "measurement_item_contract_service_conversion")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MeasurementItemContractServiceConversionJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "measurement_version_id", nullable = false, updatable = false)
    private UUID measurementVersionId;

    @Column(name = "measurement_item_id", nullable = false, updatable = false)
    private UUID measurementItemId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "contract_service_id", nullable = false, updatable = false)
    private UUID contractServiceId;

    MeasurementItemContractServiceConversionJpaEntity(MeasurementItemContractServiceConversion conversion) {
        this.companyId = conversion.getCompanyId();
        this.measurementVersionId = conversion.getMeasurementVersionId();
        this.measurementItemId = conversion.getMeasurementItemId();
        this.contractId = conversion.getContractId();
        this.contractServiceId = conversion.getContractServiceId();
    }
}
