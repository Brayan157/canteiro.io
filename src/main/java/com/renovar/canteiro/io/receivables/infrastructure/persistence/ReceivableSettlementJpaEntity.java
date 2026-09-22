package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementEntryType;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementStatus;
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
@Table(name = "receivable_settlement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ReceivableSettlementJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "receivable_id", nullable = false, updatable = false)
    private UUID receivableId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "received_on", nullable = false, updatable = false)
    private LocalDate receivedOn;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20, updatable = false)
    private ReceivableSettlementEntryType entryType;

    @Column(name = "reversal_of_settlement_id", updatable = false)
    private UUID reversalOfSettlementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30, updatable = false)
    private ReceivableSettlementPaymentMethod paymentMethod;

    @Column(name = "note", length = 1000, updatable = false)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReceivableSettlementStatus status;

    ReceivableSettlementJpaEntity(UUID companyId, UUID receivableId, UUID contractId, LocalDate receivedOn,
                                  BigDecimal amount, ReceivableSettlementEntryType entryType, UUID reversalOfSettlementId,
                                  ReceivableSettlementPaymentMethod paymentMethod, String note,
                                  ReceivableSettlementStatus status) {
        this.companyId = companyId;
        this.receivableId = receivableId;
        this.contractId = contractId;
        this.receivedOn = receivedOn;
        this.amount = amount;
        this.entryType = entryType;
        this.reversalOfSettlementId = reversalOfSettlementId;
        this.paymentMethod = paymentMethod;
        this.note = note;
        this.status = status;
    }
}
