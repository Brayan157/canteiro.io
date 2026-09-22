package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.BillingInstallmentStatus;
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
@Table(name = "billing_installment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class BillingInstallmentJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillingInstallmentStatus status;

    BillingInstallmentJpaEntity(UUID companyId, UUID contractId, String reference, String description,
                                LocalDate issuedOn, LocalDate dueOn, BillingInstallmentStatus status) {
        this.companyId = companyId;
        this.contractId = contractId;
        this.reference = reference;
        this.description = description;
        this.issuedOn = issuedOn;
        this.dueOn = dueOn;
        this.status = status;
    }

    void setStatus(BillingInstallmentStatus status) { this.status = status; }
}
