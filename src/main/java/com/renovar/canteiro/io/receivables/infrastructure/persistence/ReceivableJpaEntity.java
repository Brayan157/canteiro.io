package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "receivable")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ReceivableJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "billing_installment_id", nullable = false, updatable = false)
    private UUID billingInstallmentId;

    @Column(name = "renegotiation_source_receivable_id", updatable = false)
    private UUID renegotiationSourceReceivableId;

    void linkRenegotiationOrigin(UUID source) { this.renegotiationSourceReceivableId=source; }

    @Column(name = "financial_group_id", nullable = false, updatable = false)
    private UUID financialGroupId;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(name = "original_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal originalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReceivableStatus status;

    @Version
    @Column(name = "settlement_version", nullable = false)
    private Long settlementVersion;

    ReceivableJpaEntity(UUID companyId, UUID contractId, UUID billingInstallmentId, UUID financialGroupId,
                        String reference, String description, LocalDate issuedOn, LocalDate dueOn,
                        BigDecimal originalAmount, ReceivableStatus status) {
        this.companyId = companyId;
        this.contractId = contractId;
        this.billingInstallmentId = billingInstallmentId;
        this.financialGroupId = financialGroupId;
        this.reference = reference;
        this.description = description;
        this.issuedOn = issuedOn;
        this.dueOn = dueOn;
        this.originalAmount = originalAmount;
        this.status = status;
    }

    void setStatus(ReceivableStatus status) {
        this.status = status;
    }
}
