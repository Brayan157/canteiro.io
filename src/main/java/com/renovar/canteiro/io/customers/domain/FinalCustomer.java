package com.renovar.canteiro.io.customers.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class FinalCustomer {

    private final UUID id;
    private final UUID companyId;
    private final FinalCustomerType customerType;
    private String name;
    private final String document;
    private boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private FinalCustomer(
            UUID id,
            UUID companyId,
            FinalCustomerType customerType,
            String name,
            String document,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.companyId = requireCompanyId(companyId);
        this.customerType = requireCustomerType(customerType);
        this.name = requireName(name);
        this.document = requireDocument(document, customerType);
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static FinalCustomer create(UUID companyId, FinalCustomerType customerType, String name, String document) {
        return new FinalCustomer(null, companyId, customerType, name, document, true, null, null);
    }

    public static FinalCustomer rehydrate(
            UUID id,
            UUID companyId,
            FinalCustomerType customerType,
            String name,
            String document,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new FinalCustomer(id, companyId, customerType, name, document, active, createdAt, updatedAt);
    }

    public void rename(String name) {
        this.name = requireName(name);
    }

    public void deactivate() {
        active = false;
    }

    private static UUID requireCompanyId(UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Final customer company is required");
        }
        return companyId;
    }

    private static FinalCustomerType requireCustomerType(FinalCustomerType customerType) {
        if (customerType == null) {
            throw new IllegalArgumentException("Final customer type is required");
        }
        return customerType;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Final customer name is required");
        }
        return name.trim();
    }

    private static String requireDocument(String document, FinalCustomerType customerType) {
        if (document == null) {
            throw new IllegalArgumentException("Final customer document is required");
        }
        String normalized = document.replaceAll("\\D", "");
        int expectedLength = customerType == FinalCustomerType.INDIVIDUAL ? 11 : 14;
        if (normalized.length() != expectedLength) {
            throw new IllegalArgumentException("Final customer document does not match customer type");
        }
        return normalized;
    }
}
