package com.renovar.canteiro.io.identity.infrastructure.persistence;

import com.renovar.canteiro.io.identity.domain.UserStatus;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "app_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserJpaEntity extends BaseJpaEntity {

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20)
    private UserType userType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    public UserJpaEntity(String email, UserType userType, UserStatus status) {
        this.email = email;
        this.userType = userType;
        this.status = status;
    }

    public void activate(String passwordHash, Instant activatedAt) {
        updateCredentials(passwordHash, activatedAt, activatedAt, UserStatus.ACTIVE);
    }

    public void updateCredentials(
            String passwordHash,
            Instant passwordChangedAt,
            Instant activatedAt,
            UserStatus status
    ) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = passwordChangedAt;
        this.activatedAt = activatedAt;
        this.status = status;
    }

    public void updateStatus(UserStatus status) {
        this.status = status;
    }
}
