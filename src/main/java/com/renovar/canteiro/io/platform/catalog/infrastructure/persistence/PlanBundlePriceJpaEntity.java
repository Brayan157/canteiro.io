package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "plan_bundle_price")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanBundlePriceJpaEntity extends BaseJpaEntity {

    @Column(name = "plan_bundle_id", nullable = false, updatable = false)
    private UUID planBundleId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    public PlanBundlePriceJpaEntity(UUID planBundleId, BigDecimal amount, LocalDate validFrom, LocalDate validUntil) {
        this.planBundleId = planBundleId;
        this.amount = amount;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    public void endOn(LocalDate validUntil) {
        this.validUntil = validUntil;
    }
}
