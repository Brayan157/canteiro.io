package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "measurement_version")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasurementVersionJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "measurement_id", nullable = false, updatable = false)
    private UUID measurementId;

    @Column(name = "previous_version_id", updatable = false)
    private UUID previousVersionId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MeasurementVersionStatus status;

    @Version
    @Column(name = "lock_version", nullable = false)
    private int lockVersion;

    @Column(name = "external_acceptance_on")
    private LocalDate externalAcceptanceOn;

    @Column(name = "external_acceptance_notes", length = 1000)
    private String externalAcceptanceNotes;

    public MeasurementVersionJpaEntity(UUID companyId, UUID measurementId, int versionNumber, UUID previousVersionId,
                                       MeasurementVersionStatus status) {
        this.companyId = companyId;
        this.measurementId = measurementId;
        this.versionNumber = versionNumber;
        this.previousVersionId = previousVersionId;
        this.status = status;
    }

    void updateLifecycle(MeasurementVersionStatus status, LocalDate externalAcceptanceOn, String externalAcceptanceNotes) {
        this.status = status;
        this.externalAcceptanceOn = externalAcceptanceOn;
        this.externalAcceptanceNotes = externalAcceptanceNotes;
    }
}
