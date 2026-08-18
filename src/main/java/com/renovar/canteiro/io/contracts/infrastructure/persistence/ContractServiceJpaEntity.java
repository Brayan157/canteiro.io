package com.renovar.canteiro.io.contracts.infrastructure.persistence;

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
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.DiscountType;

@Getter
@Entity
@Table(name = "contract_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ContractServiceJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "service_template_id", updatable = false)
    private UUID serviceTemplateId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContractServiceStatus status;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal grossAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", precision = 19, scale = 4)
    private BigDecimal discountValue;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal netAmount;

    ContractServiceJpaEntity(UUID companyId, UUID contractId, UUID serviceTemplateId, String name, String description,
                             ContractServiceStatus status, BigDecimal quantity, BigDecimal unitPrice, BigDecimal grossAmount,
                             DiscountType discountType, BigDecimal discountValue, BigDecimal discountAmount, BigDecimal netAmount) {
        this.companyId = companyId;
        this.contractId = contractId;
        this.serviceTemplateId = serviceTemplateId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.grossAmount = grossAmount;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.discountAmount = discountAmount;
        this.netAmount = netAmount;
    }
}
