package com.renovar.canteiro.io.measurements.infrastructure.persistence;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRecognition;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;
import java.math.BigDecimal;
@Getter @Entity @Table(name="measurement_item_recognition") @NoArgsConstructor(access=AccessLevel.PROTECTED)
class MeasurementItemRecognitionJpaEntity extends BaseJpaEntity {
    @Column(name="company_id", nullable=false, updatable=false) private UUID companyId;
    @Column(name="measurement_id", nullable=false, updatable=false) private UUID measurementId;
    @Column(name="measurement_version_id", nullable=false, updatable=false) private UUID measurementVersionId;
    @Column(name="measurement_item_id", nullable=false, updatable=false) private UUID measurementItemId;
    @Column(name="root_item_id", nullable=false, updatable=false) private UUID rootItemId;
    @Column(name="continuous_service_id", nullable=false, updatable=false) private UUID continuousServiceId;
    @Column(name="gross_amount", nullable=false, updatable=false, precision=19, scale=2) private BigDecimal grossAmount;
    @Column(name="net_amount", nullable=false, updatable=false, precision=19, scale=2) private BigDecimal netAmount;
    @Column(name="total_weight_kg", updatable=false, precision=18, scale=4) private BigDecimal totalWeightKg;
    @Column(name="active", nullable=false, updatable=false) private boolean active;
    @Column(name="version_number", nullable=false, updatable=false) private int versionNumber;
    MeasurementItemRecognitionJpaEntity(MeasurementItemRecognition r) {
        companyId=r.companyId(); measurementId=r.measurementId(); measurementVersionId=r.measurementVersionId();
        measurementItemId=r.measurementItemId(); rootItemId=r.rootItemId(); continuousServiceId=r.continuousServiceId();
        grossAmount=r.grossAmount(); netAmount=r.netAmount(); totalWeightKg=r.totalWeightKg(); active=r.active(); versionNumber=r.versionNumber();
    }
    MeasurementItemRecognition domain() {
        return new MeasurementItemRecognition(companyId,measurementId,measurementVersionId,measurementItemId,rootItemId,
            continuousServiceId,grossAmount,netAmount,totalWeightKg,active,versionNumber);
    }
}

