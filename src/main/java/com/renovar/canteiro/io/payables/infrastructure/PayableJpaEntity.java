package com.renovar.canteiro.io.payables.infrastructure;

import com.renovar.canteiro.io.payables.domain.PayableFinancialScope;
import com.renovar.canteiro.io.payables.domain.PayableStatus;
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
@Table(name = "payable")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PayableJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false) private UUID companyId;
    @Enumerated(EnumType.STRING) @Column(name = "financial_scope", nullable = false, length = 20) private PayableFinancialScope financialScope;
    @Column(name = "work_id", updatable = false) private UUID workId;
    @Column(name = "contract_id", updatable = false) private UUID contractId;
    @Column(name = "tax_obligation_id", updatable = false) private UUID taxObligationId;
    @Column(name = "reference", length = 100) private String reference;
    @Column(name = "description", length = 1000) private String description;
    @Column(name = "due_on", nullable = false) private LocalDate dueOn;
    @Column(name = "original_amount", nullable = false, precision = 19, scale = 2) private BigDecimal originalAmount;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 30) private PayableStatus status;

    PayableJpaEntity(UUID companyId, PayableFinancialScope financialScope, UUID workId, UUID contractId,
                     UUID taxObligationId, String reference, String description, LocalDate dueOn,
                     BigDecimal originalAmount, PayableStatus status) {
        this.companyId = companyId;
        this.financialScope = financialScope;
        this.workId = workId;
        this.contractId = contractId;
        this.taxObligationId = taxObligationId;
        this.reference = reference;
        this.description = description;
        this.dueOn = dueOn;
        this.originalAmount = originalAmount;
        this.status = status;
    }

    void setStatus(PayableStatus status) { this.status = status; }
}
