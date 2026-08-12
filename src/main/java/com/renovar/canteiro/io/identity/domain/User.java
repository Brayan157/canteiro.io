package com.renovar.canteiro.io.identity.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class User {

    private final UUID id;
    private final String email;
    private final UserType userType;
    private UserStatus status;
    private String passwordHash;
    private Instant passwordChangedAt;
    private Instant activatedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private User(
            UUID id,
            String email,
            UserType userType,
            UserStatus status,
            String passwordHash,
            Instant passwordChangedAt,
            Instant activatedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.email = email;
        this.userType = userType;
        this.status = status;
        this.passwordHash = passwordHash;
        this.passwordChangedAt = passwordChangedAt;
        this.activatedAt = activatedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static User create(String email, UserType userType) {
        return new User(null, email, userType, UserStatus.PENDING_ACTIVATION, null, null, null, null, null);
    }

    public static User rehydrate(
            UUID id,
            String email,
            UserType userType,
            UserStatus status,
            String passwordHash,
            Instant passwordChangedAt,
            Instant activatedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new User(id, email, userType, status, passwordHash, passwordChangedAt, activatedAt, createdAt, updatedAt);
    }

    public void activate(String passwordHash, Instant activatedAt) {
        if (status != UserStatus.PENDING_ACTIVATION) {
            throw new IllegalStateException("Only pending users can be activated");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash is required for activation");
        }

        this.passwordHash = passwordHash;
        this.passwordChangedAt = activatedAt;
        this.activatedAt = activatedAt;
        this.status = UserStatus.ACTIVE;
    }

    public void changePassword(String passwordHash, Instant changedAt) {
        if (status != UserStatus.ACTIVE) {
            throw new IllegalStateException("Only active users can change their password");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash is required");
        }

        this.passwordHash = passwordHash;
        this.passwordChangedAt = changedAt;
    }

    public void deactivate() {
        if (status != UserStatus.ACTIVE && status != UserStatus.PENDING_ACTIVATION) {
            throw new IllegalStateException("Only active or pending users can be deactivated");
        }
        this.status = UserStatus.INACTIVE;
    }
}
