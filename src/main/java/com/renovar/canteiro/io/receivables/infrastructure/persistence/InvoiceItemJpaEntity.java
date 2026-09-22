package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.InvoiceItemStatus;
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
@Table(name = "invoice_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class InvoiceItemJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "billing_allocation_id", nullable = false, updatable = false)
    private UUID billingAllocationId;

    @Column(name = "contract_service_id", updatable = false)
    private UUID contractServiceId;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal allocatedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceItemStatus status;

    InvoiceItemJpaEntity(UUID companyId, UUID invoiceId, UUID contractId, UUID billingAllocationId,
                         UUID contractServiceId, String description, BigDecimal allocatedAmount, InvoiceItemStatus status) {
        this.companyId = companyId;
        this.invoiceId = invoiceId;
        this.contractId = contractId;
        this.billingAllocationId = billingAllocationId;
        this.contractServiceId = contractServiceId;
        this.description = description;
        this.allocatedAmount = allocatedAmount;
        this.status = status;
    }

    void setStatus(InvoiceItemStatus status) { this.status = status; }
}
