package com.renovar.canteiro.io.customers.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Getter
public final class FinalCustomerAddress {

    private final UUID id;
    private final UUID companyId;
    private final UUID finalCustomerId;
    private final String label;
    private final String postalCode;
    private final String street;
    private final String number;
    private final String complement;
    private final String district;
    private final String city;
    private final String state;
    private final String country;
    private final boolean primaryAddress;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private FinalCustomerAddress(UUID id, UUID companyId, UUID finalCustomerId, String label, String postalCode,
                                 String street, String number, String complement, String district, String city,
                                 String state, String country, boolean primaryAddress, boolean active, Instant createdAt,
                                 Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Final customer address company is required");
        this.finalCustomerId = require(finalCustomerId, "Final customer address customer is required");
        this.label = normalize(label);
        this.postalCode = normalizePostalCode(postalCode);
        this.street = requireText(street, "Final customer address street is required");
        this.number = normalize(number);
        this.complement = normalize(complement);
        this.district = normalize(district);
        this.city = requireText(city, "Final customer address city is required");
        this.state = requireCode(state, "Final customer address state must use two letters");
        this.country = requireCode(country == null ? "BR" : country, "Final customer address country must use two letters");
        this.primaryAddress = primaryAddress;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static FinalCustomerAddress create(UUID companyId, UUID finalCustomerId, String label, String postalCode,
                                              String street, String number, String complement, String district, String city,
                                              String state, String country, boolean primaryAddress) {
        return new FinalCustomerAddress(null, companyId, finalCustomerId, label, postalCode, street, number, complement,
                district, city, state, country, primaryAddress, true, null, null);
    }

    public static FinalCustomerAddress rehydrate(UUID id, UUID companyId, UUID finalCustomerId, String label,
                                                 String postalCode, String street, String number, String complement,
                                                 String district, String city, String state, String country,
                                                 boolean primaryAddress, boolean active, Instant createdAt, Instant updatedAt) {
        return new FinalCustomerAddress(id, companyId, finalCustomerId, label, postalCode, street, number, complement,
                district, city, state, country, primaryAddress, active, createdAt, updatedAt);
    }

    private static String normalizePostalCode(String postalCode) {
        if (postalCode == null || postalCode.isBlank()) {
            return null;
        }
        String normalized = postalCode.replaceAll("\\D", "");
        if (normalized.length() != 8) {
            throw new IllegalArgumentException("Final customer address postal code must contain eight digits");
        }
        return normalized;
    }

    private static String requireCode(String value, String message) {
        String normalized = requireText(value, message).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
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
