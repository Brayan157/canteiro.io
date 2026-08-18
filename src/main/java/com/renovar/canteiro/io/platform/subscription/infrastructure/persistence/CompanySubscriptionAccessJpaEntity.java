package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionAccessLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "company_subscription_access")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanySubscriptionAccessJpaEntity {

    @Id
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 30)
    private SubscriptionAccessLevel accessLevel;

    @Column(name = "restriction_charge_id")
    private UUID restrictionChargeId;

    @Column(name = "effective_on", nullable = false)
    private LocalDate effectiveOn;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CompanySubscriptionAccessJpaEntity(
            UUID companyId,
            SubscriptionAccessLevel accessLevel,
            UUID restrictionChargeId,
            LocalDate effectiveOn
    ) {
        this.companyId = companyId;
        this.accessLevel = accessLevel;
        this.restrictionChargeId = restrictionChargeId;
        this.effectiveOn = effectiveOn;
    }

    public void update(
            SubscriptionAccessLevel accessLevel,
            UUID restrictionChargeId,
            LocalDate effectiveOn
    ) {
        this.accessLevel = accessLevel;
        this.restrictionChargeId = restrictionChargeId;
        this.effectiveOn = effectiveOn;
    }
}
