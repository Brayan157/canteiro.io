package com.renovar.canteiro.io.customers.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class FinalCustomerContact {

    private final UUID id;
    private final UUID companyId;
    private final UUID finalCustomerId;
    private final String name;
    private final String email;
    private final String phone;
    private final boolean primaryContact;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private FinalCustomerContact(UUID id, UUID companyId, UUID finalCustomerId, String name, String email, String phone,
                                 boolean primaryContact, boolean active, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Final customer contact company is required");
        this.finalCustomerId = require(finalCustomerId, "Final customer contact customer is required");
        this.name = requireText(name, "Final customer contact name is required");
        this.email = normalize(email);
        this.phone = normalize(phone);
        if (this.email == null && this.phone == null) {
            throw new IllegalArgumentException("Final customer contact requires email or phone");
        }
        this.primaryContact = primaryContact;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static FinalCustomerContact create(UUID companyId, UUID finalCustomerId, String name, String email, String phone,
                                              boolean primaryContact) {
        return new FinalCustomerContact(null, companyId, finalCustomerId, name, email, phone, primaryContact, true, null, null);
    }

    public static FinalCustomerContact rehydrate(UUID id, UUID companyId, UUID finalCustomerId, String name, String email,
                                                 String phone, boolean primaryContact, boolean active, Instant createdAt,
                                                 Instant updatedAt) {
        return new FinalCustomerContact(id, companyId, finalCustomerId, name, email, phone, primaryContact, active, createdAt, updatedAt);
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
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
}
