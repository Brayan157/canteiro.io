package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
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
@Table(name = "invoice")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class InvoiceJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "number", nullable = false, length = 100)
    private String number;

    @Column(name = "series", length = 50)
    private String series;

    @Column(name = "access_key", length = 100)
    private String accessKey;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InvoiceStatus status;

    InvoiceJpaEntity(UUID companyId, UUID contractId, String number, String series, String accessKey,
                     LocalDate issuedOn, BigDecimal totalAmount, InvoiceStatus status) {
        this.companyId = companyId;
        this.contractId = contractId;
        this.number = number;
        this.series = series;
        this.accessKey = accessKey;
        this.issuedOn = issuedOn;
        this.totalAmount = totalAmount;
        this.status = status;
    }

    void setStatus(InvoiceStatus status) { this.status = status; }
}
