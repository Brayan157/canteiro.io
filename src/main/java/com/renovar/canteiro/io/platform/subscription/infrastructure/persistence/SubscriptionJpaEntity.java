package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionStatus;
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
@Table(name = "subscription")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SubscriptionStatus status;

    @Column(name = "quoted_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal quotedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_source", nullable = false, length = 30, updatable = false)
    private CatalogPricingSource pricingSource;

    @Column(name = "plan_bundle_id", updatable = false)
    private UUID planBundleId;

    @Column(name = "pricing_effective_date", nullable = false, updatable = false)
    private LocalDate pricingEffectiveDate;

    public SubscriptionJpaEntity(
            UUID companyId,
            SubscriptionStatus status,
            BigDecimal quotedAmount,
            CatalogPricingSource pricingSource,
            UUID planBundleId,
            LocalDate pricingEffectiveDate
    ) {
        this.companyId = companyId;
        this.status = status;
        this.quotedAmount = quotedAmount;
        this.pricingSource = pricingSource;
        this.planBundleId = planBundleId;
        this.pricingEffectiveDate = pricingEffectiveDate;
    }
}
