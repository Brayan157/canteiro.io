package com.renovar.canteiro.io.contracts.infrastructure.persistence;

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
@Table(name = "contract_revision")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ContractRevisionJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "revision_number", nullable = false, updatable = false)
    private int revisionNumber;

    @Column(name = "previous_net_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal previousNetAmount;

    @Column(name = "proposed_net_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal proposedNetAmount;

    @Column(name = "approved_billed_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal approvedBilledAmount;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    ContractRevisionJpaEntity(UUID companyId, UUID contractId, int revisionNumber, BigDecimal previousNetAmount,
                              BigDecimal proposedNetAmount, BigDecimal approvedBilledAmount, String reason) {
        this.companyId = companyId;
        this.contractId = contractId;
        this.revisionNumber = revisionNumber;
        this.previousNetAmount = previousNetAmount;
        this.proposedNetAmount = proposedNetAmount;
        this.approvedBilledAmount = approvedBilledAmount;
        this.reason = reason;
    }
}
