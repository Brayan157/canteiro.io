package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "subscription_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionItemJpaEntity extends BaseJpaEntity {

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "plan_code", nullable = false, length = 50, updatable = false)
    private String planCode;

    @Column(name = "plan_name", nullable = false, length = 100, updatable = false)
    private String planName;

    public SubscriptionItemJpaEntity(UUID subscriptionId, UUID planId, String planCode, String planName) {
        this.subscriptionId = subscriptionId;
        this.planId = planId;
        this.planCode = planCode;
        this.planName = planName;
    }
}
