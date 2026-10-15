package com.renovar.canteiro.io.measurements.infrastructure.persistence;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuousService;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;
@Getter @Entity @Table(name="measurement_continuous_service") @NoArgsConstructor(access=AccessLevel.PROTECTED)
class MeasurementContinuousServiceJpaEntity extends BaseJpaEntity {
    @Column(name="company_id", nullable=false, updatable=false) private UUID companyId;
    @Column(name="measurement_id", nullable=false, updatable=false) private UUID measurementId;
    @Column(name="service_title", nullable=false, length=255) private String serviceTitle;
    @Column(name="sort_order", nullable=false) private int sortOrder;
    @Column(name="destination_action", length=40) private String destinationAction;
    @Column(name="contract_service_id") private UUID contractServiceId;
    @Version @Column(name="lock_version", nullable=false) private int lockVersion;
    MeasurementContinuousServiceJpaEntity(MeasurementContinuousService s) {
        companyId=s.companyId(); measurementId=s.measurementId(); serviceTitle=s.serviceTitle(); sortOrder=s.sortOrder();
        destinationAction=s.destinationAction(); contractServiceId=s.contractServiceId();
    }
    void destination(MeasurementContinuousService s) { destinationAction=s.destinationAction(); contractServiceId=s.contractServiceId(); }
}

