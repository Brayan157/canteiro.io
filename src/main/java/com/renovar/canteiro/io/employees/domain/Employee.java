package com.renovar.canteiro.io.employees.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class Employee {

    private final UUID id;
    private final UUID companyId;
    private final String fullName;
    private final String jobTitle;
    private final String phone;
    private final boolean active;
    private UUID userId;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Employee(UUID id, UUID companyId, String fullName, String jobTitle, String phone, boolean active,
                     UUID userId, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Employee company is required");
        this.fullName = requireText(fullName, "Employee full name is required");
        this.jobTitle = normalize(jobTitle);
        this.phone = normalize(phone);
        this.active = active;
        this.userId = userId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Employee create(UUID companyId, String fullName, String jobTitle, String phone) {
        return new Employee(null, companyId, fullName, jobTitle, phone, true, null, null, null);
    }

    public static Employee rehydrate(UUID id, UUID companyId, String fullName, String jobTitle, String phone,
                                     boolean active, UUID userId, Instant createdAt, Instant updatedAt) {
        return new Employee(id, companyId, fullName, jobTitle, phone, active, userId, createdAt, updatedAt);
    }

    public void linkUser(UUID userId) {
        require(userId, "Employee user is required");
        if (!active) {
            throw new IllegalStateException("An inactive employee cannot receive system access");
        }
        if (this.userId != null) {
            throw new IllegalStateException("Employee already has a system user");
        }
        this.userId = userId;
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
