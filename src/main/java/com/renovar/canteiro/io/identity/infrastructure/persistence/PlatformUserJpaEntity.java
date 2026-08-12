package com.renovar.canteiro.io.identity.infrastructure.persistence;

import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "platform_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformUserJpaEntity extends BaseJpaEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "global_role", nullable = false)
    private PlatformUserRole globalRole;

    public PlatformUserJpaEntity(UUID userId, PlatformUserRole globalRole) {
        this.userId = userId;
        this.globalRole = globalRole;
    }
}
