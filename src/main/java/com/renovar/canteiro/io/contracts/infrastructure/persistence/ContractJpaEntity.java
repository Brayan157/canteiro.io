package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "contract")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "work_id", nullable = false, updatable = false)
    private UUID workId;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContractStatus status;

    @Column(name = "started_on")
    private LocalDate startedOn;

    @Column(name = "expected_completion_on")
    private LocalDate expectedCompletionOn;

    @Column(name = "completed_on")
    private LocalDate completedOn;

    public ContractJpaEntity(UUID companyId, UUID workId, String reference, String name, ContractStatus status,
                             LocalDate startedOn, LocalDate expectedCompletionOn, LocalDate completedOn) {
        this.companyId = companyId;
        this.workId = workId;
        this.reference = reference;
        this.name = name;
        this.status = status;
        this.startedOn = startedOn;
        this.expectedCompletionOn = expectedCompletionOn;
        this.completedOn = completedOn;
    }
}
