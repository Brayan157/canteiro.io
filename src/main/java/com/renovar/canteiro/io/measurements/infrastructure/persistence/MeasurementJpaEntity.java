package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "measurement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasurementJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "work_id", nullable = false, updatable = false)
    private UUID workId;

    @Column(name = "contract_id", updatable = false)
    private UUID contractId;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "measured_on")
    private LocalDate measuredOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MeasurementStatus status;

    @Version
    @Column(name = "lock_version", nullable = false)
    private int lockVersion;

    public MeasurementJpaEntity(UUID companyId, UUID workId, UUID contractId, String reference, String description,
                                LocalDate measuredOn, MeasurementStatus status) {
        this.companyId = companyId;
        this.workId = workId;
        this.contractId = contractId;
        this.reference = reference;
        this.description = description;
        this.measuredOn = measuredOn;
        this.status = status;
    }

    void updateStatus(MeasurementStatus status) {
        this.status = status;
    }
}
