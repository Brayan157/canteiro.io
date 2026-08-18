package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Immutable
@Table(name = "trust_unlock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrustUnlockJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "charge_id", nullable = false, updatable = false)
    private UUID chargeId;

    @Column(name = "granted_by_user_id", nullable = false, updatable = false)
    private UUID grantedByUserId;

    @Column(name = "reason", nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    public TrustUnlockJpaEntity(
            UUID companyId,
            UUID chargeId,
            UUID grantedByUserId,
            String reason,
            Instant startsAt,
            Instant expiresAt
    ) {
        this.companyId = companyId;
        this.chargeId = chargeId;
        this.grantedByUserId = grantedByUserId;
        this.reason = reason;
        this.startsAt = startsAt;
        this.expiresAt = expiresAt;
    }
}
