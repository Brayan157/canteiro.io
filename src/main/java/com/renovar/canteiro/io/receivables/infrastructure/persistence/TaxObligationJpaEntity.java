package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.TaxObligationStatus;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
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
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "tax_obligation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class TaxObligationJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;
    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;
    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;
    @Column(name = "receivable_id", updatable = false)
    private UUID receivableId;
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    @Column(name = "description", length = 1000)
    private String description;
    @Column(name = "due_on")
    private LocalDate dueOn;
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(name = "treatment", nullable = false, length = 30)
    private TaxTreatment treatment;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TaxObligationStatus status;

    TaxObligationJpaEntity(UUID companyId, UUID invoiceId, UUID contractId, UUID receivableId, String name,
                           String description, LocalDate dueOn, BigDecimal amount, TaxTreatment treatment,
                           TaxObligationStatus status) {
        this.companyId = companyId;
        this.invoiceId = invoiceId;
        this.contractId = contractId;
        this.receivableId = receivableId;
        this.name = name;
        this.description = description;
        this.dueOn = dueOn;
        this.amount = amount;
        this.treatment = treatment;
        this.status = status;
    }

    void setStatus(TaxObligationStatus status) { this.status = status; }
}
