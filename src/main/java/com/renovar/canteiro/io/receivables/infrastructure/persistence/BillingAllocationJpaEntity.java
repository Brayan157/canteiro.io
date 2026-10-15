package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@Table(name = "billing_allocation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class BillingAllocationJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "billing_installment_id", nullable = false, updatable = false)
    private UUID billingInstallmentId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "contract_service_id", updatable = false)
    private UUID contractServiceId;

    @Column(name = "measurement_contract_composition_id", updatable = false)
    private UUID measurementContractCompositionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_type", nullable = false, length = 30)
    private BillingAllocationType type;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal allocatedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BillingAllocationStatus status;

    BillingAllocationJpaEntity(UUID companyId, UUID billingInstallmentId, UUID contractId, UUID contractServiceId,
                               UUID measurementContractCompositionId,
                               BillingAllocationType type, BigDecimal allocatedAmount, BillingAllocationStatus status) {
        this.companyId = companyId;
        this.billingInstallmentId = billingInstallmentId;
        this.contractId = contractId;
        this.contractServiceId = contractServiceId;
        this.measurementContractCompositionId = measurementContractCompositionId;
        this.type = type;
        this.allocatedAmount = allocatedAmount;
        this.status = status;
    }

    void setStatus(BillingAllocationStatus status) { this.status = status; }
}
