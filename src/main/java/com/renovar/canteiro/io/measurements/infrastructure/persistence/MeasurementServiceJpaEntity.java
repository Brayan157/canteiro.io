package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementService;
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
@Table(name = "measurement_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MeasurementServiceJpaEntity extends BaseJpaEntity {
    @Column(name = "company_id", nullable = false, updatable = false) private UUID companyId;
    @Column(name = "measurement_version_id", nullable = false, updatable = false) private UUID measurementVersionId;
    @Column(name = "service_title", nullable = false, updatable = false, length = 255) private String serviceTitle;
    @Column(name = "sort_order", nullable = false, updatable = false) private int sortOrder;

    MeasurementServiceJpaEntity(MeasurementService service) {
        this.companyId = service.getCompanyId();
        this.measurementVersionId = service.getMeasurementVersionId();
        this.serviceTitle = service.getServiceTitle();
        this.sortOrder = service.getSortOrder();
    }
}
